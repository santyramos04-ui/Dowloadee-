package com.santyramos.mirador.servidor

import com.santyramos.mirador.BuildConfig
import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Sesión abierta en tu servidor Jellyfin (se guarda en el celular). */
@Serializable
data class ServidorJf(
    val direccion: String,
    val nombre: String,
    val usuario: String,
    val usuarioId: String,
    val token: String,
    val dispositivo: String,
) {
    fun aTexto(): String = Jellyfin.json.encodeToString(this)

    companion object {
        fun deTexto(t: String): ServidorJf? = runCatching { Jellyfin.json.decodeFromString<ServidorJf>(t) }.getOrNull()
    }
}

/** Una película, serie, episodio, video o carpeta de tu servidor. */
data class ItemJf(
    val id: String,
    val nombre: String,
    val tipo: String,
    val anio: Int? = null,
    val sinopsis: String? = null,
    val duracionMs: Long = 0,
    val posicionMs: Long = 0,
    val visto: Boolean = false,
    val serie: String? = null,
    val serieId: String? = null,
    val temporada: Int? = null,
    val episodio: Int? = null,
    val tipoColeccion: String? = null,
    val tieneFondo: Boolean = false,
) {
    /** Se puede reproducir directamente (no es una serie ni una carpeta). */
    val reproducible: Boolean get() = tipo in TIPOS_VIDEO
    val esSerie: Boolean get() = tipo == "Series"
    /** Carpeta o colección: al tocarla se ve lo que tiene dentro. */
    val esCarpeta: Boolean get() = !reproducible && !esSerie
    /** Cuánto llevas visto (0 a 1), para la barrita de «Continuar viendo». */
    val avance: Float get() = if (duracionMs > 0) (posicionMs.toFloat() / duracionMs).coerceIn(0f, 1f) else 0f

    /** «T1 · E3» para episodios. */
    val etiquetaEpisodio: String? get() = if (tipo == "Episode" && episodio != null) listOfNotNull(temporada?.let { "T$it" }, "E$episodio").joinToString(" · ") else null

    companion object {
        val TIPOS_VIDEO = setOf("Movie", "Episode", "Video", "MusicVideo", "Trailer")
    }
}

/** Un servidor que respondió en tu Wi‑Fi. */
data class Encontrado(val nombre: String, val direccion: String)

/**
 * Cliente de Jellyfin (servidor de películas gratis y de código abierto que se instala en tu mini PC).
 * Entra con usuario y clave, lista tus bibliotecas y reproduce el archivo original sin recomprimir
 * (máxima calidad y arranque rápido). Si el celular no puede con el formato, el servidor lo convierte.
 */
object Jellyfin {
    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val tipoJson = "application/json".toMediaType()
    const val PUERTO = 8096
    private const val TICKS_POR_MS = 10_000L

    /** Acepta «192.168.1.20», «mipc:8096», «http://…» o «https://…» y devuelve la dirección completa. */
    fun normalizarDireccion(texto: String): String? {
        var t = texto.trim().trimEnd('/')
        if (t.isEmpty()) return null
        if (!t.startsWith("http://", true) && !t.startsWith("https://", true)) t = "http://$t"
        val url = runCatching { t.toHttpUrl() }.getOrNull() ?: return null
        // Sin puerto escrito, Jellyfin usa el 8096 (salvo https, que normalmente va por un proxy en el 443).
        val conPuerto = if (url.port == 80 && url.scheme == "http" && !t.substringAfter("://").substringBefore('/').contains(':')) {
            url.newBuilder().port(PUERTO).build()
        } else url
        return conPuerto.toString().trimEnd('/')
    }

    /** Encabezado con el que Jellyfin reconoce a la app (y la sesión, si hay token). */
    fun encabezado(dispositivo: String, token: String? = null): String = buildString {
        append("MediaBrowser Client=\"Mirador\", Device=\"Android\", DeviceId=\"").append(dispositivo)
        append("\", Version=\"").append(BuildConfig.VERSION_NAME).append('"')
        if (token != null) append(", Token=\"").append(token).append('"')
    }

    // ------------------------------------------------------------------ Buscar en el Wi‑Fi

