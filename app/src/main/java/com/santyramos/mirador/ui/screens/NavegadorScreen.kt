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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun NavegadorScreen(onAtras: () -> Unit, onDescargar: (String) -> Unit) {
    val contexto = LocalContext.current
    val foco = LocalFocusManager.current
    var direccion by remember { mutableStateOf("") }
    var actual by remember { mutableStateOf<String?>(null) }
    var progreso by remember { mutableFloatStateOf(0f) }
    var puedeVolver by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf(false) }
    val web = remember {
        WebView(contexto).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFF000000.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = true
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
            override fun onPageFinished(view: WebView, url: String) { actual = url; puedeVolver = view.canGoBack(); if (!editando) direccion = url }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progreso = newProgress / 100f }
        }
        onDispose { web.stopLoading(); web.destroy() }
    }
    BackHandler { if (web.canGoBack()) web.goBack() else onAtras() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BotonIcono(Icons.AutoMirrored.Filled.ArrowBack, if (puedeVolver) "Atrás" else "Cerrar navegador", { if (web.canGoBack()) web.goBack() else onAtras() })
                OutlinedTextField(
                    value = direccion, onValueChange = { direccion = it },
                    modifier = Modifier.weight(1f).onFocusChanged { editando = it.isFocused },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Escribe una dirección o busca", color = Paleta.Texto3) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Paleta.S2, unfocusedContainerColor = Paleta.S2,
                        focusedBorderColor = Acento, unfocusedBorderColor = Paleta.Linea, cursorColor = Acento,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onGo = { foco.clearFocus(); if (direccion.isNotBlank()) web.loadUrl(aDireccion(direccion)) }),
                )
                BotonIcono(if (progreso in 0.01f..0.99f) Icons.Outlined.Close else Icons.Outlined.Refresh, "Recargar", { if (progreso in 0.01f..0.99f) web.stopLoading() else web.reload() })
            }
            if (progreso in 0.01f..0.99f) LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth(), color = Acento, trackColor = Paleta.S3)
            else androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 1.dp))

            if (actual == null) {
                // Pantalla de inicio del navegador
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Navegador de Mirador", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Entra al sitio, abre la publicación que quieras y toca el botón verde «Descargar». Sirve para X, Instagram, TikTok, Facebook y más.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(Atajos) { (nombre, url) -> Filtro(nombre, false) { web.loadUrl(url) } }
                    }
                }
            }
            if (actual != null) AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
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
