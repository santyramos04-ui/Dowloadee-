package com.santyramos.mirador.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SuscripcionesScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Suscripciones", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Aquí vas a ver las novedades de tus canales, guardados solo en tu celular, sin cuenta de Google.\n\nLlega en la próxima versión (Fase 2), junto con la importación desde Google Takeout.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
fun BibliotecaScreen(onAjustes: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text("Biblioteca", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        Fila(Icons.Filled.History, "Historial", "Próximamente (Fase 2)", null)
        Fila(Icons.Filled.Schedule, "Ver más tarde", "Próximamente (Fase 2)", null)
        Fila(Icons.Filled.PlaylistPlay, "Mis listas", "Próximamente (Fase 2)", null)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Fila(Icons.Filled.Settings, "Ajustes", "Descargas, motor yt-dlp, actualizaciones, batería", onAjustes)
    }
}

@Composable
private fun Fila(icono: ImageVector, titulo: String, detalle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, null, tint = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Column(Modifier.padding(start = 16.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
