package com.santyramos.mirador.data.lib

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.santyramos.mirador.extractor.Elemento

/** Un canal al que sigues (guardado solo en el celular, sin cuenta de Google). */
@Entity(tableName = "suscripciones")
data class Suscripcion(
    /** Identificador del canal de YouTube (empieza con UC…). */
    @PrimaryKey val canalId: String,
    val nombre: String,
    val urlCanal: String,
    val avatar: String? = null,
    val agregadaEn: Long = System.currentTimeMillis(),
)

/** Un video publicado por un canal que sigues (copia local del feed RSS). */
@Entity(tableName = "novedades", indices = [Index("canalId"), Index("publicadoMs")])
data class Novedad(
    @PrimaryKey val url: String,
    val canalId: String,
    val canalNombre: String,
    val titulo: String,
    val miniatura: String?,
    val publicadoMs: Long,
    val vistas: Long = -1,
)

/** Video visto, con el punto donde te quedaste. */
@Entity(tableName = "historial", indices = [Index("vistoEn")])
data class Visto(
    @PrimaryKey val url: String,
    val titulo: String,
    val miniatura: String?,
    val autor: String?,
    val urlAutor: String?,
    val duracionSeg: Long,
    val posicionMs: Long = 0,
    val vistoEn: Long = System.currentTimeMillis(),
)

@Entity(tableName = "ver_mas_tarde", indices = [Index("agregadoEn")])
data class Guardado(
    @PrimaryKey val url: String,
    val titulo: String,
    val miniatura: String?,
    val autor: String?,
    val urlAutor: String?,
    val duracionSeg: Long,
    val agregadoEn: Long = System.currentTimeMillis(),
)

@Entity(tableName = "listas")
data class ListaPropia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val creadaEn: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lista_items", indices = [Index(value = ["listaId", "url"], unique = true)])
data class ItemLista(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listaId: Long,
    val url: String,
    val titulo: String,
    val miniatura: String?,
    val autor: String?,
    val urlAutor: String?,
    val duracionSeg: Long,
    val agregadoEn: Long = System.currentTimeMillis(),
)

/** Una lista propia con su cantidad de videos y la miniatura del último agregado. */
data class ResumenLista(val id: Long, val nombre: String, val cantidad: Int, val miniatura: String?)

fun Visto.aElemento() = Elemento.Video(titulo, url, miniatura, autor, urlAutor, duracionSeg, -1, null, false, false)
fun Guardado.aElemento() = Elemento.Video(titulo, url, miniatura, autor, urlAutor, duracionSeg, -1, null, false, false)
fun ItemLista.aElemento() = Elemento.Video(titulo, url, miniatura, autor, urlAutor, duracionSeg, -1, null, false, false)
fun Novedad.aElemento() = Elemento.Video(titulo, url, miniatura, canalNombre, null, 0, vistas, null, false, false)

fun Elemento.Video.aGuardado() = Guardado(url, titulo, miniatura, autor, urlAutor, duracionSeg)
fun Elemento.Video.aItem(listaId: Long) = ItemLista(listaId = listaId, url = url, titulo = titulo, miniatura = miniatura, autor = autor, urlAutor = urlAutor, duracionSeg = duracionSeg)
