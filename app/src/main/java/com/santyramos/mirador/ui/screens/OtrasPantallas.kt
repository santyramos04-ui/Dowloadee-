package com.santyramos.mirador.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
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
import com.santyramos.mirador.ui.Encabezado
import com.santyramos.mirador.ui.EstadoVacio
import com.santyramos.mirador.ui.Insignia
import com.santyramos.mirador.ui.theme.Paleta

@Composable
fun SuscripcionesScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Encabezado("Suscripciones", "Sin cuenta de Google")
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EstadoVacio(
                Icons.Outlined.Subscriptions,
                "Tus canales, sin cuenta de Google",
                "Aquí verás las novedades de los canales que sigas, guardados solo en tu celular.\n\nLlega en la próxima versión, junto con la importación desde Google Takeout.",
            )
        }
    }
}

@Composable
fun BibliotecaScreen(onAjustes: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Encabezado("Biblioteca", "Todo queda guardado en tu celular", Modifier.padding(bottom = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Fila(Icons.Outlined.History, "Historial", "Seguir viendo donde te quedaste", proximamente = true, onClick = null)
            Fila(Icons.Outlined.Schedule, "Ver más tarde", "Tus videos guardados para después", proximamente = true, onClick = null)
            Fila(Icons.AutoMirrored.Outlined.PlaylistPlay, "Mis listas", "Listas propias, guardadas en el celular", proximamente = true, onClick = null)
            Spacer(Modifier.padding(4.dp))
            Fila(Icons.Outlined.Settings, "Ajustes", "Descargas, motor yt-dlp, actualizaciones y batería", proximamente = false, onClick = onAjustes)
        }
    }
}

@Composable
private fun Fila(icono: ImageVector, titulo: String, detalle: String, proximamente: Boolean, onClick: (() -> Unit)?) {
    val activo = onClick != null
    Surface(
        onClick = onClick ?: {}, enabled = activo, shape = RoundedCornerShape(20.dp), color = Paleta.S1,
        border = BorderStroke(1.dp, Paleta.Linea), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(if (activo) Paleta.AcentoSuave else Paleta.S3),
                contentAlignment = Alignment.Center,
            ) { Icon(icono, null, Modifier.size(22.dp), tint = if (activo) MaterialTheme.colorScheme.primary else Paleta.Texto3) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, style = MaterialTheme.typography.titleSmall, color = if (activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (proximamente) { Spacer(Modifier.width(8.dp)); Insignia("Pronto") }
                }
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (activo) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Paleta.Texto3)
        }
    }
}
