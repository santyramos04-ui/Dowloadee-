package com.santyramos.mirador.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Miniatura del punto al que arrastras la barra (como YouTube).
 * - Videos de YouTube: YouTube publica «hojas» con cuadros pequeños de todo el video; se baja la hoja
 *   y se recorta el cuadro de ese segundo.
 * - Archivos descargados: se saca el cuadro directamente del archivo.
 */
object Miniaturas {
    private val hojas = LruCache<String, Bitmap>(16)
    private val candado = Mutex()
    private var lectorUri: String? = null
    private var lector: MediaMetadataRetriever? = null

    /** El juego de cuadros más nítido que siga siendo liviano (hasta ~320 px de ancho). */
    internal fun elegir(juegos: List<Frameset>): Frameset? =
        juegos.filter { it.frameWidth in 1..320 && it.urls.isNotEmpty() }.maxByOrNull { it.frameWidth }
            ?: juegos.filter { it.urls.isNotEmpty() }.minByOrNull { it.frameWidth }

    suspend fun cuadro(contexto: Context, info: StreamInfo?, archivoUri: String?, ms: Long): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (info != null) deYoutube(info, ms)
            else if (archivoUri != null && archivoUri.startsWith("content://")) deArchivo(contexto, archivoUri, ms)
            else null
        }.getOrNull()
    }

    private fun deYoutube(info: StreamInfo, ms: Long): Bitmap? {
        val juego = elegir(info.previewFrames.orEmpty()) ?: return null
        // [hoja, izquierda, arriba, derecha, abajo]
        val b = juego.getFrameBoundsAt(ms.coerceAtLeast(0))
        val url = juego.urls.getOrNull(b[0]) ?: return null
        val hoja = hojas.get(url) ?: run {
            val bytes = Http.client.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null } ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { hojas.put(url, it) } ?: return null
        }
        val x = b[1].coerceIn(0, hoja.width - 1); val y = b[2].coerceIn(0, hoja.height - 1)
        val w = (b[3] - b[1]).coerceIn(1, hoja.width - x); val h = (b[4] - b[2]).coerceIn(1, hoja.height - y)
        return Bitmap.createBitmap(hoja, x, y, w, h)
    }

    private suspend fun deArchivo(contexto: Context, uri: String, ms: Long): Bitmap? = candado.withLock {
        if (lectorUri != uri) {
            runCatching { lector?.release() }
            lector = MediaMetadataRetriever().apply { setDataSource(contexto, Uri.parse(uri)) }
            lectorUri = uri
        }
        val cuadro = lector?.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@withLock null
        // Se achica para que sea liviano (como las de YouTube).
        val ancho = 320
        if (cuadro.width <= ancho) cuadro
        else Bitmap.createScaledBitmap(cuadro, ancho, (cuadro.height * ancho / cuadro.width).coerceAtLeast(1), true).also { if (it !== cuadro) cuadro.recycle() }
    }

    /** Suelta el lector del archivo (al cerrar el reproductor). */
    fun liberar() {
        runCatching { lector?.release() }
        lector = null; lectorUri = null
    }
}
