package com.santyramos.mirador

import com.santyramos.mirador.extractor.Letras
import org.junit.Assert.assertEquals
import org.junit.Test

class LetrasTest {
    @Test fun separaArtistaYCancion() {
        assertEquals("Sean Paul" to "Get Busy", Letras.separar("Sean Paul - Get Busy (Official Video)", "SeanPaulVEVO"))
        assertEquals("Shakira" to "Antología", Letras.separar("Antología [Lyrics]", "Shakira - Topic"))
        assertEquals("Juanes" to "La Camisa Negra", Letras.separar("Juanes - La Camisa Negra ft. Alguien", null))
    }

    @Test fun leeUnaLetraSincronizada() {
        val l = Letras.leerLrc("[00:12.30] Hola\n[01:02.5] Mundo\nsin tiempo\n[00:00.00]")
        assertEquals(listOf(0L, 12_300L, 62_500L), l.map { it.ms })
        assertEquals("Hola", l[1].texto)
    }
}
