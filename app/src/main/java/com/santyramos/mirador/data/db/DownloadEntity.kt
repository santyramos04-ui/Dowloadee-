package com.santyramos.mirador.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, DONE, ERROR, CANCELED }

@Entity(tableName = "descargas")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val titulo: String,
    val miniatura: String? = null,
    val autor: String? = null,
    /** Nombre de [com.santyramos.mirador.download.Preset]. */
    val preset: String,
    val estado: String = DownloadStatus.QUEUED.name,
    val progreso: Float = 0f,
    val bytesDescargados: Long = 0,
    val bytesTotales: Long = 0,
    val velocidadBps: Long = 0,
    val etaSeg: Long = 0,
    val error: String? = null,
    /** content:// del archivo ya guardado en Descargas/Mirador. */
    val archivoUri: String? = null,
    val archivoMime: String? = null,
    val creadaEn: Long = System.currentTimeMillis(),
    val terminadaEn: Long? = null,
    /** true = solo el video del enlace aunque venga de una lista. */
    val sinLista: Boolean = true,
    /** Para descargas de archivos sueltos (imágenes/videos directos): JSON con la lista. */
    val extraJson: String? = null,
) {
    val status: DownloadStatus get() = runCatching { DownloadStatus.valueOf(estado) }.getOrDefault(DownloadStatus.ERROR)
}
