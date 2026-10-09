package com.santyramos.mirador.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class Formato(
    val id: String,
    val ext: String,
    val altura: Int,
    val vcodec: String,
    val acodec: String,
    val bytes: Long?,
    val tbr: Double?,
) {
    val tieneVideo get() = vcodec != "none" && vcodec.isNotEmpty()
    val tieneAudio get() = acodec != "none" && acodec.isNotEmpty()
    val esH264 get() = vcodec.startsWith("avc1") || vcodec.startsWith("h264")
}

/** Un archivo suelto (foto o video) que se baja directo, sin yt-dlp. */
@Serializable
data class ArchivoDirecto(
    val url: String,
    val nombre: String,
    val esVideo: Boolean,
    val mime: String,
    val referer: String? = null,
)

/** Lo que mostramos en la vista previa antes de descargar. */
data class MediaInfo(
    val url: String,
    val titulo: String,
    val miniatura: String?,
    val duracionSeg: Long?,
    val autor: String?,
    val sitio: String?,
    val formatos: List<Formato>,
    val esLista: Boolean = false,
    val cantidadLista: Int = 0,
    /** Si no es null, este contenido se baja con descarga directa (fotos/carruseles). */
    val directos: List<ArchivoDirecto> = emptyList(),
) {
    val soloImagenes get() = directos.isNotEmpty() && directos.none { it.esVideo }
    val tieneVideo get() = formatos.any { it.tieneVideo } || directos.any { it.esVideo }
}

object MediaInfoParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.lng(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull

    fun parsear(texto: String, urlOriginal: String): MediaInfo {
        // yt-dlp a veces imprime avisos antes del JSON: buscamos la primera llave.
        val inicio = texto.indexOf('{')
        require(inicio >= 0) { "yt-dlp no devolvió datos" }
        val raiz: JsonElement = json.parseToJsonElement(texto.substring(inicio))
        return desdeObjeto(raiz.jsonObject, urlOriginal)
    }

    internal fun desdeObjeto(o: JsonObject, urlOriginal: String): MediaInfo {
        val tipo = o.str("_type")
        val entradas = (o["entries"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (tipo == "playlist" && entradas.isNotEmpty()) {
            // Lista o carrusel: mostramos el primer elemento como referencia.
            val primero = entradas.first()
            val base = desdeObjeto(primero, urlOriginal)
            return base.copy(
                titulo = o.str("title") ?: base.titulo,
                esLista = true,
                cantidadLista = entradas.size,
                miniatura = base.miniatura ?: o.str("thumbnail"),
            )
        }
        val formatos = (o["formats"] as? JsonArray).orEmpty().mapNotNull { f ->
            val fo = f as? JsonObject ?: return@mapNotNull null
            val ext = fo.str("ext") ?: return@mapNotNull null
            if (ext == "mhtml") return@mapNotNull null
            Formato(
                id = fo.str("format_id") ?: "?",
                ext = ext,
                altura = (fo.lng("height") ?: 0L).toInt(),
                vcodec = fo.str("vcodec") ?: "none",
                acodec = fo.str("acodec") ?: "none",
                bytes = fo.lng("filesize") ?: fo.lng("filesize_approx"),
                tbr = fo.dbl("tbr"),
            )
        }.ifEmpty {
            // Sitios sin lista de formatos: un solo archivo en el nivel superior.
            val ext = o.str("ext")
            if (ext != null && o.str("url") != null) listOf(
                Formato("unico", ext, (o.lng("height") ?: 0L).toInt(), o.str("vcodec") ?: "unknown",
                    o.str("acodec") ?: "unknown", o.lng("filesize") ?: o.lng("filesize_approx"), o.dbl("tbr")),
            ) else emptyList()
        }
        return MediaInfo(
            url = o.str("webpage_url") ?: urlOriginal,
            titulo = o.str("title") ?: o.str("fulltitle") ?: o.str("id") ?: "Sin título",
            miniatura = o.str("thumbnail") ?: (o["thumbnails"] as? JsonArray)?.lastOrNull()?.let { (it as? JsonObject)?.str("url") },
            duracionSeg = o.lng("duration"),
            autor = o.str("uploader") ?: o.str("channel") ?: o.str("creator"),
            sitio = o.str("extractor_key") ?: o.str("extractor"),
            formatos = formatos,
        )
    }
}

/** Calcula el peso estimado de cada opción (B2). Devuelve null si no hay datos suficientes. */
object SizeEstimator {
    private fun peso(f: Formato, duracion: Long?): Long? =
        f.bytes ?: if (f.tbr != null && duracion != null && duracion > 0) (f.tbr * 1000 / 8 * duracion).toLong() else null

    /** Mejor formato de video con altura <= tope, prefiriendo H.264 y luego la mayor altura. */
    fun mejorVideo(formatos: List<Formato>, tope: Int): Formato? {
        val candidatos = formatos.filter { it.tieneVideo && (tope <= 0 || it.altura <= tope) }
        val maxAlt = candidatos.maxOfOrNull { it.altura } ?: return null
        val enMaxAlt = candidatos.filter { it.altura == maxAlt }
        return enMaxAlt.firstOrNull { it.esH264 } ?: enMaxAlt.maxByOrNull { it.tbr ?: 0.0 }
    }

    fun mejorAudio(formatos: List<Formato>): Formato? {
        val soloAudio = formatos.filter { it.tieneAudio && !it.tieneVideo }
        return soloAudio.firstOrNull { it.ext == "m4a" } ?: soloAudio.maxByOrNull { it.tbr ?: 0.0 }
    }

    fun estimar(info: MediaInfo, preset: Preset): Long? {
        if (info.directos.isNotEmpty()) return null
        val dur = info.duracionSeg
        return when {
            preset == Preset.MP3 -> dur?.let { it * 192_000 / 8 }
            preset == Preset.IMAGENES -> null
            else -> {
                val v = mejorVideo(info.formatos, preset.altura) ?: return null
                val pv = peso(v, dur)
                if (v.tieneAudio) pv else {
                    val a = mejorAudio(info.formatos)
                    val pa = a?.let { peso(it, dur) } ?: 0L
                    pv?.plus(pa)
                }
            }
        }
    }

    /** Alturas realmente disponibles, para ocultar opciones que no existen (ej. 4K). */
    fun alturaMaxima(info: MediaInfo): Int = info.formatos.filter { it.tieneVideo }.maxOfOrNull { it.altura } ?: 0
}
