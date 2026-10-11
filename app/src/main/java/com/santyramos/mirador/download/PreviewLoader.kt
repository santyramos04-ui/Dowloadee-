package com.santyramos.mirador.download

import java.io.File

/** Decide cómo obtener la información de un enlace: yt-dlp primero; complementos si hace falta. */
object PreviewLoader {
    suspend fun cargar(url: String, cookies: File? = null, soloEste: Boolean = true): MediaInfo {
        // Spotify cifra su audio (DRM): ninguna herramienta legítima lo descarga. Se avisa de entrada, sin intentos inútiles.
        if (UrlTools.esSpotify(url)) throw IllegalStateException("spotify-drm")
        // Publicaciones de X con fotos: yt-dlp no las baja, así que se leen directo (y se incluyen sus videos).
        if (UrlTools.esX(url)) {
            val x = runCatching { DirectResolver.x(url) }.getOrNull()
            if (x != null && x.directos.any { !it.esVideo }) return x
        }
        val errorYtdlp: Throwable = try {
            return YtDlpEngine.info(url, cookies, soloEste)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e
        }
        val respaldo = runCatching {
            when {
                UrlTools.esX(url) -> DirectResolver.x(url)
                UrlTools.esInstagram(url) -> DirectResolver.instagram(url) ?: DirectResolver.pagina(url)
                else -> DirectResolver.pagina(url)
            }
        }.getOrNull()
        return respaldo ?: throw errorYtdlp
    }
}

/**
 * Vista previa rápida de YouTube con NewPipeExtractor (1–2 s, o al instante si el video ya está
 * sonando) en vez de esperar a yt-dlp (5–15 s en el celular). Da título, miniatura, duración y
 * calidades disponibles con su peso aproximado; yt-dlp hace la extracción completa al descargar.
 */
object PreviewRapido {
    suspend fun youtube(url: String): MediaInfo? {
        if (!UrlTools.esYoutube(url) || UrlTools.esListaYoutube(url)) return null
        val sonando = com.santyramos.mirador.player.VideoController.video.value
            ?.takeIf { it.urlOriginal == url }?.info
        val info = sonando ?: runCatching {
            kotlinx.coroutines.withTimeout(10_000) { com.santyramos.mirador.extractor.Youtube.video(url) }
        }.getOrNull() ?: return null
        return desdeStreamInfo(info, url)
    }

    internal fun desdeStreamInfo(info: org.schabi.newpipe.extractor.stream.StreamInfo, url: String): MediaInfo? {
        val formatos = buildList {
            (info.videoStreams + info.videoOnlyStreams).forEach { v ->
                add(
                    Formato(
                        id = v.itag.toString(), ext = v.format?.suffix ?: "mp4", altura = v.height,
                        vcodec = v.codec ?: if (v.format?.suffix == "mp4") "avc1" else "vp9",
                        acodec = if (v.isVideoOnly) "none" else "mp4a",
                        bytes = v.itagItem?.contentLength?.takeIf { it > 0 }, tbr = v.bitrate.takeIf { it > 0 }?.let { it / 1000.0 },
                    ),
                )
            }
            info.audioStreams.forEach { a ->
                add(
                    Formato(
                        id = a.itag.toString(), ext = a.format?.suffix ?: "m4a", altura = 0, vcodec = "none",
                        acodec = a.codec ?: "mp4a", bytes = a.itagItem?.contentLength?.takeIf { it > 0 },
                        tbr = a.averageBitrate.takeIf { it > 0 }?.toDouble(),
                    ),
                )
            }
        }
        if (formatos.isEmpty()) return null // directos y casos raros: mejor que decida yt-dlp
        return MediaInfo(
            url = url, titulo = info.name.orEmpty(), miniatura = info.thumbnails.maxByOrNull { it.width }?.url,
            duracionSeg = info.duration.takeIf { it > 0 }, autor = info.uploaderName, sitio = "YouTube", formatos = formatos,
        )
    }
}
