package com.santyramos.mirador.download

import java.net.URI
import java.net.URLDecoder

object UrlTools {
    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Saca el primer enlace de un texto compartido ("Mira esto https://... jaja"). */
    fun primerEnlace(texto: String?): String? =
        URL_REGEX.find(texto.orEmpty())?.value?.trimEnd('.', ',', ')', ';', '!', '?', ']')

    /** Todos los enlaces distintos de un texto (varios pegados, uno por línea o separados por espacios). */
    fun todosEnlaces(texto: String?): List<String> =
        URL_REGEX.findAll(texto.orEmpty()).map { it.value.trimEnd('.', ',', ')', ';', '!', '?', ']') }.distinct().toList()

    private fun host(url: String): String? =
        runCatching { URI(url).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") }.getOrNull()

    fun esYoutube(url: String): Boolean {
        val h = host(url) ?: return false
        return h == "youtube.com" || h == "youtu.be" || h == "music.youtube.com" || h.endsWith(".youtube.com")
    }

    fun esX(url: String): Boolean {
        val h = host(url) ?: return false
        return h == "x.com" || h == "twitter.com" || h == "mobile.twitter.com" || h.endsWith(".x.com")
    }

    fun esInstagram(url: String): Boolean = host(url)?.let { it == "instagram.com" || it.endsWith(".instagram.com") } == true

    private fun parametros(url: String): Map<String, String> {
        val q = runCatching { URI(url).rawQuery }.getOrNull() ?: return emptyMap()
        return q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else URLDecoder.decode(it.substring(0, i), "UTF-8") to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
    }

    /** ¿Es un video de YouTube que viene dentro de una lista (v= y list=)? */
    fun videoDentroDeLista(url: String): Boolean {
        if (!esYoutube(url)) return false
        val p = parametros(url)
        val lista = p["list"]
        // Las listas "RD..." son mezclas automáticas; igualmente se pregunta.
        return !lista.isNullOrBlank() && (p.containsKey("v") || host(url) == "youtu.be")
    }

    private fun ruta(url: String): String = runCatching { URI(url).path.orEmpty() }.getOrDefault("")

    /** ¿Es la página de una lista de YouTube (/playlist?list=...)? */
    fun esListaYoutube(url: String): Boolean = esYoutube(url) && ruta(url).startsWith("/playlist") && !parametros(url)["list"].isNullOrBlank()

    /** Enlace a la lista completa a partir de un video que viene dentro de ella. */
    fun urlDeLista(url: String): String {
        val id = parametros(url)["list"] ?: return url
        // Las "mezclas" automáticas (RD...) solo existen dentro de un video: se conserva el enlace original.
        return if (id.startsWith("RD")) url else "https://www.youtube.com/playlist?list=$id"
    }

    /** ID de la tarjeta de un tuit: https://x.com/usuario/status/123456 */
    fun idTuit(url: String): String? =
        Regex("""/status(?:es)?/(\d{5,25})""").find(url)?.groupValues?.get(1)

    /** Código de una publicación de Instagram: /p/CODE/, /reel/CODE/, /tv/CODE/ */
    fun codigoInstagram(url: String): String? =
        Regex("""/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""").find(url)?.groupValues?.get(1)

    /** Nombre de archivo seguro. */
    fun nombreSeguro(s: String, max: Int = 80): String {
        val limpio = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trim('.')
        return (if (limpio.isEmpty()) "archivo" else limpio).take(max)
    }
}
