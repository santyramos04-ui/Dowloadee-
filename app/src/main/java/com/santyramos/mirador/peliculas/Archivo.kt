package com.santyramos.mirador.peliculas

import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Una película o episodio de Internet Archive (dominio público o con licencia libre). */
data class Pelicula(
    val id: String,
    val titulo: String,
    val anio: String?,
    val descripcion: String?,
) {
    val portada: String get() = "https://archive.org/services/img/$id"
    val pagina: String get() = "https://archive.org/details/$id"
}

/** Categorías: colecciones de Internet Archive con cine y TV de dominio público. */
enum class CategoriaPeli(val etiqueta: String, val consulta: String) {
    POPULARES("Populares", "collection:(feature_films)"),
    ESPANOL("En español", "collection:(feature_films) AND language:(spa OR Spanish OR Español)"),
    ANIMACION("Animación", "collection:(classic_cartoons)"),
    SERIES("Series clásicas", "collection:(classic_tv)"),
    TERROR("Ciencia ficción y terror", "collection:(SciFi_Horror)"),
}

/**
 * Cine legal y gratis de Internet Archive (archive.org): películas cuyos derechos expiraron o que
 * sus dueños liberaron. Usa la búsqueda pública de archive.org; no hace falta cuenta.
 */
object Archivo {
    private val json = Json { ignoreUnknownKeys = true }

    private fun texto(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.firstOrNull()?.let { texto(it) }
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    /** Quita etiquetas HTML y espacios de sobra de las descripciones. */
    internal fun limpiar(t: String?): String? = t
        ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        ?.replace(Regex("<[^>]+>"), "")
        ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")?.replace("&nbsp;", " ")
        ?.lines()?.joinToString("\n") { it.trim() }
        ?.replace(Regex("\n{3,}"), "\n\n")
        ?.trim()?.takeIf { it.isNotEmpty() }

    internal fun parsearBusqueda(cuerpo: String): List<Pelicula> {
        val docs = (json.parseToJsonElement(cuerpo) as? JsonObject)
            ?.get("response")?.let { it as? JsonObject }?.get("docs") as? JsonArray ?: return emptyList()
        return docs.mapNotNull { d ->
            val o = d as? JsonObject ?: return@mapNotNull null
            val id = texto(o["identifier"]) ?: return@mapNotNull null
            Pelicula(id, texto(o["title"]) ?: id, texto(o["year"]), limpiar(texto(o["description"])))
        }
    }

    /** Busca por categoría (y opcionalmente por texto). [pagina] empieza en 1. */
    suspend fun buscar(categoria: CategoriaPeli, pagina: Int = 1, textoBusqueda: String? = null): List<Pelicula> = withContext(Dispatchers.IO) {
        val filtro = if (textoBusqueda.isNullOrBlank()) categoria.consulta
        else "(${textoBusqueda.replace("\"", "")}) AND collection:(feature_films OR classic_tv OR classic_cartoons OR SciFi_Horror)"
        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$filtro AND mediatype:(movies)")
            .addQueryParameter("fl[]", "identifier").addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "year").addQueryParameter("fl[]", "description")
            .addQueryParameter("sort[]", "downloads desc")
            .addQueryParameter("rows", "40").addQueryParameter("page", pagina.toString())
            .addQueryParameter("output", "json").build()
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Internet Archive respondió ${r.code}")
            parsearBusqueda(r.body.string())
        }
    }

    /** Elige el mejor archivo de video para reproducir: MP4 (H.264) primero, el más liviano que no sea miniatura. */
    internal fun elegirVideo(id: String, cuerpo: String): String? {
        val files = (json.parseToJsonElement(cuerpo) as? JsonObject)?.get("files") as? JsonArray ?: return null
        data class F(val nombre: String, val formato: String, val tamano: Long)
        val candidatos = files.mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val nombre = texto(o["name"]) ?: return@mapNotNull null
            val formato = texto(o["format"]).orEmpty()
            F(nombre, formato, texto(o["size"])?.toLongOrNull() ?: 0L)
        }.filter { it.nombre.endsWith(".mp4", true) && !it.formato.contains("Thumb", true) }
        val elegido = candidatos.firstOrNull { it.formato.contains("h.264", true) && !it.formato.contains("HD", true) }
            ?: candidatos.firstOrNull { it.formato.contains("h.264", true) }
            ?: candidatos.maxByOrNull { it.tamano }
            ?: return null
        val ruta = elegido.nombre.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "https://archive.org/download/$id/$ruta"
    }

    /** Dirección del video para reproducir dentro de la app (o null si no tiene un MP4). */
    suspend fun urlVideo(id: String): String? = withContext(Dispatchers.IO) {
        Http.client.newCall(Request.Builder().url("https://archive.org/metadata/$id").build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Internet Archive respondió ${r.code}")
            elegirVideo(id, r.body.string())
        }
    }
}
