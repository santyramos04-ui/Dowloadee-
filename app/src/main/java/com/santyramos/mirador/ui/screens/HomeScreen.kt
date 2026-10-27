package com.santyramos.mirador.ui.screens

import com.santyramos.mirador.ui.listaSugerencias
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import com.santyramos.mirador.data.lib.Recomendador
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.BotonPrimario
import com.santyramos.mirador.ui.Entrada
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
    onSinConexion: () -> Unit = {},
    toqueInicio: Int = 0,
    modifier: Modifier = Modifier,
) {
    val contextoHome = androidx.compose.ui.platform.LocalContext.current
    val ajustesHome = remember { com.santyramos.mirador.data.Settings(contextoHome) }
    val modoSinConexion by ajustesHome.sinConexion.collectAsState(initial = false)
    // Sugerencias: tu historial de búsquedas y lo que propone YouTube mientras escribes.
    val historialBusquedas by ajustesHome.busquedas.collectAsState(initial = emptyList())
    var enfocado by remember { mutableStateOf(false) }
    var remotas by remember { mutableStateOf<List<String>>(emptyList()) }
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
        scope.launch { ajustesHome.agregarBusqueda(q) }
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

    LaunchedEffect(consulta, enfocado) {
        if (!enfocado || consulta.isBlank()) { remotas = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(220) // se espera a que dejes de escribir
        remotas = runCatching { Youtube.sugerencias(consulta.trim()) }.getOrDefault(emptyList())
    }
    val sugerencias = remember(consulta, historialBusquedas, remotas) {
        com.santyramos.mirador.ui.Sugerir.combinar(consulta, historialBusquedas, remotas)
    }
    androidx.activity.compose.BackHandler(enabled = enfocado) { foco.clearFocus() }

    // Búsquedas pedidas desde fuera (canción de Spotify → YouTube).
    val pedida = Entrada.buscar
    LaunchedEffect(pedida) {
        if (pedida != null) { consulta = pedida; filtro = FiltroBusqueda.VIDEOS; Entrada.buscar = null; buscar() }
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

    // La cabecera (marca y buscador) acompaña al dedo: se esconde al bajar y reaparece al subir, sin saltos.
    var altoCabecera by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var desfase by remember { androidx.compose.runtime.mutableFloatStateOf(0f) } // 0 = visible, -alto = escondida
    var animandoCabecera by remember { mutableStateOf<Job?>(null) }
    fun moverCabecera(destino: Float) {
        animandoCabecera?.cancel()
        animandoCabecera = scope.launch {
            androidx.compose.animation.core.animate(desfase, destino, animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { v, _ -> desfase = v }
        }
    }
    val conexionScroll = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (enfocado || altoCabecera == 0) return androidx.compose.ui.geometry.Offset.Zero
                animandoCabecera?.cancel()
                val nuevo = (desfase + available.y).coerceIn(-altoCabecera.toFloat(), 0f)
                val usado = nuevo - desfase
                desfase = nuevo
                return androidx.compose.ui.geometry.Offset(0f, usado)
            }
            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                // Al soltar, la cabecera termina de abrirse o de esconderse (nunca queda a medias).
                val alto = altoCabecera.toFloat()
                if (desfase != 0f && desfase != -alto) {
                    val arriba = lista.firstVisibleItemIndex == 0 && lista.firstVisibleItemScrollOffset == 0
                    moverCabecera(if (arriba || desfase > -alto / 2) 0f else -alto)
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    LaunchedEffect(buscado) { moverCabecera(0f) }
    LaunchedEffect(enfocado) { if (enfocado) moverCabecera(0f) }

    // Tocar «Inicio» otra vez: si no estás arriba, sube; si ya estás arriba, vuelve a la portada (recomendados).
    val toqueAlEntrar = remember { toqueInicio }
    LaunchedEffect(toqueInicio) {
        if (toqueInicio == toqueAlEntrar) return@LaunchedEffect
        val arriba = lista.firstVisibleItemIndex == 0 && lista.firstVisibleItemScrollOffset == 0
        if (!arriba) {
            moverCabecera(0f)
            if (lista.firstVisibleItemIndex > 12) lista.scrollToItem(6)
            lista.animateScrollToItem(0)
        } else if (buscado != null || consulta.isNotEmpty() || enfocado) {
            trabajo?.cancel(); foco.clearFocus()
            consulta = ""; buscado = null; error = null; resultados.clear(); siguiente = null; cargando = false
            moverCabecera(0f)
        }
    }

    // Filtros en un menú aparte, para que la cabecera quede limpia.
    var verFiltros by remember { mutableStateOf(false) }
    val conFiltros = filtro != FiltroBusqueda.TODO || duracion != FiltroDuracion.CUALQUIERA
    if (verFiltros) com.santyramos.mirador.ui.HojaMirador(onCerrar = { verFiltros = false }) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filtrar", style = MaterialTheme.typography.titleLarge)
            Text("Mostrar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FiltroBusqueda.entries.forEach { f -> Filtro(f.etiqueta, filtro == f) { filtro = f } }
            }
            if (filtro == FiltroBusqueda.TODO || filtro == FiltroBusqueda.VIDEOS) {
                Text("Duración", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FiltroDuracion.entries.forEach { d -> Filtro(d.etiqueta, duracion == d) { duracion = d } }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (conFiltros) BotonSecundario("Quitar filtros", { filtro = FiltroBusqueda.TODO; duracion = FiltroDuracion.CUALQUIERA }, Modifier.weight(1f))
                com.santyramos.mirador.ui.BotonPrimario("Listo", { verFiltros = false }, Modifier.weight(1f))
            }
        }
    }

    Column(modifier.fillMaxSize().statusBarsPadding().nestedScroll(conexionScroll)) {
      // La cabecera se dibuja corrida hacia arriba según «desfase»; solo cambia la posición, no se recompone.
      Box(
        Modifier.fillMaxWidth().clipToBounds().layout { medible, limites ->
            val p = medible.measure(limites.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
            val d = desfase.roundToInt().coerceIn(-p.height, 0)
            layout(p.width, p.height + d) { p.placeRelative(0, d) }
        },
      ) {
       Column(Modifier.onSizeChanged { altoCabecera = it.height }.graphicsLayer {
           alpha = if (altoCabecera == 0) 1f else (1f + desfase / altoCabecera * 1.4f).coerceIn(0f, 1f)
       }) {
        // ---- Marca + atajo para pegar ----
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_mark), contentDescription = null, modifier = Modifier.size(30.dp))
            Text(com.santyramos.mirador.util.Marca.NOMBRE.lowercase(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 10.dp).weight(1f))
            BotonIcono(Icons.Outlined.Public, "Navegador de ${com.santyramos.mirador.util.Marca.NOMBRE}", { onAbrirNavegador(null) }, bordeado = true)
            BotonIcono(Icons.Outlined.ContentPaste, "Pegar enlaces para descargar", onIrADescargas, Modifier.padding(start = 8.dp), bordeado = true)
        }
        // ---- Aviso del modo sin conexión ----
        if (modoSinConexion) {
            androidx.compose.material3.Surface(
                onClick = onSinConexion, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = Paleta.AcentoSuave,
                border = androidx.compose.foundation.BorderStroke(1.dp, Paleta.AcentoBorde),
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Modo sin conexión activado", style = MaterialTheme.typography.titleSmall)
                        Text("Toca para ver y reproducir lo descargado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // ---- Buscador + Filtrar ----
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = consulta, onValueChange = { consulta = it },
                modifier = Modifier.weight(1f).focusRequester(pedirFoco).onFocusChanged { enfocado = it.isFocused }.semantics { contentDescription = "Buscar en YouTube" },
                placeholder = { Text("Buscar videos, canales o listas…", color = Paleta.Texto3, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
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
            Box(Modifier.padding(start = 8.dp)) {
                BotonIcono(Icons.Outlined.Tune, if (conFiltros) "Filtrar (hay filtros activos)" else "Filtrar", { foco.clearFocus(); verFiltros = true }, bordeado = true, destacado = conFiltros)
                if (conFiltros) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Acento))
            }
        }
        // Filtros activos: una línea discreta para recordar qué se está filtrando.
        if (conFiltros) {
            val partes = listOfNotNull(filtro.takeIf { it != FiltroBusqueda.TODO }?.etiqueta, duracion.takeIf { it != FiltroDuracion.CUALQUIERA }?.etiqueta)
            Text(
                "Filtrando: " + partes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 20.dp, bottom = 6.dp),
            )
        }
       }
      }
        Box(Modifier.fillMaxSize()) {
            val visibles = resultados.filter { duracion.acepta(it) }
            when {
                enfocado && sugerencias.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    listaSugerencias(
                        sugerencias,
                        onElegir = { consulta = it.texto; buscar() },
                        onCompletar = { consulta = it.texto + " " },
                        onQuitar = { s -> scope.launch { ajustesHome.quitarBusqueda(s.texto) } },
                        onBorrarTodo = { scope.launch { ajustesHome.borrarBusquedas() } },
                    )
                }
                error != null -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    BotonSecundario("Reintentar", { buscar() })
                }
                buscado == null && !cargando -> Portada(lista, onAbrirNavegador, { pedirFoco.requestFocus() }, onAbrirVideo, onDescargar)
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
/** Transmisiones en vivo populares (se conservan al cambiar de pestaña; se renuevan cada 10 min). */
private object EnVivoCache {
    var datos: List<Elemento.Video>? = null
    var hora = 0L
}

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
    Plataforma("Spotify", R.drawable.ic_plat_spotify, "https://open.spotify.com"),
    Plataforma("Pinterest", R.drawable.ic_plat_pinterest, "https://www.pinterest.com"),
    Plataforma("Otro sitio", null, ""),
)

/** Inicio sin búsqueda: banda de plataformas, tendencias (si YouTube las ofrece) y recomendaciones según tu uso. */
@Composable
private fun Portada(
    estado: androidx.compose.foundation.lazy.LazyListState, onAbrirNavegador: (String?) -> Unit, onBuscarYoutube: () -> Unit,
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
    var enVivo by remember { mutableStateOf(EnVivoCache.datos) }
    LaunchedEffect(Unit) {
        if (EnVivoCache.datos == null || System.currentTimeMillis() - EnVivoCache.hora > 10 * 60_000) {
            val r = runCatching { Youtube.buscar("en vivo", com.santyramos.mirador.extractor.FiltroBusqueda.EN_VIVO).elementos.filterIsInstance<Elemento.Video>() }.getOrNull()
            if (r != null) { EnVivoCache.datos = r; EnVivoCache.hora = System.currentTimeMillis(); enVivo = r }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = estado, contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
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
        if (!enVivo.isNullOrEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFD32F2F)))
                Text("En vivo ahora", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp))
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(enVivo!!, key = { "v" + it.url }) { v -> TarjetaEnVivo(v) { onAbrirVideo(v.url) } }
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

/** Cada sitio es una «ventana» en arco, como las de un mirador: el único gesto decorativo de la pantalla. */
@Composable
private fun BotonPlataforma(p: Plataforma, onClick: () -> Unit) {
    val arco = RoundedCornerShape(topStart = 29.dp, topEnd = 29.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
    Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Surface(
            onClick = onClick, shape = arco, color = Paleta.S2,
            border = androidx.compose.foundation.BorderStroke(1.dp, Paleta.Linea), modifier = Modifier.size(width = 58.dp, height = 72.dp),
        ) {
            Box(Modifier.padding(top = 6.dp), contentAlignment = Alignment.Center) {
                if (p.icono != null) Icon(painterResource(p.icono), p.nombre, tint = Acento, modifier = Modifier.size(26.dp))
                else Icon(Icons.Outlined.Public, p.nombre, tint = Acento, modifier = Modifier.size(26.dp))
            }
        }
        Text(p.nombre, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Tarjeta compacta de una transmisión en vivo para la fila «En vivo ahora». */
@Composable
private fun TarjetaEnVivo(v: Elemento.Video, onClick: () -> Unit) {
    Column(Modifier.width(248.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Ver en vivo", onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Paleta.S2)) {
            coil3.compose.AsyncImage(model = v.miniatura, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text(
                "EN VIVO", style = MaterialTheme.typography.labelSmall, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xE6D32F2F)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(v.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (!v.autor.isNullOrBlank()) Text(v.autor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
