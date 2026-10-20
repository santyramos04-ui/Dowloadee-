package com.santyramos.mirador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.FiltroBusqueda
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.peliculas.Archivo
import com.santyramos.mirador.peliculas.CategoriaPeli
import com.santyramos.mirador.peliculas.Pelicula
import com.santyramos.mirador.player.ElementoCola
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.BotonPrimario
import com.santyramos.mirador.ui.BotonSecundario
import com.santyramos.mirador.ui.Encabezado
import com.santyramos.mirador.ui.EstadoVacio
import com.santyramos.mirador.ui.Filtro
import com.santyramos.mirador.ui.HojaMirador
import com.santyramos.mirador.ui.theme.Paleta
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Lo que se muestra: una colección de Internet Archive o películas gratis de YouTube. */
private sealed interface FuentePeli {
    /** Tu servidor Jellyfin (mini PC en casa). */
    data object Servidor : FuentePeli
    data class Archivo(val categoria: CategoriaPeli) : FuentePeli
    data object YouTube : FuentePeli
}

/** «Mi servidor» (Jellyfin en la mini PC) queda escondido hasta que el servidor esté listo: cambiar a true para mostrarlo. */
private const val MOSTRAR_SERVIDOR = false

private val FUENTES: List<FuentePeli> = (if (MOSTRAR_SERVIDOR) listOf(FuentePeli.Servidor) else emptyList()) + CategoriaPeli.entries.map { FuentePeli.Archivo(it) } + FuentePeli.YouTube
private fun FuentePeli.etiqueta() = when (this) {
    FuentePeli.Servidor -> "Mi servidor"
    is FuentePeli.Archivo -> categoria.etiqueta
    FuentePeli.YouTube -> "Gratis en YouTube"
}

/** Elemento que suena en el reproductor para una película de Internet Archive. */
internal fun Pelicula.aCola(urlVideo: String) = ElementoCola(
    url = pagina, titulo = titulo, autor = anio?.let { "Año $it" } ?: "Internet Archive", miniatura = portada,
    archivoUri = urlVideo, archivoMime = "video/mp4", origen = "Internet Archive · dominio público",
)

/**
 * Películas y series gratis y legales: cine de dominio público de Internet Archive y películas
 * que sus dueños publican gratis en YouTube.
 */
