package com.santyramos.mirador.download

import java.io.File

/** Decide cómo obtener la información de un enlace: yt-dlp primero; complementos si hace falta. */
object PreviewLoader {
    suspend fun cargar(url: String, cookies: File? = null, soloEste: Boolean = true): MediaInfo {
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
