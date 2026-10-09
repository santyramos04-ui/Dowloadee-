package com.santyramos.mirador

import com.santyramos.mirador.download.DirectResolver
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.download.MediaInfoParser
import com.santyramos.mirador.download.Preset
import com.santyramos.mirador.download.ProgressParser
import com.santyramos.mirador.download.SizeEstimator
import com.santyramos.mirador.download.UrlTools
import com.santyramos.mirador.player.PlayerData
import com.santyramos.mirador.update.AppUpdater
import com.santyramos.mirador.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlToolsTest {
    @Test fun sacaElPrimerEnlaceDeUnTextoCompartido() {
        assertEquals("https://x.com/user/status/123456789", UrlTools.primerEnlace("Mira esto https://x.com/user/status/123456789 jaja"))
        assertEquals("https://youtu.be/abc", UrlTools.primerEnlace("(https://youtu.be/abc)."))
        assertNull(UrlTools.primerEnlace("sin enlaces"))
        assertNull(UrlTools.primerEnlace(null))
    }

    @Test fun detectaSitios() {
        assertTrue(UrlTools.esYoutube("https://www.youtube.com/watch?v=jNQXAC9IVRw"))
        assertTrue(UrlTools.esYoutube("https://youtu.be/jNQXAC9IVRw"))
        assertTrue(UrlTools.esYoutube("https://m.youtube.com/shorts/abc"))
        assertFalse(UrlTools.esYoutube("https://notyoutube.com/x"))
        assertTrue(UrlTools.esX("https://x.com/a/status/1234567"))
        assertTrue(UrlTools.esX("https://twitter.com/a/status/1234567"))
        assertTrue(UrlTools.esInstagram("https://www.instagram.com/p/ABC/"))
    }

    @Test fun detectaVideoDentroDeUnaLista() {
        assertTrue(UrlTools.videoDentroDeLista("https://www.youtube.com/watch?v=abc&list=PLxyz"))
        assertTrue(UrlTools.videoDentroDeLista("https://youtu.be/abc?list=PLxyz"))
        assertFalse(UrlTools.videoDentroDeLista("https://www.youtube.com/watch?v=abc"))
        assertFalse(UrlTools.videoDentroDeLista("https://www.youtube.com/playlist?list=PLxyz"))
    }

    @Test fun extraeIdsDeX_eInstagram() {
        assertEquals("1400547400690286594", UrlTools.idTuit("https://x.com/NASA/status/1400547400690286594?s=20"))
        assertEquals("Chunk8-jurw", UrlTools.codigoInstagram("https://www.instagram.com/reel/Chunk8-jurw/"))
        assertEquals("BQ0eAlwhDrw", UrlTools.codigoInstagram("https://www.instagram.com/p/BQ0eAlwhDrw/?igsh=1"))
    }

    @Test fun nombresSeguros() {
        assertEquals("a_b_c", UrlTools.nombreSeguro("a/b:c"))
        assertEquals("archivo", UrlTools.nombreSeguro("   "))
    }
}

class ErrorMapperTest {
    @Test fun traduceErroresComunes() {
        assertTrue(ErrorMapper.mensaje("ERROR: Unable to resolve host name").contains("conexión a internet"))
        assertTrue(ErrorMapper.mensaje("ERROR: [youtube] abc: Private video. Sign in if you've been granted access").contains("privado"))
        assertTrue(ErrorMapper.mensaje("ERROR: Sign in to confirm your age").contains("+18"))
        assertTrue(ErrorMapper.mensaje("ERROR: Unsupported URL: https://example.com").contains("no es compatible"))
        assertTrue(ErrorMapper.mensaje("OSError: [Errno 28] No space left on device").contains("espacio"))
        assertTrue(ErrorMapper.mensaje("ERROR: Video unavailable").contains("ya no está disponible"))
        assertTrue(ErrorMapper.mensaje("ERROR: [Twitter] 123: No video could be found in this tweet").contains("no tiene un video"))
        assertTrue(ErrorMapper.mensaje("ERROR: HTTP Error 403: Forbidden").contains("403"))
        assertTrue(ErrorMapper.mensaje("ERROR: Sign in to confirm you’re not a bot").contains("robot"))
    }

    @Test fun mensajeGenericoSiNoLoConoce() {
        assertTrue(ErrorMapper.mensaje("ERROR: algo raro nunca visto").startsWith("No se pudo descargar: algo raro"))
    }
}

class ProgressParserTest {
    @Test fun leeLaPlantillaDeProgreso() {
        val a = ProgressParser.parsear("MIR|1048576|4194304|NA|524288.5|6")!!
        assertEquals(1048576L, a.descargados)
        assertEquals(4194304L, a.totales)
        assertEquals(524288L, a.velocidadBps)
        assertEquals(6L, a.etaSeg)
        assertEquals(25f, a.porcentaje, 0.01f)
    }

    @Test fun usaElTotalEstimadoSiNoHayTotal() {
        val a = ProgressParser.parsear("MIR|1000|NA|2000|NA|NA")!!
        assertEquals(2000L, a.totales)
        assertEquals(50f, a.porcentaje, 0.01f)
    }

