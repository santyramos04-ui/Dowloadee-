package com.santyramos.mirador.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.player.aCola
import com.santyramos.mirador.ui.theme.Paleta

/** Menú ⋮ de un video: todo lo que se puede hacer con él, en un solo lugar. */
@Composable
fun AccionesSheetContent(
    video: Elemento.Video,
    onCerrar: () -> Unit,
    onGuardar: () -> Unit,
    onDescargar: () -> Unit,
    onSoloAudio: () -> Unit,
    onCanal: (() -> Unit)?,
) {
    val contexto = LocalContext.current
    val algoSuena = VideoController.video.value != null
    fun avisar(t: String) = android.widget.Toast.makeText(contexto, t, android.widget.Toast.LENGTH_SHORT).show()
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(80.dp).height(45.dp).clip(RoundedCornerShape(10.dp)).background(Paleta.S3)) {
                AsyncImage(model = video.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(video.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!video.autor.isNullOrBlank()) Text(video.autor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = Paleta.Linea)
        Column(Modifier.padding(top = 4.dp)) {
            if (!video.esDirecto) {
                Accion(Icons.Outlined.QueuePlayNext, "Reproducir a continuación", destacada = true) {
                    VideoController.reproducirDespues(video.aCola()); avisar(if (algoSuena) "Sonará después del actual" else "Reproduciendo"); onCerrar()
                }
                Accion(Icons.AutoMirrored.Outlined.QueueMusic, "Añadir a la cola", destacada = true) {
                    VideoController.agregarACola(video.aCola()); avisar(if (algoSuena) "Se añadió a la cola" else "Reproduciendo"); onCerrar()
                }
            }
            Accion(Icons.Outlined.BookmarkBorder, "Guardar en Ver más tarde o en una lista") { onCerrar(); onGuardar() }
            Accion(Icons.Outlined.FileDownload, "Descargar") { onCerrar(); onDescargar() }
            if (!video.esDirecto) Accion(Icons.Outlined.Headphones, "Escuchar solo el audio") { onCerrar(); onSoloAudio() }
            if (onCanal != null && !video.urlAutor.isNullOrBlank()) Accion(Icons.Outlined.AccountCircle, "Ir al canal") { onCerrar(); onCanal() }
            Accion(Icons.Outlined.Share, "Compartir") {
                onCerrar()
                contexto.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, video.url), "Compartir enlace"))
            }
        }
    }
}

@Composable
private fun Accion(icono: ImageVector, texto: String, destacada: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icono, null, tint = if (destacada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}
