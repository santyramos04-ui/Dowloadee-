package com.santyramos.mirador.download

import android.content.Context
import java.io.File

/**
 * Sesión opcional (FASE 3): cookies de los sitios en formato Netscape, guardadas solo en el
 * almacenamiento privado de la app. Hoy devuelve null si no se activó.
 */
object Cookies {
    private const val NOMBRE = "cookies.txt"
    fun archivo(context: Context): File? {
        val f = File(context.filesDir, NOMBRE)
        return if (f.exists() && f.length() > 0 && activadas(context)) f else null
    }

    private fun activadas(context: Context): Boolean =
        context.getSharedPreferences("sesion", Context.MODE_PRIVATE).getBoolean("usar_cookies", false)
}
