package com.santyramos.mirador

import com.santyramos.mirador.util.Fallos
import org.junit.Assert.assertTrue
import org.junit.Test

class FallosTest {
    @Test fun elInformeTraeVersionYCausa() {
        val t = Fallos.informe("main", IllegalStateException("se rompió"))
        assertTrue(t.contains("main"))
        assertTrue(t.contains("IllegalStateException: se rompió"))
    }
}
