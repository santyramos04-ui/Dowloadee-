package com.santyramos.mirador.download

import android.content.Context
import android.util.Log
import com.santyramos.mirador.data.YtdlpChannel
import com.santyramos.mirador.data.db.DownloadEntity
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Envoltorio de youtubedl-android (yt-dlp + Python + ffmpeg + QuickJS empaquetados).
 *
 * El runtime de JavaScript que yt-dlp exige para YouTube lo resuelve la propia librería:
 * en cada ejecución añade `--js-runtimes quickjs:<libqjs.so>` (QuickJS viene dentro del APK).
 */
object YtDlpEngine {
    private const val TAG = "YtDlpEngine"

    sealed interface Estado {
        data object SinIniciar : Estado
        data object Listo : Estado
        data class Fallo(val mensaje: String) : Estado
    }

    @Volatile var estado: Estado = Estado.SinIniciar
        private set
    private val listo = CompletableDeferred<Unit>()
    private var iniciando = false

    /** Se llama una vez al abrir la app (en segundo plano). */
    @Synchronized
    fun iniciar(context: Context) {
        if (estado is Estado.Listo || iniciando) return
        iniciando = true
        try {
            sembrarYtdlpEmbebido(context.applicationContext)
            YoutubeDL.init(context.applicationContext)
            FFmpeg.init(context.applicationContext)
            estado = Estado.Listo
            listo.complete(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "No se pudo iniciar yt-dlp", e)
            estado = Estado.Fallo(e.message ?: e.javaClass.simpleName)
            listo.completeExceptionally(e)
        } finally {
            iniciando = false
        }
    }

    suspend fun esperarListo() = listo.await()

    /** "2026.10.09.123456" -> [2026,10,9,123456] */
    internal fun partesVersion(v: String): List<Int> = v.trim().split('.', '-', '_').mapNotNull { it.toIntOrNull() }

