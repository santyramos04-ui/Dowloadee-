package com.santyramos.mirador.player

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import com.santyramos.mirador.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Sonido de todo lo que suena en la app:
 * - «Volumen parejo»: un compresor suave y un limitador (Android 9 o más nuevo) que suben lo bajito y bajan
 *   lo muy fuerte, para que una canción no suene mucho más baja que otra.
 * - Ecualizador con estilos listos (Graves, Voz, Brillante, Fiesta).
 */
object Sonido {
    /** Cada estilo da una ganancia (en milibelios) según la frecuencia de cada banda del ecualizador del celular. */
    enum class Estilo(val etiqueta: String, val ganancia: (hz: Int) -> Int) {
        NORMAL("Normal", { 0 }),
        GRAVES("Graves", { hz -> when { hz < 150 -> 600; hz < 400 -> 350; else -> 0 } }),
        VOZ("Voz", { hz -> when { hz < 200 -> -300; hz in 1000..4000 -> 400; hz > 8000 -> 100; else -> 100 } }),
        BRILLANTE("Brillante", { hz -> when { hz > 6000 -> 500; hz > 2500 -> 300; else -> 0 } }),
        FIESTA("Fiesta", { hz -> when { hz < 150 -> 500; hz < 500 -> 200; hz < 2000 -> -100; hz < 6000 -> 200; else -> 400 } }),
        ;
        companion object { fun de(n: String?) = entries.firstOrNull { it.name == n } ?: NORMAL }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private var sesion = 0
    private var eq: Equalizer? = null
    private var dinamica: DynamicsProcessing? = null

    private val _parejo = MutableStateFlow(false)
    val parejo: StateFlow<Boolean> = _parejo.asStateFlow()
    private val _estilo = MutableStateFlow(Estilo.NORMAL)
    val estilo: StateFlow<Estilo> = _estilo.asStateFlow()

    /** El «volumen parejo» necesita Android 9 o más nuevo. */
    val parejoDisponible: Boolean get() = Build.VERSION.SDK_INT >= 28

    fun init(context: Context, audioSessionId: Int) {
        app = context.applicationContext
        sesion = audioSessionId
        scope.launch {
            val a = Settings(app)
            _parejo.value = a.volumenParejo.first()
            _estilo.value = Estilo.de(a.ecualizador.first())
            aplicar()
        }
    }

    fun cambiarParejo(v: Boolean) { _parejo.value = v; aplicar(); scope.launch { Settings(app).setVolumenParejo(v) } }
    fun cambiarEstilo(e: Estilo) { _estilo.value = e; aplicar(); scope.launch { Settings(app).setEcualizador(e.name) } }

    private fun aplicar() {
        if (sesion == 0) return
        runCatching { aplicarEcualizador() }
        runCatching { aplicarParejo() }
    }

    private fun aplicarEcualizador() {
        val e = _estilo.value
        if (e == Estilo.NORMAL) { eq?.enabled = false; return }
        val q = eq ?: Equalizer(0, sesion).also { eq = it }
        val (min, max) = q.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
        for (b in 0 until q.numberOfBands) {
            val hz = q.getCenterFreq(b.toShort()) / 1000
            q.setBandLevel(b.toShort(), e.ganancia(hz).coerceIn(min, max).toShort())
        }
        q.enabled = true
    }

    private fun aplicarParejo() {
        if (Build.VERSION.SDK_INT < 28) return
        if (!_parejo.value) { dinamica?.enabled = false; return }
        val d = dinamica ?: run {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                false, 0, true, 1, false, 0, true,
            ).build()
            DynamicsProcessing(0, sesion, config).also { dinamica = it }
        }
        // Compresor de una sola banda: desde −24 dB comprime 3 a 1, y luego sube +6 dB para compensar.
        d.setMbcBandAllChannelsTo(0, DynamicsProcessing.MbcBand(true, 20000f, 10f, 250f, 3f, -24f, 6f, -90f, 1f, 0f, 6f))
        // Limitador: nada pasa de −1 dB (evita que algo suene reventado).
        d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
        d.enabled = true
    }
}
