package com.santyramos.mirador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import com.santyramos.mirador.player.VideoController

/**
 * Barra pequeña que queda abajo cuando minimizas el reproductor: el audio sigue sonando.
 * Pausa y Siguiente a mano; para cerrarla se desliza hacia un lado (o con la acción «Cerrar reproductor» de TalkBack).
 */
@Composable
fun MiniReproductor(onAbrir: () -> Unit, modifier: Modifier = Modifier) {
    val video by VideoController.video.collectAsState()
    val estado by VideoController.estado.collectAsState()
    val cola by VideoController.cola.collectAsState()
    val v = video ?: return
    val scope = rememberCoroutineScope()
    val desplazamiento = remember(v.urlOriginal) { Animatable(0f) }
    var ancho by remember { mutableFloatStateOf(1f) }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .onSizeChanged { ancho = it.width.toFloat().coerceAtLeast(1f) }
            .offset { IntOffset(desplazamiento.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(desplazamiento.value) / ancho).coerceIn(0f, 0.8f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { d -> scope.launch { desplazamiento.snapTo(desplazamiento.value + d) } },
                onDragStopped = { velocidad ->
                    if (abs(desplazamiento.value) > ancho * 0.35f || abs(velocidad) > 1800f) {
                        desplazamiento.animateTo(sign(if (desplazamiento.value != 0f) desplazamiento.value else velocidad) * ancho, tween(160))
                        VideoController.cerrar()
                    } else desplazamiento.animateTo(0f, tween(180))
                },
            )
            .semantics { customActions = listOf(CustomAccessibilityAction("Cerrar reproductor") { VideoController.cerrar(); true }) }
            .clip(RoundedCornerShape(18.dp)).background(com.santyramos.mirador.ui.theme.Paleta.S2)
            .border(1.dp, com.santyramos.mirador.ui.theme.Paleta.Linea, RoundedCornerShape(18.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Abrir reproductor", onClick = onAbrir),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = v.miniatura, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(64.dp).height(36.dp).clip(RoundedCornerShape(8.dp)).background(com.santyramos.mirador.ui.theme.Paleta.S3),
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(v.titulo.ifBlank { if (v.error != null) "No se pudo cargar" else "Cargando…" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v.autor.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { VideoController.alternar() }, modifier = Modifier.size(48.dp)) {
                Icon(if (estado.reproduciendo) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (estado.reproduciendo) "Pausar" else "Reproducir")
            }
            val haySiguiente = v.listo && (cola.isNotEmpty() || VideoController.haySiguiente())
            IconButton(onClick = { VideoController.siguiente() }, enabled = haySiguiente, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.SkipNext, "Siguiente") }
        }
        val dur = estado.duracionMs
        if (dur > 0) LinearProgressIndicator(progress = { (estado.posicionMs.toFloat() / dur).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp), color = com.santyramos.mirador.ui.theme.Acento, trackColor = com.santyramos.mirador.ui.theme.Paleta.S3)
    }
}
