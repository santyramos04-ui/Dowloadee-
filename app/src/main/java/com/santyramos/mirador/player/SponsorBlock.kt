package com.santyramos.mirador.player

import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.security.MessageDigest

data class Segmento(val inicioMs: Long, val finMs: Long, val categoria: String)

/**
 * SponsorBlock (sponsor.ajay.app): una base de datos hecha por la comunidad con los tramos de cada video
 * que son patrocinio, autopromoción, pedidos de «suscríbete», introducciones… Mirador los salta solo.
 * Por privacidad no se envía el código del video completo, solo los primeros 4 caracteres de su huella (hash).
 */
object SponsorBlock {
    val CATEGORIAS = linkedMapOf(
        "sponsor" to "Patrocinio",
        "selfpromo" to "Autopromoción",
        "interaction" to "Pedidos de like y suscripción",
        "intro" to "Introducción",
        "outro" to "Créditos finales",
        "music_offtopic" to "Música sin relación",
    )

    fun idDeUrl(url: String): String? =
        Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)

    fun prefijoHash(id: String): String =
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }.take(4)

    internal fun parsear(json: String, videoId: String): List<Segmento> {
        val salida = mutableListOf<Segmento>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            if (v.optString("videoID") != videoId) continue
            val segs = v.optJSONArray("segments") ?: continue
            for (j in 0 until segs.length()) {
                val s = segs.optJSONObject(j) ?: continue
                if (s.optString("actionType", "skip") != "skip") continue
                val t = s.optJSONArray("segment") ?: continue
                if (t.length() != 2) continue
                val ini = (t.optDouble(0) * 1000).toLong()
                val fin = (t.optDouble(1) * 1000).toLong()
                if (fin > ini) salida.add(Segmento(ini, fin, s.optString("category")))
            }
        }
        return salida.sortedBy { it.inicioMs }
    }

    suspend fun segmentos(urlVideo: String, categorias: Set<String>): List<Segmento> = withContext(Dispatchers.IO) {
        val id = idDeUrl(urlVideo) ?: return@withContext emptyList()
        if (categorias.isEmpty()) return@withContext emptyList()
        runCatching {
            val cats = URLEncoder.encode(JSONArray(categorias.toList()).toString(), "UTF-8")
            val acc = URLEncoder.encode("[\"skip\"]", "UTF-8")
            val url = "https://sponsor.ajay.app/api/skipSegments/${prefijoHash(id)}?categories=$cats&actionTypes=$acc"
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) emptyList() else parsear(r.body.string(), id)
            }
        }.getOrDefault(emptyList())
    }
}
