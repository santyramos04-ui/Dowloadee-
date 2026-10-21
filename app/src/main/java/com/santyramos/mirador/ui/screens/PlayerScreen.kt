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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.BrightnessMedium
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
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicNote
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
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope

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
    var verCola by remember { mutableStateOf(false) }
    val cola by VideoController.cola.collectAsState()
    if (verCola) com.santyramos.mirador.ui.HojaMirador(onCerrar = { verCola = false }) { com.santyramos.mirador.ui.ColaSheetContent { verCola = false } }
    var verMas by remember { mutableStateOf(false) }
    var verTv by remember { mutableStateOf(false) }
    if (verTv) com.santyramos.mirador.ui.HojaMirador(onCerrar = { verTv = false }) { com.santyramos.mirador.ui.TvSheetContent { verTv = false } }
    val enTv by com.santyramos.mirador.tv.Tv.enTv.collectAsState()
    // Candado: con la pantalla bloqueada, los toques no hacen nada (ni pausar, ni gestos, ni botones).
    var bloqueado by remember { mutableStateOf(false) }
    if (verMas) com.santyramos.mirador.ui.HojaMirador(onCerrar = { verMas = false }) { OpcionesSheetContent { verMas = false } }

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
    BackHandler(enabled = bloqueado) {
        android.widget.Toast.makeText(contexto, "Pantalla bloqueada: mantén presionado el candado para desbloquear", android.widget.Toast.LENGTH_SHORT).show()
    }

    val audio = remember { contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val gestosBrilloVolumen by remember { com.santyramos.mirador.data.Settings(contexto).gestosBrilloVolumen }.collectAsState(initial = false)
    var pistaGesto by remember { mutableStateOf<String?>(null) }
    // Respuesta del video al dedo cuando no se minimiza (hacia arriba o en pantalla completa).
    val empujeVideo = remember { androidx.compose.animation.core.Animatable(0f) }
    val alcance = rememberCoroutineScope()
    // Cuánto bajó el reproductor con el dedo (0 = en su sitio).
    val arrastre = remember { androidx.compose.animation.core.Animatable(0f) }
    var bordeVideo by remember { mutableFloatStateOf(0f) }
    val altoPantalla = with(androidx.compose.ui.platform.LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    Box(
        Modifier.fillMaxSize()
            // El gesto se detecta aquí, fuera de la capa que se mueve, para que siga al dedo sin saltos.
            .pointerInput(gestosBrilloVolumen, enHorizontal) {
                if (gestosBrilloVolumen) return@pointerInput
                // Predeterminado (como YouTube): hacia abajo el reproductor baja con el dedo y se minimiza;
                // hacia arriba, pantalla completa.
                val umbral = 72.dp.toPx()
                var total = 0f
                val velocidad = androidx.compose.ui.input.pointer.util.VelocityTracker()
                val conMovimiento = !enHorizontal
                var desdeVideo = false
                detectVerticalDragGestures(
                    onDragStart = { o -> total = 0f; velocidad.resetTracking(); desdeVideo = enHorizontal || o.y <= bordeVideo },
                    onVerticalDrag = { cambio, dy ->
                        if (!desdeVideo) return@detectVerticalDragGestures
                        cambio.consume()
                        total += dy
                        velocidad.addPosition(cambio.uptimeMillis, androidx.compose.ui.geometry.Offset(0f, total))
                        alcance.launch {
                            if (conMovimiento && total > 0f) { arrastre.snapTo(total); empujeVideo.snapTo(0f) }
                            else { arrastre.snapTo(0f); empujeVideo.snapTo(total * 0.4f) }
                        }
                        pistaGesto = when {
                            total > umbral && enHorizontal -> "Suelta para salir de pantalla completa"
                            total < -umbral && !enHorizontal -> "Suelta para pantalla completa"
                            else -> null
                        }
                    },
                    onDragCancel = {
                        pistaGesto = null
                        alcance.launch { arrastre.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 400f)) }
                        alcance.launch { empujeVideo.animateTo(0f) }
                    },
                    onDragEnd = {
                        if (!desdeVideo) return@detectVerticalDragGestures
                        pistaGesto = null
                        val v = velocidad.calculateVelocity().y
                        val resorte = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.85f, stiffness = 400f)
                        alcance.launch { empujeVideo.animateTo(0f, resorte) }
                        when {
                            enHorizontal -> if (total > umbral) actividad?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            total < -umbral -> actividad?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            total > altoPantalla * 0.22f || (total > umbral / 2 && v > 1200f) -> alcance.launch {
                                // Termina de bajar con suavidad y queda en el mini reproductor.
                                if (conMovimiento) arrastre.animateTo(altoPantalla, androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialVelocity = v.coerceAtLeast(0f))
                                onMinimizar()
                            }
                            else -> alcance.launch { arrastre.animateTo(0f, resorte) }
                        }
                    },
                )
            }
            .graphicsLayer {
                // Como YouTube: baja con el dedo, se achica un poco y se redondea.
                val d = arrastre.value
                val f = (d / size.height).coerceIn(0f, 1f)
                translationY = d
                val escala = 1f - 0.12f * f
                scaleX = escala; scaleY = escala
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                shape = RoundedCornerShape((24f * (f * 4f).coerceAtMost(1f)).dp)
                clip = d > 0f
                alpha = 1f - 0.35f * f
            },
    ) {
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
                .onGloballyPositioned { if (arrastre.value == 0f) bordeVideo = it.boundsInRoot().bottom }
                .graphicsLayer {
                    // Hacia arriba el video crece un poco; en pantalla completa baja con el dedo.
                    val e = empujeVideo.value
                    if (enHorizontal) translationY = e.coerceAtLeast(0f)
                    else { val f = 1f + (-e).coerceAtLeast(0f) / size.height * 0.25f; scaleX = f; scaleY = f }
                }
                .pointerInput(gestosBrilloVolumen, enHorizontal) {
                    if (!gestosBrilloVolumen) return@pointerInput
                    // Opcional (Ajustes): izquierda = brillo, derecha = volumen.
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
                    (android.view.LayoutInflater.from(c).inflate(com.santyramos.mirador.R.layout.vista_video, null) as PlayerView).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = VideoController.player
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = VideoController.player },
                modifier = Modifier.fillMaxSize(),
            )

            // Canciones descargadas: no hay imagen de video, se muestra la portada.
            if (video?.esAudio == true) {
                Box(Modifier.fillMaxSize().background(Paleta.S2), contentAlignment = Alignment.Center) {
                    if (video?.miniatura != null) coil3.compose.AsyncImage(
                        model = video?.miniatura, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    ) else Icon(Icons.Outlined.MusicNote, null, Modifier.size(64.dp), tint = Paleta.Texto3)
                }
            }
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
            // El video está en el televisor: aquí solo se muestra a dónde se envió.
            enTv?.let { e ->
                Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Cast, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Text("Reproduciendo en ${e.tv.nombre}", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { verTv = true }) { Text("Controles del televisor", color = com.santyramos.mirador.ui.theme.Acento) }
                }
            }
            (pistaGesto ?: aviso)?.let {
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
                        Text(video?.titulo.orEmpty(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (video?.local != true) Box {
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
                                // Idioma del audio (solo si el video tiene varios): uso ocasional, por eso va aquí y no en la pantalla.
                                val pistas = video?.pistas.orEmpty()
                                if (pistas.size > 1) {
                                    HorizontalDivider()
                                    Text("Idioma del audio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                    val actual = pistas.firstOrNull { it.id == video?.pista } ?: pistas.firstOrNull { it.original } ?: pistas.first()
                                    pistas.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.nombre) },
                                            onClick = { menuCalidad = false; VideoController.cambiarPista(p.id) },
                                            trailingIcon = { if (p.id == actual.id) Icon(Icons.Filled.Check, null) },
                                        )
                                    }
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
                        Box {
                            var menuBrillo by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuBrillo = true }) { Icon(Icons.Filled.BrightnessMedium, "Brillo", tint = Color.White) }
                            DropdownMenu(expanded = menuBrillo, onDismissRequest = { menuBrillo = false }) {
                                listOf("Como el celular" to -1f, "25 %" to 0.25f, "50 %" to 0.5f, "75 %" to 0.75f, "100 %" to 1f).forEach { (nombre, valor) ->
                                    DropdownMenuItem(text = { Text(nombre) }, onClick = {
                                        menuBrillo = false
                                        actividad?.window?.let { w -> val p = w.attributes; p.screenBrightness = valor; w.attributes = p }
                                    })
                                }
                            }
                        }
                        IconButton(onClick = { verTv = true }) { Icon(Icons.Outlined.Cast, "Ver en el televisor", tint = if (enTv != null) com.santyramos.mirador.ui.theme.Acento else Color.White) }
                        IconButton(onClick = { bloqueado = true; mostrarControles = false }) { Icon(Icons.Outlined.Lock, "Bloquear pantalla", tint = Color.White) }
                        IconButton(onClick = onEntrarPip) { Icon(Icons.Filled.PictureInPictureAlt, "Imagen en imagen", tint = Color.White) }
                    }
                    // Aviso de SponsorBlock
                    val aviso by VideoController.aviso.collectAsState()
                    aviso?.let { a ->
                        Text(
                            a, color = Color.White, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    // Centro
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { VideoController.anterior() }) { Icon(Icons.Filled.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(36.dp)) }
                        IconButton(onClick = { VideoController.saltar(-10_000) }) { Icon(Icons.Filled.Replay10, "Atrasar 10 segundos", tint = Color.White, modifier = Modifier.size(40.dp)) }
                        IconButton(onClick = { VideoController.alternar() }, modifier = Modifier.size(68.dp).clip(CircleShape).background(Color(0x99000000))) {
                            Icon(if (estado.reproduciendo) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (estado.reproduciendo) "Pausar" else "Reproducir", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        IconButton(onClick = { VideoController.saltar(10_000) }) { Icon(Icons.Filled.Forward10, "Adelantar 10 segundos", tint = Color.White, modifier = Modifier.size(40.dp)) }
                        val haySiguiente = video?.listo == true && (cola.isNotEmpty() || VideoController.haySiguiente())
                        IconButton(onClick = { VideoController.siguiente() }, enabled = haySiguiente) {
                            Icon(Icons.Filled.SkipNext, "Siguiente", tint = if (haySiguiente) Color.White else Color(0x66FFFFFF), modifier = Modifier.size(36.dp))
                        }
                    }
                    // Barra inferior
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
                        val dur = estado.duracionMs.coerceAtLeast(1)
                        val coloresBarra = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = com.santyramos.mirador.ui.theme.Acento, activeTrackColor = com.santyramos.mirador.ui.theme.Acento,
                            inactiveTrackColor = Color(0x55FFFFFF),
                        )
                        // Barra fina con un punto redondo (la de serie traía un tirador alto que tapaba el video).
                        Slider(
                            value = if (arrastrando) posArrastre else (estado.posicionMs.toFloat() / dur).coerceIn(0f, 1f),
                            onValueChange = { arrastrando = true; posArrastre = it },
                            onValueChangeFinished = { VideoController.moverA((posArrastre * dur).toLong()); arrastrando = false },
                            colors = coloresBarra,
                            thumb = {
                                androidx.compose.material3.SliderDefaults.Thumb(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    colors = coloresBarra, thumbSize = androidx.compose.ui.unit.DpSize(16.dp, 16.dp),
                                )
                            },
                            track = { estadoBarra ->
                                androidx.compose.material3.SliderDefaults.Track(
                                    sliderState = estadoBarra, colors = coloresBarra, modifier = Modifier.height(4.dp),
                                    thumbTrackGapSize = 0.dp, trackInsideCornerSize = 0.dp, drawStopIndicator = null,
                                )
                            },
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
            val v = video
            if (v != null && v.local) {
                // Archivo descargado: detalles mínimos (no hace falta internet).
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(v.titulo, style = MaterialTheme.typography.titleLarge)
                            if (!v.autor.isNullOrBlank()) Text(v.autor, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(if (v.origen == null) Icons.Outlined.DownloadDone else Icons.Outlined.Movie, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(v.origen ?: "Desde tus descargas · sin conexión", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            BarraAcciones(local = true, onCola = { verCola = true }, onMas = { verMas = true })
                            TextButton(onClick = { VideoController.cerrar(); onMinimizar() }) { Text("Cerrar reproductor", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    aContinuacion(cola)
                }
            } else if (info == null && video?.error == null) {
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
                                    com.santyramos.mirador.extractor.fechaLegible(info.textualUploadDate),
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
                            // Acciones: una sola fila (Descargar es la principal); lo de uso ocasional va en «Más».
                            val guardar = com.santyramos.mirador.ui.LocalGuardar.current
                            BarraAcciones(
                                local = false,
                                onDescargar = { onDescargar(info.url) },
                                onGuardar = { guardar(Elemento.Video(info.name.orEmpty(), info.url, info.thumbnails.bestUrl(), info.uploaderName, info.uploaderUrl, info.duration, info.viewCount, info.textualUploadDate, false, false)) },
                                onCompartir = { contexto.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, info.url), "Compartir enlace")) },
                                onCola = { verCola = true }, onMas = { verMas = true },
                            )
                            Surface(
                                onClick = { verComentarios = true }, shape = RoundedCornerShape(14.dp), color = Paleta.S1,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Paleta.Linea), modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Comentarios", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Paleta.Texto3)
                                }
                            }
                            val desc = Youtube.limpiarHtml(info.description?.content.orEmpty())
                            if (desc.isNotBlank()) {
                                Text(
                                    desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (verTodo) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(onClickLabel = if (verTodo) "Ver menos" else "Ver más") { verTodo = !verTodo },
                                )
                            }
                            TextButton(onClick = { VideoController.cerrar(); onMinimizar() }) { Text("Cerrar reproductor", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        HorizontalDivider(color = Paleta.Linea)
                    }
                    aContinuacion(cola)
                    if (relacionados.isNotEmpty()) item { Text("Videos relacionados", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
                    items(relacionados, key = { it.url }) { v -> FilaVideo(v, onClick = { onAbrirVideo(v.url) }, onDescargar = { onDescargar(v.url) }) }
                }
            }
        }
    }
    if (bloqueado) CapaBloqueo { bloqueado = false }
    }
}

