package com.santyramos.mirador.escritorio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File

/**
 * Los videos se ven con mpv (reproductor libre y liviano) en su propia ventana.
 * YouTube: se le pasan directo la imagen y el sonido original (rápido). Otros sitios, o si falla:
 * mpv usa yt-dlp para sacar el video.
 */
object Reproductor {
    private fun agente(url: String) = if (url.contains("c=VISIONOS")) YoutubeParsingHelper.getVisionOsUserAgent(null)
    else "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    private fun base(titulo: String) = listOf(
        Herramientas.mpv.path, "--force-window=immediate", "--keep-open=no", "--hwdec=auto-safe",
        "--title=$titulo · ${App.nombre}",
    )

    /** Argumentos para mpv con streams directos de YouTube. */
    internal fun argsDirectos(titulo: String, video: String, audio: String?): List<String> =
        base(titulo) + listOf("--user-agent=${agente(video)}", "--ytdl=no") +
            (if (audio != null) listOf("--audio-file=$audio") else emptyList()) + video

    /** Argumentos para que mpv use yt-dlp (cualquier sitio). */
    internal fun argsConYtdlp(titulo: String, url: String, tope: Int): List<String> = base(titulo) + listOf(
        "--script-opts=ytdl_hook-ytdl_path=${Herramientas.ytdlp.path}",
        "--ytdl-format=bv*[height<=$tope]+ba/b[height<=$tope]/b",
        "--ytdl-raw-options-append=js-runtimes=quickjs:${Herramientas.quickjs.path}",
        url,
    )

    private fun lanzar(args: List<String>): Process =
        ProcessBuilder(args).directory(Herramientas.carpetaHerramientas).redirectErrorStream(true)
            .redirectOutput(File(System.getProperty("java.io.tmpdir"), "mirador-mpv.log")).start()

    /** Abre el video. Devuelve un mensaje de error o null si arrancó. */
    suspend fun ver(url: String, titulo: String): String? = withContext(Dispatchers.IO) {
        if (!Herramientas.mpv.exists()) return@withContext "Falta el reproductor (mpv). Reinstala ${App.nombre}."
        val tope = Ajustes.calidadVer
        if (Youtube.esYoutube(url)) {
            val directo = runCatching {
                val info = Youtube.info(url)
                val v = Seleccion.video(info.videoOnlyStreams, tope)
                val a = Seleccion.audio(info.audioStreams)
                when {
                    v != null && a != null -> argsDirectos(titulo, v.content, a.content)
                    else -> Seleccion.video(info.videoStreams, tope)?.let { argsDirectos(titulo, it.content, null) }
                }
            }.getOrNull()
            if (directo != null) {
                val p = lanzar(directo)
                // Si mpv no pudo abrir los streams directos, sale enseguida con error: se intenta con yt-dlp.
                val salio = p.waitFor(6, java.util.concurrent.TimeUnit.SECONDS)
                if (!salio || p.exitValue() == 0) return@withContext null
            }
        }
        lanzar(argsConYtdlp(titulo, url, tope))
        null
    }

    /** Abre un archivo descargado con mpv. */
    fun abrirArchivo(f: File) {
        if (Herramientas.mpv.exists()) lanzar(base(f.nameWithoutExtension) + f.path)
        else runCatching { java.awt.Desktop.getDesktop().open(f) }
    }
}
