package com.santyramos.mirador.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.TextButton
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.santyramos.mirador.ui.theme.Paleta
import com.santyramos.mirador.util.Format
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.download.Cookies
import com.santyramos.mirador.download.DownloadCenter
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.download.ListaInfo
import com.santyramos.mirador.download.Preset
import com.santyramos.mirador.download.UrlTools
import com.santyramos.mirador.download.YtDlpEngine
import kotlinx.coroutines.launch

/** Formatos que tienen sentido para muchos archivos a la vez. */
private val PRESETS_MASIVOS = listOf(Preset.MEJOR, Preset.P720, Preset.P480, Preset.MP3)

@Composable
private fun SelectorFormato(elegido: Preset, onElegir: (Preset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PRESETS_MASIVOS.forEach { p ->
            OpcionTarjeta(
                seleccionada = elegido == p, titulo = p.etiqueta, detalle = p.descripcion,
                recomendada = p == Preset.MEJOR, onClick = { onElegir(p) },
            )
        }
    }
}

/** Descargar una lista de reproducción de YouTube: se leen sus videos y se eligen todos o solo algunos. */
@Composable
fun ListaSheetContent(url: String, onCerrar: () -> Unit, onEncolada: () -> Unit) {
    val contexto = LocalContext.current
    var lista by remember(url) { mutableStateOf<ListaInfo?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var intento by remember(url) { mutableStateOf(0) }

    LaunchedEffect(url, intento) {
        error = null; lista = null
        try {
            val l = YtDlpEngine.lista(url, Cookies.archivo(contexto))
            if (l.entradas.isEmpty()) error = "La lista está vacía o sus videos son privados." else lista = l
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = ErrorMapper.mensaje(e)
        }
    }

    val l = lista
    if (l != null) {
        SeleccionListaContent(l, onCerrar, onEncolada)
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (error != null) {
            Text("No se puede leer la lista", style = MaterialTheme.typography.titleMedium)
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BotonSecundario("Cerrar", onCerrar, Modifier.weight(1f))
                BotonPrimario("Reintentar", { intento++ }, Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text("Leyendo la lista…")
            }
        }
    }
}

/** Lista ya conocida (de YouTube o propia): formato + casillas para elegir qué videos bajar. Todos vienen marcados. */
@Composable
fun SeleccionListaContent(l: ListaInfo, onCerrar: () -> Unit, onEncolada: () -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var elegido by remember { mutableStateOf(Preset.MEJOR) }
    var seleccion by remember(l) { mutableStateOf(l.entradas.indices.toSet()) }
    var trabajando by remember { mutableStateOf(false) }
    val total = l.entradas.size

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp)) {
            item {
                Text(l.titulo, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "$total videos" + (l.autor?.let { " · $it" } ?: "") + "\nSe guardan en Descargas/Mirador/…/${UrlTools.nombreSeguro(l.titulo, 60)}, numerados en orden.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                    items(PRESETS_MASIVOS) { p -> Filtro(p.etiqueta, elegido == p) { elegido = p } }
                }
                Text(elegido.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${seleccion.size} de $total elegidos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { seleccion = l.entradas.indices.toSet() }, enabled = seleccion.size < total) { Text("Todos") }
                    TextButton(onClick = { seleccion = emptySet() }, enabled = seleccion.isNotEmpty()) { Text("Ninguno") }
                }
            }
            itemsIndexed(l.entradas) { i, e ->
                val marcado = i in seleccion
                Row(
                    Modifier.fillMaxWidth().clickable { seleccion = if (marcado) seleccion - i else seleccion + i }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .then(if (marcado) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(2.dp, Paleta.Texto3, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) { if (marcado) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Paleta.SobreAcento) }
                    Box(Modifier.size(width = 84.dp, height = 48.dp).clip(RoundedCornerShape(10.dp)).background(Paleta.S3)) {
                        AsyncImage(model = e.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.titulo, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (marcado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        e.duracionSeg?.takeIf { it > 0 }?.let { Text(Format.duracion(it), style = MaterialTheme.typography.labelSmall, color = Paleta.Texto3) }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (seleccion.size > 50) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    "Son ${seleccion.size} videos: tardará bastante y ocupará mucho espacio. Puedes pausar o cancelar desde Descargas.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BotonSecundario("Cancelar", onCerrar, Modifier.weight(1f))
                BotonPrimario(
                    if (seleccion.isEmpty()) "Elige videos" else "Descargar ${seleccion.size}", enabled = !trabajando && seleccion.isNotEmpty(),
                    modifier = Modifier.weight(1.5f), icono = Icons.Outlined.FileDownload,
                    onClick = {
                        trabajando = true
                        scope.launch {
                            val n = DownloadCenter.encolarLista(l, elegido, seleccion)
                            Toast.makeText(contexto, "Se agregaron $n videos a la cola", Toast.LENGTH_LONG).show()
                            onEncolada()
                        }
                    },
                )
            }
        }
    }
}

/** Varios enlaces a la vez: se descargan todos con el mismo formato. */
@Composable
fun LoteSheetContent(urls: List<String>, onCerrar: () -> Unit, onEncolada: () -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var elegido by remember { mutableStateOf(Preset.MEJOR) }
    var listasCompletas by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf(false) }
    val hayVideosDeLista = urls.any { UrlTools.videoDentroDeLista(it) }
    val hayListas = urls.any { UrlTools.esListaYoutube(it) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${urls.size} enlaces", style = MaterialTheme.typography.titleLarge)
        Text(
            "Se descargarán todos, uno tras otro (2 a la vez). Las publicaciones con fotos se bajan como fotos.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        urls.take(4).forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (urls.size > 4) Text("… y ${urls.size - 4} más", style = MaterialTheme.typography.bodySmall)
        SelectorFormato(elegido) { elegido = it }
        if (hayListas) Text("Los enlaces de listas de YouTube se descargan completos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (hayVideosDeLista) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Si un video viene de una lista, bajar la lista completa", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = listasCompletas, onCheckedChange = { listasCompletas = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BotonSecundario("Cancelar", onCerrar, Modifier.weight(1f))
            BotonPrimario(
                "Descargar todo", enabled = !trabajando, modifier = Modifier.weight(1.5f), icono = Icons.Outlined.FileDownload,
                onClick = {
                    trabajando = true
                    scope.launch {
                        val r = DownloadCenter.encolarLote(urls, elegido, listasCompletas)
                        val msg = "Se agregaron ${r.archivos} descargas a la cola" + if (r.listasConError.isNotEmpty()) " (alguna lista no se pudo leer)" else ""
                        Toast.makeText(contexto, msg, Toast.LENGTH_LONG).show()
                        onEncolada()
                    }
                },
            )
        }
    }
}
