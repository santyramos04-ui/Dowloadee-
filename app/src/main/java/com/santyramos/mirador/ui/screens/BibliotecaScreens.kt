package com.santyramos.mirador.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.data.lib.Biblioteca
import com.santyramos.mirador.data.lib.aElemento
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.ui.BotonIcono
import com.santyramos.mirador.ui.Encabezado
import com.santyramos.mirador.ui.EstadoVacio
import com.santyramos.mirador.ui.LocalGuardar
import com.santyramos.mirador.ui.theme.Paleta
import com.santyramos.mirador.util.Format

/** Barra superior de las pantallas interiores de la biblioteca. */
@Composable
private fun Barra(titulo: String, onAtras: () -> Unit, acciones: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BotonIcono(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onAtras)
        Text(titulo, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        acciones()
    }
}

/** Fila de video con barra de progreso opcional y menú de tres puntos (descargar, guardar, quitar). */
@Composable
private fun FilaBiblioteca(
    v: Elemento.Video, progreso: Float?, onClick: () -> Unit, onDescargar: () -> Unit, textoQuitar: String, onQuitar: () -> Unit,
) {
    val guardar = LocalGuardar.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(14.dp)).background(Paleta.S2)) {
            AsyncImage(model = v.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            if (v.duracionSeg > 0) Text(
                Format.duracion(v.duracionSeg), color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            if (progreso != null && progreso > 0.02f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progreso.coerceIn(0f, 1f)).height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(v.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(v.autor.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Box {
            BotonIcono(Icons.Outlined.MoreVert, "Más opciones", { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Paleta.S2) {
                DropdownMenuItem(text = { Text("Descargar") }, onClick = { menu = false; onDescargar() })
                DropdownMenuItem(text = { Text("Guardar en una lista") }, onClick = { menu = false; guardar(v) })
                DropdownMenuItem(text = { Text(textoQuitar) }, onClick = { menu = false; onQuitar() })
            }
        }
    }
}

@Composable
fun HistorialScreen(onAtras: () -> Unit, onAbrirVideo: (String) -> Unit, onDescargar: (String) -> Unit) {
    BackHandler(onBack = onAtras)
    val lista by Biblioteca.dao.observarHistorial().collectAsState(initial = emptyList())
    var confirmar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barra("Historial", onAtras) {
            if (lista.isNotEmpty()) BotonIcono(Icons.Outlined.DeleteOutline, "Borrar historial", { confirmar = true })
        }
        if (lista.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.History, "Aún no has visto nada", "Los videos que veas aparecerán aquí y podrás seguir justo donde te quedaste.")
            }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(lista, key = { it.url }) { v ->
                val prog = if (v.duracionSeg > 0) v.posicionMs / (v.duracionSeg * 1000f) else null
                FilaBiblioteca(v.aElemento(), prog, { onAbrirVideo(v.url) }, { onDescargar(v.url) }, "Quitar del historial") { Biblioteca.quitarDelHistorial(v.url) }
            }
        }
    }
    if (confirmar) AlertDialog(
        onDismissRequest = { confirmar = false },
        title = { Text("¿Borrar todo el historial?") },
        text = { Text("Se borra solo de este celular. No afecta a tus descargas ni a tus listas.") },
        confirmButton = { TextButton(onClick = { Biblioteca.borrarHistorial(); confirmar = false }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
    )
}

@Composable
fun GuardadosScreen(onAtras: () -> Unit, onAbrirVideo: (String) -> Unit, onDescargar: (String) -> Unit) {
    BackHandler(onBack = onAtras)
    val lista by Biblioteca.dao.observarGuardados().collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barra("Ver más tarde", onAtras)
        if (lista.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.BookmarkBorder, "Nada guardado todavía", "Toca el marcador de un video, o mantén pulsado cualquier video, para guardarlo aquí.")
            }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(lista, key = { it.url }) { g ->
                FilaBiblioteca(g.aElemento(), null, { com.santyramos.mirador.player.VideoController.definirCola(lista.map { it.url }); onAbrirVideo(g.url) }, { onDescargar(g.url) }, "Quitar de la lista") { Biblioteca.quitarGuardado(g.url) }
            }
        }
    }
}

