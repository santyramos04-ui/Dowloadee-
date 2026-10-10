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
    onAbrirNavegador: () -> Unit = {},
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
        // ---- Marca + atajo para pegar ----
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_mark), contentDescription = null, modifier = Modifier.size(30.dp))
            Text("mirador", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 10.dp).weight(1f))
            BotonIcono(Icons.Outlined.Public, "Navegador de Mirador", onAbrirNavegador, bordeado = true)
            BotonIcono(Icons.Outlined.ContentPaste, "Pegar enlaces para descargar", onIrADescargas, Modifier.padding(start = 8.dp), bordeado = true)
        }
        // ---- Buscador ----
        OutlinedTextField(
            value = consulta, onValueChange = { consulta = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
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
        if (filtro == FiltroBusqueda.TODO || filtro == FiltroBusqueda.VIDEOS) {
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
                buscado == null && !cargando -> Portada(onIrADescargas, onAbrirNavegador, onAbrirVideo, onDescargar)
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

/** Pantalla de bienvenida: motivo de la marca, atajos y, si YouTube las ofrece, las tendencias de Colombia. */
@Composable
private fun Portada(onIrADescargas: () -> Unit, onAbrirNavegador: () -> Unit, onAbrirVideo: (String) -> Unit, onDescargar: (String) -> Unit) {
    var tendencias by remember { mutableStateOf(TendenciasCache.datos) }
    var cargando by remember { mutableStateOf(!TendenciasCache.intentado) }
    LaunchedEffect(Unit) {
        if (!TendenciasCache.intentado) {
            tendencias = Youtube.tendencias()
            TendenciasCache.datos = tendencias; TendenciasCache.intentado = true; cargando = false
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 28.dp).padding(top = 28.dp)) {
                Arcos(Modifier.padding(bottom = 26.dp))
                Text("Mira y descarga,", style = MaterialTheme.typography.displaySmall)
                Text("sin ruido.", style = MaterialTheme.typography.displaySmall, color = Acento)
                Text(
                    "YouTube sin anuncios y sin cuenta de Google. Descarga videos, audio y fotos de cualquier sitio.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, end = 24.dp),
                )
                Spacer(Modifier.height(26.dp))
                Atajo(Icons.Outlined.FileDownload, "Pega uno o varios enlaces", "En la pestaña Descargas", onIrADescargas)
                Spacer(Modifier.height(10.dp))
                Atajo(Icons.Outlined.Public, "Navegador de Mirador", "Entra a X, Instagram o TikTok y descarga con un toque", onAbrirNavegador)
                Spacer(Modifier.height(10.dp))
                Atajo(Icons.Outlined.Share, "Compártelos desde otra app", "Compartir → Mirador, sin salir de X o Instagram", null)
            }
        }
        if (!tendencias.isNullOrEmpty()) {
            item { Text("Tendencias en Colombia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 6.dp)) }
            items(tendencias!!, key = { it.url }) { v -> TarjetaVideo(v, onClick = { onAbrirVideo(v.url) }, onDescargar = { onDescargar(v.url) }) }
        } else if (cargando) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Acento, modifier = Modifier.size(28.dp)) } }
        }
    }
}

@Composable
private fun Atajo(icono: ImageVector, titulo: String, detalle: String, onClick: (() -> Unit)?) {
    androidx.compose.material3.Surface(
        onClick = { onClick?.invoke() }, enabled = onClick != null, shape = RoundedCornerShape(18.dp), color = Paleta.S1,
        border = androidx.compose.foundation.BorderStroke(1.dp, Paleta.Linea), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Paleta.AcentoSuave), contentAlignment = Alignment.Center) {
                Icon(icono, null, tint = Acento, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Tres arcos de ventana dibujados con líneas finas (el central, en verde). */
@Composable
private fun Arcos(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 190.dp, height = 104.dp)) {
        val trazo = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        fun arco(x: Float, ancho: Float, alto: Float, color: Color) {
            val r = ancho / 2
            val p = Path().apply {
                moveTo(x, size.height)
                lineTo(x, size.height - alto + r)
                arcTo(androidx.compose.ui.geometry.Rect(x, size.height - alto, x + ancho, size.height - alto + ancho), 180f, 180f, false)
                lineTo(x + ancho, size.height)
            }
            drawPath(p, color, style = trazo)
        }
        val ancho = 54.dp.toPx()
        val sep = 10.dp.toPx()
        arco(0f, ancho, 76.dp.toPx(), Paleta.Linea)
        arco(ancho + sep, ancho, 104.dp.toPx(), Acento.copy(alpha = 0.55f))
        arco((ancho + sep) * 2, ancho, 76.dp.toPx(), Paleta.Linea)
    }
}
