package com.santyramos.mirador.extractor

import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Letra de una canción. [lineas] con tiempo si está sincronizada; si no, [texto] plano. */
data class Letra(val lineas: List<Linea>, val texto: String?) {
    data class Linea(val ms: Long, val texto: String)
    val sincronizada: Boolean get() = lineas.isNotEmpty()
}

/**
 * Letras desde LRCLIB (lrclib.net): base de datos libre y gratuita de letras, muchas sincronizadas.
 * Solo se envía el nombre de la canción, el artista y la duración.
 */
object Letras {
    private val json = Json { ignoreUnknownKeys = true }
    private const val AGENTE = "RACQ (https://github.com/santyramos04-ui/Dowloadee-)"

    /** «Artista - Canción (Official Video) [HD]» → ("Artista", "Canción"). Si no hay guion, el artista es el canal. */
    internal fun separar(titulo: String, canal: String?): Pair<String?, String> {
        val limpio = titulo
            .replace(Regex("[\\(\\[][^\\)\\]]*(official|oficial|video|v[ií]deo|audio|lyrics?|letra|visualizer|hd|4k|mv|live|en vivo)[^\\)\\]]*[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(ft\\.?|feat\\.?)\\s.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ").trim()
        val partes = limpio.split(Regex("\\s+[-–—|]\\s+"), limit = 2)
        val artistaCanal = canal?.replace(Regex("\\s*-\\s*Topic$|VEVO$|\\s*Oficial$|\\s*Official$", RegexOption.IGNORE_CASE), "")?.trim()?.takeIf { it.isNotEmpty() }
        return if (partes.size == 2) partes[0].trim() to partes[1].trim() else artistaCanal to limpio
    }

    /** «[01:23.45] texto» → líneas con su tiempo. */
    internal fun leerLrc(lrc: String): List<Letra.Linea> {
        val re = Regex("""^\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]\s*(.*)$""")
        return lrc.lines().mapNotNull { l ->
            val m = re.find(l.trim()) ?: return@mapNotNull null
            val (mm, ss, frac, txt) = m.destructured
            val ms = (mm.toLong() * 60 + ss.toLong()) * 1000 + when (frac.length) { 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; 3 -> frac.toLong(); else -> 0 }
            Letra.Linea(ms, txt.trim())
        }.sortedBy { it.ms }
    }

    private fun texto(e: JsonElement?) = (e as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun desdeObjeto(o: JsonObject): Letra? {
        val sync = texto(o["syncedLyrics"])?.let(::leerLrc).orEmpty()
        val plano = texto(o["plainLyrics"])
        return if (sync.isEmpty() && plano == null) null else Letra(sync, plano)
    }

    private fun pedir(url: okhttp3.HttpUrl): JsonElement? =
        Http.client.newCall(Request.Builder().url(url).header("User-Agent", AGENTE).build()).execute().use { r ->
            if (!r.isSuccessful) null else runCatching { json.parseToJsonElement(r.body.string()) }.getOrNull()
        }

    suspend fun buscar(titulo: String, canal: String?, duracionSeg: Long): Letra? = withContext(Dispatchers.IO) {
        val (artista, cancion) = separar(titulo, canal)
        if (cancion.isBlank()) return@withContext null
        // 1) Coincidencia exacta (artista + canción + duración).
        if (artista != null && duracionSeg > 0) {
            val u = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("artist_name", artista).addQueryParameter("track_name", cancion)
                .addQueryParameter("duration", duracionSeg.toString()).build()
            (runCatching { pedir(u) }.getOrNull() as? JsonObject)?.let(::desdeObjeto)?.let { return@withContext it }
        }
        // 2) Búsqueda: la de duración más parecida, prefiriendo las sincronizadas.
        val q = listOfNotNull(artista, cancion).joinToString(" ")
        val u = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", q).build()
        val lista = (runCatching { pedir(u) }.getOrNull() as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        lista.sortedWith(compareBy<JsonObject>(
            { if (texto(it["syncedLyrics"]) != null) 0 else 1 },
            { val d = (it["duration"] as? JsonPrimitive)?.doubleOrNull ?: 0.0; if (duracionSeg > 0) kotlin.math.abs(d - duracionSeg) else 0.0 },
        )).firstNotNullOfOrNull(::desdeObjeto)
    }
}
