package com.santyramos.mirador.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.data.Settings
import com.santyramos.mirador.player.ElementoCola
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.servidor.Encontrado
import com.santyramos.mirador.servidor.ItemJf
import com.santyramos.mirador.servidor.Jellyfin
import com.santyramos.mirador.servidor.Seguimiento
import com.santyramos.mirador.servidor.ServidorJf
import com.santyramos.mirador.ui.BotonIcono
import com.santyramos.mirador.ui.BotonPrimario
import com.santyramos.mirador.ui.BotonSecundario
import com.santyramos.mirador.ui.EstadoVacio
import com.santyramos.mirador.ui.Filtro
import com.santyramos.mirador.ui.HojaMirador
import com.santyramos.mirador.ui.theme.Acento
import com.santyramos.mirador.ui.theme.Paleta
import com.santyramos.mirador.util.Format
import kotlinx.coroutines.launch

/** Guía paso a paso para montar el servidor en la mini PC. */
private const val GUIA = "https://github.com/santyramos04-ui/Dowloadee-/blob/main/docs/servidor-mini-pc.md"

private sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object Sin : EstadoSesion
    data class Con(val s: ServidorJf) : EstadoSesion
}

/** Elemento de la cola para algo de tu servidor: el original y, de respaldo, la versión convertida. */
private fun ItemJf.aCola(s: ServidorJf, ahorro: Boolean, desdeInicio: Boolean = false): ElementoCola {
    val convertida = Jellyfin.urlConvertida(s, id, maxBitrate = if (ahorro) 3_000_000 else 20_000_000, altoMax = if (ahorro) 720 else null)
    return ElementoCola(
        url = Jellyfin.pagina(s, id),
        titulo = if (tipo == "Episode" && serie != null) "$serie · ${etiquetaEpisodio ?: ""} $nombre".replace("  ", " ") else nombre,
        autor = listOfNotNull(anio?.toString(), s.nombre).joinToString(" · "),
        miniatura = Jellyfin.imagenAncha(s, this),
        duracionSeg = duracionMs / 1000,
        archivoUri = if (ahorro) convertida else Jellyfin.urlOriginal(s, id),
        archivoMime = if (ahorro) androidx.media3.common.MimeTypes.APPLICATION_M3U8 else null,
        origen = "Tu servidor · ${s.nombre}",
        inicioMs = if (desdeInicio || visto) 1 else posicionMs,
        respaldoUri = if (ahorro) null else convertida,
    )
}

/**
 * «Mi servidor»: tus películas, series y videos personales desde Jellyfin en tu mini PC, al estilo Netflix.
 * [consulta] es lo que se escribió en el buscador de Películas.
 */
@Composable
fun MiServidor(consulta: String, onAbrirReproductor: () -> Unit) {
    val contexto = LocalContext.current
    val ajustes = remember { Settings(contexto) }
    val sesion by produceState<EstadoSesion>(EstadoSesion.Cargando) {
        ajustes.servidor.collect { value = if (it == null) EstadoSesion.Sin else EstadoSesion.Con(it) }
    }
    when (val e = sesion) {
        EstadoSesion.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Acento) }
        EstadoSesion.Sin -> Conectar(ajustes)
        is EstadoSesion.Con -> Catalogo(e.s, ajustes, consulta, onAbrirReproductor)
    }
}

// ---------------------------------------------------------------------------------- Conectar