/**
 * Capa encima de todo el reproductor que se traga los toques. Al tocar aparece el candado;
 * se desbloquea manteniéndolo presionado (o con la acción «Desbloquear» de TalkBack).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CapaBloqueo(onDesbloquear: () -> Unit) {
    var verCandado by remember { mutableStateOf(true) }
    var toques by remember { mutableStateOf(0) }
    LaunchedEffect(toques) { verCandado = true; delay(2500); verCandado = false }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.changes.any { it.pressed && !it.previousPressed }) toques++
                    e.changes.forEach { it.consume() }
                }
            }
        },
    ) {
        if (verCandado) {
            androidx.compose.material3.Surface(
                shape = CircleShape, color = Color(0xCC000000),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp).size(64.dp)
                    .combinedClickable(
                        onClickLabel = "Mantén presionado para desbloquear", onClick = { toques++ },
                        onLongClickLabel = "Desbloquear", onLongClick = onDesbloquear,
                    ),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Lock, "Pantalla bloqueada", tint = Color.White, modifier = Modifier.size(30.dp)) } }
            Text(
                "Mantén presionado el candado para desbloquear", color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 96.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Lo que suena después (la cola), a la vista debajo del video. */
private fun androidx.compose.foundation.lazy.LazyListScope.aContinuacion(cola: List<com.santyramos.mirador.player.ElementoCola>) {
    if (cola.isEmpty()) return
    item { Text("A continuación (${cola.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) }
    itemsIndexed(cola, key = { i, e -> "cola $i ${e.url}" }) { i, e ->
        com.santyramos.mirador.ui.FilaElemento(e, { VideoController.reproducirDeCola(i) }) {
            com.santyramos.mirador.ui.BotonIcono(Icons.Filled.Close, "Quitar de la cola", { VideoController.quitarDeCola(i) })
        }
    }
}

/**
 * Fila de acciones del reproductor (se desliza de lado si no cabe). Descargar es la acción principal;
 * Repetir, Aleatorio, Temporizador y Reproducción automática viven en «Más» porque se usan poco.
 */
@Composable
private fun BarraAcciones(
    local: Boolean,
    onCola: () -> Unit,
    onMas: () -> Unit,
    onDescargar: (() -> Unit)? = null,
    onGuardar: (() -> Unit)? = null,
    onCompartir: (() -> Unit)? = null,
) {
    val soloAudio by VideoController.soloAudio.collectAsState()
    val cola by VideoController.cola.collectAsState()
    val repetir by VideoController.repetir.collectAsState()
    val aleatorio by VideoController.aleatorio.collectAsState()
    val temporizador by VideoController.temporizador.collectAsState()
    val masActivo = repetir != com.santyramos.mirador.player.Repetir.NO || aleatorio || temporizador != null
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onDescargar != null) item {
            Surface(onClick = onDescargar, shape = RoundedCornerShape(14.dp), color = Paleta.Relleno, modifier = Modifier.height(40.dp)) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp), tint = Paleta.SobreRelleno)
                    Text("Descargar", style = MaterialTheme.typography.labelMedium, color = Paleta.SobreRelleno, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (onGuardar != null) item { com.santyramos.mirador.ui.ChipOpcion(Icons.Outlined.BookmarkBorder, "Guardar", false, onGuardar) }
        if (onCompartir != null) item { com.santyramos.mirador.ui.ChipOpcion(Icons.Outlined.Share, "Compartir", false, onCompartir) }
        if (!local) item { com.santyramos.mirador.ui.ChipOpcion(Icons.Outlined.Headphones, "Solo audio", soloAudio, { VideoController.cambiarSoloAudio(!soloAudio) }) }
        item { com.santyramos.mirador.ui.ChipOpcion(Icons.AutoMirrored.Outlined.QueueMusic, if (cola.isEmpty()) "Cola" else "Cola (${cola.size})", false, onCola) }
        item { com.santyramos.mirador.ui.ChipOpcion(Icons.Outlined.MoreHoriz, "Más", masActivo, onMas) }
    }
}

