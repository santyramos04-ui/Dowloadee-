package com.santyramos.mirador

import com.santyramos.mirador.data.db.DownloadEntity
import com.santyramos.mirador.player.ColaLogica
import com.santyramos.mirador.player.ElementoCola
import com.santyramos.mirador.player.Repetir
import com.santyramos.mirador.player.restaurarOrden
import com.santyramos.mirador.ui.screens.agruparSinConexion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColaTest {
    private fun e(n: String) = ElementoCola(url = "https://youtu.be/$n", titulo = n)

    @Test fun agregarNoRepite() {
        val c = ColaLogica.agregar(ColaLogica.agregar(emptyList(), e("a")), e("a"))
        assertEquals(listOf("a"), c.map { it.titulo })
    }

    @Test fun aContinuacionVaPrimeroYSeMueve() {
        val c = ColaLogica.aContinuacion(listOf(e("a"), e("b"), e("c")), e("c"))
        assertEquals(listOf("c", "a", "b"), c.map { it.titulo })
    }

    @Test fun moverYQuitar() {
        val base = listOf(e("a"), e("b"), e("c"))
        assertEquals(listOf("b", "a", "c"), ColaLogica.mover(base, 1, -1).map { it.titulo })
        assertEquals(base, ColaLogica.mover(base, 0, -1)) // fuera de rango: no cambia
        assertEquals(listOf("a", "c"), ColaLogica.quitar(base, 1).map { it.titulo })
        assertEquals(base, ColaLogica.quitar(base, 9))
    }

    @Test fun saltarAQuitaLosAnteriores() {
        val (suena, resto) = ColaLogica.saltarA(listOf(e("a"), e("b"), e("c")), 1)!!
        assertEquals("b", suena.titulo)
        assertEquals(listOf("c"), resto.map { it.titulo })
        assertNull(ColaLogica.saltarA(emptyList(), 0))
    }

    @Test fun agruparDescargasPorLista() {
        fun d(id: Long, n: String, grupo: Long?, prefijo: String? = null) = DownloadEntity(
            id = id, url = "https://youtu.be/$n", titulo = n, preset = "M4A", estado = "DONE",
            archivoUri = "content://media/$id", archivoMime = "audio/mp4", grupo = grupo, grupoNombre = grupo?.let { "Lista $it" }, prefijo = prefijo,
        )
        val grupos = agruparSinConexion(listOf(d(1, "suelta", null), d(2, "dos", 7, "02 - "), d(3, "uno", 7, "01 - ")))
        assertEquals(listOf("Lista 7", "Descargas sueltas"), grupos.map { it.nombre })
        assertEquals(listOf("uno", "dos"), grupos[0].elementos.map { it.titulo }) // respeta el orden de la lista
        assertTrue(grupos[0].elementos.all { it.esLocal && it.esAudio })
    }

    @Test fun repetirDaLaVuelta() {
        assertEquals(Repetir.UNA, Repetir.NO.siguiente())
        assertEquals(Repetir.TODAS, Repetir.UNA.siguiente())
        assertEquals(Repetir.NO, Repetir.TODAS.siguiente())
    }

    @Test fun quitarAleatorioRecuperaElOrden() {
        val original = listOf(e("a"), e("b"), e("c"))
        val mezclada = listOf(e("c"), e("a"), e("nuevo")) // «b» ya sonó y se añadió «nuevo»
        assertEquals(listOf("a", "c", "nuevo"), restaurarOrden(original, mezclada).map { it.titulo })
    }
}
