package com.santyramos.mirador.escritorio

import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * La corre el CI con la app ya empaquetada (`Mirador.exe --autoprueba=informe.txt`): comprueba que
 * están las herramientas, que ffmpeg convierte audio y que la búsqueda carga. Escribe un informe.
 */
object Autoprueba {
    fun correr(informe: File): Int {
        val l = mutableListOf<String>()
        var ok = true
        fun paso(nombre: String, critico: Boolean = true, bloque: () -> String) {
            val r = runCatching(bloque)
            l += "${if (r.isSuccess) "OK" else if (critico) "FALLA" else "AVISO"} $nombre: ${r.getOrElse { it.javaClass.simpleName + ": " + it.message }}"
            if (r.isFailure && critico) ok = false
        }
        fun ejecutar(vararg cmd: String): String {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val salida = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "código ${p.exitValue()}: ${salida.takeLast(300)}" }
            return salida.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(120)
        }
        l += "${App.nombre} ${App.version} · Java ${System.getProperty("java.version")} · ${System.getProperty("os.name")}"
        l += "Herramientas en: ${Herramientas.carpetaHerramientas}"
        paso("herramientas") { Herramientas.faltantes().let { check(it.isEmpty()) { "faltan $it" }; "todas presentes" } }
        paso("yt-dlp") { ejecutar(Herramientas.ytdlp.path, "--version") }
        paso("mpv") { ejecutar(Herramientas.mpv.path, "--version") }
        paso("ffmpeg convierte a MP3") {
            val salida = File(System.getProperty("java.io.tmpdir"), "mirador-prueba.mp3").apply { delete() }
            ejecutar(Herramientas.ffmpeg.path, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-c:a", "libmp3lame", salida.path)
            check(salida.length() > 1000) { "archivo vacío" }
            "${salida.length()} bytes"
        }
        paso("argumentos de descarga") { Pedido.argumentos("https://youtu.be/jNQXAC9IVRw", Formato.M4A, Ajustes.carpetaDescargas).size.toString() + " argumentos" }
        // YouTube suele bloquear las IP de los servidores de GitHub: no se cuenta como falla.
        paso("búsqueda en YouTube", critico = false) { runBlocking { "${Youtube.buscar("documental naturaleza").videos.size} resultados" } }
        l += if (ok) "RESULTADO: OK" else "RESULTADO: FALLA"
        informe.writeText(l.joinToString("\n"))
        return if (ok) 0 else 1
    }
}