@Composable
fun PeliculasScreen(onAbrirVideo: (String) -> Unit, onDescargar: (String) -> Unit, onAbrirReproductor: () -> Unit) {
    val scope = rememberCoroutineScope()
    val foco = LocalFocusManager.current
    // Si ya conectaste tu servidor, Películas abre en «Mi servidor»; si no, en «Populares».
    val contextoPeli = LocalContext.current
    var fuenteIndice by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        if (fuenteIndice < 0) fuenteIndice = if (!MOSTRAR_SERVIDOR) 0 else if (com.santyramos.mirador.data.Settings(contextoPeli).servidor.first() != null) 0 else 1
    }
    val fuente = FUENTES[fuenteIndice.coerceAtLeast(0)]
    var consulta by rememberSaveable { mutableStateOf("") }
    var buscada by rememberSaveable { mutableStateOf("") }
    val peliculas = remember { mutableStateListOf<Pelicula>() }
    val videos = remember { mutableStateListOf<Elemento.Video>() }
    var pagina by remember { mutableIntStateOf(1) }
    var hayMas by remember { mutableStateOf(true) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var elegida by remember { mutableStateOf<Pelicula?>(null) }
    val grilla = rememberLazyGridState()

    suspend fun cargar(reiniciar: Boolean) {
        if (cargando) return
        cargando = true; error = null
        try {
            if (reiniciar) { peliculas.clear(); videos.clear(); pagina = 1; hayMas = true }
            when (fuente) {
                FuentePeli.Servidor -> {}
                is FuentePeli.Archivo -> {
                    val nuevas = Archivo.buscar(fuente.categoria, pagina, buscada.ifBlank { null })
                    peliculas.addAll(nuevas.filter { n -> peliculas.none { it.id == n.id } })
                    hayMas = nuevas.size >= 40; pagina++
                }
                FuentePeli.YouTube -> {
                    val q = (buscada.ifBlank { "" } + " película completa en español").trim()
                    val r = Youtube.buscar(q, FiltroBusqueda.VIDEOS)
                    // Solo lo que dura como una película (más de una hora).
                    videos.addAll(r.elementos.filterIsInstance<Elemento.Video>().filter { it.duracionSeg >= 3600 && !it.esDirecto })
                    hayMas = false
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = com.santyramos.mirador.download.ErrorMapper.mensajeVer(e)
        } finally { cargando = false }
    }

    LaunchedEffect(fuenteIndice, buscada) { if (fuenteIndice >= 0 && fuente != FuentePeli.Servidor) cargar(reiniciar = true) }
    val alFinal by remember { derivedStateOf { (grilla.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grilla.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(alFinal) { if (alFinal && hayMas && !cargando && peliculas.isNotEmpty()) cargar(reiniciar = false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Encabezado("Películas", if (fuente == FuentePeli.Servidor) "Tus películas, series y videos desde tu mini PC" else "Gratis y legales: cine de dominio público y estrenos que sus dueños comparten")
        OutlinedTextField(
            value = consulta, onValueChange = { consulta = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).semantics { contentDescription = "Buscar películas" },
            placeholder = { Text(if (fuente == FuentePeli.Servidor) "Buscar en mi servidor…" else "Buscar una película…", color = Paleta.Texto3) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { foco.clearFocus(); buscada = consulta.trim() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Paleta.S2, unfocusedContainerColor = Paleta.S2,
                focusedBorderColor = com.santyramos.mirador.ui.theme.Acento, unfocusedBorderColor = Paleta.Linea,
            ),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            items(FUENTES.size) { i -> Filtro(FUENTES[i].etiqueta(), fuenteIndice == i) { fuenteIndice = i } }
        }

        val vacio = peliculas.isEmpty() && videos.isEmpty()
        when {
            fuenteIndice < 0 -> Box(Modifier.fillMaxSize())
            fuente == FuentePeli.Servidor -> MiServidor(buscada, onAbrirReproductor)
            vacio && cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            vacio && error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    BotonSecundario("Reintentar", { scope.launch { cargar(reiniciar = true) } })
                }
            }
            vacio -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.Movie, "No encontré películas", "Prueba con otra palabra o con otra categoría.")
            }
            fuente == FuentePeli.YouTube -> androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(videos, key = { it.url }) { v -> FilaVideo(v, onClick = { onAbrirVideo(v.url) }, onDescargar = { onDescargar(v.url) }) }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp), state = grilla,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(peliculas, key = { it.id }) { p -> TarjetaPelicula(p) { elegida = p } }
                if (cargando) item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }

    elegida?.let { p ->
        HojaMirador(onCerrar = { elegida = null }) {
            DetallePelicula(p, onCerrar = { elegida = null }, onDescargar = { elegida = null; onDescargar(p.pagina) }, onAbrirReproductor = onAbrirReproductor)
        }
    }
}

@Composable
private fun TarjetaPelicula(p: Pelicula, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Ver detalles", onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Paleta.S2), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Movie, null, tint = Paleta.Texto3)
            AsyncImage(model = p.portada, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Text(p.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        if (p.anio != null) Text(p.anio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetallePelicula(p: Pelicula, onCerrar: () -> Unit, onDescargar: () -> Unit, onAbrirReproductor: () -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var buscando by remember { mutableStateOf(false) }
    fun conVideo(accion: (String) -> Unit) {
        buscando = true
        scope.launch {
            val url = runCatching { Archivo.urlVideo(p.id) }.getOrNull()
            buscando = false
            if (url == null) android.widget.Toast.makeText(contexto, "Esta película no tiene un video compatible. Prueba con «Descargar».", android.widget.Toast.LENGTH_LONG).show()
            else accion(url)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncImage(
                model = p.portada, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(12.dp)).background(Paleta.S3),
            )
            Column(Modifier.weight(1f)) {
                Text(p.titulo, style = MaterialTheme.typography.titleLarge)
                Text(listOfNotNull(p.anio, "Internet Archive", "Dominio público").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (!p.descripcion.isNullOrBlank()) {
            var verTodo by remember { mutableStateOf(false) }
            Text(
                p.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (verTodo) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClickLabel = if (verTodo) "Ver menos" else "Ver más") { verTodo = !verTodo },
            )
        }
        BotonPrimario(if (buscando) "Preparando…" else "Reproducir", {
            conVideo { url -> VideoController.abrirArchivo(p.aCola(url)); onCerrar(); onAbrirReproductor() }
        }, Modifier.fillMaxWidth(), icono = Icons.Outlined.PlayArrow, enabled = !buscando)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario("A la cola", {
                conVideo { url ->
                    VideoController.agregarACola(p.aCola(url))
                    android.widget.Toast.makeText(contexto, "Se añadió a la cola", android.widget.Toast.LENGTH_SHORT).show(); onCerrar()
                }
            }, Modifier.weight(1f), icono = Icons.AutoMirrored.Outlined.QueueMusic)
            BotonSecundario("Descargar", onDescargar, Modifier.weight(1f), icono = Icons.Outlined.FileDownload)
        }
        if (buscando) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary) }
    }
}

