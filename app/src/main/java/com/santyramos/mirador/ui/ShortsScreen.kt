package com.santyramos.mirador.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.FiltroBusqueda
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.player.Repetir
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.theme.Acento
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

/** Los Shorts que se están viendo (sobreviven a girar la pantalla o abrir otra cosa). */
object Shorts {
    var abierto by mutableStateOf(false)
    var inicio = 0
    val lista = mutableStateListOf<Elemento.Video>()
    var consulta = "shorts"
    var pagina: Page? = null
    var cargando by mutableStateOf(false)

    fun abrir(videos: List<Elemento.Video>, desde: Int, consulta: String, pagina: Page?) {
        lista.clear(); lista.addAll(videos.distinctBy { it.url })
        inicio = desde.coerceIn(0, (lista.size - 1).coerceAtLeast(0))
        this.consulta = consulta; this.pagina = pagina
        abierto = true
    }

    /** Solo lo vertical: Shorts de verdad o videos de un minuto o menos. */
    fun soloCortos(l: List<Elemento>): List<Elemento.Video> =
        l.filterIsInstance<Elemento.Video>().filter { !it.esDirecto && (it.esShort || it.duracionSeg in 1..60) }

    suspend fun cargarMas() {
        val p = pagina ?: return
        if (cargando) return
        cargando = true
        try {
            val r = Youtube.masBusqueda(consulta, FiltroBusqueda.VIDEOS, p)
            pagina = r.siguiente
            val vistos = lista.map { it.url }.toSet()
            lista.addAll(soloCortos(r.elementos).filter { it.url !in vistos })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { pagina = null } finally { cargando = false }
    }
}

/**
 * Shorts a pantalla completa, uno por pantalla: se desliza hacia arriba para el siguiente.
 * Cada Short se repite mientras lo miras; al salir se pausa y se deja la repetición como estaba.
 */
@Composable
fun ShortsScreen(
    onCerrar: () -> Unit,
    onDescargar: (String) -> Unit,
    onAbrirCanal: (String) -> Unit,
    onVerCompleto: (String) -> Unit,
) {
    BackHandler(onBack = onCerrar)
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val lista = Shorts.lista
    val pager = rememberPagerState(initialPage = Shorts.inicio) { lista.size }
    val estado by VideoController.estado.collectAsState()
    val video by VideoController.video.collectAsState()

    DisposableEffect(Unit) {
        val antes = VideoController.repetir.value
        VideoController.establecerRepetir(Repetir.UNA)
        onDispose {
            VideoController.player.pause()
            VideoController.establecerRepetir(antes)
        }
    }
    // Al quedarse quieto en un Short, empieza a sonar. Cerca del final se traen más.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { i ->
            Shorts.inicio = i
            lista.getOrNull(i)?.let { v ->
                VideoController.abrir(v.url, previo = com.santyramos.mirador.player.ElementoCola(v.url, v.titulo, v.autor, v.miniatura, v.duracionSeg))
            }
            if (i >= lista.size - 3) scope.launch { Shorts.cargarMas() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { lista.getOrNull(it)?.url ?: it }, beyondViewportPageCount = 1) { i ->
            val v = lista.getOrNull(i) ?: return@VerticalPager
            val actual = i == pager.settledPage && video?.urlOriginal == v.url
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClickLabel = "Pausar o seguir",
                ) { if (actual) VideoController.alternar() },
            ) {
                // Mientras carga se ve la miniatura; luego el video encima.
                coil3.compose.AsyncImage(model = v.miniatura, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                if (actual && video?.listo == true) AndroidView(
                    factory = { c ->
                        (android.view.LayoutInflater.from(c).inflate(com.santyramos.mirador.R.layout.vista_video, null) as PlayerView).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = VideoController.player
                        }
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
                if (actual && (video?.cargando == true || estado.almacenando)) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center).size(44.dp), color = Acento)
                }
                if (actual && video?.listo == true && !estado.reproduciendo && !estado.almacenando) {
                    Icon(
                        Icons.Filled.PlayArrow, null, tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.Center).size(72.dp).background(Color(0x66000000), CircleShape).padding(8.dp),
                    )
                }
                if (actual) video?.error?.let { msg ->
                    Text(msg, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }

                // Abajo: datos del Short y acciones, sobre un degradado para que se lea.
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .navigationBarsPadding().padding(start = 16.dp, end = 8.dp, top = 48.dp, bottom = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            if (!v.autor.isNullOrBlank()) Text(
                                v.autor, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.then(if (v.urlAutor != null) Modifier.clickable(onClickLabel = "Abrir el canal") { onAbrirCanal(v.urlAutor) } else Modifier).padding(vertical = 6.dp),
                            )
                            Text(v.titulo, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            AccionShort(Icons.Outlined.Download, "Descargar") { onDescargar(v.url) }
                            AccionShort(Icons.Outlined.Share, "Compartir") {
                                val i2 = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, v.url)
                                runCatching { contexto.startActivity(android.content.Intent.createChooser(i2, "Compartir")) }
                            }
                            AccionShort(Icons.Outlined.OpenInFull, "Ver en el reproductor") { onVerCompleto(v.url) }
                        }
                    }
                }
                if (actual && estado.duracionMs > 0) LinearProgressIndicator(
                    progress = { (estado.posicionMs.toFloat() / estado.duracionMs).coerceIn(0f, 1f) },
                    color = Color.White, trackColor = Color(0x33FFFFFF),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().height(2.dp),
                )
            }
        }
        if (lista.isEmpty()) Text("No hay Shorts para mostrar ahora.", color = Color.White, modifier = Modifier.align(Alignment.Center))
        IconButton(onClick = onCerrar, modifier = Modifier.statusBarsPadding().padding(4.dp).align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Salir de Shorts", tint = Color.White)
        }
        Text(
            "Shorts", color = Color.White, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.statusBarsPadding().padding(top = 16.dp).align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun AccionShort(icono: ImageVector, texto: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClickLabel = texto, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icono, texto, tint = Color.White, modifier = Modifier.size(48.dp).background(Color(0x55000000), CircleShape).padding(12.dp))
        Text(texto.substringBefore(" "), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
