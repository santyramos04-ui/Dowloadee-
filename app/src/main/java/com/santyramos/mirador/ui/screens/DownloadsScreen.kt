package com.santyramos.mirador.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.data.db.DownloadEntity
import com.santyramos.mirador.data.db.DownloadStatus
import com.santyramos.mirador.download.DownloadCenter
import com.santyramos.mirador.download.Preset
import com.santyramos.mirador.download.UrlTools
import com.santyramos.mirador.ui.DownloadSheetContent
import com.santyramos.mirador.ui.EstadoVacio
import com.santyramos.mirador.ui.Insignia
import com.santyramos.mirador.ui.LoteSheetContent
import com.santyramos.mirador.ui.theme.Paleta
import com.santyramos.mirador.util.Format
import kotlinx.coroutines.launch

private enum class FiltroDescargas(val etiqueta: String) { TODAS("Todas"), EN_CURSO("En curso"), LISTAS("Listas"), ERRORES("Con error") }

private fun DownloadEntity.coincide(f: FiltroDescargas) = when (f) {
    FiltroDescargas.TODAS -> true
    FiltroDescargas.EN_CURSO -> status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED)
    FiltroDescargas.LISTAS -> status == DownloadStatus.DONE
    FiltroDescargas.ERRORES -> status == DownloadStatus.ERROR || status == DownloadStatus.CANCELED
}

