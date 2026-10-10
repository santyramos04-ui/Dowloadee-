package com.santyramos.mirador.data.lib

import android.content.Context
import com.santyramos.mirador.data.Settings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class SuscDto(val canalId: String, val nombre: String, val urlCanal: String, val avatar: String? = null)
@Serializable data class VideoDto(val url: String, val titulo: String, val miniatura: String? = null, val autor: String? = null, val urlAutor: String? = null, val duracionSeg: Long = 0, val posicionMs: Long = 0, val fecha: Long = 0)
@Serializable data class ListaDto(val nombre: String, val creadaEn: Long = 0, val videos: List<VideoDto> = emptyList())
@Serializable data class RespaldoDto(
    val version: Int = 1,
    val creadoEn: Long = 0,
    val suscripciones: List<SuscDto> = emptyList(),
    val historial: List<VideoDto> = emptyList(),
    val verMasTarde: List<VideoDto> = emptyList(),
    val listas: List<ListaDto> = emptyList(),
    val ajustes: Map<String, String> = emptyMap(),
)

/**
 * Respaldo en un solo archivo .json (suscripciones, historial, ver más tarde, listas propias y ajustes).
 * Al restaurar se SUMA a lo que ya hay: no se borra nada.
 */
object Respaldo {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun aTexto(r: RespaldoDto): String = json.encodeToString(r)
    fun deTexto(t: String): RespaldoDto = json.decodeFromString(t)

    suspend fun crear(context: Context): RespaldoDto {
        val dao = Biblioteca.dao
        val listas = dao.observarListas().first().map { l ->
            ListaDto(l.nombre, 0, dao.observarItems(l.id).first().map { VideoDto(it.url, it.titulo, it.miniatura, it.autor, it.urlAutor, it.duracionSeg, 0, it.agregadoEn) })
        }
        return RespaldoDto(
            creadoEn = System.currentTimeMillis(),
            suscripciones = dao.suscripciones().map { SuscDto(it.canalId, it.nombre, it.urlCanal, it.avatar) },
            historial = dao.observarHistorial().first().map { VideoDto(it.url, it.titulo, it.miniatura, it.autor, it.urlAutor, it.duracionSeg, it.posicionMs, it.vistoEn) },
            verMasTarde = dao.observarGuardados().first().map { VideoDto(it.url, it.titulo, it.miniatura, it.autor, it.urlAutor, it.duracionSeg, 0, it.agregadoEn) },
            listas = listas,
            ajustes = Settings(context).exportar(),
        )
    }

    class Resumen(val suscripciones: Int, val historial: Int, val guardados: Int, val listas: Int)

    suspend fun restaurar(context: Context, r: RespaldoDto): Resumen {
        val dao = Biblioteca.dao
        dao.suscribirVarias(r.suscripciones.map { Suscripcion(it.canalId, it.nombre, it.urlCanal, it.avatar) })
        r.historial.forEach { dao.registrarVisto(Visto(it.url, it.titulo, it.miniatura, it.autor, it.urlAutor, it.duracionSeg, it.posicionMs, it.fecha.takeIf { f -> f > 0 } ?: System.currentTimeMillis())) }
        r.verMasTarde.forEach { dao.guardar(Guardado(it.url, it.titulo, it.miniatura, it.autor, it.urlAutor, it.duracionSeg, it.fecha.takeIf { f -> f > 0 } ?: System.currentTimeMillis())) }
        val existentes = dao.observarListas().first().associate { it.nombre to it.id }
        r.listas.forEach { l ->
            val id = existentes[l.nombre] ?: dao.crearLista(ListaPropia(nombre = l.nombre, creadaEn = l.creadaEn.takeIf { it > 0 } ?: System.currentTimeMillis()))
            l.videos.forEach { dao.agregarALista(ItemLista(listaId = id, url = it.url, titulo = it.titulo, miniatura = it.miniatura, autor = it.autor, urlAutor = it.urlAutor, duracionSeg = it.duracionSeg, agregadoEn = it.fecha.takeIf { f -> f > 0 } ?: System.currentTimeMillis())) }
        }
        Settings(context).importar(r.ajustes)
        return Resumen(r.suscripciones.size, r.historial.size, r.verMasTarde.size, r.listas.size)
    }
}
