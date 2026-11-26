package com.santyramos.mirador

import com.santyramos.mirador.player.Ritmo
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** El «oído» del mapache: reconoce el tempo de una serie de golpes. */
class RitmoTest {
    /** 8 s de golpes cada [periodoMs] (medidos cada 10 ms), con algo de ruido y golpes suaves entre medio. */
    private fun golpes(periodoMs: Int, azar: Random = Random(7)): FloatArray {
        val n = 800
        return FloatArray(n) { i ->
            val t = i * 10
            val fuerte = if (t % periodoMs < 10) 1f else 0f
            val medio = if ((t + periodoMs / 2) % periodoMs < 10) 0.3f else 0f
            fuerte + medio + azar.nextFloat() * 0.08f
        }
    }

    @Test fun reconoce120() = assertEquals(120f, Ritmo.tempo(golpes(500), 10), 3f)

    @Test fun reconoce90() = assertEquals(90f, Ritmo.tempo(golpes(667), 10), 3f)

    @Test fun reconoce140() = assertEquals(140f, Ritmo.tempo(golpes(429), 10), 4f)

    @Test fun silencioNoTieneTempo() = assertEquals(0f, Ritmo.tempo(FloatArray(800), 10), 0.01f)
}
