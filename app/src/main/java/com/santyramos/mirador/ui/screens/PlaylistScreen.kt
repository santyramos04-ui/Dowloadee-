package com.santyramos.mirador.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.Youtube
import org.schabi.newpipe.extractor.Page

@Composable
fun PlaylistScreen(url: String, onAtras: () -> Unit, onAbrirVideo: (String) -> Unit, onDescargar: (String) -> Unit) {
    BackHandler(onBack = onAtras)
    var titulo by remember(url) { mutableStateOf("Lista") }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var intento by remember(url) { mutableStateOf(0) }
    var cargado by remember(url) { mutableStateOf(false) }
    val videos = remember(url) { mutableStateListOf<Elemento>() }
    var siguiente by remember(url) { mutableStateOf<Page?>(null) }
    var cargandoMas by remember(url) { mutableStateOf(false) }
    val lista = rememberLazyListState()
    var descargarLista by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url, intento) {
        error = null
        try {
            val d = Youtube.lista(url)
            titulo = d.info.name.orEmpty(); videos.clear(); videos.addAll(d.videos.elementos); siguiente = d.videos.siguiente; cargado = true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { error = ErrorMapper.mensajeVer(e) }
    }
    val alFinal by remember { derivedStateOf { val u = lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; u >= videos.size - 3 && videos.isNotEmpty() } }
    LaunchedEffect(alFinal, siguiente) {
        val s = siguiente
        if (alFinal && s != null && !cargandoMas) {
            cargandoMas = true
            try { val p = Youtube.masLista(url, s); videos.addAll(p.elementos); siguiente = p.siguiente }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { siguiente = null } finally { cargandoMas = false }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            Text(titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxSize()) {
            when {
                error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { intento++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Reintentar") }
                }
                !cargado -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyColumn(state = lista, contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        androidx.compose.material3.Button(
                            onClick = { descargarLista = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Download, null, Modifier.size(18.dp))
                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)); Text("Descargar toda la lista")
                        }
                    }
                    items(videos.filterIsInstance<Elemento.Video>(), key = { it.url }) { v ->
                        FilaVideo(v, onClick = { onAbrirVideo(v.url) }, onDescargar = { onDescargar(v.url) })
                    }
                    if (cargandoMas) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }

    if (descargarLista) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { descargarLista = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            com.santyramos.mirador.ui.ListaSheetContent(url = url, onCerrar = { descargarLista = false }, onEncolada = { descargarLista = false })
        }
    }
}
