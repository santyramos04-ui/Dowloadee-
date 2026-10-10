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
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.animation.AnimatedVisibility
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
}

/** Cosas que llegan desde fuera de la pantalla (otras apps, el portapapeles). */
object Entrada {
    var abrirVideo by mutableStateOf<String?>(null)
    var abrirCanal by mutableStateOf<String?>(null)
    var abrirLista by mutableStateOf<String?>(null)
    var pestana by mutableStateOf<Int?>(null)
    var enlacePortapapeles by mutableStateOf<String?>(null)
}

@Composable
fun AppRaiz(onEntrarPip: () -> Unit, onPortapapelesVisto: (String) -> Unit) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val pila = remember { mutableStateListOf<Destino>() }
    var reproductorAbierto by rememberSaveable { mutableStateOf(false) }
    var urlDescarga by remember { mutableStateOf<String?>(null) }
    var urlsLote by remember { mutableStateOf<List<String>?>(null) }
    val video by VideoController.video.collectAsState()
    val actualizacion by AppUpdater.estado.collectAsState()

    fun abrirVideo(url: String) { VideoController.abrir(url); reproductorAbierto = true }

    // Entradas externas
    Entrada.abrirVideo?.let { u -> LaunchedEffect(u) { abrirVideo(u); Entrada.abrirVideo = null } }
    Entrada.abrirCanal?.let { u -> LaunchedEffect(u) { pila.add(Destino.Canal(u)); reproductorAbierto = false; Entrada.abrirCanal = null } }
    Entrada.abrirLista?.let { u -> LaunchedEffect(u) { pila.add(Destino.Lista(u)); reproductorAbierto = false; Entrada.abrirLista = null } }
    Entrada.pestana?.let { p -> LaunchedEffect(p) { pestana = p; pila.clear(); Entrada.pestana = null } }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = video != null && !reproductorAbierto,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) { MiniReproductor(onAbrir = { reproductorAbierto = true }) }
                    androidx.compose.material3.HorizontalDivider(color = com.santyramos.mirador.ui.theme.Paleta.Linea)
                    NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Black, tonalElevation = 0.dp) {
                        val items = listOf(
                            Triple("Inicio", Icons.Filled.Home to Icons.Outlined.Home, 0),
                            Triple("Suscripciones", Icons.Filled.Subscriptions to Icons.Outlined.Subscriptions, 1),
                            Triple("Descargas", Icons.Outlined.Download to Icons.Outlined.Download, 2),
                            Triple("Biblioteca", Icons.Filled.VideoLibrary to Icons.Outlined.VideoLibrary, 3),
                        )
                        items.forEach { (nombre, iconos, i) ->
                            val activo = pestana == i && pila.isEmpty()
                            NavigationBarItem(
                                selected = activo,
                                onClick = { pestana = i; pila.clear() },
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
                when (destino) {
                    is Destino.Canal -> ChannelScreen(destino.url, onAtras = { pila.removeLastOrNull() }, onAbrirVideo = ::abrirVideo, onDescargar = { urlDescarga = it })
                    is Destino.Lista -> PlaylistScreen(destino.url, onAtras = { pila.removeLastOrNull() }, onAbrirVideo = ::abrirVideo, onDescargar = { urlDescarga = it })
                    Destino.Ajustes -> SettingsScreen(onAtras = { pila.removeLastOrNull() })
                    null -> Crossfade(targetState = pestana, label = "pestanas") { p ->
                        when (p) {
                            0 -> HomeScreen(
                                onAbrirVideo = ::abrirVideo, onDescargar = { urlDescarga = it },
                                onAbrirCanal = { pila.add(Destino.Canal(it)) }, onAbrirLista = { pila.add(Destino.Lista(it)) },
                                onIrADescargas = { pestana = 2 },
                            )
                            1 -> SuscripcionesScreen()
                            2 -> DownloadsScreen()
                            else -> BibliotecaScreen(onAjustes = { pila.add(Destino.Ajustes) })
                        }
                    }
                }
                // Aviso de versión nueva
                (actualizacion as? AppUpdater.Estado.Disponible)?.let { d ->
                    if (destino != Destino.Ajustes) Surface(
                        color = com.santyramos.mirador.ui.theme.Paleta.AcentoSuave,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).fillMaxWidth().padding(12.dp).clickable { pila.add(Destino.Ajustes) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.santyramos.mirador.ui.theme.Paleta.AcentoBorde),
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Text("Hay una versión nueva de Mirador (${d.nueva.version}). Toca para instalarla.", color = com.santyramos.mirador.ui.theme.Acento, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (reproductorAbierto && video != null) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                PlayerScreen(
                    onMinimizar = { reproductorAbierto = false },
                    onDescargar = { urlDescarga = it },
                    onAbrirCanal = { pila.add(Destino.Canal(it)); reproductorAbierto = false },
                    onAbrirVideo = ::abrirVideo,
                    onEntrarPip = onEntrarPip,
                )
            }
        } else if (reproductorAbierto && video == null) {
            reproductorAbierto = false
        }
    }

    urlDescarga?.let { u ->
        com.santyramos.mirador.ui.HojaMirador(onCerrar = { urlDescarga = null }) {
            DownloadSheetContent(url = u, onCerrar = { urlDescarga = null }, onEncolada = { urlDescarga = null })
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
