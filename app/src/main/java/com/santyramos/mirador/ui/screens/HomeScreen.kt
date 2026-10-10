package com.santyramos.mirador.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import com.santyramos.mirador.data.lib.Recomendador
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.BotonPrimario
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.R
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.FiltroBusqueda
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.ui.BotonIcono
import com.santyramos.mirador.ui.BotonSecundario
import com.santyramos.mirador.ui.Filtro
import com.santyramos.mirador.ui.theme.Acento
import com.santyramos.mirador.ui.theme.Paleta
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
    onIrADescargas: () -> Unit = {},
    onAbrirNavegador: (String?) -> Unit = {},
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
    val pedirFoco = remember { androidx.compose.ui.focus.FocusRequester() }
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
        // ---- Marca + atajo para pegar ----
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_mark), contentDescription = null, modifier = Modifier.size(30.dp))
            Text("mirador", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 10.dp).weight(1f))
            BotonIcono(Icons.Outlined.Public, "Navegador de Mirador", { onAbrirNavegador(null) }, bordeado = true)
            BotonIcono(Icons.Outlined.ContentPaste, "Pegar enlaces para descargar", onIrADescargas, Modifier.padding(start = 8.dp), bordeado = true)
        }
        // ---- Buscador ----
        OutlinedTextField(
            value = consulta, onValueChange = { consulta = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).focusRequester(pedirFoco),
            placeholder = { Text("Buscar videos, canales o listas", color = Paleta.Texto3) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = { if (consulta.isNotEmpty()) IconButton(onClick = { consulta = "" }) { Icon(Icons.Filled.Clear, "Borrar") } },
            singleLine = true, shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Paleta.S2, unfocusedContainerColor = Paleta.S2,
                focusedBorderColor = Acento, unfocusedBorderColor = Paleta.Linea, cursorColor = Acento,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { buscar() }),
        )
        // ---- Filtros ----
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FiltroBusqueda.entries) { f -> Filtro(f.etiqueta, filtro == f) { filtro = f } }
        }
        if (buscado != null && (filtro == FiltroBusqueda.TODO || filtro == FiltroBusqueda.VIDEOS)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FiltroDuracion.entries) { d -> Filtro(d.etiqueta, duracion == d) { duracion = d } }
            }
        }
        Box(Modifier.fillMaxSize()) {
            val visibles = resultados.filter { duracion.acepta(it) }
            when {
                error != null -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    BotonSecundario("Reintentar", { buscar() })
                }
                buscado == null && !cargando -> Portada(onAbrirNavegador, { pedirFoco.requestFocus() }, onAbrirVideo, onDescargar)
                visibles.isEmpty() && !cargando -> Text("No encontré resultados.", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(state = lista, contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                    items(visibles, key = { it.url }) { e ->
                        when (e) {
                            is Elemento.Video -> TarjetaVideo(e, onClick = { onAbrirVideo(e.url) }, onDescargar = { onDescargar(e.url) })
                            is Elemento.Canal -> FilaCanal(e, onClick = { onAbrirCanal(e.url) })
                            is Elemento.Lista -> FilaLista(e, onClick = { onAbrirLista(e.url) })
                        }
                    }
                    if (cargando) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Acento) } }
                }
            }
            if (cargando && resultados.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Acento)
        }
    }
}

/** Tendencias ya cargadas (se conservan al cambiar de pestaña). */
private object TendenciasCache {
    var datos: List<Elemento.Video>? = null
    var intentado = false
}

private class Plataforma(val nombre: String, @androidx.annotation.DrawableRes val icono: Int?, val url: String?)

