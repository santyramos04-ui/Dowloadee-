package com.santyramos.mirador.escritorio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import java.io.File

private val esEnlace = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

@Composable
private fun campoColores() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = P.s2, unfocusedContainerColor = P.s2, focusedBorderColor = P.acento, unfocusedBorderColor = P.linea, cursorColor = P.acento,
)

@Composable
fun BotonPrincipal(texto: String, icono: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.height(44.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = P.relleno, contentColor = P.sobreRelleno)) {
        if (icono != null) { Icon(icono, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BotonSecundario(texto: String, icono: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick, modifier.height(44.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, P.linea),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = P.s2, contentColor = P.texto1)) {
        if (icono != null) { Icon(icono, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Encabezado(titulo: String, sub: String?) {
    Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 8.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineMedium, color = P.texto1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = P.texto2, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------- Inicio

@Composable
fun InicioPantalla(onDescargar: (PedidoDescarga) -> Unit) {
    val scope = rememberCoroutineScope()
    var consulta by remember { mutableStateOf("") }
    var buscada by remember { mutableStateOf<String?>(null) }
    val videos = remember { mutableStateListOf<Video>() }
    var siguiente by remember { mutableStateOf<Page?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val grilla = rememberLazyGridState()

    fun buscar() {
        val q = consulta.trim()
        if (q.isEmpty()) return
        if (esEnlace.matches(q)) { onDescargar(PedidoDescarga(q, null)); consulta = ""; return }
        scope.launch {
            cargando = true; error = null; videos.clear(); siguiente = null; buscada = q
            runCatching { Youtube.buscar(q) }
                .onSuccess { videos.addAll(it.videos); siguiente = it.siguiente }
                .onFailure { error = "No se pudo buscar. Revisa tu conexión a internet." }
            cargando = false
        }
    }
    fun ver(v: Video) = scope.launch { aviso = "Abriendo «${v.titulo.take(60)}»…"; aviso = Reproductor.ver(v.url, v.titulo) }

    val alFinal by remember { derivedStateOf { (grilla.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grilla.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(alFinal) {
        val s = siguiente; val q = buscada
        if (alFinal && s != null && q != null && !cargando) {
            cargando = true
            runCatching { Youtube.mas(q, s) }.onSuccess { r -> videos.addAll(r.videos.filter { n -> videos.none { it.url == n.url } }); siguiente = r.siguiente }
            cargando = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                consulta, { consulta = it }, singleLine = true, modifier = Modifier.weight(1f).widthIn(max = 760.dp),
                placeholder = { Text("Busca en YouTube o pega un enlace para descargar…", color = P.texto3) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = P.texto2) },
                trailingIcon = { if (consulta.isNotEmpty()) IconButton({ consulta = "" }) { Icon(Icons.Outlined.Close, "Borrar") } },
                shape = RoundedCornerShape(16.dp), colors = campoColores(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { buscar() }),
            )
            Spacer(Modifier.width(12.dp))
            BotonPrincipal(if (esEnlace.matches(consulta.trim())) "Descargar" else "Buscar", if (esEnlace.matches(consulta.trim())) Icons.Outlined.FileDownload else Icons.Outlined.Search, { buscar() })
        }
        aviso?.let { Text(it, color = P.texto2, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 28.dp, bottom = 8.dp)) }
        when {
            buscada == null -> Portada()
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!, color = P.error) }
            videos.isEmpty() && cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = P.acento) }
            videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No encontré resultados.", color = P.texto2) }
            else -> LazyVerticalGrid(
                GridCells.Adaptive(280.dp), state = grilla, contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(videos, key = { it.url }) { v ->
                    TarjetaVideo(v, onVer = { ver(v) }, onDescargar = { onDescargar(PedidoDescarga(v.url, v.titulo)) })
                }
                if (cargando) item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = P.acento) }
                }
            }
        }
    }
}