@Composable
private fun Conectar(ajustes: Settings) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var direccion by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }
    var encontrados by remember { mutableStateOf<List<Encontrado>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val colores = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Paleta.S2, unfocusedContainerColor = Paleta.S2,
        focusedBorderColor = Acento, unfocusedBorderColor = Paleta.Linea, cursorColor = Acento,
    )
    fun buscarEnWifi() {
        buscando = true; error = null
        scope.launch {
            val r = Jellyfin.descubrir()
            encontrados = r; buscando = false
            if (r.size == 1) direccion = r.first().direccion
        }
    }
    LaunchedEffect(Unit) { buscarEnWifi() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EstadoVacio(Icons.Outlined.Dns, "Tu propio Netflix", "Mira tus películas, series y videos personales guardados en tu mini PC, en calidad original.")
        Surface(shape = RoundedCornerShape(16.dp), color = Paleta.S2) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Wifi, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        when {
                            buscando -> "Buscando tu servidor en el Wi‑Fi…"
                            encontrados.isNullOrEmpty() -> "No encontré un servidor en este Wi‑Fi"
                            else -> "Encontré en tu Wi‑Fi:"
                        },
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    if (buscando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Acento)
                    else TextButton(onClick = { buscarEnWifi() }) { Text("Buscar otra vez", color = Acento) }
                }
                encontrados.orEmpty().forEach { s ->
                    Filtro("${s.nombre} · ${s.direccion.removePrefix("http://")}", direccion == s.direccion) { direccion = s.direccion }
                }
                if (!buscando && encontrados.isNullOrEmpty()) Text(
                    "Fuera de casa, escribe la dirección de Tailscale de la mini PC (por ejemplo 100.101.102.103).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = direccion, onValueChange = { direccion = it }, singleLine = true, label = { Text("Dirección del servidor") },
            placeholder = { Text("192.168.1.20", color = Paleta.Texto3) }, shape = RoundedCornerShape(16.dp), colors = colores,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = usuario, onValueChange = { usuario = it }, singleLine = true, label = { Text("Usuario de Jellyfin") },
            shape = RoundedCornerShape(16.dp), colors = colores, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = clave, onValueChange = { clave = it }, singleLine = true, label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(16.dp), colors = colores, modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        BotonPrimario(if (trabajando) "Conectando…" else "Conectar", {
            trabajando = true; error = null
            scope.launch {
                runCatching { Jellyfin.entrar(direccion, usuario, clave, ajustes.dispositivo()) }
                    .onSuccess { ajustes.setServidor(it) }
                    .onFailure { error = it.message ?: "No pude conectarme." }
                trabajando = false
            }
        }, Modifier.fillMaxWidth(), enabled = !trabajando && direccion.isNotBlank() && usuario.isNotBlank())
        TextButton(onClick = {
            runCatching { contexto.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(GUIA))) }
        }) { Text("¿Aún no tienes el servidor? Ver la guía para la mini PC", color = Acento) }
    }
}

// ---------------------------------------------------------------------------------- Catálogo

private class Fila(val biblioteca: ItemJf, val items: List<ItemJf>)

