package com.santyramos.mirador.escritorio

import java.io.File
import java.util.prefs.Preferences

/** Nombre y versión (los pone el empaquetado; en desarrollo, valores por defecto). */
object App {
    val nombre: String = System.getProperty("mirador.nombre") ?: "Mirador"
    val version: String = System.getProperty("mirador.version") ?: "desarrollo"
}

/**
 * yt-dlp, ffmpeg, mpv y QuickJS van dentro de la instalación (carpeta de recursos de la app).
 * En desarrollo se buscan en escritorio/recursos/windows.
 */
object Herramientas {
    private val carpeta: File by lazy {
        System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }
            ?: File("recursos/windows").absoluteFile
    }

    private fun exe(nombre: String): File = File(carpeta, if (esWindows) "$nombre.exe" else nombre)

    val esWindows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")
    val ytdlp: File get() = exe("yt-dlp")
    val ffmpeg: File get() = exe("ffmpeg")
    val mpv: File get() = exe("mpv")
    val quickjs: File get() = exe("qjs")
    val carpetaHerramientas: File get() = carpeta

    fun faltantes(): List<String> = listOf(ytdlp, ffmpeg, mpv, quickjs).filterNot { it.exists() }.map { it.name }
}

/** Ajustes guardados en el registro de Windows (Preferences de Java). */
object Ajustes {
    private val p = Preferences.userRoot().node("com/santyramos/mirador")

    var carpetaDescargas: File
        get() = File(p.get("carpeta", File(System.getProperty("user.home"), "Downloads${File.separator}${App.nombre}").path))
        set(v) = p.put("carpeta", v.path)

    /** Altura máxima al ver videos (1080 por defecto). */
    var calidadVer: Int
        get() = p.getInt("calidadVer", 1080)
        set(v) = p.putInt("calidadVer", v)

    var simultaneas: Int
        get() = p.getInt("simultaneas", 2).coerceIn(1, 4)
        set(v) = p.putInt("simultaneas", v.coerceIn(1, 4))
}
