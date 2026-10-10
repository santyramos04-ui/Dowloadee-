package com.santyramos.mirador.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.ui.EstadoVacio
import com.santyramos.mirador.ui.Insignia

@Composable
fun SuscripcionesScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text("Suscripciones", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EstadoVacio(
                Icons.Filled.Subscriptions,
                "Tus canales, sin cuenta de Google",
                "Aquí verás las novedades de los canales que sigas, guardados solo en tu celular.\n\nLlega en la próxima versión, junto con la importación desde Google Takeout.",
            )
        }
    }
}

@Composable
fun BibliotecaScreen(onAjustes: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text("Biblioteca", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Fila(Icons.Filled.History, "Historial", "Seguir viendo donde te quedaste", proximamente = true, onClick = null)
            Fila(Icons.Filled.Schedule, "Ver más tarde", "Tus videos guardados para después", proximamente = true, onClick = null)
            Fila(Icons.Filled.PlaylistPlay, "Mis listas", "Listas propias, guardadas en el celular", proximamente = true, onClick = null)
            Spacer(Modifier.padding(4.dp))
            Fila(Icons.Filled.Settings, "Ajustes", "Descargas, motor yt-dlp, actualizaciones y batería", proximamente = false, onClick = onAjustes)
        }
    }
}

@Composable
private fun Fila(icono: ImageVector, titulo: String, detalle: String, proximamente: Boolean, onClick: (() -> Unit)?) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (onClick != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(icono, null, tint = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, style = MaterialTheme.typography.titleSmall, color = if (onClick != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (proximamente) { Spacer(Modifier.width(8.dp)); Insignia("Pronto") }
                }
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
