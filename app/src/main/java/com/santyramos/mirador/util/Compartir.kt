package com.santyramos.mirador.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Enviar archivos descargados a otras apps (WhatsApp, Instagram, TikTok, Telegram, correo…). */
object Compartir {
    /** Tipo que entienden las redes sociales: «video/*», «audio/*», «image/*» o el exacto si se conoce. */
    fun tipo(mime: String?): String = when {
        mime.isNullOrBlank() -> "*/*"
        mime.contains('/') -> mime
        else -> "*/*"
    }

    /** Intención de compartir el archivo (lista para el selector de Android). */
    fun intencion(uri: Uri, mime: String?, titulo: String?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = tipo(mime)
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!titulo.isNullOrBlank()) putExtra(Intent.EXTRA_TITLE, titulo)
            // ClipData + permiso de lectura: así la app que lo recibe puede abrir el archivo.
            clipData = ClipData.newRawUri(titulo ?: "archivo", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Selector de Android para enviar el archivo a otra app o red social. */
    fun selector(uri: Uri, mime: String?, titulo: String?): Intent =
        Intent.createChooser(intencion(uri, mime, titulo), "Compartir ${if (mime?.startsWith("audio/") == true) "audio" else if (mime?.startsWith("image/") == true) "imagen" else "video"}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun archivo(contexto: Context, uri: String, mime: String?, titulo: String?) {
        runCatching { contexto.startActivity(selector(Uri.parse(uri), mime, titulo)) }
            .onFailure { android.widget.Toast.makeText(contexto, "No se pudo compartir el archivo.", android.widget.Toast.LENGTH_SHORT).show() }
    }
}
