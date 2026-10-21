package com.santyramos.mirador.escritorio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EscritorioTest {
    @Test fun leeElProgresoDeYtdlp() {
        val p = SalidaYtdlp.progreso("[mirador]5242880|10485760|NA|1048576.5|5")!!
        assertEquals(0.5f, p.fraccion)
        assertEquals(1048576L, p.velocidad)
        val estimado = SalidaYtdlp.progreso("[download] [mirador]100|NA|400|NA|NA")!!
        assertEquals(400L, estimado.total) // sin total exacto se usa el estimado
        assertNull(SalidaYtdlp.progreso("[youtube] abc: Downloading webpage"))
    }

    @Test fun tituloYArchivo() {
        assertEquals("Mi video", SalidaYtdlp.titulo("[titulo]Mi video"))
        assertEquals("C:\\Descargas\\a.mp4", SalidaYtdlp.archivo("[archivo]C:\\Descargas\\a.mp4"))
        assertNull(SalidaYtdlp.titulo("otra cosa"))
    }

    @Test fun erroresEnPalabrasSencillas() {
        assertEquals("El video es privado.", SalidaYtdlp.mensajeError(listOf("ERROR: [youtube] x: Private video")))
        assertEquals("Ese enlace no es de un sitio compatible.", SalidaYtdlp.mensajeError(listOf("ERROR: Unsupported URL: https://a.b")))
    }

    @Test fun detectaListas() {
        assertTrue(Pedido.esLista("https://www.youtube.com/playlist?list=PL123"))
        assertFalse(Pedido.esLista("https://www.youtube.com/watch?v=abc&list=PL123")) // un video dentro de una lista: solo el video
        assertFalse(Pedido.esLista("https://youtu.be/abc"))
    }

    @Test fun argumentosSegunFormato() {
        val carpeta = File("descargas")
        val mp3 = Pedido.argumentos("https://youtu.be/abc", Formato.MP3, carpeta)
        assertTrue(mp3.containsAll(listOf("-x", "--audio-format", "mp3", "--embed-thumbnail", "--no-playlist")))
        assertEquals("https://youtu.be/abc", mp3.last())
        val video = Pedido.argumentos("https://www.youtube.com/playlist?list=PL1", Formato.P720, carpeta)
        assertTrue(video.contains("res:720,vcodec:h264,acodec:aac"))
        assertTrue(video.contains("--yes-playlist"))
        assertTrue(video.any { it.contains("playlist_index") })
    }

    @Test fun formatos() {
        assertEquals("1,5 MB", Formatos.bytes(1572864))
        assertEquals("1:02:03", Formatos.duracion(3723))
        assertEquals("4:05", Formatos.duracion(245))
    }
}
