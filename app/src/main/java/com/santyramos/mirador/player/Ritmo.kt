package com.santyramos.mirador.player

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * El «oído» del mapache: escucha el sonido que sale del reproductor (antes de llegar al parlante,
 * sin micrófono ni permisos) y calcula el tempo en golpes por minuto, para que baile al ritmo.
 *
 * Cómo: cada 10 ms mide la fuerza de los graves; los saltos bruscos son golpes (bombo, palmas).
 * Cada 2 s busca cada cuánto se repiten esos saltos (autocorrelación) entre 65 y 170 golpes/min.
 */
object Ritmo : TeeAudioProcessor.AudioBufferSink {
    private const val PASO_MS = 10
    private const val VENTANA = 800 // 8 s de historia
    private const val MIN_BPM = 65f
    private const val MAX_BPM = 170f

    private val _bpm = MutableStateFlow(0f)
    /** Tempo de lo que suena; 0 = no se sabe (silencio, una charla, aún escuchando). */
    val bpm: StateFlow<Float> = _bpm.asStateFlow()

    private val candado = Any()
    private val golpes = FloatArray(VENTANA)
    private var escritos = 0
    private var tasa = 44100
    private var canales = 2
    private var codificacion = C.ENCODING_INVALID
    private var muestrasPorPaso = 441
    private var cuenta = 0
    private var grave = 0f
    private var energia = 0f
    private var anterior = 0f
    private var candidato = 0f
    private var analizador: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Arranca el análisis periódico (una vez por proceso). */
    fun iniciar() {
        if (analizador != null) return
        analizador = scope.launch {
            while (isActive) {
                delay(2000)
                analizar()
            }
        }
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        synchronized(candado) {
            tasa = sampleRateHz.coerceAtLeast(8000)
            canales = channelCount.coerceAtLeast(1)
            codificacion = encoding
            muestrasPorPaso = tasa * PASO_MS / 1000
            cuenta = 0; energia = 0f; grave = 0f; anterior = 0f
            // Un salto en la canción (o una canción nueva) borra lo escuchado.
            escritos = 0
            golpes.fill(0f)
        }
        _bpm.value = 0f
        candidato = 0f
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        val enc = codificacion
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return
        // Filtro de graves sencillo (~150 Hz): resalta el bombo sobre las voces.
        val a = 2f * Math.PI.toFloat() * 150f / tasa
        val ch = canales
        synchronized(candado) {
            while (b.remaining() >= ch * (if (enc == C.ENCODING_PCM_16BIT) 2 else 4)) {
                var suma = 0f
                for (c in 0 until ch) suma += if (enc == C.ENCODING_PCM_16BIT) b.short / 32768f else b.float
                val x = suma / ch
                grave += a * (x - grave)
                energia += grave * grave
                if (++cuenta >= muestrasPorPaso) {
                    val nivel = ln(1f + energia * 1000f)
                    // Solo cuenta cuando sube (el golpe), no cuando baja.
                    golpes[escritos % VENTANA] = max(0f, nivel - anterior)
                    escritos++
                    anterior = nivel
                    cuenta = 0; energia = 0f
                }
            }
        }
    }

    private fun analizar() {
        val serie: FloatArray
        synchronized(candado) {
            val n = minOf(escritos, VENTANA)
            if (n < 400) return // menos de 4 s escuchados
            serie = FloatArray(n) { golpes[(escritos - n + it) % VENTANA] }
        }
        val calculado = tempo(serie, PASO_MS)
        if (calculado <= 0f) { candidato = 0f; return }
        val actual = _bpm.value
        when {
            // Parecido al que ya había: se suaviza.
            actual > 0f && abs(calculado - actual) / actual < 0.08f -> _bpm.value = actual * 0.7f + calculado * 0.3f
            // Distinto: se acepta solo si se repite dos veces seguidas (evita saltos por un compás raro).
            candidato > 0f && abs(calculado - candidato) / candidato < 0.08f -> { _bpm.value = calculado; candidato = 0f }
            else -> candidato = calculado
        }
    }

    /**
     * Tempo (golpes/min) de una serie de «golpes» medida cada [pasoMs] ms, o 0 si no hay ritmo claro.
     * Público para poder probarlo sin el reproductor.
     */
    internal fun tempo(serie: FloatArray, pasoMs: Int): Float {
        val n = serie.size
        val media = serie.average().toFloat()
        val o = FloatArray(n) { serie[it] - media }
        var cero = 0f
        for (i in 0 until n) cero += o[i] * o[i]
        if (cero <= 1e-6f) return 0f
        val porMinuto = 60_000f / pasoMs
        val desde = (porMinuto / MAX_BPM).toInt().coerceAtLeast(2)
        val hasta = (porMinuto / MIN_BPM).toInt().coerceAtMost(n / 2)
        if (hasta <= desde + 2) return 0f
        val puntos = FloatArray(hasta + 2)
        for (l in desde..hasta + 1) {
            var s = 0f
            for (i in 0 until n - l) s += o[i] * o[i + l]
            puntos[l] = s / (n - l)
        }
        var mejor = -1; var mejorValor = 0f
        for (l in desde..hasta) {
            val bpm = porMinuto / l
            // Se prefieren tempos de baile normales (~115) frente a la mitad o el doble.
            val d = ln(bpm / 115f) / ln(2f) / 0.9f
            val v = puntos[l] * exp(-0.5f * d * d)
            if (v > mejorValor) { mejorValor = v; mejor = l }
        }
        if (mejor < 0) return 0f
        val claridad = puntos[mejor] / (cero / n)
        if (claridad < 0.08f) return 0f
        // Afinación entre los dos vecinos (parábola).
        val y0 = puntos[mejor - 1]; val y1 = puntos[mejor]; val y2 = puntos[mejor + 1]
        val den = y0 - 2 * y1 + y2
        val ajuste = if (abs(den) > 1e-9f) (0.5f * (y0 - y2) / den).coerceIn(-0.5f, 0.5f) else 0f
        return porMinuto / (mejor + ajuste)
    }
}
