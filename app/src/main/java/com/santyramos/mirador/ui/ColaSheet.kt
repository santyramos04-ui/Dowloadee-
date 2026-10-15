package com.santyramos.mirador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.santyramos.mirador.player.ElementoCola
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.theme.Paleta

/** Miniatura + título + autor de algo que puede sonar. Si es un archivo descargado, lo indica. */
@Composable
fun FilaElemento(
    e: ElementoCola, onClick: (() -> Unit)?, modifier: Modifier = Modifier, sonando: Boolean = false,
    acciones: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Reproducir", onClick = onClick) else Modifier)
            .heightIn(min = 64.dp).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(80.dp).height(45.dp).clip(RoundedCornerShape(10.dp)).background(Paleta.S3), contentAlignment = Alignment.Center) {
            if (e.miniatura != null) AsyncImage(model = e.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            else Icon(Icons.Outlined.MusicNote, null, tint = Paleta.Texto3)
        }
        Column(Modifier.weight(1f)) {
            Text(
                e.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (sonando) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (e.esDescarga) Icon(Icons.Outlined.DownloadDone, "Descargado", Modifier.size(14.dp), tint = Paleta.Texto3)
                Text(e.autor.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        acciones()
    }
}

/** Hoja «Cola de reproducción»: lo que suena y lo que viene, con subir, bajar y quitar. */
@Composable
fun ColaSheetContent(onCerrar: () -> Unit) {
    val video by VideoController.video.collectAsState()
    val cola by VideoController.cola.collectAsState()
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cola de reproducción", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (cola.isNotEmpty()) TextButton(onClick = { VideoController.vaciarCola() }) { Text("Vaciar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        video?.let { v ->
            Text("Sonando ahora", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
            FilaElemento(ElementoCola(v.urlOriginal, v.titulo.ifBlank { "Cargando…" }, v.autor, v.miniatura, archivoUri = if (v.local) "local" else null), null, sonando = true)
        }
        Text(
            if (cola.isEmpty()) "A continuación" else "A continuación (${cola.size})",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
        )
        if (cola.isEmpty()) {
            Text(
                "La cola está vacía. Toca ⋮ en un video y elige «Añadir a la cola». " +
                    "Cuando la cola se acaba, sigue la lista o los videos relacionados (si la reproducción automática está activada).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(cola, key = { i, e -> "$i ${e.url}" }) { i, e ->
                    FilaElemento(e, { VideoController.reproducirDeCola(i) }, Modifier.animateItem()) {
                        BotonIcono(Icons.Outlined.KeyboardArrowUp, "Subir", { VideoController.moverEnCola(i, -1) })
                        BotonIcono(Icons.Outlined.KeyboardArrowDown, "Bajar", { VideoController.moverEnCola(i, 1) })
                        BotonIcono(Icons.Outlined.Close, "Quitar de la cola", { VideoController.quitarDeCola(i) })
                    }
                }
            }
        }
        BotonPrimario("Listo", onCerrar, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
    }
}
