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
    /** Copia del último informe ya revisado: queda disponible en Ajustes para compartirlo cuando quieras. */
    private fun revisado(c: Context) = File(c.filesDir, "ultimo_fallo_revisado.txt")

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

    /** Ya se mostró el aviso: el informe pasa a «revisado» (no se pierde, sigue en Ajustes). */
    fun descartar(c: Context) { runCatching { val a = archivo(c); if (a.exists()) { a.copyTo(revisado(c), overwrite = true); a.delete() } } }

    /** El último informe (pendiente o ya revisado), para compartirlo desde Ajustes. */
    fun ultimo(c: Context): String? = pendiente(c) ?: runCatching { revisado(c).takeIf { it.exists() }?.readText() }.getOrNull()

    /**
     * Guarda el informe como archivo de texto en Descargas/RACQ/Informes, para que no se pierda aunque no
     * se comparta en el momento. Devuelve el nombre del archivo (o null si no se pudo).
     */
    fun guardarEnDescargas(c: Context, texto: String): String? = runCatching {
        val fecha = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())
        val nombre = "${Marca.NOMBRE}-fallo-$fecha.txt"
        val tmp = File(c.cacheDir, nombre).apply { writeText(texto) }
        try { com.santyramos.mirador.download.MediaStoreSaver.guardar(c, tmp, nombre = nombre, mime = "text/plain") } finally { tmp.delete() }
        nombre
    }.getOrNull()

    /** Abre el menú de Android para enviar el informe (WhatsApp, correo…). */
    fun compartir(c: Context, texto: String) {
        runCatching {
            c.startActivity(
                android.content.Intent.createChooser(
                    android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Informe de fallo de ${Marca.NOMBRE}")
                        .putExtra(android.content.Intent.EXTRA_TEXT, texto),
                    "Compartir informe",
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
