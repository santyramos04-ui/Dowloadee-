package com.santyramos.mirador.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.FiltroBusqueda
import com.santyramos.mirador.extractor.Youtube
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

private enum class FiltroDuracion(val etiqueta: String) {
    CUALQUIERA("Cualquier duración"), CORTOS("Menos de 4 min"), MEDIOS("4 a 20 min"), LARGOS("Más de 20 min");

    fun acepta(e: Elemento): Boolean {
        if (e !is Elemento.Video) return true
        val min = e.duracionSeg / 60.0
        return when (this) {
            CUALQUIERA -> true
            CORTOS -> e.duracionSeg in 1..239
            MEDIOS -> min >= 4 && min <= 20
            LARGOS -> min > 20
        }
    }
}

/** Pestaña Inicio: búsqueda de videos, canales y listas con filtros. */
@Composable
fun HomeScreen(
    onAbrirVideo: (String) -> Unit,
    onDescargar: (String) -> Unit,
    onAbrirCanal: (String) -> Unit,
    onAbrirLista: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var consulta by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf(FiltroBusqueda.TODO) }
    var duracion by rememberSaveable { mutableStateOf(FiltroDuracion.CUALQUIERA) }
    val resultados = remember { mutableStateListOf<Elemento>() }
    var siguiente by remember { mutableStateOf<Page?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var buscado by remember { mutableStateOf<String?>(null) }
    var trabajo by remember { mutableStateOf<Job?>(null) }
    val foco = LocalFocusManager.current
    val lista = rememberLazyListState()

    fun buscar() {
        val q = consulta.trim()
        if (q.isEmpty()) return
        foco.clearFocus()
        trabajo?.cancel()
        trabajo = scope.launch {
            cargando = true; error = null; resultados.clear(); siguiente = null
            try {
                val p = Youtube.buscar(q, filtro)
                resultados.addAll(p.elementos); siguiente = p.siguiente; buscado = q
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = ErrorMapper.mensajeVer(e)
            } finally { cargando = false }
        }
    }

    // Al cambiar el filtro se repite la búsqueda.
    LaunchedEffect(filtro) { if (buscado != null) buscar() }

    // Carga la página siguiente al llegar al final.
    val alFinal by remember { derivedStateOf { val u = lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; u >= resultados.size - 4 && resultados.isNotEmpty() } }
    LaunchedEffect(alFinal, siguiente) {
        val s = siguiente
        val q = buscado
        if (alFinal && s != null && q != null && !cargando) {
            cargando = true
            try {
                val p = Youtube.masBusqueda(q, filtro, s)
                resultados.addAll(p.elementos); siguiente = p.siguiente
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { siguiente = null } finally { cargando = false }
        }
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = consulta, onValueChange = { consulta = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Buscar videos, canales o listas") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (consulta.isNotEmpty()) IconButton(onClick = { consulta = "" }) { Icon(Icons.Filled.Clear, "Borrar") } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { buscar() }),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FiltroBusqueda.entries) { f ->
                FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text(f.etiqueta) })
            }
        }
        if (filtro == FiltroBusqueda.TODO || filtro == FiltroBusqueda.VIDEOS) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FiltroDuracion.entries) { d ->
                    FilterChip(selected = duracion == d, onClick = { duracion = d }, label = { Text(d.etiqueta) })
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            val visibles = resultados.filter { duracion.acepta(it) }
            when {
                error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { buscar() }, modifier = Modifier.padding(top = 12.dp)) { Text("Reintentar") }
                }
                buscado == null && !cargando -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mirador", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Busca un video, un canal o una lista.\nSin anuncios y sin cuenta de Google.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                visibles.isEmpty() && !cargando -> Text("No encontré resultados.", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(state = lista, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(visibles, key = { it.url }) { e ->
                        when (e) {
                            is Elemento.Video -> FilaVideo(e, onClick = { onAbrirVideo(e.url) }, onDescargar = { onDescargar(e.url) })
                            is Elemento.Canal -> FilaCanal(e, onClick = { onAbrirCanal(e.url) })
                            is Elemento.Lista -> FilaLista(e, onClick = { onAbrirLista(e.url) })
                        }
                    }
                    if (cargando) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
            if (cargando && resultados.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