/** «Más»: Repetir, Aleatorio, Reproducción automática y Temporizador, todo en una hoja. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OpcionesSheetContent(onCerrar: () -> Unit) {
    val repetir by VideoController.repetir.collectAsState()
    val aleatorio by VideoController.aleatorio.collectAsState()
    val auto by VideoController.autoSiguiente.collectAsState()
    val temporizador by VideoController.temporizador.collectAsState()
    // El tiempo que queda del temporizador se refresca cada 20 s.
    var ahora by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(temporizador) { while (temporizador?.finMs != null) { ahora = System.currentTimeMillis(); delay(20_000) } }
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Opciones de reproducción", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

        Text("Repetir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 10.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.santyramos.mirador.ui.Filtro("No", repetir == com.santyramos.mirador.player.Repetir.NO) { VideoController.establecerRepetir(com.santyramos.mirador.player.Repetir.NO) }
            com.santyramos.mirador.ui.Filtro("Este video", repetir == com.santyramos.mirador.player.Repetir.UNA) { VideoController.establecerRepetir(com.santyramos.mirador.player.Repetir.UNA) }
            com.santyramos.mirador.ui.Filtro("Toda la cola", repetir == com.santyramos.mirador.player.Repetir.TODAS) { VideoController.establecerRepetir(com.santyramos.mirador.player.Repetir.TODAS) }
        }

        com.santyramos.mirador.ui.FilaInterruptor("Aleatorio", aleatorio, { if (it != aleatorio) VideoController.cambiarAleatorio() }, "Mezcla el orden de la cola")
        com.santyramos.mirador.ui.FilaInterruptor("Reproducción automática", auto, { VideoController.cambiarAutoSiguiente(it) }, "Al terminar, sigue con la cola o el siguiente video")

        Text("Temporizador para dormir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 6.dp))
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.santyramos.mirador.ui.Filtro("Apagado", temporizador == null) { VideoController.programarTemporizador(null) }
            listOf(15 to "15 min", 30 to "30 min", 60 to "1 hora").forEach { (m, t) ->
                com.santyramos.mirador.ui.Filtro(t, temporizador?.minutos == m && temporizador?.finMs != null) { VideoController.programarTemporizador(m) }
            }
            com.santyramos.mirador.ui.Filtro("Al terminar este video", temporizador?.alTerminar == true) { VideoController.programarTemporizador(0) }
        }
        temporizador?.finMs?.let { fin ->
            Text(
                "Se pausa en ${((fin - ahora) / 60_000 + 1).coerceAtLeast(1)} min", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        BotonSecundario("Listo", onCerrar, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp))
    }
}
