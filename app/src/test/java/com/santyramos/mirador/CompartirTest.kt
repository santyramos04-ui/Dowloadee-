package com.santyramos.mirador

import com.santyramos.mirador.util.Compartir
import org.junit.Assert.assertEquals
import org.junit.Test

class CompartirTest {
    @Test fun tipoDelArchivo() {
        assertEquals("video/mp4", Compartir.tipo("video/mp4"))
        assertEquals("audio/mpeg", Compartir.tipo("audio/mpeg"))
        assertEquals("*/*", Compartir.tipo(null))
        assertEquals("*/*", Compartir.tipo(""))
        assertEquals("*/*", Compartir.tipo("raro"))
    }
}
