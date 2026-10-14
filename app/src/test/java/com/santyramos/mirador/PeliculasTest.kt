package com.santyramos.mirador

import com.santyramos.mirador.peliculas.Archivo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeliculasTest {
    @Test fun leeLaBusqueda() {
        val cuerpo = """{"response":{"numFound":2,"docs":[
            {"identifier":"night_of_the_living_dead","title":"Night of the Living Dead","year":"1968","description":["<p>Clásico &amp; terror</p>"]},
            {"identifier":"sin_titulo"}]}}"""
        val lista = Archivo.parsearBusqueda(cuerpo)
        assertEquals(2, lista.size)
        assertEquals("Night of the Living Dead", lista[0].titulo)
        assertEquals("Clásico & terror", lista[0].descripcion)
        assertEquals("sin_titulo", lista[1].titulo) // sin título se usa el identificador
        assertEquals("https://archive.org/services/img/night_of_the_living_dead", lista[0].portada)
    }

    @Test fun eligeElMp4Liviano() {
        val cuerpo = """{"files":[
            {"name":"peli.ogv","format":"Ogg Video","size":"1000"},
            {"name":"peli_512kb.mp4","format":"h.264","size":"300000000"},
            {"name":"peli HD.mp4","format":"h.264 HD","size":"900000000"},
            {"name":"peli.thumbs/peli_000001.jpg","format":"Thumbnail"}]}"""
        assertEquals("https://archive.org/download/peli/peli_512kb.mp4", Archivo.elegirVideo("peli", cuerpo))
        assertNull(Archivo.elegirVideo("x", """{"files":[{"name":"a.ogv","format":"Ogg Video"}]}"""))
    }
}
