package com.santyramos.mirador.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.santyramos.mirador.data.db.AppDatabase
import com.santyramos.mirador.data.db.DownloadEntity
import kotlinx.coroutines.flow.first

/**
 * Lo que se ve al navegar RACQ desde Android Auto (o un reloj, o cualquier app que explore música):
 * tu música descargada, tus videos descargados (suenan como audio) y la cola.
 */
object BibliotecaAuto {
    const val RAIZ = "raiz"
    const val MUSICA = "musica"
    const val VIDEOS = "videos"
    const val COLA = "cola"
    private const val DESCARGA = "d:"
    private const val EN_COLA = "c:"
    const val EXTRA_URL = "racq.url"
    private const val MAXIMO = 500

    fun raiz(): MediaItem = carpeta(RAIZ, "RACQ")

    fun carpetas(): List<MediaItem> = listOf(
        carpeta(MUSICA, "Música descargada"),
        carpeta(VIDEOS, "Videos descargados"),
        carpeta(COLA, "Cola"),
    )

    private fun carpeta(id: String, titulo: String) = MediaItem.Builder().setMediaId(id).setMediaMetadata(
        MediaMetadata.Builder().setTitle(titulo).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
    ).build()

    /** Los hijos de una carpeta. Las descargas, de la más nueva a la más vieja. */
    suspend fun hijos(context: Context, id: String): List<MediaItem> = when (id) {
        RAIZ -> carpetas()
        MUSICA -> reproducibles(context).filter { it.archivoMime?.startsWith("audio/") == true }.take(MAXIMO).map(::deDescarga)
        VIDEOS -> reproducibles(context).filter { it.archivoMime?.startsWith("video/") == true }.take(MAXIMO).map(::deDescarga)
        COLA -> if (VideoController.iniciado) VideoController.cola.value.mapIndexed { i, e -> deCola(i, e) } else emptyList()
        else -> emptyList()
    }

    private suspend fun reproducibles(context: Context): List<DownloadEntity> =
        AppDatabase.get(context).descargas().observarReproducibles().first().asReversed()

    /** Completa lo que pide el carro (solo trae el id) con el archivo y los datos. */
    suspend fun resolver(context: Context, item: MediaItem): MediaItem? {
        val id = item.mediaId
        return when {
            id.startsWith(DESCARGA) -> id.removePrefix(DESCARGA).toLongOrNull()
                ?.let { n -> reproducibles(context).firstOrNull { it.id == n } }?.let(::deDescarga)
            id.startsWith(EN_COLA) -> item
            else -> null
        }
    }

    private fun deDescarga(d: DownloadEntity): MediaItem = MediaItem.Builder()
        .setMediaId(DESCARGA + d.id)
        .setUri(d.archivoUri?.let(Uri::parse))
        .setMimeType(d.archivoMime)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(d.titulo).setArtist(d.autor)
                .setArtworkUri(d.miniatura?.let(Uri::parse))
                .setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(if (d.archivoMime?.startsWith("audio/") == true) MediaMetadata.MEDIA_TYPE_MUSIC else MediaMetadata.MEDIA_TYPE_VIDEO)
                .setExtras(Bundle().apply { putString(EXTRA_URL, d.url) })
                .build(),
        ).build()

    private fun deCola(i: Int, e: ElementoCola): MediaItem = MediaItem.Builder()
        .setMediaId(EN_COLA + i)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(e.titulo).setArtist(e.autor)
                .setArtworkUri(e.miniatura?.let(Uri::parse))
                .setIsBrowsable(false).setIsPlayable(true).build(),
        ).build()

    /** Del elemento que eligieron en el carro a la cola de la app. */
    internal fun aElemento(item: MediaItem): ElementoCola? {
        val uri = item.localConfiguration?.uri?.toString() ?: return null
        val m = item.mediaMetadata
        return ElementoCola(
            url = m.extras?.getString(EXTRA_URL) ?: item.mediaId,
            titulo = m.title?.toString().orEmpty(), autor = m.artist?.toString(),
            miniatura = m.artworkUri?.toString(), archivoUri = uri, archivoMime = item.localConfiguration?.mimeType,
        )
    }

    /** Reproduce lo elegido: si es de la cola salta a ese punto; si son descargas, las pone como lista. */
    fun reproducir(items: List<MediaItem>, desde: Int) {
        val primero = items.getOrNull(desde) ?: items.firstOrNull() ?: return
        if (primero.mediaId.startsWith(EN_COLA)) {
            primero.mediaId.removePrefix(EN_COLA).toIntOrNull()?.let(VideoController::reproducirDeCola)
            return
        }
        val lista = items.mapNotNull(::aElemento)
        if (lista.isEmpty()) return
        VideoController.reproducirLista(lista, lista.indexOfFirst { it.archivoUri == primero.localConfiguration?.uri?.toString() }.coerceAtLeast(0))
    }
}
