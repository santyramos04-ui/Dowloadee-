package com.santyramos.mirador.data.lib

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Lee el feed público de un canal (https://www.youtube.com/feeds/videos.xml?channel_id=…).
 * Es una sola petición ligera por canal, sin cuenta y sin anuncios, con los últimos ~15 videos.
 */
object FeedRss {
    fun url(canalId: String) = "https://www.youtube.com/feeds/videos.xml?channel_id=$canalId"

    private fun etiqueta(bloque: String, nombre: String): String? =
        Regex("<$nombre(?:\\s[^>]*)?>([\\s\\S]*?)</$nombre>").find(bloque)?.groupValues?.get(1)?.trim()

    private fun atributo(bloque: String, etiqueta: String, atributo: String): String? =
        Regex("<$etiqueta\\s[^>]*?$atributo=\"([^\"]*)\"").find(bloque)?.groupValues?.get(1)

    private fun descodificar(s: String) = s
        .replace("<![CDATA[", "").replace("]]>", "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

    private fun fechaMs(iso: String?): Long {
        if (iso == null) return 0
        val limpio = iso.replace(Regex("(\\.\\d+)"), "")
        for (patron in listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            runCatching {
                val f = SimpleDateFormat(patron, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return f.parse(limpio)!!.time
            }
        }
        return 0
    }

    fun parsear(xml: String, canalId: String): List<Novedad> {
        val autorCanal = etiqueta(xml.substringBefore("<entry>"), "name")?.let(::descodificar)
        return Regex("<entry>([\\s\\S]*?)</entry>").findAll(xml).mapNotNull { m ->
            val b = m.groupValues[1]
            val id = etiqueta(b, "yt:videoId") ?: return@mapNotNull null
            Novedad(
                url = "https://www.youtube.com/watch?v=$id",
                canalId = canalId,
                canalNombre = etiqueta(b, "name")?.let(::descodificar) ?: autorCanal ?: "",
                titulo = etiqueta(b, "title")?.let(::descodificar) ?: id,
                miniatura = atributo(b, "media:thumbnail", "url") ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                publicadoMs = fechaMs(etiqueta(b, "published")),
                vistas = atributo(b, "media:statistics", "views")?.toLongOrNull() ?: -1,
            )
        }.toList()
    }
}
