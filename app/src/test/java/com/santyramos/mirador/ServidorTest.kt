package com.santyramos.mirador

import com.santyramos.mirador.servidor.Jellyfin
import com.santyramos.mirador.servidor.ServidorJf
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServidorTest {
    private val s = ServidorJf("http://192.168.1.20:8096", "Casa", "santy", "u1", "tok", "dev1")

    @Test fun completaLaDireccion() {
        assertEquals("http://192.168.1.20:8096", Jellyfin.normalizarDireccion("192.168.1.20"))
        assertEquals("http://192.168.1.20:8096", Jellyfin.normalizarDireccion(" http://192.168.1.20:8096/ "))
        assertEquals("http://mipc:9000", Jellyfin.normalizarDireccion("mipc:9000"))
        assertEquals("http://mipc", Jellyfin.normalizarDireccion("mipc:80")) // el 80 es el puerto normal de http
        assertEquals("https://cine.ejemplo.com", Jellyfin.normalizarDireccion("https://cine.ejemplo.com"))
        assertNull(Jellyfin.normalizarDireccion("   "))
    }

    @Test fun leeLaRespuestaDelWifi() {
        val e = Jellyfin.leerDescubrimiento("""{"Address":"http://192.168.1.20:8096","Id":"abc","Name":"MiniPC"}""")!!
        assertEquals("MiniPC", e.nombre)
        assertEquals("http://192.168.1.20:8096", e.direccion)
        assertNull(Jellyfin.leerDescubrimiento("hola"))
    }

    @Test fun leeLosElementos() {
        val cuerpo = """{"Items":[
            {"Id":"m1","Name":"Coco","Type":"Movie","ProductionYear":2017,"RunTimeTicks":63000000000,
             "UserData":{"PlaybackPositionTicks":31500000000,"Played":false},"BackdropImageTags":["x"]},
            {"Id":"e1","Name":"Piloto","Type":"Episode","SeriesName":"Serie","SeriesId":"s1","ParentIndexNumber":1,"IndexNumber":3},
            {"Id":"s1","Name":"Serie","Type":"Series"},
            {"Id":"f1","Name":"Vacaciones","Type":"Folder"},
            {"Name":"sin id"}]}"""
        val items = Jellyfin.leerItems(Json.parseToJsonElement(cuerpo))
        assertEquals(4, items.size)
        val coco = items[0]
        assertEquals(2017, coco.anio)
        assertEquals(6_300_000L, coco.duracionMs)
        assertEquals(0.5f, coco.avance, 0.001f)
        assertTrue(coco.reproducible && coco.tieneFondo)
        assertEquals("T1 · E3", items[1].etiquetaEpisodio)
        assertTrue(items[2].esSerie && !items[2].reproducible)
        assertTrue(items[3].esCarpeta)
        // Una lista directa (como /Items/Latest) también se lee.
        assertEquals(1, Jellyfin.leerItems(Json.parseToJsonElement("""[{"Id":"a","Type":"Video"}]""")).size)
    }

    @Test fun armaLasDirecciones() {
        val original = Jellyfin.urlOriginal(s, "m1")
        assertTrue(original.startsWith("http://192.168.1.20:8096/Videos/m1/stream?static=true"))
        assertTrue("api_key=tok" in original)
        val convertida = Jellyfin.urlConvertida(s, "m1", altoMax = 720)
        assertTrue(convertida.startsWith("http://192.168.1.20:8096/Videos/m1/master.m3u8?"))
        assertTrue("VideoCodec=h264" in convertida && "MaxHeight=720" in convertida)
        val pagina = Jellyfin.pagina(s, "m1")
        assertEquals("m1", Jellyfin.idDePagina(s, pagina))
        assertNull(Jellyfin.idDePagina(s, "https://www.youtube.com/watch?v=x"))
    }

    @Test fun guardaLaSesion() {
        assertEquals(s, ServidorJf.deTexto(s.aTexto()))
        assertNull(ServidorJf.deTexto("basura"))
        assertTrue(Jellyfin.encabezado("dev1", "tok").contains("Token=\"tok\""))
        assertFalse(Jellyfin.encabezado("dev1").contains("Token"))
        assertTrue(Jellyfin.cuerpoAvance("m1", 1000, true).contains("\"PositionTicks\":10000000"))
    }
}
