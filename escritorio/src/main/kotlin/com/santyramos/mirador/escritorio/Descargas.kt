package com.santyramos.mirador.escritorio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Formatos de descarga (los mismos del celular, en lo esencial). */
enum class Formato(val etiqueta: String, val detalle: String, val esAudio: Boolean) {
    MEJOR("Video · mejor calidad", "MP4, la mayor resolución disponible", false),
    P1080("Video · 1080p", "MP4 Full HD, buen equilibrio", false),
    P720("Video · 720p", "MP4 HD, archivo más liviano", false),
    MP3("Audio · MP3", "Funciona en cualquier reproductor", true),
    M4A("Audio · M4A", "Mejor calidad que MP3 con el mismo peso", true),
    OPUS("Audio · Opus", "El audio original de YouTube, sin convertir", true),
}

enum class Estado { EN_COLA, DESCARGANDO, PROCESANDO, LISTA, ERROR, CANCELADA }

data class Descarga(
    val id: Long, val url: String, val formato: Formato,
    val titulo: String = url, val estado: Estado = Estado.EN_COLA,
    val progreso: Float = 0f, val bajado: Long = 0, val total: Long = 0, val velocidad: Long = 0, val eta: Long = 0,
    val archivo: File? = null, val error: String? = null,
)

/** Lee las líneas de yt-dlp: progreso (plantilla propia), título, archivo final y errores. */
object SalidaYtdlp {
    const val PREFIJO = "[mirador]"
    const val PLANTILLA = "download:$PREFIJO%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s"

    data class Progreso(val bajado: Long, val total: Long, val velocidad: Long, val eta: Long) {
        val fraccion: Float get() = if (total > 0) (bajado.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    private fun num(s: String) = s.trim().takeIf { it != "NA" && it != "None" && it.isNotEmpty() }?.toDoubleOrNull()?.toLong()

    fun progreso(linea: String): Progreso? {
        val i = linea.indexOf(PREFIJO).takeIf { it >= 0 } ?: return null
        val p = linea.substring(i + PREFIJO.length).split('|')
        if (p.size < 5) return null
        val bajado = num(p[0]) ?: return null
        return Progreso(bajado, num(p[1]) ?: num(p[2]) ?: 0, num(p[3]) ?: 0, num(p[4]) ?: 0)
    }

    fun titulo(linea: String) = linea.substringAfter("[titulo]", "").takeIf { linea.contains("[titulo]") }?.trim()
    fun archivo(linea: String) = linea.substringAfter("[archivo]", "").takeIf { linea.contains("[archivo]") }?.trim()

    /** Errores de yt-dlp en palabras sencillas. */
    fun mensajeError(lineas: List<String>): String {
        val e = lineas.lastOrNull { it.startsWith("ERROR") } ?: lineas.lastOrNull().orEmpty()
        val t = e.lowercase()
        return when {
            "unsupported url" in t -> "Ese enlace no es de un sitio compatible."
            "private" in t -> "El video es privado."
            "sign in" in t || "login" in t || "age" in t -> "El sitio pide iniciar sesión para ese contenido."
            "unavailable" in t || "not available" in t -> "El video no está disponible (borrado o bloqueado en tu país)."
            "drm" in t -> "Ese contenido está protegido (DRM) y no se puede descargar."
            "getaddrinfo" in t || "timed out" in t || "connection" in t -> "Sin conexión a internet o el sitio no responde."
            e.isBlank() -> "La descarga falló."
            else -> e.removePrefix("ERROR:").trim().take(200)
        }
    }
}

/** Argumentos de yt-dlp para una descarga (función pura: se prueba sin Windows). */
object Pedido {
    fun esLista(url: String) = Regex("[?&]list=").containsMatchIn(url) && !Regex("[?&]v=|youtu\\.be/").containsMatchIn(url)

    fun argumentos(url: String, f: Formato, carpeta: File, herramientas: Herramientas = Herramientas): List<String> {
        val plantilla = if (esLista(url)) "%(playlist_title).80B/%(playlist_index)02d - %(title).120B.%(ext)s" else "%(title).150B.%(ext)s"
        val a = mutableListOf(
            herramientas.ytdlp.path,
            "--ffmpeg-location", herramientas.ffmpeg.parentFile.path,
            "--js-runtimes", "quickjs:${herramientas.quickjs.path}",
            "--newline", "--progress", "--progress-template", SalidaYtdlp.PLANTILLA,
            "--print", "before_dl:[titulo]%(title)s", "--print", "after_move:[archivo]%(filepath)s", "--no-simulate",
            "-o", File(carpeta, plantilla).path,
            "--windows-filenames", "--no-mtime", "-N", "4", "--retries", "10", "--fragment-retries", "10",
            if (esLista(url)) "--yes-playlist" else "--no-playlist",
        )
        when (f) {
            Formato.MEJOR -> a += listOf("-S", "res,vcodec:h264,acodec:aac", "--merge-output-format", "mp4")
            Formato.P1080 -> a += listOf("-S", "res:1080,vcodec:h264,acodec:aac", "--merge-output-format", "mp4")
            Formato.P720 -> a += listOf("-S", "res:720,vcodec:h264,acodec:aac", "--merge-output-format", "mp4")
            Formato.MP3 -> a += listOf("-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "0")
            Formato.M4A -> a += listOf("-f", "ba[ext=m4a]/ba/b", "-x", "--audio-format", "m4a", "--audio-quality", "0")
            Formato.OPUS -> a += listOf("-f", "ba[acodec=opus]/ba/b", "-x", "--audio-format", "opus")
        }
        if (f.esAudio) a += listOf("--embed-thumbnail", "--convert-thumbnails", "jpg", "--embed-metadata")
        a += url
        return a
    }
}

/** Cola de descargas: varias a la vez (2 por defecto), con progreso y cancelar. */
object CentroDescargas {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = AtomicLong(1)
    private val _lista = MutableStateFlow<List<Descarga>>(emptyList())
    val lista: StateFlow<List<Descarga>> = _lista.asStateFlow()
    private val procesos = ConcurrentHashMap<Long, Process>()
    private val turnos = Semaphore(Ajustes.simultaneas)

