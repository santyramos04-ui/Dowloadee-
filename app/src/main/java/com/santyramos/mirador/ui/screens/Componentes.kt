package com.santyramos.mirador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.util.Format

@Composable
fun FilaVideo(v: Elemento.Video, onClick: () -> Unit, onDescargar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(150.dp).height(84.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(model = v.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            val etiqueta = when {
                v.esDirecto -> "EN VIVO"
                v.duracionSeg > 0 -> Format.duracion(v.duracionSeg)
                else -> null
            }
            if (etiqueta != null) Text(
                etiqueta, color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(v.titulo, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(v.autor, if (v.vistas >= 0) "${Format.contar(v.vistas)} vistas" else null, v.fecha).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDescargar, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Download, "Descargar", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Tarjeta grande de video (como el inicio de las apps de video): miniatura 16:9, título y datos. */
@Composable
fun TarjetaVideo(v: Elemento.Video, onClick: () -> Unit, onDescargar: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            AsyncImage(model = v.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            val etiqueta = when {
                v.esDirecto -> "EN VIVO"
                v.duracionSeg > 0 -> Format.duracion(v.duracionSeg)
                else -> null
            }
            if (etiqueta != null) Text(
                etiqueta, color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (v.esDirecto) Color(0xE6D32F2F) else Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (v.esShort) Text(
                "Short", color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(v.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(v.autor, if (v.vistas >= 0) "${Format.contar(v.vistas)} vistas" else null, v.fecha).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            androidx.compose.material3.FilledTonalIconButton(onClick = onDescargar, modifier = Modifier.padding(start = 8.dp).size(40.dp)) {
                Icon(Icons.Filled.Download, "Descargar", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun FilaCanal(c: Elemento.Canal, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(model = c.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        Column {
            Text(c.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.suscriptores >= 0) Text("${Format.contar(c.suscriptores)} suscriptores", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!c.descripcion.isNullOrBlank()) Text(c.descripcion, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FilaLista(l: Elemento.Lista, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(150.dp).height(84.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(model = l.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Text(
                "☰ ${l.cantidad}", color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(l.titulo, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(l.autor.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
