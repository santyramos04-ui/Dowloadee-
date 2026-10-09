package com.santyramos.mirador.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Guarda los archivos en Descargas/Mirador/{Videos,Audio,Imagenes} usando MediaStore:
 * no pide permisos de almacenamiento y los archivos se ven en «Archivos» y en la Galería.
 */
object MediaStoreSaver {
    enum class Carpeta(val ruta: String) {
        VIDEOS("Mirador/Videos"), AUDIO("Mirador/Audio"), IMAGENES("Mirador/Imagenes")
    }

    data class Guardado(val uri: Uri, val mime: String, val nombre: String)

    fun mimeDe(archivo: String): String {
        val ext = archivo.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    fun carpetaPara(mime: String): Carpeta = when {
        mime.startsWith("video/") -> Carpeta.VIDEOS
        mime.startsWith("audio/") -> Carpeta.AUDIO
        mime.startsWith("image/") -> Carpeta.IMAGENES
        else -> Carpeta.VIDEOS
    }

    fun guardar(context: Context, archivo: File, nombre: String = archivo.name, mime: String = mimeDe(archivo.name)): Guardado {
        val resolver = context.contentResolver
        val carpeta = carpetaPara(mime)
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + carpeta.ruta)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
            ?: error("No se pudo crear el archivo en Descargas")
        try {
            resolver.openOutputStream(uri)!!.use { salida -> archivo.inputStream().use { it.copyTo(salida, 256 * 1024) } }
            val listo = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, listo, null, null)
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return Guardado(uri, mime, nombre)
    }
}
