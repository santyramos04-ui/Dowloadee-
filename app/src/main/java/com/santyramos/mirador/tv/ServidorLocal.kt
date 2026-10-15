package com.santyramos.mirador.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import com.santyramos.mirador.net.Http
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Lo que el servidor del celular le entrega al televisor. */
sealed interface Recurso {
    val mime: String
    /** Un archivo descargado (content://). */
    data class Archivo(val uri: Uri, override val mime: String) : Recurso
    /** Un video de internet que se lee por partes (YouTube corta las lecturas largas de una sola vez). */
    data class Remoto(val url: String, val agente: String, val total: Long, override val mime: String) : Recurso
    /** Imagen + sonido de YouTube unidos al vuelo con ffmpeg en un solo video (MPEG-TS) que cualquier TV entiende. */
    data class Mezcla(val video: String, val audio: String, val desdeSeg: Long) : Recurso { override val mime = "video/mp2t" }
}

/**
 * Servidor web mínimo dentro del celular para el televisor (DLNA). Cada recurso tiene una dirección
 * secreta; acepta saltos (Range) donde se puede.
 */
class ServidorLocal(context: Context) {
    private val ctx = context.applicationContext
    private val clave = UUID.randomUUID().toString().replace("-", "").take(16)
    private var socket: ServerSocket? = null
    private var base: String? = null
    private val hilos = Executors.newCachedThreadPool()
    private val recursos = ConcurrentHashMap<String, Recurso>()
    private val procesos = ConcurrentHashMap.newKeySet<Process>()