    /** Lee la respuesta de descubrimiento de Jellyfin: {"Address":"http://…:8096","Id":"…","Name":"…"}. */
    internal fun leerDescubrimiento(t: String): Encontrado? {
        val o = runCatching { json.parseToJsonElement(t) as? JsonObject }.getOrNull() ?: return null
        val dir = texto(o["Address"])?.let { normalizarDireccion(it) } ?: return null
        return Encontrado(texto(o["Name"]) ?: "Jellyfin", dir)
    }

    /** Pregunta en tu Wi‑Fi quién es un servidor Jellyfin (tarda unos 2 segundos). */
    suspend fun descubrir(): List<Encontrado> = withContext(Dispatchers.IO) {
        val encontrados = linkedMapOf<String, Encontrado>()
        runCatching {
            DatagramSocket().use { s ->
                s.broadcast = true
                s.soTimeout = 2000
                val msg = "who is JellyfinServer?".toByteArray()
                s.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 7359))
                val buf = ByteArray(4096)
                val fin = System.currentTimeMillis() + 2000
                while (System.currentTimeMillis() < fin) {
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (_: SocketTimeoutException) { break }
                    leerDescubrimiento(String(p.data, 0, p.length))?.let { encontrados[it.direccion] = it }
                }
            }
        }
        encontrados.values.toList()
    }

    // ------------------------------------------------------------------ Sesión

    /** Entra con tu usuario de Jellyfin. Lanza un error con un mensaje claro si algo falla. */
    suspend fun entrar(direccionEscrita: String, usuario: String, clave: String, dispositivo: String): ServidorJf = withContext(Dispatchers.IO) {
        val dir = normalizarDireccion(direccionEscrita) ?: throw IllegalArgumentException("Esa dirección no es válida. Ejemplo: 192.168.1.20")
        val nombre = runCatching {
            Http.client.newCall(Request.Builder().url("$dir/System/Info/Public").build()).execute().use { r ->
                if (!r.isSuccessful) null else texto((json.parseToJsonElement(r.body.string()) as? JsonObject)?.get("ServerName"))
            }
        }.getOrElse { throw IllegalStateException("No encontré el servidor en $dir. Revisa que la mini PC esté prendida y en la misma red (o con Tailscale).") }
            ?: "Mi servidor"
        val cuerpo = buildJsonObject { put("Username", usuario.trim()); put("Pw", clave) }.toString()
        val pedido = Request.Builder().url("$dir/Users/AuthenticateByName")
            .header("Authorization", encabezado(dispositivo))
            .post(cuerpo.toRequestBody(tipoJson)).build()
        Http.client.newCall(pedido).execute().use { r ->
            if (r.code == 401) throw IllegalStateException("Usuario o contraseña incorrectos.")
            if (!r.isSuccessful) throw IllegalStateException("El servidor respondió ${r.code}.")
            val o = json.parseToJsonElement(r.body.string()) as JsonObject
            val token = texto(o["AccessToken"]) ?: throw IllegalStateException("El servidor no entregó la sesión.")
            val uid = texto((o["User"] as? JsonObject)?.get("Id")) ?: throw IllegalStateException("El servidor no entregó el usuario.")
            ServidorJf(dir, nombre, usuario.trim(), uid, token, dispositivo)
        }
    }

    // ------------------------------------------------------------------ Contenido

    private const val CAMPOS = "Overview,ProductionYear,UserData,SeriesName,ParentIndexNumber,IndexNumber,CollectionType,BackdropImageTags"

    private fun get(s: ServidorJf, ruta: String, vararg parametros: Pair<String, String?>): JsonElement {
        val url = "${s.direccion}$ruta".toHttpUrl().newBuilder().apply {
            parametros.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
        val pedido = Request.Builder().url(url).header("Authorization", encabezado(s.dispositivo, s.token)).build()
        return Http.client.newCall(pedido).execute().use { r ->
            if (r.code == 401) throw IllegalStateException("La sesión del servidor expiró. Vuelve a conectarte.")
            if (!r.isSuccessful) throw IllegalStateException("El servidor respondió ${r.code}.")
            json.parseToJsonElement(r.body.string())
        }
    }

    internal fun texto(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun numero(e: JsonElement?): Long? = (e as? JsonPrimitive)?.longOrNull

    /** Convierte un objeto de la API de Jellyfin en [ItemJf]. */
    internal fun leerItem(o: JsonObject): ItemJf? {
        val id = texto(o["Id"]) ?: return null
        val datos = o["UserData"] as? JsonObject
        return ItemJf(
            id = id,
            nombre = texto(o["Name"]) ?: "Sin título",
            tipo = texto(o["Type"]) ?: "Video",
            anio = numero(o["ProductionYear"])?.toInt(),
            sinopsis = texto(o["Overview"]),
            duracionMs = (numero(o["RunTimeTicks"]) ?: 0) / TICKS_POR_MS,
            posicionMs = (numero(datos?.get("PlaybackPositionTicks")) ?: 0) / TICKS_POR_MS,
            visto = (datos?.get("Played") as? JsonPrimitive)?.booleanOrNull ?: false,
            serie = texto(o["SeriesName"]),
            serieId = texto(o["SeriesId"]),
            temporada = numero(o["ParentIndexNumber"])?.toInt(),
            episodio = numero(o["IndexNumber"])?.toInt(),
            tipoColeccion = texto(o["CollectionType"]),
            tieneFondo = (o["BackdropImageTags"] as? JsonArray)?.isNotEmpty() == true,
        )
    }

    /** Lee una respuesta que trae {"Items":[…]} o directamente […]. */
    internal fun leerItems(e: JsonElement): List<ItemJf> {
        val lista = (e as? JsonArray) ?: ((e as? JsonObject)?.get("Items") as? JsonArray) ?: return emptyList()
        return lista.mapNotNull { (it as? JsonObject)?.let(::leerItem) }
    }

    /** Tus bibliotecas (Películas, Series, Videos personales…). */
    suspend fun bibliotecas(s: ServidorJf): List<ItemJf> = withContext(Dispatchers.IO) {
        leerItems(get(s, "/Users/${s.usuarioId}/Views")).filter { it.tipoColeccion in setOf(null, "movies", "tvshows", "homevideos", "musicvideos", "mixed", "folders") }
    }

    /** Lo que dejaste a medias. */
    suspend fun continuar(s: ServidorJf): List<ItemJf> = withContext(Dispatchers.IO) {
        leerItems(get(s, "/Users/${s.usuarioId}/Items/Resume", "MediaTypes" to "Video", "Limit" to "20", "Fields" to CAMPOS))
    }

    /** Lo último que agregaste a una biblioteca. */
    suspend fun recientes(s: ServidorJf, bibliotecaId: String): List<ItemJf> = withContext(Dispatchers.IO) {
        leerItems(get(s, "/Users/${s.usuarioId}/Items/Latest", "ParentId" to bibliotecaId, "Limit" to "24", "Fields" to CAMPOS))
    }

    /** Todo lo que hay dentro de una biblioteca o carpeta, por nombre. */
    suspend fun contenido(s: ServidorJf, padreId: String, desde: Int = 0): List<ItemJf> = withContext(Dispatchers.IO) {
        leerItems(get(
            s, "/Users/${s.usuarioId}/Items", "ParentId" to padreId, "SortBy" to "SortName", "SortOrder" to "Ascending",
            "StartIndex" to desde.toString(), "Limit" to "100", "Fields" to CAMPOS,
        ))
    }

    /** Episodios de una serie (todas las temporadas, en orden). */
    suspend fun episodios(s: ServidorJf, serieId: String): List<ItemJf> = withContext(Dispatchers.IO) {
        leerItems(get(s, "/Shows/$serieId/Episodes", "UserId" to s.usuarioId, "Fields" to CAMPOS))
    }

    suspend fun buscar(s: ServidorJf, consulta: String): List<ItemJf> = withContext(Dispatchers.IO) {
        leerItems(get(
            s, "/Users/${s.usuarioId}/Items", "SearchTerm" to consulta, "Recursive" to "true",
            "IncludeItemTypes" to "Movie,Series,Episode,Video,MusicVideo", "Limit" to "60", "Fields" to CAMPOS,
        ))
    }

    // ------------------------------------------------------------------ Imágenes y video

    /** Portada vertical (o la miniatura del episodio). */
    fun portada(s: ServidorJf, id: String, alto: Int = 450): String = "${s.direccion}/Items/$id/Images/Primary?fillHeight=$alto&quality=90"
    /** Imagen ancha de fondo. */
    fun fondo(s: ServidorJf, id: String, ancho: Int = 1280): String = "${s.direccion}/Items/$id/Images/Backdrop?fillWidth=$ancho&quality=85"
    /** Imagen ancha para una tarjeta: el fondo si existe; si no, la portada. */
    fun imagenAncha(s: ServidorJf, i: ItemJf): String = when {
        i.tipo == "Episode" -> "${s.direccion}/Items/${i.id}/Images/Primary?fillWidth=640&quality=85"
        i.tieneFondo -> fondo(s, i.id, 640)
        else -> portada(s, i.id, 360)
    }

    /** El archivo original tal cual (máxima calidad, arranca enseguida). */
    fun urlOriginal(s: ServidorJf, id: String): String =
        "${s.direccion}/Videos/$id/stream".toHttpUrl().newBuilder()
            .addQueryParameter("static", "true").addQueryParameter("MediaSourceId", id)
            .addQueryParameter("DeviceId", s.dispositivo).addQueryParameter("api_key", s.token).build().toString()

    /** Versión que el servidor convierte al vuelo a H.264 + AAC (para formatos que el celular no lee o poca señal). */
    fun urlConvertida(s: ServidorJf, id: String, maxBitrate: Int = 20_000_000, altoMax: Int? = null): String =
        "${s.direccion}/Videos/$id/master.m3u8".toHttpUrl().newBuilder()
            .addQueryParameter("MediaSourceId", id).addQueryParameter("DeviceId", s.dispositivo)
            .addQueryParameter("api_key", s.token).addQueryParameter("PlaySessionId", java.util.UUID.randomUUID().toString())
            .addQueryParameter("VideoCodec", "h264").addQueryParameter("AudioCodec", "aac")
            .addQueryParameter("TranscodingMaxAudioChannels", "2").addQueryParameter("SegmentContainer", "ts")
            .addQueryParameter("MaxStreamingBitrate", maxBitrate.toString())
            .apply { if (altoMax != null) addQueryParameter("MaxHeight", altoMax.toString()) }
            .build().toString()

    /** Dirección «de la página» del elemento: identifica lo que suena (historial, cola y avance). */
    fun pagina(s: ServidorJf, id: String): String = "${s.direccion}/web/#/details?id=$id"

    /** Saca el id de Jellyfin de una dirección creada con [pagina] (o null si no es de este servidor). */
    fun idDePagina(s: ServidorJf, url: String): String? =
        url.takeIf { it.startsWith("${s.direccion}/web/#/details?id=") }?.substringAfter("details?id=")?.takeIf { it.isNotBlank() }

    // ------------------------------------------------------------------ Avance (para «Continuar viendo» en todos tus equipos)

    private fun post(s: ServidorJf, ruta: String, cuerpo: String) {
        val pedido = Request.Builder().url("${s.direccion}$ruta").header("Authorization", encabezado(s.dispositivo, s.token))
            .post(cuerpo.toRequestBody(tipoJson)).build()
        runCatching { Http.client.newCall(pedido).execute().close() }
    }

    internal fun cuerpoAvance(id: String, posicionMs: Long, pausado: Boolean): String = buildJsonObject {
        put("ItemId", id); put("MediaSourceId", id)
        put("PositionTicks", posicionMs * TICKS_POR_MS); put("IsPaused", pausado)
        put("PlayMethod", "DirectPlay"); put("CanSeek", true)
    }.toString()

    suspend fun empezo(s: ServidorJf, id: String, posicionMs: Long) = withContext(Dispatchers.IO) { post(s, "/Sessions/Playing", cuerpoAvance(id, posicionMs, false)) }
    suspend fun avance(s: ServidorJf, id: String, posicionMs: Long, pausado: Boolean) = withContext(Dispatchers.IO) { post(s, "/Sessions/Playing/Progress", cuerpoAvance(id, posicionMs, pausado)) }
    suspend fun termino(s: ServidorJf, id: String, posicionMs: Long) = withContext(Dispatchers.IO) { post(s, "/Sessions/Playing/Stopped", cuerpoAvance(id, posicionMs, true)) }
}
