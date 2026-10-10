package com.santyramos.mirador

import com.santyramos.mirador.data.lib.FeedRss
import com.santyramos.mirador.data.lib.Importar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BibliotecaTest {
    private val csv = "﻿Channel Id,Channel Url,Channel Title\n" +
        "UCX6OQ3DkcsbYNE6H8uQQuVA,http://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA,MrBeast\n" +
        "UC_x5XG1OV2P6uZZ5FSM9Ttw,http://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw,\"Google, Developers\"\n" +
        "mal,http://x,Roto\n" +
        "UCX6OQ3DkcsbYNE6H8uQQuVA,http://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA,MrBeast repetido\n"

    @Test fun csvDeTakeout() {
        val c = Importar.deCsv(csv)
        assertEquals(2, c.size)
        assertEquals("MrBeast", c[0].nombre)
        assertEquals("Google, Developers", c[1].nombre)
        assertEquals("https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw", c[1].urlCanal)
    }

    @Test fun zipDeTakeout() {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { z ->
            z.putNextEntry(ZipEntry("Takeout/YouTube y YouTube Music/historial/historial de reproducciones.html")); z.write("<html/>".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("Takeout/YouTube y YouTube Music/suscripciones/suscripciones.csv")); z.write(csv.toByteArray()); z.closeEntry()
        }
        assertEquals(2, Importar.deArchivo(salida.toByteArray()).size)
        // ZIP sin suscripciones
        val vacio = ByteArrayOutputStream()
        ZipOutputStream(vacio).use { z -> z.putNextEntry(ZipEntry("otra/cosa.csv")); z.write("a,b".toByteArray()); z.closeEntry() }
        assertTrue(Importar.deArchivo(vacio.toByteArray()).isEmpty())
    }

    @Test fun respaldoNewPipe() {
        val json = """{"app_version":"0.27","subscriptions":[
            {"service_id":0,"url":"https://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA","name":"MrBeast"},
            {"service_id":3,"url":"https://media.ccc.de/c/x","name":"Otro servicio"}]}"""
        val c = Importar.deArchivo(json.toByteArray())
        assertEquals(1, c.size)
        assertEquals("UCX6OQ3DkcsbYNE6H8uQQuVA", c[0].canalId)
    }

    @Test fun idDeCanalEnUrl() {
        assertEquals("UCX6OQ3DkcsbYNE6H8uQQuVA", Importar.canalIdDeUrl("https://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA/videos"))
        assertNull(Importar.canalIdDeUrl("https://www.youtube.com/@mrbeast"))
    }

    @Test fun feedRss() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
 <title>Canal de prueba</title>
 <author><name>Canal de prueba</name><uri>https://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA</uri></author>
 <entry>
  <id>yt:video:abcdefghijk</id>
  <yt:videoId>abcdefghijk</yt:videoId>
  <title>Café &amp; música: ¿qué tal?</title>
  <author><name>Canal de prueba</name></author>
  <published>2026-10-01T12:00:00+00:00</published>
  <media:group>
   <media:thumbnail url="https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg" width="480" height="360"/>
   <media:community><media:statistics views="12345"/></media:community>
  </media:group>
 </entry>
 <entry>
  <yt:videoId>zzzzzzzzzzz</yt:videoId>
  <title>Segundo</title>
  <published>2026-09-30T08:30:00+00:00</published>
 </entry>
</feed>"""
        val n = FeedRss.parsear(xml, "UCX6OQ3DkcsbYNE6H8uQQuVA")
        assertEquals(2, n.size)
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", n[0].url)
        assertEquals("Café & música: ¿qué tal?", n[0].titulo)
        assertEquals(12345L, n[0].vistas)
        assertEquals("Canal de prueba", n[1].canalNombre)
        assertTrue(n[0].publicadoMs > n[1].publicadoMs)
        assertEquals(1790856000000L, n[0].publicadoMs)
        assertEquals("https://i.ytimg.com/vi/zzzzzzzzzzz/hqdefault.jpg", n[1].miniatura)
    }
}

class ComentariosTest {
    @Test fun limpiaHtml() {
        assertEquals("We're so honored & proud\nlinea 2", com.santyramos.mirador.extractor.Youtube.limpiarHtml("We&apos;re so honored &amp; proud<br>linea 2"))
    }
}

class FechaRelativaTest {
    private val ahora = 1_800_000_000_000L
    @Test fun entiendeTextoRelativo() {
        assertEquals(ahora - 3 * 86_400_000L, com.santyramos.mirador.extractor.FechaRelativa.aMs("hace 3 días", ahora))
        assertEquals(ahora - 2 * 7 * 86_400_000L, com.santyramos.mirador.extractor.FechaRelativa.aMs("Streamed 2 weeks ago", ahora))
        assertEquals(ahora - 5 * 3_600_000L, com.santyramos.mirador.extractor.FechaRelativa.aMs("hace 5 horas", ahora))
        assertEquals(0L, com.santyramos.mirador.extractor.FechaRelativa.aMs(null, ahora))
    }
}

class RecomendadorTest {
    private fun v(id: String, autor: String) = com.santyramos.mirador.extractor.Elemento.Video(id, "u/$id", null, autor, null, 100, 0, null, false, false)

    @Test fun ordenaPorRepeticionYCanales() {
        val a = listOf(v("a", "X"), v("b", "Y"), v("visto", "Z"))
        val b = listOf(v("b", "Y"), v("c", "W"))
        val r = com.santyramos.mirador.data.lib.Recomendador.ordenar(
            listOf(a, b), emptyList(), urlsVistas = setOf("u/visto"), autoresVistos = mapOf("Y" to 3), autoresSeguidos = setOf("W"),
        )
        assertTrue(r.none { it.url == "u/visto" })
        assertEquals("u/b", r.first().url) // aparece en dos listas y es de un canal que ves
        assertEquals(3, r.size)
    }

    @Test fun fechaLegible() {
        assertEquals("8 feb 2013", com.santyramos.mirador.extractor.fechaLegible("2013-02-08T17:38:24-08:00"))
        assertEquals("hace 3 días", com.santyramos.mirador.extractor.fechaLegible("hace 3 días"))
    }
}

class Fase3Test {
    @Test fun sponsorBlockParsea() {
        val json = """[{"videoID":"otro","segments":[{"segment":[1,2],"category":"sponsor","actionType":"skip"}]},
            {"videoID":"abcdefghijk","segments":[
              {"segment":[10.5,40],"category":"sponsor","actionType":"skip"},
              {"segment":[100,100],"category":"intro","actionType":"skip"},
              {"segment":[5,8],"category":"selfpromo","actionType":"mute"},
              {"segment":[0,3.2],"category":"intro","actionType":"skip"}]}]"""
        val s = com.santyramos.mirador.player.SponsorBlock.parsear(json, "abcdefghijk")
        assertEquals(2, s.size)
        assertEquals(0L, s[0].inicioMs); assertEquals(3200L, s[0].finMs)
        assertEquals(10500L, s[1].inicioMs); assertEquals("sponsor", s[1].categoria)
    }

    @Test fun sponsorBlockIdYHash() {
        assertEquals("abcdefghijk", com.santyramos.mirador.player.SponsorBlock.idDeUrl("https://www.youtube.com/watch?v=abcdefghijk&t=3"))
        assertEquals("abcdefghijk", com.santyramos.mirador.player.SponsorBlock.idDeUrl("https://youtu.be/abcdefghijk"))
        // sha256("dQw4w9WgXcQ") empieza por 1b1d... se comprueba solo el largo y que sea hexadecimal
        val h = com.santyramos.mirador.player.SponsorBlock.prefijoHash("dQw4w9WgXcQ")
        assertTrue(h.length == 4 && h.all { it in "0123456789abcdef" })
    }

    @Test fun cookiesNetscape() {
        val l = com.santyramos.mirador.download.Cookies.lineasNetscape("instagram.com", "sessionid=abc%3A1; csrftoken=xyz; vacio; =sinnombre", 1900000000)
        assertEquals(2, l.size)
        assertEquals(".instagram.com\tTRUE\t/\tTRUE\t1900000000\tsessionid\tabc%3A1", l[0])
    }

    @Test fun respaldoIdaYVuelta() {
        val r = com.santyramos.mirador.data.lib.RespaldoDto(
            creadoEn = 5,
            suscripciones = listOf(com.santyramos.mirador.data.lib.SuscDto("UCX6OQ3DkcsbYNE6H8uQQuVA", "Canal ñandú", "https://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA")),
            historial = listOf(com.santyramos.mirador.data.lib.VideoDto("https://youtu.be/a", "Título \"con comillas\"", posicionMs = 12345)),
            listas = listOf(com.santyramos.mirador.data.lib.ListaDto("Mi lista", 1, listOf(com.santyramos.mirador.data.lib.VideoDto("https://youtu.be/b", "B")))),
            ajustes = mapOf("calidad" to "P720"),
        )
        val t = com.santyramos.mirador.data.lib.Respaldo.aTexto(r)
        assertEquals(r, com.santyramos.mirador.data.lib.Respaldo.deTexto(t))
        // Un respaldo de una versión futura con campos nuevos también se lee
        val futuro = t.replaceFirst("{", "{\"campoNuevo\": 1,")
        assertEquals(r, com.santyramos.mirador.data.lib.Respaldo.deTexto(futuro))
    }
}
