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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
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
import com.santyramos.mirador.ui.BotonIcono
import com.santyramos.mirador.ui.BotonPrimario
import com.santyramos.mirador.ui.Encabezado
import com.santyramos.mirador.ui.EstadoVacio
import com.santyramos.mirador.ui.Filtro
import com.santyramos.mirador.ui.Panel
import com.santyramos.mirador.ui.theme.Acento
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
        Encabezado(
            "Descargas",
            if (descargas.isEmpty()) "Pega un enlace para empezar"
            else "$enCurso en curso · ${descargas.count { it.status == DownloadStatus.DONE }} listas",
        )

        // ---- Panel para pegar enlaces ----
        Panel(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = texto, onValueChange = { texto = it }, modifier = Modifier.fillMaxWidth(),
                    minLines = 1, maxLines = 5, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Pega aquí uno o varios enlaces", color = Paleta.Texto3) },
                    trailingIcon = { IconButton(onClick = { pegar() }) { Icon(Icons.Outlined.ContentPaste, "Pegar del portapapeles", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    supportingText = {
                        if (enlaces.size > 1) Text("${enlaces.size} enlaces detectados")
                        else if (avisoEnlace != null) Text(avisoEnlace!!, color = MaterialTheme.colorScheme.error)
                    },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Paleta.S2, unfocusedContainerColor = Paleta.S2,
                        focusedBorderColor = Acento, unfocusedBorderColor = Paleta.Linea, cursorColor = Acento,
                    ),
                )
                BotonPrimario(
                    if (enlaces.size > 1) "Descargar ${enlaces.size} enlaces" else "Buscar opciones",
                    onClick = { continuar() }, modifier = Modifier.fillMaxWidth(), icono = Icons.Outlined.FileDownload,
                )
            }
        }

        // ---- Filtros ----
        if (descargas.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FiltroDescargas.entries) { f ->
                    val n = descargas.count { it.coincide(f) }
                    Filtro(if (f == FiltroDescargas.TODAS) f.etiqueta else "${f.etiqueta} ($n)", filtro == f) { filtro = f }
                }
            }
            if (descargas.any { it.status in setOf(DownloadStatus.DONE, DownloadStatus.ERROR, DownloadStatus.CANCELED) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { scope.launch { DownloadCenter.limpiarTerminadas() } }) { Text("Limpiar terminadas", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        if (descargas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.FileDownload, "Aún no hay descargas", "Pega un enlace arriba o usa «Compartir → Mirador» desde otra app.")
            }
        } else if (visibles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay nada en esta categoría.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)) {
                items(visibles, key = { it.id }) { d -> FilaDescarga(d) }
            }
        }
    }

    urlsLote?.let { lote ->
        com.santyramos.mirador.ui.HojaMirador(onCerrar = { urlsLote = null }) {
            LoteSheetContent(urls = lote, onCerrar = { urlsLote = null }, onEncolada = { urlsLote = null; texto = "" })
        }
    }
    urlParaDescargar?.let { u ->
        com.santyramos.mirador.ui.HojaMirador(onCerrar = { urlParaDescargar = null }) {
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
private fun FilaDescarga(d: DownloadEntity) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val estado = d.status
    val colorEstado = when (estado) {
        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
        DownloadStatus.PAUSED -> Paleta.Aviso
        DownloadStatus.DONE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val progresoAnimado by animateFloatAsState((d.progreso / 100f).coerceIn(0f, 1f), label = "progreso")

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(104.dp).height(60.dp).clip(RoundedCornerShape(12.dp)).background(Paleta.S2)) {
                AsyncImage(model = d.miniatura, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                if (estado == DownloadStatus.DONE || estado == DownloadStatus.ERROR) {
                    Box(Modifier.matchParentSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                        Icon(
                            if (estado == DownloadStatus.DONE) Icons.Filled.Check else Icons.Outlined.ErrorOutline, null,
                            tint = if (estado == DownloadStatus.DONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(d.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(colorEstado))
                    Text(textoEstado(d), style = MaterialTheme.typography.bodySmall, color = colorEstado, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Insignia(etiquetaPreset(d))
                }
                when (estado) {
                    DownloadStatus.RUNNING ->
                        if (d.progreso in 0f..98.9f) LinearProgressIndicator(progress = { progresoAnimado }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Acento, trackColor = Paleta.S3, gapSize = 0.dp, drawStopIndicator = {})
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Acento, trackColor = Paleta.S3)
                    DownloadStatus.PAUSED -> LinearProgressIndicator(progress = { progresoAnimado }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Paleta.Aviso, trackColor = Paleta.S3, gapSize = 0.dp, drawStopIndicator = {})
                    DownloadStatus.QUEUED -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), color = Paleta.Texto3, trackColor = Paleta.S3)
                    else -> {}
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                when (estado) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
                        BotonIcono(Icons.Outlined.Pause, "Pausar", { scope.launch { DownloadCenter.pausar(d.id) } })
                        BotonIcono(Icons.Outlined.Close, "Cancelar", { scope.launch { DownloadCenter.cancelar(d.id) } })
                    }
                    DownloadStatus.PAUSED -> {
                        BotonIcono(Icons.Outlined.PlayArrow, "Reanudar", { scope.launch { DownloadCenter.reanudar(d.id) } }, destacado = true)
                        BotonIcono(Icons.Outlined.Close, "Cancelar", { scope.launch { DownloadCenter.cancelar(d.id) } })
                    }
                    DownloadStatus.ERROR, DownloadStatus.CANCELED -> {
                        BotonIcono(Icons.Outlined.Refresh, "Reintentar", { scope.launch { DownloadCenter.reintentar(d.id) } }, destacado = true)
                        BotonIcono(Icons.Outlined.Delete, "Quitar de la lista", { scope.launch { DownloadCenter.borrar(d.id) } })
                    }
                    DownloadStatus.DONE -> {
                        if (d.archivoUri != null) BotonIcono(Icons.Outlined.OpenInNew, "Abrir", { abrir(contexto, d) }, destacado = true)
                        BotonIcono(Icons.Outlined.Delete, "Quitar de la lista", { scope.launch { DownloadCenter.borrar(d.id) } })
                    }
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(color = Color(0xFF1A1A1E), modifier = Modifier.padding(start = 20.dp))
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
    DownloadStatus.DONE -> "Lista"
    DownloadStatus.ERROR -> d.error ?: "Error"
    DownloadStatus.CANCELED -> "Cancelada"
}

private fun abrir(contexto: Context, d: DownloadEntity) {
    val uri = Uri.parse(d.archivoUri ?: return)
    val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.archivoMime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { contexto.startActivity(Intent.createChooser(ver, "Abrir con").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