@Composable
fun ListasScreen(onAtras: () -> Unit, onAbrirLista: (Long) -> Unit) {
    BackHandler(onBack = onAtras)
    val listas by Biblioteca.dao.observarListas().collectAsState(initial = emptyList())
    var nueva by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barra("Mis listas", onAtras) { BotonIcono(Icons.Outlined.PlaylistAdd, "Nueva lista", { nueva = true }, destacado = true) }
        if (listas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.PlaylistAdd, "Crea tu primera lista", "Agrupa videos como quieras. Se guardan en tu celular y los puedes reproducir o descargar juntos.") {
                    com.santyramos.mirador.ui.BotonPrimario("Nueva lista", { nueva = true }, Modifier.fillMaxWidth())
                }
            }
        } else LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(listas, key = { it.id }) { l ->
                Surface(
                    onClick = { onAbrirLista(l.id) }, shape = RoundedCornerShape(20.dp), color = Paleta.S1,
                    border = BorderStroke(1.dp, Paleta.Linea), modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(96.dp).height(56.dp).clip(RoundedCornerShape(12.dp)).background(Paleta.S3)) {
                            AsyncImage(model = l.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        }
                        Column(Modifier.weight(1f)) {
                            Text(l.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${l.cantidad} videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (nueva) AlertDialog(
        onDismissRequest = { nueva = false },
        title = { Text("Nueva lista") },
        text = { androidx.compose.material3.OutlinedTextField(value = nombre, onValueChange = { nombre = it.take(60) }, singleLine = true, label = { Text("Nombre") }) },
        confirmButton = { TextButton(enabled = nombre.isNotBlank(), onClick = { Biblioteca.crearLista(nombre); nombre = ""; nueva = false }) { Text("Crear") } },
        dismissButton = { TextButton(onClick = { nueva = false }) { Text("Cancelar") } },
    )
}

@Composable
fun ListaPropiaScreen(id: Long, onAtras: () -> Unit, onAbrirVideo: (String) -> Unit, onDescargar: (String) -> Unit, onDescargarTodo: (com.santyramos.mirador.download.ListaInfo) -> Unit) {
    BackHandler(onBack = onAtras)
    val items by Biblioteca.dao.observarItems(id).collectAsState(initial = emptyList())
    val listas by Biblioteca.dao.observarListas().collectAsState(initial = emptyList())
    val nombreActual = listas.firstOrNull { it.id == id }?.nombre ?: "Lista"
    var confirmar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barra(nombreActual, onAtras) { BotonIcono(Icons.Outlined.DeleteOutline, "Borrar lista", { confirmar = true }) }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.PlaylistAdd, "Lista vacía", "Mantén pulsado un video y elige esta lista para agregarlo.")
            }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                com.santyramos.mirador.ui.BotonPrimario(
                    "Descargar videos de la lista", {
                        onDescargarTodo(
                            com.santyramos.mirador.download.ListaInfo(
                                nombreActual, null,
                                items.map { com.santyramos.mirador.download.EntradaLista(it.url, it.titulo, it.miniatura, it.duracionSeg) },
                            ),
                        )
                    },
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    icono = Icons.Outlined.FileDownload,
                )
            }
            items(items, key = { it.id }) { it ->
                FilaBiblioteca(it.aElemento(), null, { com.santyramos.mirador.player.VideoController.definirCola(items.map { x -> x.url }); onAbrirVideo(it.url) }, { onDescargar(it.url) }, "Quitar de la lista") { Biblioteca.quitarDeLista(id, it.url) }
            }
        }
    }
    if (confirmar) AlertDialog(
        onDismissRequest = { confirmar = false },
        title = { Text("¿Borrar «$nombreActual»?") },
        text = { Text("Los videos no se borran de ningún lado; solo desaparece la lista.") },
        confirmButton = { TextButton(onClick = { Biblioteca.borrarLista(id); confirmar = false; onAtras() }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
    )
}


