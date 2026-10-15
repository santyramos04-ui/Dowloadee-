package com.santyramos.mirador.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.santyramos.mirador.ui.BotonIcono
import com.santyramos.mirador.ui.BotonPrimario
import com.santyramos.mirador.ui.Filtro
import com.santyramos.mirador.ui.theme.Acento
import com.santyramos.mirador.ui.theme.Paleta

private val Atajos = listOf(
    "X" to "https://x.com",
    "Instagram" to "https://www.instagram.com",
    "TikTok" to "https://www.tiktok.com",
    "Facebook" to "https://m.facebook.com",
    "YouTube" to "https://m.youtube.com",
)

/** Convierte lo que escribes en una dirección: si no parece un sitio, lo busca en DuckDuckGo. */
internal fun aDireccion(texto: String): String {
    val t = texto.trim()
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    if (!t.contains(' ') && t.contains('.')) return "https://$t"
    return "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
}

/**
 * Navegador interno: entras a X, Instagram, TikTok, Facebook… con tu propia sesión del sitio si quieres,
 * y el botón verde flotante manda la página actual a descargar.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NavegadorScreen(urlInicial: String?, onAtras: () -> Unit, onDescargar: (String) -> Unit) {
    val contexto = LocalContext.current
    val foco = LocalFocusManager.current
    var direccion by remember { mutableStateOf("") }
    var actual by remember { mutableStateOf<String?>(null) }
    var progreso by remember { mutableFloatStateOf(0f) }
    var puedeVolver by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ajustes = remember { com.santyramos.mirador.data.Settings(contexto) }
    val sitios by ajustes.sitios.collectAsState(initial = emptyList())
    var remotas by remember { mutableStateOf<List<String>>(emptyList()) }
    val web = remember {
        WebView(contexto).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = true
            // Los inicios de sesión de Instagram, X, etc. suelen necesitar cookies de terceros.
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }
    DisposableEffect(web) {
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Solo páginas web: se ignoran intent://, market://, etc. para que ninguna página abra otras apps sola.
                val esquema = request.url.scheme?.lowercase()
                return esquema != "http" && esquema != "https"
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { actual = url; if (!editando) direccion = url }
            override fun onPageFinished(view: WebView, url: String) {
                actual = url; puedeVolver = view.canGoBack(); if (!editando) direccion = url
                // Historial del navegador (solo en este celular).
                if (url.startsWith("http")) scope.launch { ajustes.agregarSitio(view.title.orEmpty(), url) }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progreso = newProgress / 100f }
        }
        onDispose { web.stopLoading(); web.destroy() }
    }
    androidx.compose.runtime.LaunchedEffect(urlInicial) { urlInicial?.let { web.loadUrl(it) } }
    BackHandler { if (editando) foco.clearFocus() else if (web.canGoBack()) web.goBack() else onAtras() }

    // Sugerencias: páginas que ya visitaste que coinciden y búsquedas que propone DuckDuckGo.
    val textoEscrito = if (editando && direccion != actual) direccion else ""
    androidx.compose.runtime.LaunchedEffect(textoEscrito) {
        if (textoEscrito.isBlank() || textoEscrito.startsWith("http")) { remotas = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(220)
        remotas = SugerenciasWeb.duckduckgo(textoEscrito)
    }
    val sugerencias = remember(textoEscrito, sitios, remotas) { SugerenciasWeb.combinar(textoEscrito, sitios, remotas) }
    fun ir(texto: String) { foco.clearFocus(); if (texto.isNotBlank()) web.loadUrl(aDireccion(texto)) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BotonIcono(Icons.AutoMirrored.Filled.ArrowBack, if (puedeVolver) "Atrás" else "Cerrar navegador", { if (web.canGoBack()) web.goBack() else onAtras() })
                OutlinedTextField(
                    value = direccion, onValueChange = { direccion = it },
                    modifier = Modifier.weight(1f).onFocusChanged { editando = it.isFocused }.semantics { contentDescription = "Dirección o búsqueda" },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Escribe una dirección o busca…", color = Paleta.Texto3) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Paleta.S2, unfocusedContainerColor = Paleta.S2,
                        focusedBorderColor = Acento, unfocusedBorderColor = Paleta.Linea, cursorColor = Acento,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onGo = { ir(direccion) }),
                )
                BotonIcono(if (progreso in 0.01f..0.99f) Icons.Outlined.Close else Icons.Outlined.Refresh, "Recargar", { if (progreso in 0.01f..0.99f) web.stopLoading() else web.reload() })
            }
            if (progreso in 0.01f..0.99f) LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth(), color = Acento, trackColor = Paleta.S3)
            else androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 1.dp))

            if (actual == null) {
                // Pantalla de inicio del navegador
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Navegador de ${com.santyramos.mirador.util.Marca.NOMBRE}", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Entra al sitio, abre la publicación que quieras y toca el botón verde «Descargar». Sirve para X, Instagram, TikTok, Facebook y más.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(Atajos) { (nombre, url) -> Filtro(nombre, false) { web.loadUrl(url) } }
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (actual != null) AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                if (editando && sugerencias.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        com.santyramos.mirador.ui.listaSugerencias(
                            sugerencias,
                            onElegir = { ir(it.url ?: it.texto) },
                            onCompletar = { direccion = (it.url ?: it.texto) },
                            onQuitar = { s -> scope.launch { s.url?.let { ajustes.quitarSitio(it) } } },
                            onBorrarTodo = { scope.launch { ajustes.borrarSitios() } },
                        )
                    }
                }
            }
        }

        val enlace = actual
        if (enlace != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
                color = Color.Transparent,
            ) {
                BotonPrimario("Descargar", { onDescargar(web.url ?: enlace) }, icono = Icons.Outlined.FileDownload)
            }
        }
    }
}

/** Sugerencias del navegador: historial de páginas + autocompletado de DuckDuckGo (el buscador que ya usa). */
internal object SugerenciasWeb {
    fun combinar(texto: String, sitios: List<com.santyramos.mirador.data.SitioVisitado>, remotas: List<String>): List<com.santyramos.mirador.ui.Sugerencia> {
        val t = texto.trim()
        val coinciden = (if (t.isEmpty()) sitios.take(12) else sitios.filter { it.titulo.contains(t, true) || it.url.contains(t, true) }.take(4))
            .map { com.santyramos.mirador.ui.Sugerencia(it.titulo, com.santyramos.mirador.ui.Sugerencia.Tipo.SITIO, detalle = it.url, url = it.url) }
        if (t.isEmpty()) return coinciden
        val vistas = coinciden.map { it.texto.lowercase() }.toMutableSet()
        return coinciden + remotas.filter { vistas.add(it.lowercase()) }.take(8)
            .map { com.santyramos.mirador.ui.Sugerencia(it, com.santyramos.mirador.ui.Sugerencia.Tipo.SUGERIDA) }
    }

    suspend fun duckduckgo(texto: String): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val url = "https://duckduckgo.com/ac/?type=list&q=" + java.net.URLEncoder.encode(texto, "UTF-8")
            com.santyramos.mirador.net.Http.client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(r.body.string()) as kotlinx.serialization.json.JsonArray
                (arr.getOrNull(1) as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }
        }.getOrDefault(emptyList())
    }
}
