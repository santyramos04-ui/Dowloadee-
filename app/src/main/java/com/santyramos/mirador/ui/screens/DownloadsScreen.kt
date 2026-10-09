package com.santyramos.mirador.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.data.db.DownloadEntity
import com.santyramos.mirador.data.db.DownloadStatus
import com.santyramos.mirador.download.DownloadCenter
import com.santyramos.mirador.download.UrlTools
import com.santyramos.mirador.ui.DownloadSheetContent
import com.santyramos.mirador.util.Format
import kotlinx.coroutines.launch

/** Pestaña Descargas: pegar un enlace + cola de descargas con pausa, reanudar, cancelar y reintentar. */
@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val descargas by DownloadCenter.observarTodas().collectAsState(initial = emptyList())
    var texto by rememberSaveable { mutableStateOf("") }
    var urlParaDescargar by remember { mutableStateOf<String?>(null) }
    var urlsLote by remember { mutableStateOf<List<String>?>(null) }
    val enlaces = remember(texto) { UrlTools.todosEnlaces(texto) }
    var avisoEnlace by remember { mutableStateOf<String?>(null) }

    fun pegar() {
        val cb = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(contexto)?.toString()
        val nuevos = UrlTools.todosEnlaces(t)
        if (nuevos.isEmpty()) avisoEnlace = "El portapapeles no tiene ningún enlace."
        else {
            // Se agregan a lo que ya hay (así puedes pegar varios, uno tras otro).
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

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text("Descargas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        Text(
            "Pega uno o varios enlaces (uno por línea) de videos, audios, fotos o listas de reproducción de YouTube, X, Instagram, TikTok, Facebook y más. También puedes usar «Compartir → Mirador» desde esas apps.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = texto, onValueChange = { texto = it }, modifier = Modifier.weight(1f), minLines = 1, maxLines = 5,
                placeholder = { Text("https://…") },
                supportingText = { if (enlaces.size > 1) Text("${enlaces.size} enlaces detectados") },
                trailingIcon = { IconButton(onClick = { pegar() }) { Icon(Icons.Filled.ContentPaste, "Pegar") } },
            )
            Button(onClick = { continuar() }) { Text(if (enlaces.size > 1) "Descargar ${enlaces.size}" else "Buscar") }
        }
        avisoEnlace?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }

        if (descargas.any { it.status in setOf(DownloadStatus.DONE, DownloadStatus.ERROR, DownloadStatus.CANCELED) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { scope.launch { DownloadCenter.limpiarTerminadas() } }) { Text("Limpiar terminadas") }
            }
        }
        if (descargas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no hay descargas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(descargas, key = { it.id }) { d -> TarjetaDescarga(d) }
            }
        }
    }

    urlsLote?.let { lote ->
        ModalBottomSheet(onDismissRequest = { urlsLote = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            com.santyramos.mirador.ui.LoteSheetContent(urls = lote, onCerrar = { urlsLote = null }, onEncolada = { urlsLote = null; texto = "" })
        }
    }

    urlParaDescargar?.let { u ->
        ModalBottomSheet(onDismissRequest = { urlParaDescargar = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            DownloadSheetContent(url = u, onCerrar = { urlParaDescargar = null }, onEncolada = { urlParaDescargar = null; texto = "" })
        }
    }
}

@Composable
private fun TarjetaDescarga(d: DownloadEntity) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = d.miniatura, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(96.dp).height(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(Modifier.weight(1f)) {
                    Text(d.titulo, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(textoEstado(d), style = MaterialTheme.typography.bodySmall,
                        color = if (d.status == DownloadStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.PAUSED) {
                if (d.progreso >= 0 && d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.PAUSED)
                    LinearProgressIndicator(progress = { (d.progreso / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (d.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (d.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
                        IconButton(onClick = { scope.launch { DownloadCenter.pausar(d.id) } }) { Icon(Icons.Filled.Pause, "Pausar") }
                        IconButton(onClick = { scope.launch { DownloadCenter.cancelar(d.id) } }) { Icon(Icons.Filled.Close, "Cancelar") }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = { scope.launch { DownloadCenter.reanudar(d.id) } }) { Icon(Icons.Filled.PlayArrow, "Reanudar") }
                        IconButton(onClick = { scope.launch { DownloadCenter.cancelar(d.id) } }) { Icon(Icons.Filled.Close, "Cancelar") }
                    }
                    DownloadStatus.ERROR, DownloadStatus.CANCELED -> {
                        IconButton(onClick = { scope.launch { DownloadCenter.reintentar(d.id) } }) { Icon(Icons.Filled.Refresh, "Reintentar") }
                        IconButton(onClick = { scope.launch { DownloadCenter.borrar(d.id) } }) { Icon(Icons.Filled.Delete, "Quitar de la lista") }
                    }
                    DownloadStatus.DONE -> {
                        if (d.archivoUri != null) TextButton(onClick = { abrir(contexto, d) }) { Text("Abrir") }
                        IconButton(onClick = { scope.launch { DownloadCenter.borrar(d.id) } }) { Icon(Icons.Filled.Delete, "Quitar de la lista") }
                    }
                }
            }
        }
    }
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
    DownloadStatus.DONE -> "✅ Lista · guardada en Descargas/Mirador"
    DownloadStatus.ERROR -> "❌ ${d.error ?: "Error"}"
    DownloadStatus.CANCELED -> "Cancelada"
}

private fun abrir(contexto: Context, d: DownloadEntity) {
    val uri = Uri.parse(d.archivoUri ?: return)
    val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.archivoMime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { contexto.startActivity(Intent.createChooser(ver, "Abrir con").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