    internal fun esMasNueva(a: String, b: String): Boolean {
        val x = partesVersion(a); val y = partesVersion(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /**
     * El CI deja en el APK el yt-dlp más reciente de ese momento (assets/ytdlp). Si es más nuevo que
     * el que hay instalado, se usa; así una instalación nueva no arranca con un motor viejo.
     */
    private fun sembrarYtdlpEmbebido(ctx: Context) {
        runCatching {
            val etiqueta = ctx.assets.open("ytdlp/version.txt").bufferedReader().use { it.readText().trim() }
            if (etiqueta.isEmpty()) return
            val prefs = ctx.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE)
            val instalada = prefs.getString("dlpVersion", null)
            val destino = File(ctx.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
            if (destino.exists() && instalada != null && !esMasNueva(etiqueta, instalada)) return
            destino.parentFile?.mkdirs()
            val tmp = File(destino.parentFile, "yt-dlp.nuevo")
            ctx.assets.open("ytdlp/yt-dlp").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (destino.exists()) destino.delete()
            if (!tmp.renameTo(destino)) throw java.io.IOException("No se pudo colocar yt-dlp")
            prefs.edit().putString("dlpVersion", etiqueta).putString("dlpVersionName", etiqueta).apply()
        }.onFailure { Log.w(TAG, "No se usó el yt-dlp embebido", it) }
    }

    // ---------- Información (vista previa) ----------

    suspend fun info(url: String, cookies: File? = null, soloEste: Boolean = true): MediaInfo =
        withContext(Dispatchers.IO) {
            esperarListo()
            val r = YoutubeDLRequest(url)
            r.addOption("-J")
            r.addOption(if (soloEste) "--no-playlist" else "--yes-playlist")
            r.addOption("--no-warnings")
            r.addOption("--socket-timeout", 25)
            if (!soloEste) r.addOption("--flat-playlist")
            cookies?.let { r.addOption("--cookies", it.absolutePath) }
            val resp = YoutubeDL.execute(r, null, null)
            MediaInfoParser.parsear(resp.out, url)
        }

    // ---------- Descarga ----------

    fun construirPedido(job: DownloadEntity, dir: File, cookies: File?): YoutubeDLRequest {
        val preset = Preset.from(job.preset)
        val r = YoutubeDLRequest(job.url)
        r.addOption("-o", File(dir, "%(playlist_index&{} - |)s%(title).110B.%(ext)s").absolutePath)
        r.addOption("--newline")
        r.addOption("--progress-template", ProgressParser.PLANTILLA)
        r.addOption("--no-mtime")
        r.addOption("--windows-filenames")
        r.addOption("-N", 4)
        r.addOption("--retries", 10)
        r.addOption("--fragment-retries", 10)
        r.addOption("--socket-timeout", 30)
        r.addOption(if (job.sinLista) "--no-playlist" else "--yes-playlist")
        cookies?.let { r.addOption("--cookies", it.absolutePath) }

        if (preset.esAudio) {
            r.addOption("-f", "ba/b")
            r.addOption("-x")
            r.addOption("--audio-format", "mp3")
            r.addOption("--audio-quality", "192K")
            r.addOption("--embed-thumbnail")
            r.addOption("--convert-thumbnails", "jpg")
            r.addOption("--embed-metadata")
        } else {
            // Orden de preferencia: la mayor resolución hasta el tope, luego H.264 y AAC (compatibles
            // con cualquier celular). Si el sitio no tiene H.264 se usa lo mejor que haya.
            val orden = if (preset == Preset.MAX_4K) "res,vcodec:h264,acodec:aac"
            else "res:${preset.altura},vcodec:h264,acodec:aac"
            r.addOption("-S", orden)
            r.addOption("--merge-output-format", "mp4")
            r.addOption("--embed-metadata")
        }
        return r
    }

    class Cancelada : Exception("Descarga cancelada")

    /**
     * Ejecuta la descarga (bloqueante). Devuelve los archivos generados en [dir].
     * [alAvanzar] recibe (avance o null si es una fase de procesado, texto de la línea).
     */
    suspend fun descargar(
        job: DownloadEntity,
        dir: File,
        cookies: File?,
        alAvanzar: (Avance?, Boolean) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        esperarListo()
        dir.mkdirs()
        val pedido = construirPedido(job, dir, cookies)
        try {
            YoutubeDL.execute(pedido, procesoId(job.id)) { _, _, linea ->
                val av = ProgressParser.parsear(linea)
                when {
                    av != null -> alAvanzar(av, false)
                    ProgressParser.esProcesando(linea) -> alAvanzar(null, true)
                }
            }
        } catch (e: YoutubeDL.CanceledException) {
            throw Cancelada()
        }
        archivosResultantes(dir)
    }

    fun procesoId(id: Long) = "mirador-$id"

    /** Mata el proceso de yt-dlp (para pausar o cancelar). Los .part quedan para poder reanudar. */
    fun detener(id: Long): Boolean = runCatching { YoutubeDL.destroyProcessById(procesoId(id)) }.getOrDefault(false)

    private val extTemporales = setOf("part", "ytdl", "temp", "json", "description", "tmp")
    private val extImagen = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")

    fun archivosResultantes(dir: File): List<File> {
        val todos = dir.listFiles().orEmpty().filter { it.isFile && it.extension.lowercase() !in extTemporales && !it.name.contains(".part-") }
        val hayMedia = todos.any { it.extension.lowercase() !in extImagen }
        return (if (hayMedia) todos.filter { it.extension.lowercase() !in extImagen } else todos).sortedBy { it.name }
    }

    // ---------- Actualización del motor ----------

    sealed interface ResultadoActualizacion {
        data class Actualizado(val version: String?) : ResultadoActualizacion
        data class YaAlDia(val version: String?) : ResultadoActualizacion
        data class Error(val mensaje: String) : ResultadoActualizacion
    }

    /** Descarga la última versión de yt-dlp, sin esperar un APK nuevo. */
    suspend fun actualizar(context: Context, canal: YtdlpChannel): ResultadoActualizacion =
        withContext(Dispatchers.IO) {
            try {
                esperarListo()
                val c = when (canal) {
                    YtdlpChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                    YtdlpChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
                }
                val st = YoutubeDL.updateYoutubeDL(context.applicationContext, c)
                val v = versionInstalada(context)
                if (st == YoutubeDL.UpdateStatus.DONE) ResultadoActualizacion.Actualizado(v)
                else ResultadoActualizacion.YaAlDia(v)
            } catch (e: Throwable) {
                Log.w(TAG, "Fallo al actualizar yt-dlp", e)
                ResultadoActualizacion.Error(ErrorMapper.mensaje(e))
            }
        }

    /** Versión de yt-dlp que realmente se ejecuta (ej. 2026.10.01). */
    suspend fun versionInstalada(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            esperarListo()
            val r = YoutubeDLRequest(emptyList<String>())
            r.addOption("--version")
            YoutubeDL.execute(r).out.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        }.getOrNull() ?: YoutubeDL.version(context.applicationContext)
    }
}