@Composable
private fun Catalogo(s: ServidorJf, ajustes: Settings, consulta: String, onAbrirReproductor: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ahorro by ajustes.servidorAhorro.collectAsState(initial = false)
    var continuar by remember { mutableStateOf<List<ItemJf>>(emptyList()) }
    val filas = remember { mutableStateListOf<Fila>() }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultados by remember { mutableStateOf<List<ItemJf>?>(null) }
    val pila = remember { mutableStateListOf<ItemJf>() } // carpetas abiertas
    var elegido by remember { mutableStateOf<ItemJf?>(null) }
    var serie by remember { mutableStateOf<ItemJf?>(null) }
    var recarga by remember { mutableStateOf(0) }

    LaunchedEffect(s, recarga) {
        cargando = true; error = null
        try {
            continuar = runCatching { Jellyfin.continuar(s) }.getOrDefault(emptyList())
            val libs = Jellyfin.bibliotecas(s)
            filas.clear()
            libs.forEach { b -> filas.add(Fila(b, runCatching { Jellyfin.recientes(s, b.id) }.getOrDefault(emptyList()))) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.message ?: "No pude conectarme con tu servidor."
        } finally { cargando = false }
    }
    LaunchedEffect(consulta) {
        resultados = if (consulta.isBlank()) null else runCatching { Jellyfin.buscar(s, consulta) }.getOrDefault(emptyList())
    }

    fun abrir(i: ItemJf) {
        when {
            i.esSerie -> serie = i
            i.esCarpeta -> pila.add(i)
            else -> elegido = i
        }
    }
    fun reproducir(i: ItemJf, desdeInicio: Boolean = false) {
        Seguimiento.usar(s)
        VideoController.abrirArchivo(i.aCola(s, ahorro, desdeInicio))
        onAbrirReproductor()
    }

    BackHandler(enabled = pila.isNotEmpty()) { pila.removeAt(pila.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        // Barra: nombre del servidor, ahorro de datos y menú.
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pila.isNotEmpty()) BotonIcono(Icons.AutoMirrored.Filled.ArrowBack, "Volver", { pila.removeAt(pila.lastIndex) })
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(pila.lastOrNull()?.nombre ?: s.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (ahorro) "Ahorro de datos (720p)" else "Calidad original", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                var menu by remember { mutableStateOf(false) }
                BotonIcono(Icons.Outlined.MoreVert, "Opciones del servidor", { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (ahorro) "Usar calidad original" else "Ahorro de datos (720p)") },
                        onClick = { menu = false; scope.launch { ajustes.setServidorAhorro(!ahorro) } },
                    )
                    DropdownMenuItem(text = { Text("Actualizar") }, onClick = { menu = false; recarga++ })
                    DropdownMenuItem(text = { Text("Desconectar (${s.usuario})") }, onClick = { menu = false; scope.launch { ajustes.setServidor(null) } })
                }
            }
        }

        val carpeta = pila.lastOrNull()
        when {
            resultados != null -> Cuadricula(s, resultados.orEmpty(), vacio = "No encontré «$consulta» en tu servidor.", onAbrir = ::abrir)
            carpeta != null -> Carpeta(s, carpeta, onAbrir = ::abrir)
            cargando && filas.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Acento) }
            error != null && filas.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Text("¿Estás en el Wi‑Fi de la casa o con Tailscale encendido?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BotonSecundario("Reintentar", { recarga++ })
                }
            }
            filas.all { it.items.isEmpty() } && continuar.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(Icons.Outlined.Movie, "Tu servidor está vacío", "Agrega películas o videos a una biblioteca de Jellyfin en la mini PC.")
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                val destacado = continuar.firstOrNull() ?: filas.firstNotNullOfOrNull { it.items.firstOrNull() }
                if (destacado != null) item { Destacado(s, destacado, onVer = { if (destacado.reproducible) reproducir(destacado) else abrir(destacado) }, onInfo = { abrir(destacado) }) }
                if (continuar.isNotEmpty()) item {
                    TituloFila("Continuar viendo")
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continuar, key = { it.id }) { i -> TarjetaAncha(s, i, onClick = { reproducir(i) }) }
                    }
                }
                filas.filter { it.items.isNotEmpty() }.forEach { f ->
                    item(key = f.biblioteca.id) {
                        TituloFila("Nuevo en ${f.biblioteca.nombre}", accion = "Ver todo") { pila.add(f.biblioteca) }
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(f.items, key = { it.id }) { i ->
                                if (f.biblioteca.tipoColeccion == "homevideos") TarjetaAncha(s, i, onClick = { abrir(i) })
                                else TarjetaPoster(s, i, Modifier.width(120.dp), onClick = { abrir(i) })
                            }
                        }
                    }
                }
            }
        }
    }

    elegido?.let { i ->
        HojaMirador(onCerrar = { elegido = null }) {
            DetalleItem(s, i,
                onReproducir = { elegido = null; reproducir(i) },
                onDesdeInicio = { elegido = null; reproducir(i, desdeInicio = true) },
                onCola = { elegido = null; Seguimiento.usar(s); VideoController.agregarACola(i.aCola(s, ahorro)) },
            )
        }
    }
    serie?.let { sr ->
        HojaMirador(onCerrar = { serie = null }) {
            DetalleSerie(s, sr) { episodios, indice ->
                serie = null
                Seguimiento.usar(s)
                // Como Netflix: los episodios siguientes quedan en la cola y se reproducen solos.
                VideoController.reproducirLista(episodios.map { it.aCola(s, ahorro) }, indice)
                onAbrirReproductor()
            }
        }
    }
}

