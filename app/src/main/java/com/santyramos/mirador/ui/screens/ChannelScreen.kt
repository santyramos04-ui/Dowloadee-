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
fun ChannelScreen(url: String, onAtras: () -> Unit, onAbrirVideo: (String) -> Unit, onDescargar: (String) -> Unit) {
    BackHandler(onBack = onAtras)
    var datos by remember(url) { mutableStateOf<Youtube.DatosCanal?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var intento by remember(url) { mutableStateOf(0) }
    val videos = remember(url) { mutableStateListOf<Elemento>() }
    var siguiente by remember(url) { mutableStateOf<Page?>(null) }
    var cargandoMas by remember(url) { mutableStateOf(false) }
    val lista = rememberLazyListState()

    LaunchedEffect(url, intento) {
        error = null
        try {
            val d = Youtube.canal(url)
            datos = d; videos.clear(); videos.addAll(d.videos.elementos); siguiente = d.videos.siguiente
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { error = ErrorMapper.mensajeVer(e) }
    }
    val alFinal by remember { derivedStateOf { val u = lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; u >= videos.size - 3 && videos.isNotEmpty() } }
    LaunchedEffect(alFinal, siguiente) {
        val s = siguiente; val d = datos
        if (alFinal && s != null && d != null && !cargandoMas) {
            cargandoMas = true
            try {
                val p = Youtube.masVideosCanal(d.info, s)
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
                            }
                        }
                        if (videos.isEmpty()) Text("Este canal no tiene videos visibles.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(videos.filterIsInstance<Elemento.Video>(), key = { it.url }) { v ->
                        FilaVideo(v, onClick = { onAbrirVideo(v.url) }, onDescargar = { onDescargar(v.url) })
                    }
                    if (cargandoMas) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}
