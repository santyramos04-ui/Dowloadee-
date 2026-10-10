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
import androidx.compose.material.icons.filled.Close
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
import com.santyramos.mirador.extractor.bestUrl
import com.santyramos.mirador.player.VideoController

/** Barra pequeña que queda abajo cuando minimizas el reproductor: el audio sigue sonando. */
@Composable
fun MiniReproductor(onAbrir: () -> Unit, modifier: Modifier = Modifier) {
    val video by VideoController.video.collectAsState()
    val estado by VideoController.estado.collectAsState()
    val v = video ?: return
    Column(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).background(com.santyramos.mirador.ui.theme.Paleta.S2)
            .border(1.dp, com.santyramos.mirador.ui.theme.Paleta.Linea, RoundedCornerShape(18.dp)).clickable(onClick = onAbrir),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = v.info?.thumbnails?.bestUrl(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(64.dp).height(36.dp).clip(RoundedCornerShape(8.dp)).background(com.santyramos.mirador.ui.theme.Paleta.S3),
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(v.info?.name ?: if (v.error != null) "No se pudo cargar" else "Cargando…", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v.info?.uploaderName.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { VideoController.alternar() }, modifier = Modifier.size(48.dp)) {
                Icon(if (estado.reproduciendo) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (estado.reproduciendo) "Pausar" else "Reproducir")
            }
            IconButton(onClick = { VideoController.cerrar() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "Cerrar") }
        }
        val dur = estado.duracionMs
        if (dur > 0) LinearProgressIndicator(progress = { (estado.posicionMs.toFloat() / dur).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp), color = com.santyramos.mirador.ui.theme.Acento, trackColor = com.santyramos.mirador.ui.theme.Paleta.S3)
    }
}
