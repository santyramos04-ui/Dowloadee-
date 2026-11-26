package com.santyramos.mirador.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import com.santyramos.mirador.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Portadas y miniaturas para los widgets: se bajan pequeñas, se recortan y se les redondean las
 * esquinas aquí (los widgets no pueden redondear imágenes por sí mismos). Se guardan unas pocas en memoria.
 */
object Portadas {
    private val memoria = LruCache<String, Bitmap>(32)

    /** [ancho]×[alto] en píxeles, recortada al centro; [radio] en píxeles (o círculo). */
    suspend fun cargar(context: Context, url: String?, ancho: Int, alto: Int, radio: Float, circulo: Boolean = false): Bitmap? {
        if (url.isNullOrBlank()) return null
        val clave = "$url|$ancho|$alto|$radio|$circulo"
        memoria.get(clave)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = if (url.startsWith("http")) {
                    Http.client.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null }
                } else {
                    context.contentResolver.openInputStream(Uri.parse(url))?.use { it.readBytes() }
                } ?: return@runCatching null
                val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
                var muestra = 1
                while (limites.outWidth / (muestra * 2) >= ancho && limites.outHeight / (muestra * 2) >= alto) muestra *= 2
                val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = muestra })
                    ?: return@runCatching null
                recortar(original, ancho, alto, radio, circulo).also { if (it !== original) original.recycle() }
            }.getOrNull()?.also { memoria.put(clave, it) }
        }
    }

    private fun recortar(b: Bitmap, ancho: Int, alto: Int, radio: Float, circulo: Boolean): Bitmap {
        val salida = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val escala = maxOf(ancho.toFloat() / b.width, alto.toFloat() / b.height)
        val m = Matrix().apply {
            setScale(escala, escala)
            postTranslate((ancho - b.width * escala) / 2f, (alto - b.height * escala) / 2f)
        }
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
        }
        val lienzo = Canvas(salida)
        val r = RectF(0f, 0f, ancho.toFloat(), alto.toFloat())
        if (circulo) lienzo.drawOval(r, pincel) else lienzo.drawRoundRect(r, radio, radio, pincel)
        return salida
    }
}
