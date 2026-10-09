package com.santyramos.mirador.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM descargas ORDER BY creadaEn DESC")
    fun observarTodas(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM descargas WHERE id = :id")
    suspend fun buscar(id: Long): DownloadEntity?

    @Query("SELECT * FROM descargas WHERE estado IN ('QUEUED','RUNNING') ORDER BY creadaEn ASC")
    suspend fun pendientes(): List<DownloadEntity>

    @Query("SELECT * FROM descargas WHERE estado = 'QUEUED' ORDER BY creadaEn ASC LIMIT 1")
    suspend fun siguienteEnCola(): DownloadEntity?

    @Query("SELECT COUNT(*) FROM descargas WHERE estado IN ('QUEUED','RUNNING')")
    fun observarActivas(): Flow<Int>

    @Insert
    suspend fun insertar(d: DownloadEntity): Long

    @Update
    suspend fun actualizar(d: DownloadEntity)

    @Query("UPDATE descargas SET estado = :estado WHERE id = :id")
    suspend fun cambiarEstado(id: Long, estado: String)

    @Query("UPDATE descargas SET estado='QUEUED' WHERE estado='RUNNING'")
    suspend fun devolverEnCursoALaCola()

    @Query("DELETE FROM descargas WHERE id = :id")
    suspend fun borrar(id: Long)

    @Query("DELETE FROM descargas WHERE estado IN ('DONE','ERROR','CANCELED')")
    suspend fun limpiarTerminadas()
}
