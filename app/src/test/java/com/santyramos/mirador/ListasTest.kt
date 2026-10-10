package com.santyramos.mirador

import com.santyramos.mirador.download.MediaInfoParser
import com.santyramos.mirador.download.UrlTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListasTest {
    @Test fun variosEnlacesPegados() {
        val texto = """
            https://www.youtube.com/watch?v=aaaaaaaaaaa
            mira este: https://x.com/user/status/1234567890 y https://www.tiktok.com/@a/video/123456
            https://www.youtube.com/watch?v=aaaaaaaaaaa
        """.trimIndent()
        val e = UrlTools.todosEnlaces(texto)
        assertEquals(3, e.size) // sin repetir
        assertEquals("https://x.com/user/status/1234567890", e[1])
        assertTrue(UrlTools.todosEnlaces("nada").isEmpty())
    }

    @Test fun detectaListasDeYoutube() {
        assertTrue(UrlTools.esListaYoutube("https://www.youtube.com/playlist?list=PLabc123"))
        assertFalse(UrlTools.esListaYoutube("https://www.youtube.com/watch?v=abc&list=PLabc123"))
        assertEquals("https://www.youtube.com/playlist?list=PLabc123", UrlTools.urlDeLista("https://www.youtube.com/watch?v=abc&list=PLabc123&index=3"))
        // Las mezclas automáticas (RD...) solo existen dentro del video
        val mezcla = "https://www.youtube.com/watch?v=abc&list=RDabc"
        assertEquals(mezcla, UrlTools.urlDeLista(mezcla))
    }

    @Test fun leeUnaListaPlana() {
        val json = """
            {"_type":"playlist","title":"Mi lista","uploader":"Canal","entries":[
              {"id":"aaaaaaaaaaa","url":"https://www.youtube.com/watch?v=aaaaaaaaaaa","title":"Uno","duration":65,"thumbnails":[{"url":"https://i/1.jpg"}]},
              {"id":"bbbbbbbbbbb","title":"Dos"},
              {"id":"ccccccccccc","title":"[Private video]"},
              {"id":"ddddddddddd","title":"[Deleted video]"}
            ]}
        """.trimIndent()
        val l = MediaInfoParser.parsearLista(json)
        assertEquals("Mi lista", l.titulo)
        assertEquals(2, l.entradas.size) // sin privados ni borrados
        assertEquals("https://www.youtube.com/watch?v=bbbbbbbbbbb", l.entradas[1].url)
        assertEquals(65L, l.entradas[0].duracionSeg)
        assertEquals("https://i/1.jpg", l.entradas[0].miniatura)
    }
}

class SpotifyTest {
    @Test fun spotifyNoSeDescargaYSeExplica() {
        assertTrue(UrlTools.esSpotify("https://open.spotify.com/track/abc123"))
        assertFalse(UrlTools.esSpotify("https://www.youtube.com/watch?v=abc"))
        val msg = com.santyramos.mirador.download.ErrorMapper.mensaje(IllegalStateException("spotify-drm"))
        assertTrue(msg.contains("DRM") && msg.contains("YouTube"))
    }
}

class AudioYSpotifyTest {
    @Test fun consultaDeSpotify() {
        val html = """<meta property="og:title" content="Canción de prueba"/><meta property="og:description" content="Artista X · Álbum · Canción de prueba · 2020"/>"""
        assertEquals("Artista X Canción de prueba", com.santyramos.mirador.download.SpotifyPuente.consultaDe(html))
        assertEquals("Solo título", com.santyramos.mirador.download.SpotifyPuente.consultaDe("""<meta property="og:title" content="Solo título"/>"""))
    }

    @Test fun formatosDeAudio() {
        val audio = com.santyramos.mirador.download.Preset.entries.filter { it.esAudio }
        assertTrue(audio.all { it.formatoAudio != null && it.bitsPorSeg > 0 })
        assertEquals(listOf("mp3", "mp3", "m4a", "opus", "flac"), audio.map { it.formatoAudio })
        assertFalse(com.santyramos.mirador.download.Preset.FLAC.portada)
    }
}
