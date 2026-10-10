package com.santyramos.mirador

import com.santyramos.mirador.data.PlayerQuality
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.FiltroBusqueda
import com.santyramos.mirador.extractor.StreamSelector
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.net.Http
import com.santyramos.mirador.net.NewPipeDownloader
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * PRUEBAS DE RED (parte VER): hablan con YouTube de verdad.
 * Solo corren con  -PredTests=1  (el CI las ejecuta en el workflow «Pruebas de red»).
 * Si YouTube bloquea la IP del servidor de GitHub, fallarán aunque la app esté bien: en ese
 * caso hay que probar a mano en el celular (ver LEEME).
 */
class RedTest {
    private fun requiere() = assumeTrue("Pruebas de red desactivadas (usa -PredTests=1)", System.getProperty("redTests") == "1")

    @Test fun busquedaDevuelveResultados() = runBlocking {
        requiere()
        Youtube.iniciar()
        val pagina = Youtube.buscar("documental naturaleza", FiltroBusqueda.VIDEOS)
        println("PRUEBA_VER busqueda: ${pagina.elementos.size} resultados; primero = ${pagina.elementos.firstOrNull()?.titulo}")
        assertTrue("La búsqueda no devolvió resultados", pagina.elementos.isNotEmpty())
        assertTrue(pagina.elementos.any { it is Elemento.Video })
    }

    @Test fun obtieneUrlsDeReproduccionDeUnVideoPublico() = runBlocking {
        requiere()
        Youtube.iniciar()
        val info = try {
            Youtube.video("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        } catch (e: org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException) {
            // YouTube bloquea las IP de los servidores de GitHub: no es un fallo de la app, pero tampoco una prueba superada.
            println("PRUEBA_VER video: NO CONCLUYENTE — YouTube bloqueó la IP del servidor («no eres un robot»). Probar en el celular.")
            assumeTrue("YouTube bloqueó la IP del servidor", false)
            return@runBlocking
        }
        println("PRUEBA_VER video: «${info.name}» ${info.videoStreams.size} mezclados, ${info.videoOnlyStreams.size} solo video, ${info.audioStreams.size} audio")
        assertTrue("No hay streams de video", info.videoStreams.isNotEmpty() || info.videoOnlyStreams.isNotEmpty())
        val sel = StreamSelector.seleccionar(info.videoStreams, info.videoOnlyStreams, info.audioStreams, PlayerQuality.P720, true)!!
        println("PRUEBA_VER seleccion: ${sel.altura}p video=${sel.video.id}${sel.audio?.let { " audio=${it.id}" } ?: " (con audio incluido)"}")
        // ¿Se pueden leer bytes de verdad? Se pide un trozo de 2 KB como lo hace el reproductor.
        val url = sel.video.content
        val ua = if (url.contains("c=VISIONOS")) YoutubeParsingHelper.getVisionOsUserAgent(null) else NewPipeDownloader.USER_AGENT
        val sep = if (url.contains("?")) "&" else "?"
        val req = Request.Builder().url("$url${sep}range=0-2047").header("User-Agent", ua).build()
        Http.client.newCall(req).execute().use { r ->
            val bytes = r.body.bytes().size
            println("PRUEBA_VER trozo: HTTP ${r.code}, $bytes bytes")
            assertTrue("El servidor de video respondió ${r.code}", r.isSuccessful)
            assertTrue("No llegaron bytes de video", bytes > 0)
        }
    }

    @Test fun cargaLosVideosDeUnCanal() = runBlocking {
        requiere()
        Youtube.iniciar()
        val datos = Youtube.canal("https://www.youtube.com/@NASA")
        println("PRUEBA_VER canal: «${datos.info.name}» ${datos.videos.elementos.size} videos")
        assertTrue("El canal no devolvió videos", datos.videos.elementos.isNotEmpty())
    }

    @Test fun feedRssDeUnCanalReal() = runBlocking {
        requiere()
        Youtube.iniciar()
        val id = Youtube.canal("https://www.youtube.com/@NASA").info.id
        println("PRUEBA_VER rss: canal NASA = $id")
        val xml = Http.client.newCall(Request.Builder().url(com.santyramos.mirador.data.lib.FeedRss.url(id)).header("User-Agent", "Mozilla/5.0").build()).execute().use { r ->
            println("PRUEBA_VER rss: HTTP ${r.code}")
            // Informativa: si YouTube responde 404 la app usa el extractor en su lugar.
            if (!r.isSuccessful) return@runBlocking
            r.body.string()
        }
        val n = com.santyramos.mirador.data.lib.FeedRss.parsear(xml, id)
        println("PRUEBA_VER rss: ${n.size} videos; primero = ${n.firstOrNull()?.titulo}")
        assertTrue("El feed no trajo videos", n.isNotEmpty())
    }

    @Test fun tendenciasDeColombia() = runBlocking {
        requiere()
        Youtube.iniciar()
        val t = Youtube.tendencias()
        // Informativa: YouTube retiró su página de tendencias; si no existe, la app muestra solo la portada.
        println("PRUEBA_VER tendencias: ${t?.size ?: "no disponible"}")
    }

    @Test fun comentariosDeUnVideo() = runBlocking {
        requiere()
        Youtube.iniciar()
        try {
            val p = Youtube.comentarios("https://www.youtube.com/watch?v=jNQXAC9IVRw")
            println("PRUEBA_VER comentarios: ${p.comentarios.size}; primero = ${p.comentarios.firstOrNull()?.texto?.take(60)}")
            assertTrue("No hubo comentarios", p.comentarios.isNotEmpty())
        } catch (e: org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException) {
            println("PRUEBA_VER comentarios: NO CONCLUYENTE — YouTube bloqueó la IP del servidor")
            assumeTrue("YouTube bloqueó la IP del servidor", false)
        }
    }
}
