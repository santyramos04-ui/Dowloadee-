package com.santyramos.mirador.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Servidor web mínimo dentro del celular para que el televisor pueda leer un archivo descargado.
 * Sirve UN solo archivo, en una dirección secreta, y acepta saltos (Range) para adelantar.
 */
class ServidorLocal(context: Context, private val uri: Uri, private val mime: String) {
    private val ctx = context.applicationContext
    private val clave = UUID.randomUUID().toString().replace("-", "").take(16)
    private var socket: ServerSocket? = null
    private val hilos = Executors.newCachedThreadPool()

    /** Arranca y devuelve la dirección que hay que pasarle al televisor (o null si no hay Wi-Fi). */
    fun iniciar(): String? {
        val ip = ipLocal(ctx) ?: return null
        val s = ServerSocket(0)
        socket = s
        thread(isDaemon = true, name = "mirador-tv") {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                hilos.execute { runCatching { atender(c) } }
            }
        }
        val ext = when {
            mime.contains("mp4") -> "mp4"; mime.contains("webm") -> "webm"; mime.contains("mpeg") -> "mp3"; mime.contains("matroska") -> "mkv"; else -> "bin"
        }
        return "http://$ip:${s.localPort}/$clave/medio.$ext"
    }

    fun detener() {
        runCatching { socket?.close() }
        hilos.shutdownNow()
    }

    private fun tamano(): Long = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L

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
        val (metodo, ruta) = primera.split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val cabeceras = HashMap<String, String>()
        while (true) {
            val l = leerLinea(entrada) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':'); if (i > 0) cabeceras[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val out = c.getOutputStream()
        if (!ruta.startsWith("/$clave/")) { responder(out, "404 Not Found", emptyMap()); return }
        val total = tamano()
        val rango = RangoHttp.leer(cabeceras["range"], total)
        val base = mapOf(
            "Content-Type" to mime, "Accept-Ranges" to "bytes", "Connection" to "close",
            "transferMode.dlna.org" to "Streaming",
            "contentFeatures.dlna.org" to "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000",
        )
        if (rango == null || total < 0) {
            responder(out, "200 OK", base + (if (total >= 0) mapOf("Content-Length" to total.toString()) else emptyMap()))
            if (metodo != "HEAD") ctx.contentResolver.openInputStream(uri)?.use { it.copyTo(out, 64 * 1024) }
        } else {
            val (ini, fin) = rango
            responder(out, "206 Partial Content", base + mapOf("Content-Length" to (fin - ini + 1).toString(), "Content-Range" to "bytes $ini-$fin/$total"))
            if (metodo != "HEAD") ctx.contentResolver.openInputStream(uri)?.use { inp ->
                var saltar = ini
                while (saltar > 0) { val n = inp.skip(saltar); if (n <= 0) break; saltar -= n }
                var quedan = fin - ini + 1
                val buf = ByteArray(64 * 1024)
                while (quedan > 0) {
                    val n = inp.read(buf, 0, minOf(buf.size.toLong(), quedan).toInt()); if (n < 0) break
                    out.write(buf, 0, n); quedan -= n
                }
            }
        }
        out.flush()
    } }

    private fun responder(out: OutputStream, estado: String, cabeceras: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 $estado\r\n")
        cabeceras.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray())
    }

    companion object {
        /** IP del celular en el Wi-Fi (la que el televisor puede alcanzar). */
        fun ipLocal(context: Context): String? {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
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