/** Pestaña Descargas: pegar uno o varios enlaces + cola con pausa, reanudar, cancelar y reintentar. */
@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val descargas by DownloadCenter.observarTodas().collectAsState(initial = emptyList())
    var texto by rememberSaveable { mutableStateOf("") }
    var urlParaDescargar by remember { mutableStateOf<String?>(null) }
    var urlsLote by remember { mutableStateOf<List<String>?>(null) }
    var filtro by rememberSaveable { mutableStateOf(FiltroDescargas.TODAS) }
    val enlaces = remember(texto) { UrlTools.todosEnlaces(texto) }
    var avisoEnlace by remember { mutableStateOf<String?>(null) }

    fun pegar() {
        val cb = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(contexto)?.toString()
        val nuevos = UrlTools.todosEnlaces(t)
        if (nuevos.isEmpty()) avisoEnlace = "El portapapeles no tiene ningún enlace."
        else {
            texto = (UrlTools.todosEnlaces(texto) + nuevos).distinct().joinToString("\n")
            avisoEnlace = null
        }
    }
    fun continuar() {
        when {
            enlaces.isEmpty() -> avisoEnlace = "Escribe o pega un enlace que empiece con http…"
            enlaces.size == 1 -> { avisoEnlace = null; urlParaDescargar = enlaces.first() }
            else -> { avisoEnlace = null; urlsLote = enlaces }
        }
    }

    val enCurso = descargas.count { it.coincide(FiltroDescargas.EN_CURSO) }
    val visibles = descargas.filter { it.coincide(filtro) }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text("Descargas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp))

        // ---- Tarjeta para pegar enlaces ----
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Pega uno o varios enlaces (uno por línea): videos, audios, fotos o listas de YouTube, X, Instagram, TikTok, Facebook y más.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextField(
                    value = texto, onValueChange = { texto = it }, modifier = Modifier.fillMaxWidth(),
                    minLines = 1, maxLines = 5, shape = MaterialTheme.shapes.medium,
                    placeholder = { Text("https://…") },
                    supportingText = { if (enlaces.size > 1) Text("${enlaces.size} enlaces detectados") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                avisoEnlace?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedButton(onClick = { pegar() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Pegar")
                    }
                    Button(onClick = { continuar() }, modifier = Modifier.weight(1.4f)) {
                        Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (enlaces.size > 1) "Descargar ${enlaces.size}" else "Buscar opciones")
                    }
                }
            }
        }

        // ---- Filtros ----
        if (descargas.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FiltroDescargas.entries) { f ->
                    val n = descargas.count { it.coincide(f) }
                    FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text(if (f == FiltroDescargas.TODAS) f.etiqueta else "${f.etiqueta} ($n)") })
                }
            }
            if (descargas.any { it.status in setOf(DownloadStatus.DONE, DownloadStatus.ERROR, DownloadStatus.CANCELED) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enCurso > 0) "$enCurso en curso" else "Todo al día", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { scope.launch { DownloadCenter.limpiarTerminadas() } }) { Text("Limpiar terminadas") }
                }
            }
        }

        if (descargas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Filled.Download, "Aún no hay descargas", "Pega un enlace arriba o usa «Compartir → Mirador» desde otra app.")
            }
        } else if (visibles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay nada en esta categoría.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibles, key = { it.id }) { d -> TarjetaDescarga(d) }
            }
        }
    }

    urlsLote?.let { lote ->
        ModalBottomSheet(onDismissRequest = { urlsLote = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LoteSheetContent(urls = lote, onCerrar = { urlsLote = null }, onEncolada = { urlsLote = null; texto = "" })
        }
    }
    urlParaDescargar?.let { u ->
        ModalBottomSheet(onDismissRequest = { urlParaDescargar = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            DownloadSheetContent(url = u, onCerrar = { urlParaDescargar = null }, onEncolada = { urlParaDescargar = null; texto = "" })
        }
    }
}

private fun etiquetaPreset(d: DownloadEntity): String = when (Preset.from(d.preset)) {
    Preset.MEJOR -> "MP4 · mejor"
    Preset.P720 -> "MP4 · 720p"
    Preset.P480 -> "MP4 · 480p"
    Preset.MAX_4K -> "4K"
    Preset.MP3 -> "MP3"
    Preset.IMAGENES -> "Fotos / archivos"
}

@Composable
private fun TarjetaDescarga(d: DownloadEntity) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val estado = d.status
    val colorEstado = when (estado) {
        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
        DownloadStatus.DONE -> MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> Paleta.Aviso
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val progresoAnimado by animateFloatAsState((d.progreso / 100f).coerceIn(0f, 1f), label = "progreso")

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    AsyncImage(model = d.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    if (estado == DownloadStatus.DONE || estado == DownloadStatus.ERROR) {
                        Box(Modifier.matchParentSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                            Icon(if (estado == DownloadStatus.DONE) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null, tint = if (estado == DownloadStatus.DONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(d.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Insignia(etiquetaPreset(d))
                        d.grupoNombre?.let { Insignia("Lista", color = MaterialTheme.colorScheme.surfaceContainerHighest, colorTexto = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Text(textoEstado(d), style = MaterialTheme.typography.bodySmall, color = colorEstado, maxLines = 3, overflow = TextOverflow.Ellipsis)
            when (estado) {
                DownloadStatus.RUNNING ->
                    if (d.progreso in 0f..98.9f) LinearProgressIndicator(progress = { progresoAnimado }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                DownloadStatus.PAUSED -> LinearProgressIndicator(progress = { progresoAnimado }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Paleta.Aviso, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                DownloadStatus.QUEUED -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                else -> {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                when (estado) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
                        AccionIcono(Icons.Filled.Pause, "Pausar") { scope.launch { DownloadCenter.pausar(d.id) } }
                        AccionIcono(Icons.Filled.Close, "Cancelar") { scope.launch { DownloadCenter.cancelar(d.id) } }
                    }
                    DownloadStatus.PAUSED -> {
                        AccionIcono(Icons.Filled.PlayArrow, "Reanudar", destacado = true) { scope.launch { DownloadCenter.reanudar(d.id) } }
                        AccionIcono(Icons.Filled.Close, "Cancelar") { scope.launch { DownloadCenter.cancelar(d.id) } }
                    }
                    DownloadStatus.ERROR, DownloadStatus.CANCELED -> {
                        AccionIcono(Icons.Filled.Refresh, "Reintentar", destacado = true) { scope.launch { DownloadCenter.reintentar(d.id) } }
                        AccionIcono(Icons.Filled.Delete, "Quitar de la lista") { scope.launch { DownloadCenter.borrar(d.id) } }
                    }
                    DownloadStatus.DONE -> {
                        if (d.archivoUri != null) Button(onClick = { abrir(contexto, d) }, contentPadding = PaddingValues(horizontal = 16.dp)) {
                            Icon(Icons.Filled.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Abrir")
                        }
                        AccionIcono(Icons.Filled.Delete, "Quitar de la lista") { scope.launch { DownloadCenter.borrar(d.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccionIcono(icono: androidx.compose.ui.graphics.vector.ImageVector, descripcion: String, destacado: Boolean = false, onClick: () -> Unit) {
    if (destacado) FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(icono, descripcion, Modifier.size(20.dp)) }
    else IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(icono, descripcion, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun textoEstado(d: DownloadEntity): String = when (d.status) {
    DownloadStatus.QUEUED -> "En cola…"
    DownloadStatus.RUNNING -> buildString {
        if (d.progreso >= 99f && d.velocidadBps == 0L) append("Procesando el archivo…")
        else {
            append(if (d.progreso >= 0) "${d.progreso.toInt()}%" else "Descargando…")
            if (d.bytesTotales > 0) append(" · ${Format.bytes(d.bytesDescargados)} de ${Format.bytes(d.bytesTotales)}")
            if (d.velocidadBps > 0) append(" · ${Format.velocidad(d.velocidadBps)}")
            if (d.etaSeg > 0) append(" · quedan ${Format.eta(d.etaSeg)}")
        }
    }
    DownloadStatus.PAUSED -> "En pausa · ${d.progreso.toInt()}%"
    DownloadStatus.DONE -> "Lista · guardada en Descargas/Mirador"
    DownloadStatus.ERROR -> d.error ?: "Error"
    DownloadStatus.CANCELED -> "Cancelada"
}

private fun abrir(contexto: Context, d: DownloadEntity) {
    val uri = Uri.parse(d.archivoUri ?: return)
    val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.archivoMime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { contexto.startActivity(Intent.createChooser(ver, "Abrir con").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
