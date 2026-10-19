package com.santyramos.mirador.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.screens.BibliotecaScreen
import com.santyramos.mirador.ui.screens.ChannelScreen
import com.santyramos.mirador.ui.screens.DownloadsScreen
import com.santyramos.mirador.ui.screens.HomeScreen
import com.santyramos.mirador.ui.screens.PlayerScreen
import com.santyramos.mirador.ui.screens.PlaylistScreen
import com.santyramos.mirador.ui.screens.SettingsScreen
import com.santyramos.mirador.ui.screens.SuscripcionesScreen
import com.santyramos.mirador.update.AppUpdater

sealed interface Destino {
    data class Canal(val url: String) : Destino
    data class Lista(val url: String) : Destino
    data object Ajustes : Destino
    data class Navegador(val url: String?) : Destino
    data object Historial : Destino
    data object Guardados : Destino
    data object Listas : Destino
    data class ListaPropia(val id: Long) : Destino
    data object SinConexion : Destino
}

/** Cosas que llegan desde fuera de la pantalla (otras apps, el portapapeles). */
object Entrada {
    var abrirVideo by mutableStateOf<String?>(null)
    var abrirCanal by mutableStateOf<String?>(null)
    var abrirLista by mutableStateOf<String?>(null)
    var pestana by mutableStateOf<Int?>(null)
    /** Texto que Inicio debe buscar en YouTube apenas aparezca (por ejemplo, una canción de Spotify). */
    var buscar by mutableStateOf<String?>(null)
    var enlacePortapapeles by mutableStateOf<String?>(null)
}

