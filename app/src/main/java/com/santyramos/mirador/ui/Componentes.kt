package com.santyramos.mirador.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.ui.theme.Paleta

private val RadioBoton = RoundedCornerShape(18.dp)

/** Botón principal: verde, alto de 54 dp (cómodo para el pulgar). */
@Composable
fun BotonPrimario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, icono: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, shape = RadioBoton, modifier = modifier.height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Paleta.SobreAcento,
            disabledContainerColor = Paleta.S3, disabledContentColor = Paleta.Texto3),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    ) {
        if (icono != null) { Icon(icono, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botón secundario: gris con borde fino. */
@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, icono: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick, shape = RadioBoton, modifier = modifier.height(54.dp),
        border = BorderStroke(1.dp, Paleta.Linea),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Paleta.S2, contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    ) {
        if (icono != null) { Icon(icono, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botón de ícono de 44 dp con fondo suave. [destacado] = verde. */
@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, onClick: () -> Unit, modifier: Modifier = Modifier, destacado: Boolean = false, bordeado: Boolean = false) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp), modifier = modifier.size(44.dp).semantics { contentDescription = descripcion },
        color = if (destacado) Paleta.AcentoSuave else if (bordeado) Paleta.S2 else Color.Transparent,
        border = if (bordeado) BorderStroke(1.dp, Paleta.Linea) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icono, null, Modifier.size(21.dp), tint = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Filtro en forma de píldora rectangular (Todo, Videos, Canales...). */
@Composable
fun Filtro(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(36.dp),
        color = if (seleccionado) Paleta.AcentoSuave else Paleta.S2,
        border = BorderStroke(1.dp, if (seleccionado) Paleta.AcentoBorde else Paleta.Linea),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(texto, style = MaterialTheme.typography.labelMedium, color = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Panel con fondo y borde fino. */
@Composable
fun Panel(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Paleta.S1,
        border = BorderStroke(1.dp, Paleta.Linea),
    ) { contenido() }
}

/** Título de pantalla con subtítulo opcional. */
@Composable
fun Encabezado(titulo: String, subtitulo: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineMedium)
        if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Pantalla vacía: un ícono en círculo suave, título y explicación. */
@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    acciones: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Paleta.AcentoSuave), contentAlignment = Alignment.Center) {
            Icon(icono, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        acciones()
    }
}

/** Etiqueta pequeña (formato, cantidad...). */
@Composable
fun Insignia(texto: String, modifier: Modifier = Modifier, color: Color = Paleta.S3, colorTexto: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        texto.uppercase(), style = MaterialTheme.typography.labelSmall, color = colorTexto,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Grupo de ajustes: título verde pequeño + contenido dentro de un panel. */
@Composable
fun TarjetaSeccion(titulo: String, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Panel { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { contenido() } }
    }
}

/** Opción seleccionable (formatos de descarga): círculo de selección, nombre, detalle y peso. */
@Composable
fun OpcionTarjeta(
    seleccionada: Boolean,
    titulo: String,
    detalle: String,
    derecha: String? = null,
    recomendada: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
        color = if (seleccionada) Paleta.AcentoSuave else Paleta.S2,
        border = BorderStroke(if (seleccionada) 1.5.dp else 1.dp, if (seleccionada) MaterialTheme.colorScheme.primary else Paleta.Linea),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (seleccionada) MaterialTheme.colorScheme.primary else Paleta.Texto3, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (seleccionada) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (recomendada) Insignia("Recomendado", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.primary, colorTexto = Paleta.SobreAcento)
            }
            if (derecha != null) Text(derecha, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}


/** Hoja inferior de Mirador: fondo de panel, esquinas de 30 dp y barra de arrastre fina. */
@androidx.compose.runtime.Composable
fun HojaMirador(onCerrar: () -> Unit, contenido: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Paleta.S1,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        scrimColor = Color(0xB3000000),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Paleta.Linea)) },
    ) { contenido() }
}


/** Quien quiera guardar un video (ver más tarde / listas) llama a esto; la pantalla raíz abre la hoja. */
val LocalGuardar = androidx.compose.runtime.staticCompositionLocalOf<(com.santyramos.mirador.extractor.Elemento.Video) -> Unit> { {} }

/** Botón Suscribirse / Suscrito que lee y escribe en la biblioteca local. */
@androidx.compose.runtime.Composable
fun BotonSuscribir(canalId: String?, crear: () -> com.santyramos.mirador.data.lib.Suscripcion, modifier: Modifier = Modifier) {
    if (canalId.isNullOrBlank()) return
    val suscrito by com.santyramos.mirador.data.lib.Biblioteca.dao.estaSuscrito(canalId).collectAsState(initial = 0)
    if (suscrito > 0) {
        Surface(
            onClick = { com.santyramos.mirador.data.lib.Biblioteca.anularSuscripcion(canalId) }, shape = RoundedCornerShape(12.dp),
            color = Paleta.S2, border = BorderStroke(1.dp, Paleta.Linea), modifier = modifier.height(36.dp),
        ) { Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Suscrito", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    } else {
        Surface(
            onClick = { com.santyramos.mirador.data.lib.Biblioteca.suscribir(crear()) }, shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary, modifier = modifier.height(36.dp),
        ) { Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Suscribirme", style = MaterialTheme.typography.labelMedium, color = Paleta.SobreAcento) } }
    }
}
