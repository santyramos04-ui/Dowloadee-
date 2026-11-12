package com.santyramos.mirador.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.extractor.bestUrl
import com.santyramos.mirador.util.Format
import org.schabi.newpipe.extractor.Page

@Composable
fun ChannelScreen(url: String, onAtras: () -> Unit, onAbrirVideo: (String) -> Unit, onDescargar: (String) -> Unit, onAbrirLista: (String) -> Unit = {}) {
    BackHandler(onBack = onAtras)
    var datos by remember(url) { mutableStateOf<Youtube.DatosCanal?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var intento by remember(url) { mutableStateOf(0) }
    val videos = remember(url) { mutableStateListOf<Elemento>() }
    var siguiente by remember(url) { mutableStateOf<Page?>(null) }
    var cargandoMas by remember(url) { mutableStateOf(false) }
    val lista = rememberLazyListState()
    // Pestaña: los videos del canal, sus transmisiones en vivo o sus listas de reproducción.
    var pestana by remember(url) { mutableStateOf(org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS) }
    val enVivo = pestana == org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.LIVESTREAMS
    var cambiando by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url, intento) {
        error = null
        try {
            val d = Youtube.canal(url)
            datos = d; videos.clear(); videos.addAll(d.videos.elementos); siguiente = d.videos.siguiente
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { error = ErrorMapper.mensajeVer(e) }
    }
    var primeraVez by remember(url) { mutableStateOf(true) }
    LaunchedEffect(pestana) {
        val d = datos ?: return@LaunchedEffect
        if (primeraVez) { primeraVez = false; if (pestana == org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS) return@LaunchedEffect }
        cambiando = true
        try {
            val p = Youtube.pestanaCanal(d.info, pestana)
            videos.clear(); videos.addAll(p.elementos); siguiente = p.siguiente
            lista.scrollToItem(0)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { videos.clear(); siguiente = null } finally { cambiando = false }
    }
    val alFinal by remember { derivedStateOf { val u = lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; u >= videos.size - 3 && videos.isNotEmpty() } }
    LaunchedEffect(alFinal, siguiente) {
        val s = siguiente; val d = datos
        if (alFinal && s != null && d != null && !cargandoMas) {
            cargandoMas = true
            try {
                val p = Youtube.masVideosCanal(d.info, s, pestana)
                videos.addAll(p.elementos); siguiente = p.siguiente
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { siguiente = null } finally { cargandoMas = false }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, top = 6.dp, end = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            com.santyramos.mirador.ui.BotonIcono(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onAtras)
            Text(datos?.info?.name ?: "Canal", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxSize()) {
            val d = datos
            when {
                error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    com.santyramos.mirador.ui.BotonSecundario("Reintentar", { intento++ }, Modifier.padding(top = 12.dp))
                }
                d == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyColumn(state = lista, contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(d.info.avatars.bestUrl(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(CircleShape))
                            Column {
                                Text(d.info.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                                if (d.info.subscriberCount >= 0) Text("${Format.contar(d.info.subscriberCount)} suscriptores", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                com.santyramos.mirador.ui.BotonSuscribir(
                                    d.info.id,
                                    { com.santyramos.mirador.data.lib.Suscripcion(d.info.id, d.info.name.orEmpty(), com.santyramos.mirador.data.lib.Importar.urlDeCanal(d.info.id), d.info.avatars.bestUrl()) },
                                    Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        val conVivo = Youtube.tieneEnVivo(d.info)
                        val conListas = Youtube.tienePestana(d.info, org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS)
                        if (conVivo || conListas) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.santyramos.mirador.ui.Filtro("Videos", pestana == org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS) { pestana = org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS }
                            if (conVivo) com.santyramos.mirador.ui.Filtro("En vivo", enVivo) { pestana = org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.LIVESTREAMS }
                            if (conListas) com.santyramos.mirador.ui.Filtro("Listas", pestana == org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS) { pestana = org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS }
                        }
                        if (cambiando) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        else if (videos.isEmpty()) Text(
                            when (pestana) { org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.LIVESTREAMS -> "Este canal no tiene transmisiones en vivo."; org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS -> "Este canal no tiene listas públicas."; else -> "Este canal no tiene videos visibles." }, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // En vivo: primero lo que está transmitiendo ahora.
                    val mostrados = videos.filterIsInstance<Elemento.Video>().let { l -> if (enVivo) l.sortedByDescending { it.esDirecto } else l }
                    if (!cambiando && pestana == org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS) items(videos.filterIsInstance<Elemento.Lista>(), key = { it.url }) { l ->
                        FilaLista(l, onClick = { onAbrirLista(l.url) })
                    }
                    if (!cambiando && pestana != org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS) items(mostrados, key = { it.url }) { v ->
                        FilaVideo(v, onClick = { onAbrirVideo(v.url) }, onDescargar = { onDescargar(v.url) })
                    }
                    if (cargandoMas) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}