@Composable
fun AppRaiz(onEntrarPip: () -> Unit, onPortapapelesVisto: (String) -> Unit) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val pila = remember { mutableStateListOf<Destino>() }
    var reproductorAbierto by rememberSaveable { mutableStateOf(false) }
    var urlDescarga by remember { mutableStateOf<String?>(null) }
    var urlsLote by remember { mutableStateOf<List<String>?>(null) }
    var listaADescargar by remember { mutableStateOf<com.santyramos.mirador.download.ListaInfo?>(null) }
    var videoAGuardar by remember { mutableStateOf<com.santyramos.mirador.extractor.Elemento.Video?>(null) }
    var videoAcciones by remember { mutableStateOf<com.santyramos.mirador.extractor.Elemento.Video?>(null) }
    val video by VideoController.video.collectAsState()
    val actualizacion by AppUpdater.estado.collectAsState()

    // Atrás en la pantalla principal: primero vuelve a Inicio y luego pide confirmar para salir.
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var ultimoAtras by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    androidx.activity.compose.BackHandler(enabled = pila.isEmpty() && !reproductorAbierto) {
        if (pestana != 0) pestana = 0
        else {
            val ahora = android.os.SystemClock.elapsedRealtime()
            if (ahora - ultimoAtras < 2000) (contexto as? android.app.Activity)?.finish()
            else {
                ultimoAtras = ahora
                android.widget.Toast.makeText(contexto, "Desliza otra vez para salir", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun abrirVideo(url: String) { VideoController.abrir(url); reproductorAbierto = true }

    // Entradas externas
    Entrada.abrirVideo?.let { u -> LaunchedEffect(u) { abrirVideo(u); Entrada.abrirVideo = null } }
    Entrada.abrirCanal?.let { u -> LaunchedEffect(u) { pila.add(Destino.Canal(u)); reproductorAbierto = false; Entrada.abrirCanal = null } }
    Entrada.abrirLista?.let { u -> LaunchedEffect(u) { pila.add(Destino.Lista(u)); reproductorAbierto = false; Entrada.abrirLista = null } }
    Entrada.pestana?.let { p -> LaunchedEffect(p) { pestana = p; pila.clear(); Entrada.pestana = null } }

    // Cada toque en «Inicio» estando ya en Inicio: primero sube al principio y luego vuelve a la portada.
    var toqueInicio by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.CompositionLocalProvider(LocalGuardar provides { v -> videoAGuardar = v }, LocalAcciones provides { v -> videoAcciones = v }) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // Cada pantalla ya respeta la barra de estado; sin esto el espacio de arriba se sumaba dos veces.
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = video != null && !reproductorAbierto,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) { MiniReproductor(onAbrir = { reproductorAbierto = true }) }
                    androidx.compose.material3.HorizontalDivider(color = com.santyramos.mirador.ui.theme.Paleta.Linea)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        val items = listOf(
                            Triple("Inicio", Icons.Filled.Home to Icons.Outlined.Home, 0),
                            Triple("Películas", Icons.Filled.Movie to Icons.Outlined.Movie, 4),
                            Triple("Suscripciones", Icons.Filled.Subscriptions to Icons.Outlined.Subscriptions, 1),
                            Triple("Descargas", Icons.Outlined.Download to Icons.Outlined.Download, 2),
                            Triple("Biblioteca", Icons.Filled.VideoLibrary to Icons.Outlined.VideoLibrary, 3),
                        )
                        items.forEach { (nombre, iconos, i) ->
                            val activo = pestana == i && pila.isEmpty()
                            NavigationBarItem(
                                selected = activo,
                                onClick = { if (activo && i == 0) toqueInicio++; pestana = i; pila.clear() },
                                icon = { Icon(if (activo) iconos.first else iconos.second, nombre) },
                                label = { Text(nombre, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = com.santyramos.mirador.ui.theme.Acento,
                                    selectedTextColor = com.santyramos.mirador.ui.theme.Acento,
                                    indicatorColor = com.santyramos.mirador.ui.theme.Paleta.AcentoSuave,
                                    unselectedIconColor = com.santyramos.mirador.ui.theme.Paleta.Texto3,
                                    unselectedTextColor = com.santyramos.mirador.ui.theme.Paleta.Texto3,
                                ),
                            )
                        }
                    }
                }
            },
        ) { relleno ->
            Box(Modifier.fillMaxSize().padding(relleno)) {
                val destino = pila.lastOrNull()
                androidx.compose.animation.AnimatedContent(
                    targetState = destino to pila.size,
                    transitionSpec = {
                        val avanza = targetState.second > initialState.second
                        val entra = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { if (avanza) it / 4 else -it / 6 } +
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240))
                        val sale = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { if (avanza) -it / 6 else it / 4 } +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
                        entra togetherWith sale
                    },
                    label = "pantallas",
                ) { (d, _) ->
                when (d) {
                    is Destino.Canal -> ChannelScreen(d.url, onAtras = { pila.removeLastOrNull() }, onAbrirVideo = ::abrirVideo, onDescargar = { urlDescarga = it })
                    is Destino.Lista -> PlaylistScreen(d.url, onAtras = { pila.removeLastOrNull() }, onAbrirVideo = ::abrirVideo, onDescargar = { urlDescarga = it })
                    Destino.Ajustes -> SettingsScreen(onAtras = { pila.removeLastOrNull() }, onAbrirNavegador = { pila.add(Destino.Navegador(it)) })
                    is Destino.Navegador -> com.santyramos.mirador.ui.screens.NavegadorScreen(d.url, { pila.removeLastOrNull() }, { urlDescarga = it })
                    Destino.Historial -> com.santyramos.mirador.ui.screens.HistorialScreen({ pila.removeLastOrNull() }, ::abrirVideo, { urlDescarga = it })
                    Destino.Guardados -> com.santyramos.mirador.ui.screens.GuardadosScreen({ pila.removeLastOrNull() }, ::abrirVideo, { urlDescarga = it })
                    Destino.Listas -> com.santyramos.mirador.ui.screens.ListasScreen({ pila.removeLastOrNull() }, { pila.add(Destino.ListaPropia(it)) })
                    Destino.SinConexion -> com.santyramos.mirador.ui.screens.SinConexionScreen { pila.removeLastOrNull() }
                    is Destino.ListaPropia -> com.santyramos.mirador.ui.screens.ListaPropiaScreen(
                        d.id, { pila.removeLastOrNull() }, ::abrirVideo, { urlDescarga = it }, { listaADescargar = it },
                    )
                    null -> Crossfade(targetState = pestana, label = "pestanas") { p ->
                        when (p) {
                            0 -> HomeScreen(
                                onAbrirVideo = ::abrirVideo, onDescargar = { urlDescarga = it },
                                onAbrirCanal = { pila.add(Destino.Canal(it)) }, onAbrirLista = { pila.add(Destino.Lista(it)) },
                                onIrADescargas = { pestana = 2 }, onAbrirNavegador = { pila.add(Destino.Navegador(it)) },
                                onSinConexion = { pila.add(Destino.SinConexion) },
                                toqueInicio = toqueInicio,
                            )
                            1 -> SuscripcionesScreen(
                                onAbrirVideo = ::abrirVideo, onDescargar = { urlDescarga = it },
                                onAbrirCanal = { pila.add(Destino.Canal(it)) }, onBuscar = { pestana = 0 },
                            )
                            2 -> DownloadsScreen(onSinConexion = { pila.add(Destino.SinConexion) })
                            4 -> com.santyramos.mirador.ui.screens.PeliculasScreen(
                                onAbrirVideo = ::abrirVideo, onDescargar = { urlDescarga = it },
                                onAbrirReproductor = { reproductorAbierto = true },
                            )
                            else -> BibliotecaScreen(
                                onAjustes = { pila.add(Destino.Ajustes) }, onHistorial = { pila.add(Destino.Historial) },
                                onGuardados = { pila.add(Destino.Guardados) }, onListas = { pila.add(Destino.Listas) },
                                onSinConexion = { pila.add(Destino.SinConexion) },
                            )
                        }
                    }
                }
                }
                // Aviso de versión nueva
                (actualizacion as? AppUpdater.Estado.Disponible)?.let { d ->
                    if (destino != Destino.Ajustes) Surface(
                        color = com.santyramos.mirador.ui.theme.Paleta.AcentoSuave,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).fillMaxWidth().padding(12.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Ver la versión nueva") { pila.add(Destino.Ajustes) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.santyramos.mirador.ui.theme.Paleta.AcentoBorde),
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Text("Hay una versión nueva de ${com.santyramos.mirador.util.Marca.NOMBRE} (${d.nueva.version}). Toca para instalarla.", color = com.santyramos.mirador.ui.theme.Acento, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = reproductorAbierto && video != null,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)),
            exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
        ) {
            // Transparente: al arrastrar el reproductor hacia abajo se ve la app detrás (el fondo lo pone PlayerScreen).
            Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                PlayerScreen(
                    onMinimizar = { reproductorAbierto = false },
                    onDescargar = { urlDescarga = it },
                    onAbrirCanal = { pila.add(Destino.Canal(it)); reproductorAbierto = false },
                    onAbrirVideo = ::abrirVideo,
                    onEntrarPip = onEntrarPip,
                )
            }
        }
        if (reproductorAbierto && video == null) reproductorAbierto = false
    }

    videoAcciones?.let { v ->
        com.santyramos.mirador.ui.HojaMirador(onCerrar = { videoAcciones = null }) {
            AccionesSheetContent(
                video = v, onCerrar = { videoAcciones = null },
                onGuardar = { videoAGuardar = v },
                onDescargar = { urlDescarga = v.url },
                onSoloAudio = { VideoController.cambiarSoloAudio(true); abrirVideo(v.url) },
                onCanal = v.urlAutor?.let { u -> { pila.add(Destino.Canal(u)); reproductorAbierto = false } },
            )
        }
    }

    videoAGuardar?.let { v ->
        com.santyramos.mirador.ui.HojaMirador(onCerrar = { videoAGuardar = null }) {
            GuardarSheetContent(video = v, onCerrar = { videoAGuardar = null })
        }
    }

    urlDescarga?.let { u ->
        com.santyramos.mirador.ui.HojaMirador(onCerrar = { urlDescarga = null }) {
            DownloadSheetContent(url = u, onCerrar = { urlDescarga = null }, onEncolada = { urlDescarga = null })
        }
    }

    listaADescargar?.let { l ->
        com.santyramos.mirador.ui.HojaMirador(onCerrar = { listaADescargar = null }) {
            SeleccionListaContent(l, onCerrar = { listaADescargar = null }, onEncolada = { listaADescargar = null })
        }
    }

    urlsLote?.let { lote ->
        com.santyramos.mirador.ui.HojaMirador(onCerrar = { urlsLote = null }) {
            LoteSheetContent(urls = lote, onCerrar = { urlsLote = null }, onEncolada = { urlsLote = null })
        }
    }

    Entrada.enlacePortapapeles?.let { u ->
        val varios = u.lines().filter { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = { onPortapapelesVisto(u); Entrada.enlacePortapapeles = null },
            title = { Text(if (varios.size > 1) "¿Descargar estos ${varios.size} enlaces?" else "¿Descargar este enlace?") },
            text = { Text(varios.take(3).joinToString("\n") + if (varios.size > 3) "\n…" else "", maxLines = 5, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    onPortapapelesVisto(u); Entrada.enlacePortapapeles = null
                    if (varios.size > 1) urlsLote = varios else urlDescarga = varios.firstOrNull()
                }) { Text("Descargar") }
            },
            dismissButton = { TextButton(onClick = { onPortapapelesVisto(u); Entrada.enlacePortapapeles = null }) { Text("No") } },
        )
    }
    }
}