    /** Arranca (si hace falta) y devuelve la dirección base, o null si no hay Wi-Fi. */
    fun iniciar(): String? {
        base?.let { return it }
        val ip = ipLocal(ctx) ?: return null
        val s = ServerSocket(0)
        socket = s
        thread(isDaemon = true, name = "mirador-tv") {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                hilos.execute { runCatching { atender(c) } }
            }
        }
        return "http://$ip:${s.localPort}/$clave".also { base = it }
    }

    /** Dirección para ffmpeg dentro del mismo celular. */
    private fun interna(nombre: String) = "http://127.0.0.1:${socket!!.localPort}/$clave/$nombre"

    /** Publica un recurso y devuelve su dirección (para el televisor). */
    fun publicar(nombre: String, r: Recurso): String {
        recursos[nombre] = r
        return "${base ?: error("Servidor sin iniciar")}/$nombre"
    }

    /** Publica imagen y sonido por separado y la mezcla que el televisor va a pedir. */
    fun publicarMezcla(video: Recurso.Remoto, audio: Recurso.Remoto, desdeSeg: Long): String {
        recursos["v"] = video
        recursos["a"] = audio
        return publicar("tv.ts", Recurso.Mezcla(interna("v"), interna("a"), desdeSeg))
    }

    fun detener() {
        procesos.forEach { runCatching { it.destroy() } }
        runCatching { socket?.close() }
        hilos.shutdownNow()
    }

    private fun leerLinea(e: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = e.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    private fun atender(c: Socket) { c.use {
        val entrada = BufferedInputStream(c.getInputStream())
        val primera = leerLinea(entrada) ?: return
        val partes = primera.split(" ")
        val metodo = partes.getOrNull(0).orEmpty()
        val ruta = partes.getOrNull(1).orEmpty().substringBefore('?')
        val cabeceras = HashMap<String, String>()
        while (true) {
            val l = leerLinea(entrada) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':'); if (i > 0) cabeceras[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val out = c.getOutputStream()
        val recurso = if (ruta.startsWith("/$clave/")) recursos[ruta.removePrefix("/$clave/")] else null
        if (recurso == null) { responder(out, "404 Not Found", mapOf("Content-Length" to "0")); return }
        val cuerpo = metodo != "HEAD"
        when (recurso) {
            is Recurso.Archivo -> servirRango(out, cabeceras["range"], tamanoArchivo(recurso.uri), recurso.mime, cuerpo) { ini, largo ->
                ctx.contentResolver.openInputStream(recurso.uri)?.use { inp -> saltar(inp, ini); copiar(inp, out, largo) }
            }
            is Recurso.Remoto -> servirRango(out, cabeceras["range"], recurso.total, recurso.mime, cuerpo) { ini, largo ->
                leerRemoto(recurso, ini, largo, out)
            }
            is Recurso.Mezcla -> {
                responder(out, "200 OK", comunes(recurso.mime, buscable = false))
                if (cuerpo) mezclar(recurso, out)
            }
        }
        out.flush()
    } }

    private fun comunes(mime: String, buscable: Boolean) = mapOf(
        "Content-Type" to mime, "Connection" to "close",
        "transferMode.dlna.org" to "Streaming",
        "contentFeatures.dlna.org" to "DLNA.ORG_OP=${if (buscable) "01" else "00"};DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
    ) + if (buscable) mapOf("Accept-Ranges" to "bytes") else emptyMap()

    private fun servirRango(out: OutputStream, range: String?, total: Long, mime: String, cuerpo: Boolean, enviar: (Long, Long) -> Unit) {
        val rango = RangoHttp.leer(range, total)
        if (rango == null || total <= 0) {
            responder(out, "200 OK", comunes(mime, total > 0) + (if (total > 0) mapOf("Content-Length" to total.toString()) else emptyMap()))
            if (cuerpo) enviar(0, if (total > 0) total else Long.MAX_VALUE)
        } else {
            val (ini, fin) = rango
            responder(out, "206 Partial Content", comunes(mime, true) + mapOf("Content-Length" to (fin - ini + 1).toString(), "Content-Range" to "bytes $ini-$fin/$total"))
            if (cuerpo) enviar(ini, fin - ini + 1)
        }
    }

    private fun tamanoArchivo(uri: Uri): Long = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L

    private fun saltar(inp: InputStream, n: Long) { var q = n; while (q > 0) { val s = inp.skip(q); if (s <= 0) break; q -= s } }

    private fun copiar(inp: InputStream, out: OutputStream, largo: Long) {
        var quedan = largo
        val buf = ByteArray(64 * 1024)
        while (quedan > 0) {
            val n = inp.read(buf, 0, minOf(buf.size.toLong(), quedan).toInt()); if (n < 0) break
            out.write(buf, 0, n); quedan -= n
        }
    }

    /** Lee de YouTube en trozos de 4 MB (con «range» en la dirección), como hace su propio reproductor. */
    private fun leerRemoto(r: Recurso.Remoto, ini: Long, largo: Long, out: OutputStream) {
        var pos = ini
        val fin = ini + largo - 1
        val sep = if (r.url.contains('?')) "&" else "?"
        while (pos <= fin) {
            val hasta = minOf(pos + TROZO - 1, fin)
            val req = Request.Builder().url("${r.url}${sep}range=$pos-$hasta").header("User-Agent", r.agente).build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                resp.body.byteStream().use { copiar(it, out, hasta - pos + 1) }
            }
            pos = hasta + 1
        }
    }

    /** ffmpeg copia imagen y sonido (sin recomprimir: casi no gasta batería) a un solo video MPEG-TS. */
    private fun mezclar(m: Recurso.Mezcla, out: OutputStream) {
        val f = Ffmpeg.preparar(ctx) ?: throw IllegalStateException("ffmpeg no está listo")
        val desde = if (m.desdeSeg > 3) listOf("-ss", m.desdeSeg.toString()) else emptyList()
        val cmd = listOf(f.binario, "-nostdin", "-hide_banner", "-loglevel", "error") +
            desde + listOf("-i", m.video) + desde + listOf("-i", m.audio) +
            listOf("-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-f", "mpegts", "pipe:1")
        val p = ProcessBuilder(cmd).apply { environment().putAll(f.entorno) }.start()
        procesos += p
        thread(isDaemon = true) { runCatching { val b = ByteArray(4096); p.errorStream.use { e -> while (e.read(b) >= 0) Unit } } }
        try {
            p.inputStream.use { it.copyTo(out, 64 * 1024) }
        } finally {
            p.destroy(); procesos -= p
        }
    }

    private fun responder(out: OutputStream, estado: String, cabeceras: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 $estado\r\n")
        cabeceras.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray())
    }

    companion object {
        private const val TROZO = 4L * 1024 * 1024

        /** IP del celular en el Wi-Fi (la que el televisor puede alcanzar). */
        fun ipLocal(context: Context): String? {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
    }
}

/** El ffmpeg que trae youtubedl-android (ya descomprimido al iniciar el motor de descargas). */
object Ffmpeg {
    class Listo(val binario: String, val entorno: Map<String, String>)

    fun preparar(context: Context): Listo? {
        val bin = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!bin.exists()) return null
        val paquetes = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val libs = listOf("ffmpeg", "python").joinToString(":") { File(paquetes, "$it/usr/lib").absolutePath }
        return Listo(bin.absolutePath, mapOf("LD_LIBRARY_PATH" to libs, "TMPDIR" to context.cacheDir.absolutePath))
    }
}

/** Lee la cabecera «Range: bytes=inicio-fin». */
object RangoHttp {
    fun leer(cabecera: String?, total: Long): Pair<Long, Long>? {
        if (cabecera == null || total <= 0) return null
        val m = Regex("bytes=(\\d*)-(\\d*)").find(cabecera) ?: return null
        val a = m.groupValues[1]; val b = m.groupValues[2]
        val (ini, fin) = when {
            a.isEmpty() && b.isNotEmpty() -> (total - b.toLong()).coerceAtLeast(0) to total - 1 // los últimos N bytes
            a.isNotEmpty() -> a.toLong() to (b.toLongOrNull() ?: (total - 1)).coerceAtMost(total - 1)
            else -> return null
        }
        return if (ini in 0..fin) ini to fin else null
    }
}