    @Test fun ignoraOtrasLineas() {
        assertNull(ProgressParser.parsear("[youtube] Extracting URL"))
        assertTrue(ProgressParser.esProcesando("[Merger] Merging formats into \"x.mp4\""))
        assertFalse(ProgressParser.esProcesando("[download] 10%"))
    }
}

class MediaInfoTest {
    private val ejemplo = """
        WARNING: algo
        {"id":"abc","title":"Video de prueba","thumbnail":"https://i/x.jpg","duration":100,"uploader":"Canal","extractor_key":"Youtube","webpage_url":"https://www.youtube.com/watch?v=abc",
         "formats":[
          {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","tbr":128.0,"filesize":1600000},
          {"format_id":"137","ext":"mp4","height":1080,"vcodec":"avc1.640028","acodec":"none","tbr":4000.0,"filesize":50000000},
          {"format_id":"248","ext":"webm","height":1080,"vcodec":"vp9","acodec":"none","tbr":2500.0,"filesize":31000000},
          {"format_id":"136","ext":"mp4","height":720,"vcodec":"avc1.4d401f","acodec":"none","tbr":2000.0,"filesize":25000000},
          {"format_id":"135","ext":"mp4","height":480,"vcodec":"avc1.4d401e","acodec":"none","tbr":1000.0},
          {"format_id":"sb0","ext":"mhtml","height":90,"vcodec":"none","acodec":"none"}
         ]}
    """.trimIndent()

    @Test fun leeElJson() {
        val info = MediaInfoParser.parsear(ejemplo, "https://x")
        assertEquals("Video de prueba", info.titulo)
        assertEquals(100L, info.duracionSeg)
        assertEquals(5, info.formatos.size) // sin mhtml
        assertTrue(info.tieneVideo)
    }

    @Test fun estimaElPesoPorOpcion() {
        val info = MediaInfoParser.parsear(ejemplo, "https://x")
        // Mejor: 1080p H.264 (50 MB) + audio m4a (1.6 MB)
        assertEquals(51_600_000L, SizeEstimator.estimar(info, Preset.MEJOR))
        assertEquals(26_600_000L, SizeEstimator.estimar(info, Preset.P720))
        // 480p sin tamaño: tbr * duración
        assertEquals(1000_000L / 8 * 100 + 1_600_000L, SizeEstimator.estimar(info, Preset.P480))
        // MP3 192 kbps * 100 s
        assertEquals(2_400_000L, SizeEstimator.estimar(info, Preset.MP3))
        assertEquals(1080, SizeEstimator.alturaMaxima(info))
    }

    @Test fun listasMuestranElPrimerElemento() {
        val lista = """{"_type":"playlist","title":"Carrusel","entries":[{"id":"1","title":"Foto 1","ext":"jpg","url":"https://a/1.jpg"},{"id":"2","title":"Foto 2"}]}"""
        val info = MediaInfoParser.parsear(lista, "https://x")
        assertTrue(info.esLista)
        assertEquals(2, info.cantidadLista)
        assertEquals("Carrusel", info.titulo)
    }
}

class ActualizacionTest {
    @Test fun comparaVersiones() {
        assertTrue(AppUpdater.esMasNueva("v1.0.58", "1.0.57"))
        assertTrue(AppUpdater.esMasNueva("1.1.0", "1.0.99"))
        assertFalse(AppUpdater.esMasNueva("v1.0.57", "1.0.57"))
        assertFalse(AppUpdater.esMasNueva("1.0.9", "1.0.57"))
    }

    @Test fun leeLaRespuestaDeGitHub() {
        val json = """{"tag_name":"v1.0.60","body":"Cambios","assets":[{"name":"Mirador-1.0.60.apk","browser_download_url":"https://github.com/x/y/releases/download/v1.0.60/Mirador-1.0.60.apk","size":123456}]}"""
        val nueva = AppUpdater.interpretar(json, "1.0.57")
        assertNotNull(nueva)
        assertEquals("1.0.60", nueva!!.version)
        assertEquals(123456L, nueva.tamano)
        assertNull(AppUpdater.interpretar(json, "1.0.60"))
    }
}

class FormatoTest {
    @Test fun formatos() {
        assertEquals("1:05", Format.duracion(65))
        assertEquals("1:01:01", Format.duracion(3661))
        assertEquals("1.5 MB", Format.bytes(1_572_864))
        assertEquals("—", Format.bytes(null))
    }
}

class RangoYouTubeTest {
    @Test fun construyeElParametroRange() {
        assertNull(PlayerData.construirRango(0, -1L))
        assertEquals("&range=100-199", PlayerData.construirRango(100, 100))
        assertEquals("&range=500-", PlayerData.construirRango(500, -1L))
    }
}

class TokenXTest {
    @Test fun tokenNoVacio() {
        val t = DirectResolver.tokenSyndication("1400547400690286594")
        assertTrue(t.isNotEmpty())
        assertFalse(t.contains("."))
        assertFalse(t.contains("0"))
    }
}
