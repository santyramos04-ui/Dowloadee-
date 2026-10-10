package com.santyramos.mirador.data.lib

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BibliotecaDao {
    // ---- Suscripciones ----
    @Query("SELECT * FROM suscripciones ORDER BY nombre COLLATE NOCASE")
    fun observarSuscripciones(): Flow<List<Suscripcion>>

    @Query("SELECT * FROM suscripciones")
    suspend fun suscripciones(): List<Suscripcion>

    @Query("SELECT COUNT(*) FROM suscripciones WHERE canalId = :canalId")
    fun estaSuscrito(canalId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun suscribir(s: Suscripcion)

    /** Importar no pisa el avatar ni la fecha de canales que ya tenías. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun suscribirVarias(lista: List<Suscripcion>): List<Long>

    @Query("UPDATE suscripciones SET avatar = :avatar, nombre = :nombre WHERE canalId = :canalId")
    suspend fun actualizarCanal(canalId: String, nombre: String, avatar: String?)

    @Query("DELETE FROM suscripciones WHERE canalId = :canalId")
    suspend fun anularSuscripcion(canalId: String)

    @Query("DELETE FROM novedades WHERE canalId = :canalId")
    suspend fun borrarNovedadesDe(canalId: String)

    // ---- Novedades (feed) ----
    @Query("SELECT * FROM novedades ORDER BY publicadoMs DESC LIMIT 300")
    fun observarNovedades(): Flow<List<Novedad>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarNovedades(lista: List<Novedad>)

    /** Conserva solo lo más reciente de cada canal para que la base no crezca sin fin. */
    @Query("DELETE FROM novedades WHERE publicadoMs < :antesDe")
    suspend fun limpiarNovedadesViejas(antesDe: Long)

    // ---- Historial ----
    @Query("SELECT * FROM historial ORDER BY vistoEn DESC LIMIT 500")
    fun observarHistorial(): Flow<List<Visto>>

    @Query("SELECT * FROM historial WHERE url = :url")
    suspend fun visto(url: String): Visto?

    @Query("SELECT url, posicionMs, duracionSeg FROM historial WHERE url IN (:urls)")
    suspend fun progresos(urls: List<String>): List<Progreso>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarVisto(v: Visto)

    @Query("UPDATE historial SET posicionMs = :posicionMs, vistoEn = :ahora WHERE url = :url")
    suspend fun guardarPosicion(url: String, posicionMs: Long, ahora: Long)

    @Query("DELETE FROM historial WHERE url = :url")
    suspend fun quitarDelHistorial(url: String)

    @Query("DELETE FROM historial")
    suspend fun borrarHistorial()

    // ---- Ver más tarde ----
    @Query("SELECT * FROM ver_mas_tarde ORDER BY agregadoEn DESC")
    fun observarGuardados(): Flow<List<Guardado>>

    @Query("SELECT COUNT(*) FROM ver_mas_tarde WHERE url = :url")
    fun estaGuardado(url: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(g: Guardado)

    @Query("DELETE FROM ver_mas_tarde WHERE url = :url")
    suspend fun quitarGuardado(url: String)

    // ---- Listas propias ----
    @Query(
        "SELECT l.id AS id, l.nombre AS nombre, COUNT(i.id) AS cantidad, " +
            "(SELECT miniatura FROM lista_items WHERE listaId = l.id ORDER BY agregadoEn DESC LIMIT 1) AS miniatura " +
            "FROM listas l LEFT JOIN lista_items i ON i.listaId = l.id GROUP BY l.id ORDER BY l.creadaEn DESC"
    )
    fun observarListas(): Flow<List<ResumenLista>>

    @Query("SELECT * FROM listas WHERE id = :id")
    suspend fun lista(id: Long): ListaPropia?

    @Query("SELECT * FROM lista_items WHERE listaId = :listaId ORDER BY agregadoEn ASC")
    fun observarItems(listaId: Long): Flow<List<ItemLista>>

    @Query("SELECT listaId FROM lista_items WHERE url = :url")
    fun listasQueTienen(url: String): Flow<List<Long>>

    @Insert
    suspend fun crearLista(l: ListaPropia): Long

    @Query("UPDATE listas SET nombre = :nombre WHERE id = :id")
    suspend fun renombrarLista(id: Long, nombre: String)

    @Query("DELETE FROM listas WHERE id = :id")
    suspend fun borrarLista(id: Long)

    @Query("DELETE FROM lista_items WHERE listaId = :id")
    suspend fun vaciarLista(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun agregarALista(i: ItemLista)

    @Query("DELETE FROM lista_items WHERE listaId = :listaId AND url = :url")
    suspend fun quitarDeLista(listaId: Long, url: String)
}

data class Progreso(val url: String, val posicionMs: Long, val duracionSeg: Long)
