package com.santyramos.mirador.data.lib

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Lee los canales de un archivo de Google Takeout (subscriptions.csv / suscripciones.csv, o el ZIP completo) o de un respaldo de NewPipe (.json). */
object Importar {
    private val idCanal = Regex("^UC[0-9A-Za-z_-]{22}$")

    fun canalIdDeUrl(url: String): String? =
        Regex("youtube\\.com/channel/(UC[0-9A-Za-z_-]{22})").find(url)?.groupValues?.get(1)

    fun urlDeCanal(id: String) = "https://www.youtube.com/channel/$id"

    /** Separa una línea CSV respetando comillas. */
    internal fun partirCsv(linea: String): List<String> {
        val campos = mutableListOf<String>()
        val actual = StringBuilder()
        var entreComillas = false
        var i = 0
        while (i < linea.length) {
            val c = linea[i]
            when {
                c == '"' && entreComillas && i + 1 < linea.length && linea[i + 1] == '"' -> { actual.append('"'); i++ }
                c == '"' -> entreComillas = !entreComillas
                c == ',' && !entreComillas -> { campos.add(actual.toString()); actual.clear() }
                else -> actual.append(c)
            }
            i++
        }
        campos.add(actual.toString())
        return campos.map { it.trim() }
    }

    fun deCsv(texto: String): List<Suscripcion> =
        texto.removePrefix("﻿").lineSequence().mapNotNull { linea ->
            val c = partirCsv(linea)
            val id = c.getOrNull(0)?.takeIf { idCanal.matches(it) } ?: return@mapNotNull null
            Suscripcion(canalId = id, nombre = c.getOrNull(2)?.takeIf { it.isNotBlank() } ?: id, urlCanal = urlDeCanal(id))
        }.distinctBy { it.canalId }.toList()

    /** Respaldo de NewPipe: {"subscriptions":[{"service_id":0,"url":"https://www.youtube.com/channel/UC…","name":"…"}]} */
    fun deJsonNewPipe(texto: String): List<Suscripcion> {
        val raiz = JSONObject(texto)
        val arr: JSONArray = raiz.optJSONArray("subscriptions") ?: return emptyList()
        val salida = mutableListOf<Suscripcion>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optInt("service_id", 0) != 0) continue
            val id = canalIdDeUrl(o.optString("url")) ?: continue
            salida.add(Suscripcion(id, o.optString("name").ifBlank { id }, urlDeCanal(id)))
        }
        return salida.distinctBy { it.canalId }
    }

    /** Acepta CSV, JSON o el ZIP de Takeout (busca dentro el CSV de suscripciones). El ZIP se lee en flujo, sin cargarlo entero. */
    fun deFlujo(entrada: InputStream): List<Suscripcion> {
        val buf = BufferedInputStream(entrada)
        buf.mark(8)
        val cab = ByteArray(4)
        val leidos = buf.read(cab)
        buf.reset()
        if (leidos >= 2 && cab[0] == 'P'.code.toByte() && cab[1] == 'K'.code.toByte()) {
            ZipInputStream(buf).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val nombre = entry.name.lowercase()
                    if (!entry.isDirectory && nombre.endsWith(".csv") && (nombre.contains("suscripciones") || nombre.contains("subscriptions"))) {
                        return deCsv(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    entry = zip.nextEntry
                }
            }
            return emptyList()
        }
        val texto = buf.readBytes().toString(Charsets.UTF_8)
        return if (texto.trimStart().startsWith("{")) deJsonNewPipe(texto) else deCsv(texto)
    }

    fun deArchivo(bytes: ByteArray): List<Suscripcion> = deFlujo(ByteArrayInputStream(bytes))
}
