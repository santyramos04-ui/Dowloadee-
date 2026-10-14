package com.santyramos.mirador

import com.santyramos.mirador.tv.Dlna
import com.santyramos.mirador.tv.RangoHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvTest {
    @Test fun leeLaUbicacionDeLaRespuestaSsdp() {
        val r = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://192.168.1.20:9197/dmr\r\nST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n"
        assertEquals("http://192.168.1.20:9197/dmr", Dlna.ubicacionDeRespuesta(r))
        assertNull(Dlna.ubicacionDeRespuesta("HTTP/1.1 200 OK\r\n\r\n"))
    }

    @Test fun leeNombreYControlDelTelevisor() {
        val xml = """<root><device><friendlyName>[TV] Samsung &amp; Sala</friendlyName><serviceList>
            <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/rc</controlURL></service>
            <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/upnp/control/AVTransport1</controlURL></service>
            </serviceList></device></root>"""
        val tv = Dlna.desdeDescripcion(xml, "http://192.168.1.20:9197/dmr")!!
        assertEquals("[TV] Samsung & Sala", tv.nombre)
        assertEquals("http://192.168.1.20:9197/upnp/control/AVTransport1", tv.control)
        assertNull(Dlna.desdeDescripcion("<root><service><serviceType>x</serviceType></service></root>", "http://a/"))
    }

    @Test fun metadatosEscapados() {
        val m = Dlna.metadatos("http://x/a?b=1&c=2", "Tom & Jerry", "video/mp4")
        assertTrue(m.contains("Tom &amp; Jerry"))
        assertTrue(m.contains("http://x/a?b=1&amp;c=2"))
        assertTrue(m.contains("object.item.videoItem"))
    }

    @Test fun rangos() {
        assertEquals(0L to 999L, RangoHttp.leer("bytes=0-", 1000))
        assertEquals(100L to 199L, RangoHttp.leer("bytes=100-199", 1000))
        assertEquals(900L to 999L, RangoHttp.leer("bytes=-100", 1000))
        assertEquals(500L to 999L, RangoHttp.leer("bytes=500-5000", 1000))
        assertNull(RangoHttp.leer("bytes=2000-", 1000))
        assertNull(RangoHttp.leer(null, 1000))
    }
}
