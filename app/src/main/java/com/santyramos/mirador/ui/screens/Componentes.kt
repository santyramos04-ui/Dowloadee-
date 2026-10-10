package com.santyramos.mirador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
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
import com.santyramos.mirador.ui.BotonIcono
import com.santyramos.mirador.ui.LocalGuardar
import com.santyramos.mirador.ui.theme.Paleta
import com.santyramos.mirador.util.Format

@Composable
private fun Etiqueta(texto: String, modifier: Modifier = Modifier, fondo: Color = Color(0xCC000000)) {
    Text(
        texto, color = Color.White, style = MaterialTheme.typography.labelMedium,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(fondo).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun Elemento.Video.datos(): String =
    listOfNotNull(autor, if (vistas >= 0) "${Format.contar(vistas)} vistas" else null, fecha).joinToString(" · ")

/** Tarjeta grande de video para el feed: miniatura 16:9, título, datos y botón de descarga. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun TarjetaVideo(v: Elemento.Video, onClick: () -> Unit, onDescargar: () -> Unit) {
    val guardar = LocalGuardar.current
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { guardar(v) }).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(Paleta.S2)) {
            AsyncImage(model = v.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            val etiqueta = when {
                v.esDirecto -> "EN VIVO"
                v.duracionSeg > 0 -> Format.duracion(v.duracionSeg)
                else -> null
            }
            if (etiqueta != null) Etiqueta(etiqueta, Modifier.align(Alignment.BottomEnd).padding(10.dp), if (v.esDirecto) Color(0xE6D32F2F) else Color(0xCC000000))
            if (v.esShort) Etiqueta("Short", Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(v.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(v.datos(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            BotonIcono(Icons.Outlined.BookmarkBorder, "Guardar", { guardar(v) }, Modifier.padding(start = 8.dp))
            BotonIcono(Icons.Outlined.FileDownload, "Descargar", onDescargar, Modifier.padding(start = 4.dp), destacado = true)
        }
    }
}

/** Fila compacta (videos de un canal, de una lista o relacionados). */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun FilaVideo(v: Elemento.Video, onClick: () -> Unit, onDescargar: () -> Unit) {
    val guardar = LocalGuardar.current
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { guardar(v) }).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(14.dp)).background(Paleta.S2)) {
            AsyncImage(model = v.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            val etiqueta = if (v.esDirecto) "EN VIVO" else if (v.duracionSeg > 0) Format.duracion(v.duracionSeg) else null
            if (etiqueta != null) Etiqueta(etiqueta, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(v.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(v.datos(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        BotonIcono(Icons.Outlined.FileDownload, "Descargar", onDescargar, destacado = true)
    }
}

@Composable
fun FilaCanal(c: Elemento.Canal, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(model = c.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(CircleShape).background(Paleta.S2))
        Column {
            Text(c.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.suscriptores >= 0) Text("${Format.contar(c.suscriptores)} suscriptores", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            if (!c.descripcion.isNullOrBlank()) Text(c.descripcion, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Paleta.Texto3)
        }
    }
}

@Composable
fun FilaLista(l: Elemento.Lista, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(14.dp)).background(Paleta.S2)) {
            AsyncImage(model = l.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Etiqueta("${l.cantidad} videos", Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(l.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(l.autor.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