@Composable
private fun Portada() {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource("icono.png"), null, Modifier.size(88.dp))
        Text(App.nombre, style = MaterialTheme.typography.headlineMedium, color = P.texto1, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Busca un video para verlo sin anuncios, o pega un enlace de YouTube, TikTok, Instagram, X, Facebook y más para descargarlo.",
            style = MaterialTheme.typography.bodyLarge, color = P.texto2, modifier = Modifier.widthIn(max = 520.dp).padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun TarjetaVideo(v: Video, onVer: () -> Unit, onDescargar: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = "Ver", onClick = onVer)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(P.s2)) {
            AsyncImage(v.miniatura, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            val etq = if (v.esDirecto) "EN VIVO" else Formatos.duracion(v.duracionSeg)
            if (etq.isNotEmpty()) Text(
                etq, color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(v.titulo, style = MaterialTheme.typography.titleSmall, color = P.texto1, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text(listOfNotNull(v.autor, Formatos.vistas(v.vistas).ifEmpty { null }, v.fecha).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = P.texto2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonSecundario("Ver", Icons.Outlined.PlayArrow, onVer, Modifier.weight(1f))
            BotonPrincipal("Descargar", Icons.Outlined.FileDownload, onDescargar, Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------- Hoja de formato

@Composable
fun HojaFormato(p: PedidoDescarga, onCerrar: () -> Unit, onElegido: (Formato) -> Unit) {
    var elegido by remember { mutableStateOf(Formato.MEJOR) }
    AlertDialog(
        onDismissRequest = onCerrar, containerColor = P.s1,
        title = { Text("¿En qué formato?", style = MaterialTheme.typography.titleLarge, color = P.texto1) },
        text = {
            Column(Modifier.widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.titulo ?: p.url, style = MaterialTheme.typography.bodySmall, color = P.texto2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (Pedido.esLista(p.url)) Text("Es una lista: se descarga completa, en una carpeta.", style = MaterialTheme.typography.bodySmall, color = P.acento)
                Formato.entries.forEach { f ->
                    Surface(
                        selected = elegido == f, onClick = { elegido = f }, shape = RoundedCornerShape(14.dp),
                        color = if (elegido == f) P.acentoSuave else P.s2, border = BorderStroke(1.dp, if (elegido == f) P.acentoBorde else P.linea),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(elegido == f, null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(f.etiqueta, style = MaterialTheme.typography.titleSmall, color = P.texto1)
                                Text(f.detalle, style = MaterialTheme.typography.bodySmall, color = P.texto2)
                            }
                        }
                    }
                }
                Text("Se guarda en ${Ajustes.carpetaDescargas.path}", style = MaterialTheme.typography.bodySmall, color = P.texto3, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { BotonPrincipal("Descargar", Icons.Outlined.FileDownload, { onElegido(elegido) }) },
        dismissButton = { TextButton(onCerrar) { Text("Cancelar", color = P.texto2) } },
    )
}

// ---------------------------------------------------------------- Descargas

@Composable
fun DescargasPantalla(onDescargar: (PedidoDescarga) -> Unit) {
    val lista by CentroDescargas.lista.collectAsState()
    val portapapeles = LocalClipboardManager.current
    var texto by remember { mutableStateOf("") }
    val enlaces = remember(texto) { Regex("https?://\\S+").findAll(texto).map { it.value.trimEnd(',', '.', ')') }.distinct().toList() }
    var formatoLote by remember { mutableStateOf<List<String>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Encabezado("Descargas", "Se guardan en ${Ajustes.carpetaDescargas.path}")
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                texto, { texto = it }, modifier = Modifier.weight(1f), minLines = 1, maxLines = 4,
                placeholder = { Text("Pega aquí uno o varios enlaces…", color = P.texto3) },
                trailingIcon = { IconButton({ texto = (texto + "\n" + (portapapeles.getText()?.text ?: "")).trim() }) { Icon(Icons.Outlined.ContentPaste, "Pegar") } },
                shape = RoundedCornerShape(16.dp), colors = campoColores(),
            )
            Spacer(Modifier.width(12.dp))
            BotonPrincipal(if (enlaces.size > 1) "Descargar ${enlaces.size}" else "Descargar", Icons.Outlined.FileDownload, {
                when (enlaces.size) { 0 -> {}; 1 -> onDescargar(PedidoDescarga(enlaces.first(), null)); else -> formatoLote = enlaces }
                if (enlaces.size == 1) texto = ""
            }, enabled = enlaces.isNotEmpty())
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
            TextButton({ runCatching { java.awt.Desktop.getDesktop().open(Ajustes.carpetaDescargas.apply { mkdirs() }) } }) {
                Icon(Icons.Outlined.FolderOpen, null, tint = P.texto2); Spacer(Modifier.width(6.dp)); Text("Abrir carpeta", color = P.texto2)
            }
            if (lista.any { it.estado in setOf(Estado.LISTA, Estado.ERROR, Estado.CANCELADA) }) TextButton({ CentroDescargas.limpiarTerminadas() }) { Text("Limpiar terminadas", color = P.texto2) }
        }
        if (lista.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Aún no hay descargas. Pega un enlace arriba o busca un video en Inicio.", color = P.texto2) }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(lista, key = { it.id }) { d -> FilaDescarga(d); HorizontalDivider(color = P.linea, modifier = Modifier.padding(start = 28.dp)) }
        }
    }
    formatoLote?.let { lote ->
        HojaFormato(PedidoDescarga(lote.first(), "${lote.size} enlaces"), onCerrar = { formatoLote = null }, onElegido = { f ->
            lote.forEach { CentroDescargas.agregar(it, f) }; formatoLote = null; texto = ""
        })
    }
}

@Composable
private fun FilaDescarga(d: Descarga) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.titulo, style = MaterialTheme.typography.titleSmall, color = P.texto1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val estado = when (d.estado) {
                Estado.EN_COLA -> "En cola…"
                Estado.DESCARGANDO -> listOfNotNull(
                    "${(d.progreso * 100).toInt()}%",
                    if (d.total > 0) "${Formatos.bytes(d.bajado)} de ${Formatos.bytes(d.total)}" else null,
                    if (d.velocidad > 0) "${Formatos.bytes(d.velocidad)}/s" else null,
                    if (d.eta > 0) "quedan ${Formatos.duracion(d.eta)}" else null,
                ).joinToString(" · ")
                Estado.PROCESANDO -> "Uniendo y convirtiendo el archivo…"
                Estado.LISTA -> "Lista · ${d.formato.etiqueta}"
                Estado.ERROR -> d.error ?: "Error"
                Estado.CANCELADA -> "Cancelada"
            }
            Text(estado, style = MaterialTheme.typography.bodySmall, color = if (d.estado == Estado.ERROR) P.error else P.texto2, maxLines = 2)
            when (d.estado) {
                Estado.DESCARGANDO -> LinearProgressIndicator({ d.progreso }, Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = P.acento, trackColor = P.s3)
                Estado.EN_COLA, Estado.PROCESANDO -> LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = P.acento, trackColor = P.s3)
                else -> {}
            }
        }
        Spacer(Modifier.width(12.dp))
        when (d.estado) {
            Estado.EN_COLA, Estado.DESCARGANDO, Estado.PROCESANDO -> IconButton({ CentroDescargas.cancelar(d.id) }) { Icon(Icons.Outlined.Close, "Cancelar", tint = P.texto2) }
            Estado.LISTA -> {
                d.archivo?.takeIf { it.exists() }?.let { f -> IconButton({ Reproductor.abrirArchivo(f) }) { Icon(Icons.Outlined.PlayArrow, "Reproducir", tint = P.acento) } }
                IconButton({ abrirCarpetaDe(d.archivo) }) { Icon(Icons.Outlined.FolderOpen, "Mostrar en la carpeta", tint = P.texto2) }
                IconButton({ CentroDescargas.quitar(d.id) }) { Icon(Icons.Outlined.Delete, "Quitar de la lista", tint = P.texto2) }
            }
            Estado.ERROR, Estado.CANCELADA -> {
                IconButton({ CentroDescargas.reintentar(d.id) }) { Icon(Icons.Outlined.Refresh, "Reintentar", tint = P.acento) }
                IconButton({ CentroDescargas.quitar(d.id) }) { Icon(Icons.Outlined.Delete, "Quitar de la lista", tint = P.texto2) }
            }
        }
    }
}

private fun abrirCarpetaDe(f: File?) {
    if (f != null && f.exists() && Herramientas.esWindows) runCatching { ProcessBuilder("explorer.exe", "/select,", f.path).start(); return }
    runCatching { java.awt.Desktop.getDesktop().open(f?.parentFile ?: Ajustes.carpetaDescargas) }
}

// ---------------------------------------------------------------- Ajustes

@Composable
fun AjustesPantalla() {
    val scope = rememberCoroutineScope()
    var carpeta by remember { mutableStateOf(Ajustes.carpetaDescargas) }
    var calidad by remember { mutableStateOf(Ajustes.calidadVer) }
    var simultaneas by remember { mutableStateOf(Ajustes.simultaneas) }
    var versionYtdlp by remember { mutableStateOf<String?>(null) }
    var actualizando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { versionYtdlp = Motor.version() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Encabezado("Ajustes", null) }
        item {
            Tarjeta("Carpeta de descargas") {
                Text(carpeta.path, color = P.texto2, style = MaterialTheme.typography.bodyMedium)
                BotonSecundario("Cambiar…", Icons.Outlined.FolderOpen, {
                    val ch = javax.swing.JFileChooser(carpeta).apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Elige dónde guardar las descargas" }
                    if (ch.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) { Ajustes.carpetaDescargas = ch.selectedFile; carpeta = ch.selectedFile }
                })
            }
        }
        item {
            Tarjeta("Calidad al ver videos") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p").forEach { (h, t) ->
                        Chip(t, calidad == h) { Ajustes.calidadVer = h; calidad = h }
                    }
                }
            }
        }
        item {
            Tarjeta("Descargas al mismo tiempo") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..4).forEach { n -> Chip("$n", simultaneas == n) { Ajustes.simultaneas = n; simultaneas = n } } }
                Text("El cambio se aplica al abrir de nuevo ${App.nombre}.", color = P.texto3, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Tarjeta("Motor de descargas (yt-dlp)") {
                Text("Versión: ${versionYtdlp ?: "…"}", color = P.texto2)
                Text("Los sitios cambian seguido; si una descarga falla, actualiza el motor.", color = P.texto3, style = MaterialTheme.typography.bodySmall)
                BotonSecundario(if (actualizando) "Actualizando…" else "Actualizar motor", Icons.Outlined.Refresh, {
                    if (!actualizando) scope.launch { actualizando = true; mensaje = Motor.actualizar(); versionYtdlp = Motor.version(); actualizando = false }
                })
                mensaje?.let { Text(it, color = P.texto2, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            Tarjeta("Acerca de") {
                Text("${App.nombre} ${App.version} para Windows", color = P.texto1)
                Text("Software libre (GPLv3). Usa NewPipeExtractor, yt-dlp, ffmpeg y mpv.", color = P.texto2, style = MaterialTheme.typography.bodySmall)
                val faltan = Herramientas.faltantes()
                if (faltan.isNotEmpty()) Text("Faltan herramientas: ${faltan.joinToString()}. Reinstala ${App.nombre}.", color = P.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = P.s1, border = BorderStroke(1.dp, P.linea), modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp).widthIn(max = 760.dp).fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = P.texto1)
            contenido()
        }
    }
}

@Composable
private fun Chip(texto: String, activo: Boolean, onClick: () -> Unit) {
    Surface(selected = activo, onClick = onClick, shape = RoundedCornerShape(12.dp), color = if (activo) P.acentoSuave else P.s2,
        border = BorderStroke(1.dp, if (activo) P.acentoBorde else P.linea), modifier = Modifier.height(38.dp)) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(texto, color = if (activo) P.acento else P.texto1, style = MaterialTheme.typography.labelMedium) }
    }
}

/** Versión y actualización de yt-dlp. */
object Motor {
    suspend fun version(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!Herramientas.ytdlp.exists()) return@withContext null
        runCatching { ProcessBuilder(Herramientas.ytdlp.path, "--version").start().inputStream.bufferedReader().readText().trim() }.getOrNull()
    }

    suspend fun actualizar(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!Herramientas.ytdlp.exists()) return@withContext "No encontré yt-dlp. Reinstala ${App.nombre}."
        runCatching {
            val p = ProcessBuilder(Herramientas.ytdlp.path, "-U").redirectErrorStream(true).start()
            val salida = p.inputStream.bufferedReader().readText()
            if (p.waitFor() == 0) (if (salida.contains("up to date", true)) "Ya tienes la versión más reciente." else "Motor actualizado.")
            else "No se pudo actualizar: ${salida.lines().lastOrNull { it.isNotBlank() }.orEmpty().take(160)}"
        }.getOrElse { "No se pudo actualizar: ${it.message}" }
    }
}
