package com.santyramos.mirador.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.santyramos.mirador.data.PlayerQuality
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.bestUrl
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Share
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.BotonPrimario
import com.santyramos.mirador.ui.BotonSecundario
import com.santyramos.mirador.ui.PipState
import com.santyramos.mirador.ui.theme.Paleta
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Surface
import com.santyramos.mirador.util.Format
import kotlinx.coroutines.delay

private fun Context.activity(): Activity? {
    var c = this
    while (c is android.content.ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
fun PlayerScreen(
    onMinimizar: () -> Unit,
    onDescargar: (String) -> Unit,
    onAbrirCanal: (String) -> Unit,
    onAbrirVideo: (String) -> Unit,
    onEntrarPip: () -> Unit,
) {
    val contexto = LocalContext.current
    val actividad = contexto.activity()
    val video by VideoController.video.collectAsState()
    val estado by VideoController.estado.collectAsState()
    val enHorizontal = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var mostrarControles by remember { mutableStateOf(true) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var menuCalidad by remember { mutableStateOf(false) }
    var menuVelocidad by remember { mutableStateOf(false) }
    var arrastrando by remember { mutableStateOf(false) }
    var posArrastre by remember { mutableFloatStateOf(0f) }

    // Al abrir el reproductor: se permite PiP; al cerrar, se restablece todo.
    DisposableEffect(Unit) {
        PipState.permitido = true
        onDispose {
            PipState.permitido = false
            actividad?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            actividad?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    // Pantalla encendida mientras se ve el video con la app abierta.
    LaunchedEffect(estado.reproduciendo, PipState.activo) {
        actividad?.window?.let { w ->
            if (estado.reproduciendo) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    // Barras del sistema ocultas en pantalla completa.
    LaunchedEffect(enHorizontal) {
        actividad?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, w.decorView)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enHorizontal) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Los controles se esconden solos a los 4 s.
    LaunchedEffect(mostrarControles, estado.reproduciendo, arrastrando, menuCalidad, menuVelocidad) {
        if (mostrarControles && estado.reproduciendo && !arrastrando && !menuCalidad && !menuVelocidad) {
            delay(4000); mostrarControles = false
        }
    }
    LaunchedEffect(aviso) { if (aviso != null) { delay(800); aviso = null } }

    BackHandler(enabled = enHorizontal) { actividad?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    BackHandler(enabled = !enHorizontal && !PipState.activo) { onMinimizar() }

    val audio = remember { contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---------- Zona del video ----------
        val modificadorVideo = if (enHorizontal || PipState.activo) Modifier.fillMaxSize() else Modifier.fillMaxWidth().statusBarsPadding().aspectRatio(16f / 9f)
        Box(
            modificadorVideo
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { mostrarControles = !mostrarControles },
                        onDoubleTap = { o ->
                            val ancho = size.width
                            when {
                                o.x < ancho / 3f -> { VideoController.saltar(-10_000); aviso = "−10 s" }
                                o.x > ancho * 2f / 3f -> { VideoController.saltar(10_000); aviso = "+10 s" }
                                else -> VideoController.alternar()
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    // Gestos verticales: izquierda = brillo, derecha = volumen.
                    var ladoIzquierdo = true
                    var tamano = IntSize.Zero
                    var nivel = 0f
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            tamano = size
                            ladoIzquierdo = o.x < tamano.width / 2f
                            nivel = if (ladoIzquierdo) {
                                val b = actividad?.window?.attributes?.screenBrightness ?: -1f
                                if (b < 0) 0.5f else b
                            } else {
                                audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            }
                        },
                        onVerticalDrag = { cambio, dy ->
                            cambio.consume()
                            nivel = (nivel - dy / tamano.height * 1.3f).coerceIn(0f, 1f)
                            if (ladoIzquierdo) {
                                actividad?.window?.let { w ->
                                    val p = w.attributes; p.screenBrightness = nivel.coerceAtLeast(0.02f); w.attributes = p
                                }
                                aviso = "Brillo ${(nivel * 100).toInt()}%"
                            } else {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (nivel * max).toInt(), 0)
                                aviso = "Volumen ${(nivel * 100).toInt()}%"
                            }
                        },
                    )
                },
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = VideoController.player
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = VideoController.player },
                modifier = Modifier.fillMaxSize(),
            )

            if (video?.cargando == true || estado.almacenando) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(44.dp), color = MaterialTheme.colorScheme.primary)
            }
            video?.error?.let { msg ->
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(msg, color = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { VideoController.reintentar() }) { Text("Reintentar") }
                }
            }
            aviso?.let {
                Text(
                    it, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xAA000000)).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (mostrarControles && !PipState.activo && video?.error == null) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent, Color(0xAA000000))))) {
                    // Barra superior
                    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (enHorizontal) actividad?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else onMinimizar()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White) }
                        Text(video?.info?.name.orEmpty(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { menuCalidad = true }) {
                                val v = video
                                val etq = if (v == null) "" else if (v.calidad == PlayerQuality.AUTO) "Auto" else v.calidad.etiqueta
                                Text(etq + (if (v != null && v.alturaReproduciendo > 0) " · ${v.alturaReproduciendo}p" else ""), color = Color.White)
                            }
                            DropdownMenu(expanded = menuCalidad, onDismissRequest = { menuCalidad = false }) {
                                PlayerQuality.entries.forEach { q ->
                                    val disponible = q == PlayerQuality.AUTO || (video?.alturas?.any { it <= q.alturaMax } != false)
                                    if (disponible) DropdownMenuItem(
                                        text = { Text(q.etiqueta) },
                                        onClick = { menuCalidad = false; VideoController.cambiarCalidad(q) },
                                    )
                                }
                            }
                        }
                        Box {
                            TextButton(onClick = { menuVelocidad = true }) { Text("${estado.velocidad}x".replace(".0x", "x"), color = Color.White) }
                            DropdownMenu(expanded = menuVelocidad, onDismissRequest = { menuVelocidad = false }) {
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { v ->
                                    DropdownMenuItem(text = { Text("${v}x".replace(".0x", "x")) }, onClick = { menuVelocidad = false; VideoController.velocidad(v) })
                                }
                            }
                        }
                        IconButton(onClick = onEntrarPip) { Icon(Icons.Filled.PictureInPictureAlt, "Imagen en imagen", tint = Color.White) }
                    }
                    // Centro
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { VideoController.saltar(-10_000) }) { Icon(Icons.Filled.Replay10, "Atrasar 10 segundos", tint = Color.White, modifier = Modifier.size(40.dp)) }
                        IconButton(onClick = { VideoController.alternar() }, modifier = Modifier.size(68.dp).clip(CircleShape).background(Color(0x99000000))) {
                            Icon(if (estado.reproduciendo) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (estado.reproduciendo) "Pausar" else "Reproducir", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        IconButton(onClick = { VideoController.saltar(10_000) }) { Icon(Icons.Filled.Forward10, "Adelantar 10 segundos", tint = Color.White, modifier = Modifier.size(40.dp)) }
                        if (video?.info != null && VideoController.haySiguiente()) {
                            IconButton(onClick = { VideoController.siguiente() }) { Icon(Icons.Filled.SkipNext, "Siguiente video", tint = Color.White, modifier = Modifier.size(40.dp)) }
                        }
                    }
                    // Barra inferior
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
                        val dur = estado.duracionMs.coerceAtLeast(1)
                        Slider(
                            value = if (arrastrando) posArrastre else (estado.posicionMs.toFloat() / dur).coerceIn(0f, 1f),
                            onValueChange = { arrastrando = true; posArrastre = it },
                            onValueChangeFinished = { VideoController.moverA((posArrastre * dur).toLong()); arrastrando = false },
                            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = com.santyramos.mirador.ui.theme.Acento, activeTrackColor = com.santyramos.mirador.ui.theme.Acento, inactiveTrackColor = Color(0x55FFFFFF)),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${Format.duracion(estado.posicionMs / 1000)} / ${Format.duracion(estado.duracionMs / 1000)}", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                actividad?.requestedOrientation = if (enHorizontal) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }) { Icon(if (enHorizontal) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Pantalla completa", tint = Color.White) }
                        }
                    }
                }
            }
        }

        // ---------- Detalles (solo en vertical) ----------
        if (!enHorizontal && !PipState.activo) {
            val info = video?.info
            if (info == null && video?.error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Cargando video…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (info != null) {
                val relacionados = remember(info) { info.relatedItems.mapNotNull { Youtube.aElemento(it) }.filterIsInstance<Elemento.Video>() }
                var verTodo by remember(info) { mutableStateOf(false) }
                var verComentarios by remember(info) { mutableStateOf(false) }
                if (verComentarios) com.santyramos.mirador.ui.HojaMirador(onCerrar = { verComentarios = false }) {
                    com.santyramos.mirador.ui.ComentariosSheetContent(info.url)
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(info.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                            Text(
                                listOfNotNull(
                                    if (info.viewCount >= 0) "${Format.contar(info.viewCount)} vistas" else null,
                                    info.textualUploadDate,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Canal
                            Surface(
                                onClick = { onAbrirCanal(info.uploaderUrl) }, enabled = !info.uploaderUrl.isNullOrBlank(),
                                shape = RoundedCornerShape(16.dp), color = Paleta.S1, border = androidx.compose.foundation.BorderStroke(1.dp, Paleta.Linea),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(info.uploaderName.orEmpty(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (info.uploaderSubscriberCount > 0) Text("${Format.contar(info.uploaderSubscriberCount)} suscriptores", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
                                    val canalId = com.santyramos.mirador.data.lib.Importar.canalIdDeUrl(info.uploaderUrl.orEmpty())
                                    com.santyramos.mirador.ui.BotonSuscribir(
                                        canalId,
                                        { com.santyramos.mirador.data.lib.Suscripcion(canalId.orEmpty(), info.uploaderName.orEmpty(), com.santyramos.mirador.data.lib.Importar.urlDeCanal(canalId.orEmpty())) },
                                    )
                                }
                            }
                            // Acciones
                            BotonPrimario("Descargar", { onDescargar(info.url) }, Modifier.fillMaxWidth(), icono = Icons.Outlined.FileDownload)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                val guardar = com.santyramos.mirador.ui.LocalGuardar.current
                                BotonSecundario("Guardar", {
                                    guardar(Elemento.Video(info.name.orEmpty(), info.url, info.thumbnails.bestUrl(), info.uploaderName, info.uploaderUrl, info.duration, info.viewCount, info.textualUploadDate, false, false))
                                }, Modifier.weight(1f), icono = Icons.Outlined.BookmarkBorder)
                                BotonSecundario("Compartir", {
                                    contexto.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, info.url), "Compartir enlace"))
                                }, Modifier.weight(1f), icono = Icons.Outlined.Share)
                            }
                            val auto by VideoController.autoSiguiente.collectAsState()
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Reproducción automática", style = MaterialTheme.typography.titleSmall)
                                    Text("Al terminar, sigue con el siguiente video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                androidx.compose.material3.Switch(
                                    checked = auto, onCheckedChange = { VideoController.cambiarAutoSiguiente(it) },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = Paleta.SobreAcento, checkedTrackColor = MaterialTheme.colorScheme.primary, checkedBorderColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = Paleta.Texto2, uncheckedTrackColor = Paleta.S3, uncheckedBorderColor = Paleta.Linea,
                                    ),
                                )
                            }
                            BotonSecundario("Ver comentarios", { verComentarios = true }, Modifier.fillMaxWidth(), icono = Icons.Outlined.ChatBubbleOutline)
                            val desc = info.description?.content.orEmpty()
                            if (desc.isNotBlank()) {
                                Text(
                                    desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (verTodo) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { verTodo = !verTodo },
                                )
                            }
                            TextButton(onClick = { VideoController.cerrar(); onMinimizar() }) { Text("Cerrar reproductor", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        HorizontalDivider(color = Paleta.Linea)
                        if (relacionados.isNotEmpty()) Text("Videos relacionados", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                    }
                    items(relacionados, key = { it.url }) { v -> FilaVideo(v, onClick = { onAbrirVideo(v.url) }, onDescargar = { onDescargar(v.url) }) }
                }
            }
        }
    }
}
