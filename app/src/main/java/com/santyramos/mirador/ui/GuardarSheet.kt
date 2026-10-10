package com.santyramos.mirador.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.santyramos.mirador.data.lib.Biblioteca
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.ui.theme.Paleta

/** Hoja "Guardar en…": Ver más tarde y tus listas, con casilla en las que ya lo tienen. */
@Composable
fun GuardarSheetContent(video: Elemento.Video, onCerrar: () -> Unit) {
    val dao = Biblioteca.dao
    val guardado by dao.estaGuardado(video.url).collectAsState(initial = 0)
    val listas by dao.observarListas().collectAsState(initial = emptyList())
    val enListas by dao.listasQueTienen(video.url).collectAsState(initial = emptyList())
    var nueva by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Guardar en…", style = MaterialTheme.typography.titleLarge)
        Text(video.titulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)

        FilaGuardar(Icons.Outlined.BookmarkBorder, "Ver más tarde", null, guardado > 0) {
            if (guardado > 0) Biblioteca.quitarGuardado(video.url) else Biblioteca.guardar(video)
        }
        listas.forEach { l ->
            val esta = l.id in enListas
            FilaGuardar(Icons.Outlined.PlaylistAdd, l.nombre, "${l.cantidad} videos", esta) {
                if (esta) Biblioteca.quitarDeLista(l.id, video.url) else Biblioteca.agregarALista(l.id, video)
            }
        }
        if (nueva) {
            OutlinedTextField(
                value = nombre, onValueChange = { nombre = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre de la lista") }, shape = RoundedCornerShape(16.dp),
            )
            BotonPrimario("Crear y guardar", { Biblioteca.crearLista(nombre, video); nombre = ""; nueva = false }, Modifier.fillMaxWidth(), enabled = nombre.isNotBlank())
        } else {
            BotonSecundario("Nueva lista", { nueva = true }, Modifier.fillMaxWidth(), icono = Icons.Outlined.Add)
        }
        BotonPrimario("Listo", onCerrar, Modifier.fillMaxWidth())
    }
}

@Composable
private fun FilaGuardar(icono: androidx.compose.ui.graphics.vector.ImageVector, titulo: String, detalle: String?, marcada: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
        color = if (marcada) Paleta.AcentoSuave else Paleta.S2,
        border = BorderStroke(1.dp, if (marcada) Paleta.AcentoBorde else Paleta.Linea),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, null, tint = if (marcada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detalle != null) Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .then(if (marcada) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(2.dp, Paleta.Texto3, CircleShape)),
                contentAlignment = Alignment.Center,
            ) { if (marcada) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Paleta.SobreAcento) }
        }
    }
}