/** Sitios desde los que más se descarga. Al tocar uno se abre el navegador de Mirador ahí mismo (YouTube va al buscador). */
private val Plataformas = listOf(
    Plataforma("YouTube", R.drawable.ic_plat_youtube, null),
    Plataforma("X", R.drawable.ic_plat_x, "https://x.com"),
    Plataforma("Instagram", R.drawable.ic_plat_instagram, "https://www.instagram.com"),
    Plataforma("TikTok", R.drawable.ic_plat_tiktok, "https://www.tiktok.com"),
    Plataforma("Facebook", R.drawable.ic_plat_facebook, "https://m.facebook.com"),
    Plataforma("Reddit", R.drawable.ic_plat_reddit, "https://www.reddit.com"),
    Plataforma("Twitch", R.drawable.ic_plat_twitch, "https://www.twitch.tv"),
    Plataforma("Vimeo", R.drawable.ic_plat_vimeo, "https://vimeo.com"),
    Plataforma("Pinterest", R.drawable.ic_plat_pinterest, "https://www.pinterest.com"),
    Plataforma("Otro sitio", null, ""),
)

/** Inicio sin búsqueda: banda de plataformas, tendencias (si YouTube las ofrece) y recomendaciones según tu uso. */
@Composable
private fun Portada(
    onAbrirNavegador: (String?) -> Unit, onBuscarYoutube: () -> Unit,
    onAbrirVideo: (String) -> Unit, onDescargar: (String) -> Unit,
) {
    var tendencias by remember { mutableStateOf(TendenciasCache.datos) }
    var recomendados by remember { mutableStateOf<List<Elemento.Video>?>(null) }
    var calculando by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun recomendar(forzar: Boolean) {
        scope.launch {
            calculando = true
            recomendados = try { Recomendador.recomendar(forzar) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { null }
            calculando = false
        }
    }
    LaunchedEffect(Unit) {
        if (!TendenciasCache.intentado) {
            tendencias = Youtube.tendencias()
            TendenciasCache.datos = tendencias; TendenciasCache.intentado = true
        }
    }
    LaunchedEffect(Unit) { recomendar(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("Descargar desde", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Plataformas) { p ->
                    BotonPlataforma(p) {
                        when {
                            p.icono == null -> onAbrirNavegador(null)
                            p.url == null -> onBuscarYoutube()
                            else -> onAbrirNavegador(p.url)
                        }
                    }
                }
            }
        }
        if (!tendencias.isNullOrEmpty()) {
            item { Text("Tendencias en Colombia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 6.dp)) }
            items(tendencias!!, key = { "t" + it.url }) { v -> TarjetaVideo(v, onClick = { onAbrirVideo(v.url) }, onDescargar = { onDescargar(v.url) }) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Para ti", style = MaterialTheme.typography.titleLarge)
                    Text("Según lo que ves y los canales que sigues. Se calcula en tu celular.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!recomendados.isNullOrEmpty()) BotonIcono(Icons.Outlined.Refresh, "Actualizar recomendaciones", { recomendar(true) })
            }
        }
        val lista = recomendados
        when {
            calculando && lista == null -> item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Acento, modifier = Modifier.size(28.dp)) } }
            lista.isNullOrEmpty() -> item {
                Text(
                    "Mira algunos videos o sigue canales y aquí aparecerán recomendaciones hechas para ti.",
                    style = MaterialTheme.typography.bodyMedium, color = Paleta.Texto3, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            else -> {
                item {
                    BotonPrimario(
                        "Reproducir recomendados", {
                            VideoController.definirCola(lista.map { it.url })
                            onAbrirVideo(lista.first().url)
                        },
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), icono = Icons.Filled.PlayArrow,
                    )
                }
                items(lista, key = { "r" + it.url }) { v ->
                    TarjetaVideo(
                        v,
                        onClick = { VideoController.definirCola(lista.map { it.url }); onAbrirVideo(v.url) },
                        onDescargar = { onDescargar(v.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BotonPlataforma(p: Plataforma, onClick: () -> Unit) {
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Surface(
            onClick = onClick, shape = RoundedCornerShape(20.dp), color = Paleta.S1,
            border = androidx.compose.foundation.BorderStroke(1.dp, Paleta.Linea), modifier = Modifier.size(60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (p.icono != null) Icon(painterResource(p.icono), p.nombre, tint = Acento, modifier = Modifier.size(26.dp))
                else Icon(Icons.Outlined.Public, p.nombre, tint = Acento, modifier = Modifier.size(26.dp))
            }
        }
        Text(p.nombre, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}
