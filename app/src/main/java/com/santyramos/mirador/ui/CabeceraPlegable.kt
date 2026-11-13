package com.santyramos.mirador.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Cabecera (título, buscador, filtros…) que se esconde al bajar por la lista y reaparece al subir,
 * acompañando al dedo, para dar más espacio al contenido. Se usa así:
 * ```
 * val cabecera = rememberCabeceraPlegable()
 * Column(Modifier.nestedScroll(cabecera.conexion)) {
 *     CabeceraPlegable(cabecera) { …buscador… }
 *     LazyColumn { … }
 * }
 * ```
 */
class EstadoCabecera internal constructor(private val scope: CoroutineScope, private val activa: () -> Boolean) {
    var alto by mutableIntStateOf(0)
        internal set
    /** 0 = visible; −alto = escondida del todo. */
    var desfase by mutableFloatStateOf(0f)
        private set
    /** Mientras es true (p. ej. escribiendo en el buscador) la cabecera no se mueve. */
    var fija by mutableStateOf(false)
    private var animacion: Job? = null

    fun mostrar() = mover(0f)

    private fun mover(destino: Float) {
        animacion?.cancel()
        animacion = scope.launch {
            animate(desfase, destino, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ -> desfase = v }
        }
    }

    val conexion = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (fija || alto == 0 || !activa()) return Offset.Zero
            animacion?.cancel()
            val nuevo = (desfase + available.y).coerceIn(-alto.toFloat(), 0f)
            val usado = nuevo - desfase
            desfase = nuevo
            return Offset(0f, usado)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Al soltar, termina de abrirse o de esconderse (nunca queda a medias).
            val a = alto.toFloat()
            if (desfase != 0f && desfase != -a) mover(if (desfase > -a / 2) 0f else -a)
            return Velocity.Zero
        }
    }
}

/** [activa]: si devuelve false (p. ej. la lista es tan corta que no se desplaza), la cabecera se queda quieta. */
@Composable
fun rememberCabeceraPlegable(activa: () -> Boolean = { true }): EstadoCabecera {
    val scope = rememberCoroutineScope()
    return remember { EstadoCabecera(scope, activa) }
}

/** Dibuja la cabecera corrida hacia arriba según [estado]; solo cambia la posición, no se recompone. */
@Composable
fun CabeceraPlegable(estado: EstadoCabecera, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth().clipToBounds().layout { medible, limites ->
            val p = medible.measure(limites.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val d = estado.desfase.roundToInt().coerceIn(-p.height, 0)
            layout(p.width, p.height + d) { p.placeRelative(0, d) }
        },
    ) {
        Box(
            Modifier.onSizeChanged { estado.alto = it.height }.graphicsLayer {
                alpha = if (estado.alto == 0) 1f else (1f + estado.desfase / estado.alto * 1.4f).coerceIn(0f, 1f)
            },
        ) { contenido() }
    }
}
