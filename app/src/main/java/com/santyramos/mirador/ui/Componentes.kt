package com.santyramos.mirador.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Pantalla vacía amable: un ícono grande, un título y una explicación. */
@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    acciones: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icono, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(6.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        acciones()
    }
}

/** Etiqueta pequeña redondeada (formato, número de archivos...). */
@Composable
fun Insignia(texto: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondaryContainer, colorTexto: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(
        texto, style = MaterialTheme.typography.labelSmall, color = colorTexto,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Tarjeta de sección con título (se usa en Ajustes). */
@Composable
fun TarjetaSeccion(titulo: String, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            contenido()
        }
    }
}

/** Opción seleccionable en forma de tarjeta (formatos de descarga). */
@Composable
fun OpcionTarjeta(
    seleccionada: Boolean,
    titulo: String,
    detalle: String,
    derecha: String? = null,
    recomendada: Boolean = false,
    onClick: () -> Unit,
) {
    val borde = if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (seleccionada) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (seleccionada) 1.5.dp else 1.dp, borde),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(titulo, style = MaterialTheme.typography.titleSmall)
                    if (recomendada) Insignia("Recomendado", color = MaterialTheme.colorScheme.primary, colorTexto = MaterialTheme.colorScheme.onPrimary)
                }
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (derecha != null) Text(derecha, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Suppress("unused")
private fun Modifier.clickableSinRipple(onClick: () -> Unit) = clickable(onClick = onClick)
