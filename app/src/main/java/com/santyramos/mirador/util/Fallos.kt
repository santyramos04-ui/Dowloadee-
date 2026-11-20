package com.santyramos.mirador.util

import android.content.Context
import android.os.Build
import com.santyramos.mirador.BuildConfig
import java.io.File

/**
 * Si la app se cierra por un error, guarda qué pasó en un archivo del celular (sin datos personales:
 * solo la versión, el modelo del celular y el detalle técnico del error). Al abrir la app otra vez se
 * ofrece compartir ese informe para poder arreglarlo.
 */
object Fallos {
    private fun archivo(c: Context) = File(c.filesDir, "ultimo_fallo.txt")

    fun instalar(app: Context) {
        val previo = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            runCatching { archivo(app).writeText(informe(hilo.name, error)) }
            previo?.uncaughtException(hilo, error)
        }
    }

    internal fun informe(hilo: String, error: Throwable): String = buildString {
        appendLine("${Marca.NOMBRE} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Celular: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Hilo: $hilo")
        appendLine()
        append(error.stackTraceToString().take(15_000))
    }

    /** El informe del último cierre inesperado, si hay uno sin revisar. */
    fun pendiente(c: Context): String? = runCatching { archivo(c).takeIf { it.exists() }?.readText() }.getOrNull()

    fun descartar(c: Context) { runCatching { archivo(c).delete() } }
}
