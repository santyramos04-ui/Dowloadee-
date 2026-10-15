package com.santyramos.mirador

import com.santyramos.mirador.data.Historial
import com.santyramos.mirador.ui.Sugerencia
import com.santyramos.mirador.ui.Sugerir
import org.junit.Assert.assertEquals
import org.junit.Test

class SugerenciasTest {
    @Test fun historialSinRepetirYConLimite() {
        var h = emptyList<String>()
        h = Historial.agregar(h, "gatos", 3)
        h = Historial.agregar(h, "perros", 3)
        h = Historial.agregar(h, "Gatos ", 3) // repetida: sube al primer lugar
        assertEquals(listOf("Gatos", "perros"), h)
        h = Historial.agregar(Historial.agregar(h, "a", 3), "b", 3)
        assertEquals(listOf("b", "a", "Gatos"), h)
        assertEquals(h, Historial.agregar(h, "   ", 3))
    }

    @Test fun sinTextoMuestraElHistorial() {
        val s = Sugerir.combinar("", listOf("salsa", "bachata"), listOf("no debe salir"))
        assertEquals(listOf("salsa", "bachata"), s.map { it.texto })
        assertEquals(true, s.all { it.tipo == Sugerencia.Tipo.HISTORIAL })
    }

    @Test fun primeroLoTuyoLuegoLoNuevoSinRepetir() {
        val s = Sugerir.combinar("sal", listOf("salsa choke", "rock"), listOf("Salsa Choke", "salsa baul", "salserin"))
        assertEquals(listOf("salsa choke", "salsa baul", "salserin"), s.map { it.texto })
        assertEquals(Sugerencia.Tipo.HISTORIAL, s[0].tipo)
        assertEquals(Sugerencia.Tipo.SUGERIDA, s[1].tipo)
    }
}
