package com.santyramos.mirador

import com.santyramos.mirador.player.Subtitulo
import com.santyramos.mirador.player.Subtitulos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitulosTest {
    private val es = Subtitulo("es", "Español", "u1", "text/vtt", "es", false)
    private val esAuto = Subtitulo("es:auto", "Español (automático)", "u2", "text/vtt", "es", true)
    private val en = Subtitulo("en", "English", "u3", "text/vtt", "en", false)
    private val lista = listOf(es, en, esAuto)

    @Test fun recuerdaElIdiomaYPrefiereLosHechosPorPersonas() {
        assertEquals(es, Subtitulos.elegir(lista, null, "es"))
        assertEquals(esAuto, Subtitulos.elegir(listOf(en, esAuto), null, "es"))
    }

    @Test fun desactivadosOSinPreferenciaNoMuestraNada() {
        assertNull(Subtitulos.elegir(lista, "", "es"))   // en este video los apagaste
        assertNull(Subtitulos.elegir(lista, null, ""))   // la última vez los apagaste
        assertNull(Subtitulos.elegir(lista, null, null)) // nunca los activaste
    }

    @Test fun loElegidoEnElVideoGana() {
        assertEquals(en, Subtitulos.elegir(lista, "en", "es"))
    }
}