@Composable
private fun TituloFila(titulo: String, accion: String? = null, onAccion: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (accion != null) TextButton(onClick = onAccion) { Text(accion, color = Acento) }
    }
}

/** El gran cartel de arriba, con la imagen de fondo. */
@Composable
private fun Destacado(s: ServidorJf, i: ItemJf, onVer: () -> Unit, onInfo: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp)).background(Paleta.S2).clickable(onClickLabel = "Ver detalles", onClick = onInfo)) {
        AsyncImage(
            model = if (i.tipo == "Episode") Jellyfin.imagenAncha(s, i) else Jellyfin.fondo(s, i.serieId ?: i.id),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(i.serie ?: i.nombre, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detalle = listOfNotNull(i.etiquetaEpisodio, i.anio?.toString(), i.duracionMs.takeIf { it > 0 }?.let { Format.duracion(it / 1000) }).joinToString(" · ")
            if (detalle.isNotEmpty()) Text(detalle, style = MaterialTheme.typography.bodySmall, color = Color(0xCCFFFFFF))
            BotonPrimario(if (i.posicionMs > 0) "Continuar" else "Ver ahora", onVer, icono = Icons.Outlined.PlayArrow)
        }
    }
}

@Composable
private fun TarjetaPoster(s: ServidorJf, i: ItemJf, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Abrir", onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Paleta.S2), contentAlignment = Alignment.Center) {
            Icon(if (i.esCarpeta) Icons.Outlined.Folder else Icons.Outlined.Movie, null, tint = Paleta.Texto3)
            AsyncImage(model = Jellyfin.portada(s, i.id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            if (i.avance > 0f) LinearProgressIndicator(
                progress = { i.avance }, gapSize = 0.dp, drawStopIndicator = {}, color = Paleta.Relleno, trackColor = Color(0x66000000),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
            )
        }
        Text(i.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (i.anio != null) Text(i.anio.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tarjeta horizontal (para lo que estás viendo y videos personales), con la barrita de avance. */
@Composable
private fun TarjetaAncha(s: ServidorJf, i: ItemJf, onClick: () -> Unit) {
    Column(Modifier.width(232.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Reproducir", onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Paleta.S2), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Movie, null, tint = Paleta.Texto3)
            AsyncImage(model = Jellyfin.imagenAncha(s, i), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            if (i.avance > 0f) LinearProgressIndicator(
                progress = { i.avance }, gapSize = 0.dp, drawStopIndicator = {}, color = Paleta.Relleno, trackColor = Color(0x66000000),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
            )
        }
        Text(i.serie ?: i.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val sub = if (i.tipo == "Episode") listOfNotNull(i.etiquetaEpisodio, i.nombre).joinToString(" · ")
        else if (i.posicionMs > 0 && i.duracionMs > 0) "Quedan ${Format.duracion((i.duracionMs - i.posicionMs) / 1000)}" else i.anio?.toString()
        if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Cuadricula(s: ServidorJf, items: List<ItemJf>, vacio: String, onAbrir: (ItemJf) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EstadoVacio(Icons.Outlined.Movie, "Nada por aquí", vacio) }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items, key = { it.id }) { i -> TarjetaPoster(s, i, onClick = { onAbrir(i) }) }
    }
}

/** Todo lo de una biblioteca o carpeta. */
@Composable
private fun Carpeta(s: ServidorJf, carpeta: ItemJf, onAbrir: (ItemJf) -> Unit) {
    val items by produceState<List<ItemJf>?>(null, carpeta.id) { value = runCatching { Jellyfin.contenido(s, carpeta.id) }.getOrDefault(emptyList()) }
    val lista = items
    if (lista == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Acento) }
    else Cuadricula(s, lista, vacio = "Esta carpeta está vacía.", onAbrir = onAbrir)
}

@Composable
private fun DetalleItem(s: ServidorJf, i: ItemJf, onReproducir: () -> Unit, onDesdeInicio: () -> Unit, onCola: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Paleta.S3)) {
            AsyncImage(model = Jellyfin.imagenAncha(s, i), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (i.serie != null) "${i.serie} · ${i.nombre}" else i.nombre, style = MaterialTheme.typography.titleLarge)
            val datos = listOfNotNull(i.etiquetaEpisodio, i.anio?.toString(), i.duracionMs.takeIf { it > 0 }?.let { Format.duracion(it / 1000) }, if (i.visto) "Visto" else null)
            if (datos.isNotEmpty()) Text(datos.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (i.avance > 0f) LinearProgressIndicator(progress = { i.avance }, gapSize = 0.dp, drawStopIndicator = {}, color = Paleta.Relleno, trackColor = Paleta.S3, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
            if (!i.sinopsis.isNullOrBlank()) {
                var verTodo by remember { mutableStateOf(false) }
                Text(
                    i.sinopsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (verTodo) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClickLabel = if (verTodo) "Ver menos" else "Ver más") { verTodo = !verTodo },
                )
            }
            val retomar = i.posicionMs > 0 && !i.visto
            BotonPrimario(if (retomar) "Continuar desde ${Format.duracion(i.posicionMs / 1000)}" else "Reproducir", onReproducir, Modifier.fillMaxWidth(), icono = Icons.Outlined.PlayArrow)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (retomar) BotonSecundario("Desde el inicio", onDesdeInicio, Modifier.weight(1f), icono = Icons.Outlined.Replay)
                BotonSecundario("A la cola", onCola, Modifier.weight(1f), icono = Icons.AutoMirrored.Outlined.QueueMusic)
            }
        }
    }
}

/** Una serie: temporadas y episodios. Al tocar uno, suena y los siguientes quedan en la cola. */
@Composable
private fun DetalleSerie(s: ServidorJf, serie: ItemJf, onReproducir: (List<ItemJf>, Int) -> Unit) {
    val episodios by produceState<List<ItemJf>?>(null, serie.id) { value = runCatching { Jellyfin.episodios(s, serie.id) }.getOrDefault(emptyList()) }
    val todos = episodios
    val temporadas = remember(todos) { todos.orEmpty().map { it.temporada ?: 0 }.distinct().sorted() }
    // Empieza en la temporada del primer episodio sin terminar.
    var temporada by remember(todos) { mutableStateOf(todos.orEmpty().firstOrNull { !it.visto }?.temporada ?: temporadas.firstOrNull() ?: 0) }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Paleta.S3)) {
            AsyncImage(model = Jellyfin.fondo(s, serie.id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(serie.nombre, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        if (!serie.sinopsis.isNullOrBlank()) Text(serie.sinopsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
        if (todos == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Acento) }
        else {
            val siguiente = todos.indexOfFirst { !it.visto }.takeIf { it >= 0 } ?: 0
            if (todos.isNotEmpty()) BotonPrimario(
                todos[siguiente].let { e -> (if (e.posicionMs > 0) "Continuar " else "Ver ") + (e.etiquetaEpisodio ?: e.nombre) },
                { onReproducir(todos, siguiente) }, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), icono = Icons.Outlined.PlayArrow,
            )
            if (temporadas.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(temporadas) { t -> Filtro(if (t == 0) "Especiales" else "Temporada $t", temporada == t) { temporada = t } }
            }
            LazyColumn(Modifier.fillMaxWidth().height(420.dp), contentPadding = PaddingValues(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val deTemporada = todos.withIndex().filter { (it.value.temporada ?: 0) == temporada }
                items(deTemporada, key = { it.value.id }) { (indice, e) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Reproducir") { onReproducir(todos, indice) }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Paleta.S2)) {
                            AsyncImage(model = Jellyfin.imagenAncha(s, e), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (e.avance > 0f) LinearProgressIndicator(progress = { e.avance }, gapSize = 0.dp, drawStopIndicator = {}, color = Paleta.Relleno, trackColor = Color(0x66000000), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(listOfNotNull(e.episodio?.let { "$it." }, e.nombre).joinToString(" "), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val d = listOfNotNull(e.duracionMs.takeIf { it > 0 }?.let { Format.duracion(it / 1000) }, if (e.visto) "Visto" else null).joinToString(" · ")
                            if (d.isNotEmpty()) Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
