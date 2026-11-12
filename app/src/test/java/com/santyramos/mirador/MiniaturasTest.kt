package com.santyramos.mirador

import com.santyramos.mirador.player.Miniaturas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.stream.Frameset

class MiniaturasTest {
    private fun juego(ancho: Int, urls: List<String> = listOf("u$ancho")) = Frameset(urls, ancho, ancho * 9 / 16, 100, 2000, 5, 5)

    @Test fun eligeElMasNitidoQueSigueSiendoLiviano() {
        assertEquals(160, Miniaturas.elegir(listOf(juego(48), juego(80), juego(160), juego(1280)))!!.frameWidth)
    }

    @Test fun siSoloHayGrandesUsaElMenor() {
        assertEquals(640, Miniaturas.elegir(listOf(juego(1280), juego(640)))!!.frameWidth)
    }

    @Test fun sinCuadrosNoHayMiniatura() {
        assertNull(Miniaturas.elegir(emptyList()))
        assertNull(Miniaturas.elegir(listOf(juego(160, emptyList()))))
    }
}
