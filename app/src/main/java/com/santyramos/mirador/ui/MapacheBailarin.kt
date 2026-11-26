package com.santyramos.mirador.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.santyramos.mirador.R
import com.santyramos.mirador.player.Ritmo
import com.santyramos.mirador.player.VideoController
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Cómo está el mapache en un instante (todo en unidades del dibujo original, de 1080 de ancho). */
private data class Pose(val y: Float = 0f, val giro: Float = 0f, val oreja: Float = 0f, val parpado: Float = 0f, val ojos: Float = 0f)

// Formas de los ojos, en las coordenadas del dibujo original (el mismo de diseno/icono_mapache.svg).
private const val PARPADOS = "M648,583 C680,566 735,562 762,585 C778,600 790,612 793,622 C760,626 712,630 680,612 C665,602 655,592 648,583Z " +
    "M944,616 C944,596 960,572 982,565 C980,585 968,606 944,616Z"
private const val PESTANA = "M652,598 C690,616 745,620 792,622"
private const val OJOS_ABIERTOS = "M647,584 C674,560 736,555 764,579 C779,594 789,609 793,622 C758,628 710,632 680,613 C664,603 653,593 647,584Z " +
    "M944,617 C941,596 958,570 983,562 C983,585 969,607 944,617Z"
private const val IRIS = "M690,592 C692,578 710,572 728,575 C742,578 748,590 744,600 C736,609 712,610 698,604 C692,601 689,597 690,592Z"

/**
 * El logo de RACQ vivo: cuando suena algo asiente al ritmo (el tempo lo mide [Ritmo]), se mece,
 * mueve la oreja cada 4 golpes y abre bien los ojos cada 16. En pausa respira despacio y parpadea.
 */
@Composable
fun MapacheBailarin(modifier: Modifier = Modifier) {
    val cabeza = ImageBitmap.imageResource(R.drawable.mapache_cabeza)
    val oreja = ImageBitmap.imageResource(R.drawable.mapache_oreja)
    val estado by VideoController.estado.collectAsState()
    val bpm by Ritmo.bpm.collectAsState()
    val sonando by rememberUpdatedState(estado.reproduciendo)
    val tempo by rememberUpdatedState(bpm)
    val contexto = LocalContext.current
    val quieto = remember {
        // «Quitar animaciones» de Android: el mapache se queda quieto.
        runCatching { android.provider.Settings.Global.getFloat(contexto.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
    }
    val formas = remember {
        val p = { d: String -> PathParser().parsePathString(d).toPath() }
        listOf(p(PARPADOS), p(PESTANA), p(OJOS_ABIERTOS), p(IRIS))
    }
    var pose by remember { mutableStateOf(Pose()) }

    LaunchedEffect(quieto) {
        if (quieto) return@LaunchedEffect
        var antes = 0L
        var fase = 0.0
        var golpeVisto = 0L
        var fuerza = 0f
        var ojos = 0f
        var orejaDesde = -1L
        var ojosHasta = 0L
        var parpadeoDesde = -1L
        var proxParpadeo = 1500L
        while (true) {
            withFrameNanos { ahoraNs ->
                val ahora = ahoraNs / 1_000_000
                val dt = if (antes == 0L) 0f else ((ahora - antes) / 1000f).coerceAtMost(0.05f)
                antes = ahora
                // Sin tempo claro (una charla, aún escuchando) se mueve más suave a un ritmo tranquilo.
                val bpmUsado = if (tempo > 0f) tempo else 96f
                val beat = 60f / bpmUsado
                val objetivo = if (!sonando) 0f else if (tempo > 0f) 1f else 0.55f
                fuerza += (objetivo - fuerza) * (dt * 3f).coerceAtMost(1f)
                if (sonando) fase += dt / beat
                val golpe = fase.toLong()
                if (sonando && golpe != golpeVisto) {
                    golpeVisto = golpe
                    if (golpe % 4 == 2L) orejaDesde = ahora
                    if (golpe % 16 == 0L && tempo > 0f) ojosHasta = ahora + (beat * 2000).toLong()
                }
                val asiente = 0.5f + 0.5f * cos(2 * PI * fase).toFloat()
                val respira = sin(ahora / 1000f * PI.toFloat() / 2f)
                val y = fuerza * 26f * asiente + (1 - fuerza) * 6f * respira
                val giro = fuerza * 2f * sin(PI * fase).toFloat() + (1 - fuerza) * 0.5f * respira
                var o = 0f
                if (orejaDesde > 0) {
                    val u = (ahora - orejaDesde) / (beat * 900f)
                    if (u < 1f) o = -5f * sin(PI.toFloat() * u).pow(2) else orejaDesde = -1
                }
                ojos += ((if (ahora < ojosHasta) 1f else 0f) - ojos) * (dt * 7f).coerceAtMost(1f)
                if (ahora > proxParpadeo && parpadeoDesde < 0) {
                    if (ojos < 0.1f) parpadeoDesde = ahora
                    proxParpadeo = ahora + 3000 + (Math.random() * 4000).toLong()
                }
                var l = 0f
                if (parpadeoDesde > 0) {
                    val u = (ahora - parpadeoDesde) / 240f
                    if (u < 1f) l = sin(PI.toFloat() * u) else parpadeoDesde = -1
                }
                pose = Pose(y, giro, o, (l * 1.3f).coerceAtMost(1f), ojos)
            }
        }
    }

    Canvas(modifier.aspectRatio(1f)) {
        val s = size.width / 1080f
        val lado = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val p = pose
        withTransform({
            translate(0f, p.y * s)
            rotate(p.giro, Offset((640 - 70) * s, (1120 - 120) * s))
        }) {
            drawImage(cabeza, srcOffset = IntOffset.Zero, srcSize = IntSize(cabeza.width, cabeza.height), dstSize = lado)
            rotate(p.oreja, Offset((380 - 70) * s, (440 - 120) * s)) {
                drawImage(oreja, srcOffset = IntOffset.Zero, srcSize = IntSize(oreja.width, oreja.height), dstSize = lado)
            }
            withTransform({ scale(s, s, Offset.Zero); translate(-70f, -120f) }) {
                if (p.ojos > 0.01f) {
                    drawPath(formas[2], Color(0xFF0B0606), alpha = p.ojos)
                    drawPath(formas[3], Color(0xFF6E6888), alpha = p.ojos)
                }
                if (p.parpado > 0.01f) {
                    drawPath(formas[0], Color(0xFF2A1C1B), alpha = p.parpado)
                    drawPath(formas[0], Color(0xFF2A1C1B), alpha = p.parpado, style = Stroke(6f))
                    drawPath(formas[1], Color(0xFF0B0606), alpha = p.parpado, style = Stroke(7f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
