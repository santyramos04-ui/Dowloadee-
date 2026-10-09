package com.santyramos.mirador.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.download.Cookies
import com.santyramos.mirador.download.DownloadCenter
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.download.ListaInfo
import com.santyramos.mirador.download.Preset
import com.santyramos.mirador.download.UrlTools
import com.santyramos.mirador.download.YtDlpEngine
import kotlinx.coroutines.launch

/** Formatos que tienen sentido para muchos archivos a la vez. */
private val PRESETS_MASIVOS = listOf(Preset.MEJOR, Preset.P720, Preset.P480, Preset.MP3)

@Composable
private fun SelectorFormato(elegido: Preset, onElegir: (Preset) -> Unit) {
    PRESETS_MASIVOS.forEach { p ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onElegir(p) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = elegido == p, onClick = { onElegir(p) })
            Column(Modifier.weight(1f)) {
                Text(p.etiqueta, style = MaterialTheme.typography.bodyLarge)
                Text(p.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Descargar una lista de reproducción de YouTube: un archivo por video, en una carpeta con el nombre de la lista. */
@Composable
fun ListaSheetContent(url: String, onCerrar: () -> Unit, onEncolada: () -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var lista by remember(url) { mutableStateOf<ListaInfo?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var intento by remember(url) { mutableStateOf(0) }
    var elegido by remember { mutableStateOf(Preset.MEJOR) }
    var trabajando by remember { mutableStateOf(false) }

    LaunchedEffect(url, intento) {
        error = null; lista = null
        try {
            val l = YtDlpEngine.lista(url, Cookies.archivo(contexto))
            if (l.entradas.isEmpty()) error = "La lista está vacía o sus videos son privados." else lista = l
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = ErrorMapper.mensaje(e)
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val l = lista
        when {
            error != null -> {
                Text("No se puede leer la lista", style = MaterialTheme.typography.titleMedium)
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { intento++ }) { Text("Reintentar") }
                    OutlinedButton(onClick = onCerrar) { Text("Cerrar") }
                }
            }
            l == null -> Row(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text("Leyendo la lista…")
            }
            else -> {
                Text(l.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${l.entradas.size} videos" + (l.autor?.let { " · $it" } ?: "") + "\nSe guardan en Descargas/Mirador/…/${UrlTools.nombreSeguro(l.titulo, 60)}, numerados en orden.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectorFormato(elegido) { elegido = it }
                if (l.entradas.size > 50) Text(
                    "⚠ Son ${l.entradas.size} videos: tardará bastante y ocupará mucho espacio. Puedes pausar o cancelar desde Descargas.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onCerrar, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(
                        enabled = !trabajando,
                        onClick = {
                            trabajando = true
                            scope.launch {
                                val n = DownloadCenter.encolarLista(l, elegido)
                                Toast.makeText(contexto, "Se agregaron $n videos a la cola ⬇", Toast.LENGTH_LONG).show()
                                onEncolada()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("⬇ Descargar ${l.entradas.size}") }
                }
            }
        }
    }
}

/** Varios enlaces a la vez: se descargan todos con el mismo formato. */
@Composable
fun LoteSheetContent(urls: List<String>, onCerrar: () -> Unit, onEncolada: () -> Unit) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var elegido by remember { mutableStateOf(Preset.MEJOR) }
    var listasCompletas by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf(false) }
    val hayVideosDeLista = urls.any { UrlTools.videoDentroDeLista(it) }
    val hayListas = urls.any { UrlTools.esListaYoutube(it) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${urls.size} enlaces", style = MaterialTheme.typography.titleMedium)
        Text(
            "Se descargarán todos, uno tras otro (2 a la vez). Las publicaciones con fotos se bajan como fotos.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        urls.take(4).forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (urls.size > 4) Text("… y ${urls.size - 4} más", style = MaterialTheme.typography.bodySmall)
        SelectorFormato(elegido) { elegido = it }
        if (hayListas) Text("Los enlaces de listas de YouTube se descargan completos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (hayVideosDeLista) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Si un video viene de una lista, bajar la lista completa", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = listasCompletas, onCheckedChange = { listasCompletas = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCerrar, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(
                enabled = !trabajando,
                onClick = {
                    trabajando = true
                    scope.launch {
                        val r = DownloadCenter.encolarLote(urls, elegido, listasCompletas)
                        val msg = "Se agregaron ${r.archivos} descargas a la cola ⬇" + if (r.listasConError.isNotEmpty()) " (alguna lista no se pudo leer)" else ""
                        Toast.makeText(contexto, msg, Toast.LENGTH_LONG).show()
                        onEncolada()
                    }
                },
                modifier = Modifier.weight(1f),
            ) { if (trabajando) CircularProgressIndicator(Modifier.size(18.dp)) else Text("⬇ Descargar todo") }
        }
    }
}
