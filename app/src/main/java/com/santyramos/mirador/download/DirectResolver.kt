package com.santyramos.mirador.download

import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.PI

/**
 * Descarga de fotos y videos "sueltos" cuando yt-dlp no sirve (fotos de X, carruseles, páginas
 * con og:image / og:video). Se usa solo como complemento: yt-dlp sigue siendo el motor principal.
 */
object DirectResolver {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val UA_MOVIL =
        "Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"

    private fun get(url: String, ua: String = UA_MOVIL, extra: Map<String, String> = emptyMap()): String? {
        val rb = Request.Builder().url(url).header("User-Agent", ua).header("Accept-Language", "es,en;q=0.8")
        extra.forEach { (k, v) -> rb.header(k, v) }
        Http.client.newCall(rb.build()).execute().use { r ->
            if (!r.isSuccessful) return null
            return r.body.string()
        }
    }

    // ---------- X / Twitter (endpoint público de "syndication", el mismo que usan los embeds) ----------

    /** Equivale a ((id / 1e15) * Math.PI).toString(36).replace(/(0+|\.)/g, '') de JavaScript. */
    internal fun tokenSyndication(id: String): String {
        val valor = id.toDouble() / 1e15 * PI
        val entera = valor.toLong()
        val sb = StringBuilder(java.lang.Long.toString(entera, 36))
        var frac = valor - entera
        var n = 0
        while (frac > 0 && n < 12) {
            frac *= 36
            val d = frac.toInt()
            sb.append(Character.forDigit(d, 36))
            frac -= d
            n++
        }
        return sb.toString().replace("0", "").replace(".", "")
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject

    suspend fun x(url: String): MediaInfo? = withContext(Dispatchers.IO) {
        val id = UrlTools.idTuit(url) ?: return@withContext null
        val texto = get(
            "https://cdn.syndication.twimg.com/tweet-result?id=$id&token=${tokenSyndication(id)}",
            ua = "Googlebot",
        ) ?: return@withContext null
        val o = runCatching { json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return@withContext null
        val usuario = o.obj("user")
        val arroba = usuario?.str("screen_name") ?: "x"
        val items = mutableListOf<ArchivoDirecto>()
        var miniatura: String? = null
        var duracion: Long? = null
        o.arr("mediaDetails")?.forEachIndexed { i, el ->
            val m = el as? JsonObject ?: return@forEachIndexed
            val base = m.str("media_url_https") ?: return@forEachIndexed
            if (miniatura == null) miniatura = base
            when (m.str("type")) {
                "photo" -> {
                    val ext = base.substringAfterLast('.', "jpg").substringBefore('?')
                    items += ArchivoDirecto(
                        "$base?name=orig", "${arroba}_${id}_${i + 1}.$ext", false,
                        if (ext == "png") "image/png" else "image/jpeg",
                    )
                }
                "video", "animated_gif" -> {
                    val variantes = m.obj("video_info")?.arr("variants").orEmpty().mapNotNull { v ->
                        val vo = v as? JsonObject ?: return@mapNotNull null
                        if (vo.str("content_type") != "video/mp4") return@mapNotNull null
                        val u = vo.str("url") ?: return@mapNotNull null
                        u to ((vo["bitrate"] as? JsonPrimitive)?.doubleOrNull ?: 0.0)
                    }
                    variantes.maxByOrNull { it.second }?.let { (u, _) ->
                        items += ArchivoDirecto(u, "${arroba}_${id}_${i + 1}.mp4", true, "video/mp4")
                        duracion = (m.obj("video_info")?.get("duration_millis") as? JsonPrimitive)?.doubleOrNull?.let { (it / 1000).toLong() }
                    }
                }
            }
        }
        if (items.isEmpty()) return@withContext null
        val cuerpo = o.str("text").orEmpty().replace(Regex("""https://t\.co/\S+"""), "").replace('\n', ' ').trim()
        MediaInfo(
            url = url,
            titulo = listOfNotNull(usuario?.str("name"), cuerpo.take(80).ifBlank { null }).joinToString(" - ").ifBlank { "Publicación de X" },
            miniatura = miniatura,
            duracionSeg = duracion,
            autor = usuario?.str("name"),
            sitio = "X",
            formatos = emptyList(),
            directos = items,
        )
    }

    // ---------- Instagram (página "embed", sin iniciar sesión; solo publicaciones públicas) ----------

    private fun desescapar(s: String) = s.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")

    suspend fun instagram(url: String): MediaInfo? = withContext(Dispatchers.IO) {
        val codigo = UrlTools.codigoInstagram(url) ?: return@withContext null
        val html = get("https://www.instagram.com/p/$codigo/embed/captioned/") ?: return@withContext null
        val items = mutableListOf<ArchivoDirecto>()
        val videos = Regex(""""video_url"\s*:\s*"([^"]+)"""").findAll(html).map { desescapar(it.groupValues[1]) }.distinct().toList()
        videos.forEachIndexed { i, u -> items += ArchivoDirecto(u, "instagram_${codigo}_v${i + 1}.mp4", true, "video/mp4") }
        val fotos = (Regex(""""display_url"\s*:\s*"([^"]+)"""").findAll(html).map { desescapar(it.groupValues[1]) } +
            Regex("""class="EmbeddedMediaImage"[^>]*\ssrc="([^"]+)"""").findAll(html).map { desescapar(it.groupValues[1]) })
            .distinct().toList()
        // Si hay video, la foto es solo su portada.
        if (videos.isEmpty()) fotos.forEachIndexed { i, u ->
            items += ArchivoDirecto(u, "instagram_${codigo}_${i + 1}.jpg", false, "image/jpeg")
        }
        if (items.isEmpty()) return@withContext null
        MediaInfo(
            url = url, titulo = "Publicación de Instagram ($codigo)", miniatura = fotos.firstOrNull(),
            duracionSeg = null, autor = null, sitio = "Instagram", formatos = emptyList(), directos = items,
        )
    }

    // ---------- Cualquier página: og:video / og:image ----------

    private fun metas(html: String, nombre: String): List<String> =
        Regex("""<meta[^>]+(?:property|name)=["']$nombre["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html).map { desescapar(it.groupValues[1]) }.toList() +
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name)=["']$nombre["']""", RegexOption.IGNORE_CASE)
                .findAll(html).map { desescapar(it.groupValues[1]) }.toList()

    suspend fun pagina(url: String): MediaInfo? = withContext(Dispatchers.IO) {
        val html = get(url) ?: return@withContext null
        val items = mutableListOf<ArchivoDirecto>()
        val titulo = metas(html, "og:title").firstOrNull() ?: Regex("<title>([^<]+)</title>").find(html)?.groupValues?.get(1)?.trim() ?: url
        (metas(html, "og:video") + metas(html, "og:video:url") + metas(html, "og:video:secure_url"))
            .filter { it.startsWith("http") }.distinct().take(3).forEachIndexed { i, u ->
                items += ArchivoDirecto(u, "video_${i + 1}.mp4", true, "video/mp4", referer = url)
            }
        if (items.isEmpty()) {
            (metas(html, "og:image") + metas(html, "twitter:image")).filter { it.startsWith("http") }.distinct().take(1)
                .forEach { u ->
                    val ext = runCatching { u.toHttpUrl().encodedPath.substringAfterLast('.', "jpg") }.getOrDefault("jpg").take(4)
                    items += ArchivoDirecto(u, "imagen.$ext", false, "image/jpeg", referer = url)
                }
        }
        if (items.isEmpty()) return@withContext null
        MediaInfo(
            url = url, titulo = titulo, miniatura = items.firstOrNull { !it.esVideo }?.url ?: metas(html, "og:image").firstOrNull(),
            duracionSeg = null, autor = null, sitio = "Página web", formatos = emptyList(), directos = items,
        )
    }
}
