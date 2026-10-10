package com.santyramos.mirador.download

import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Spotify cifra su audio, así que no se descarga de ahí. Lo que sí se puede es averiguar qué canción es
 * (el título y el artista son públicos en la página del enlace) y buscarla en YouTube.
 */
object SpotifyPuente {
    private fun meta(html: String, propiedad: String): String? =
        Regex("<meta[^>]+property=\"$propiedad\"[^>]+content=\"([^\"]*)\"").find(html)?.groupValues?.get(1)
            ?: Regex("<meta[^>]+content=\"([^\"]*)\"[^>]+property=\"$propiedad\"").find(html)?.groupValues?.get(1)

    private fun limpiar(s: String) = s.replace("&amp;", "&").replace("&#039;", "'").replace("&#x27;", "'").replace("&quot;", "\"").trim()

    /** Arma la búsqueda («artista título») a partir de la página del enlace. */
    internal fun consultaDe(html: String): String? {
        val titulo = meta(html, "og:title")?.let(::limpiar)?.takeIf { it.isNotBlank() } ?: return null
        val descripcion = meta(html, "og:description")?.let(::limpiar).orEmpty()
        val artista = descripcion.split(" · ").map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals(titulo, true) && !it.matches(Regex("\\d{4}")) && !it.startsWith("Listen", true) && !it.startsWith("Escucha", true) }
        return if (artista != null && !titulo.contains(artista, true)) "$artista $titulo" else titulo
    }

    suspend fun consulta(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            Http.client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36").build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                consultaDe(r.body.string())
            }
        }.getOrNull()
    }
}