    private fun cambiar(id: Long, f: (Descarga) -> Descarga) = _lista.update { l -> l.map { if (it.id == id) f(it) else it } }

    fun agregar(url: String, formato: Formato, titulo: String? = null) {
        val d = Descarga(ids.getAndIncrement(), url.trim(), formato, titulo = titulo ?: url.trim())
        _lista.update { listOf(d) + it }
        scope.launch { turnos.withPermit { ejecutar(d.id) } }
    }

    private fun ejecutar(id: Long) {
        val d = _lista.value.firstOrNull { it.id == id } ?: return
        if (d.estado == Estado.CANCELADA) return
        val faltan = Herramientas.faltantes().filter { it.startsWith("yt-dlp") || it.startsWith("ffmpeg") }
        if (faltan.isNotEmpty()) { cambiar(id) { it.copy(estado = Estado.ERROR, error = "Faltan ${faltan.joinToString()}. Reinstala ${App.nombre}.") }; return }
        val carpeta = Ajustes.carpetaDescargas.apply { mkdirs() }
        cambiar(id) { it.copy(estado = Estado.DESCARGANDO) }
        val ultimas = ArrayDeque<String>()
        try {
            val p = ProcessBuilder(Pedido.argumentos(d.url, d.formato, carpeta)).redirectErrorStream(true).start()
            procesos[id] = p
            p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { linea ->
                SalidaYtdlp.progreso(linea)?.let { pr ->
                    cambiar(id) {
                        it.copy(
                            estado = if (pr.total > 0 && pr.bajado >= pr.total) Estado.PROCESANDO else Estado.DESCARGANDO,
                            progreso = pr.fraccion, bajado = pr.bajado, total = pr.total, velocidad = pr.velocidad, eta = pr.eta,
                        )
                    }
                    return@forEachLine
                }
                SalidaYtdlp.titulo(linea)?.let { t -> cambiar(id) { it.copy(titulo = t) }; return@forEachLine }
                SalidaYtdlp.archivo(linea)?.let { a -> cambiar(id) { it.copy(archivo = File(a)) }; return@forEachLine }
                if (linea.contains("[Merger]") || linea.contains("[ExtractAudio]")) cambiar(id) { it.copy(estado = Estado.PROCESANDO) }
                ultimas.addLast(linea); if (ultimas.size > 30) ultimas.removeFirst()
            }
            val codigo = p.waitFor()
            procesos.remove(id)
            if (_lista.value.firstOrNull { it.id == id }?.estado == Estado.CANCELADA) return
            if (codigo == 0) cambiar(id) { it.copy(estado = Estado.LISTA, progreso = 1f) }
            else cambiar(id) { it.copy(estado = Estado.ERROR, error = SalidaYtdlp.mensajeError(ultimas.toList())) }
        } catch (e: Exception) {
            procesos.remove(id)
            cambiar(id) { it.copy(estado = Estado.ERROR, error = e.message ?: "La descarga falló.") }
        }
    }

    fun cancelar(id: Long) {
        cambiar(id) { it.copy(estado = Estado.CANCELADA) }
        procesos.remove(id)?.let { p -> p.descendants().forEach { it.destroy() }; p.destroy() }
    }

    fun reintentar(id: Long) {
        val d = _lista.value.firstOrNull { it.id == id } ?: return
        cambiar(id) { it.copy(estado = Estado.EN_COLA, error = null, progreso = 0f) }
        scope.launch { turnos.withPermit { ejecutar(d.id) } }
    }

    fun quitar(id: Long) = _lista.update { l -> l.filterNot { it.id == id } }
    fun limpiarTerminadas() = _lista.update { l -> l.filterNot { it.estado in setOf(Estado.LISTA, Estado.ERROR, Estado.CANCELADA) } }
}

object Formatos {
    fun bytes(b: Long): String = when {
        b <= 0 -> ""
        b < 1024 * 1024 -> "%.0f KB".format(java.util.Locale("es"), b / 1024.0)
        b < 1024L * 1024 * 1024 -> "%.1f MB".format(java.util.Locale("es"), b / 1048576.0)
        else -> "%.2f GB".format(java.util.Locale("es"), b / 1073741824.0)
    }
    fun duracion(seg: Long): String = if (seg <= 0) "" else if (seg >= 3600) "%d:%02d:%02d".format(seg / 3600, seg % 3600 / 60, seg % 60) else "%d:%02d".format(seg / 60, seg % 60)
    fun vistas(n: Long): String = when {
        n < 0 -> ""
        n < 1000 -> "$n vistas"
        n < 1_000_000 -> "%.1f mil vistas".format(java.util.Locale("es"), n / 1000.0).replace(",0 ", " ")
        else -> "%.1f M vistas".format(java.util.Locale("es"), n / 1_000_000.0).replace(",0 ", " ")
    }
}
