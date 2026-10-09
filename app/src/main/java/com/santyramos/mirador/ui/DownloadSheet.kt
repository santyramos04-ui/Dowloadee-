package com.santyramos.mirador.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.download.DownloadCenter
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.download.MediaInfo
import com.santyramos.mirador.download.Preset
import com.santyramos.mirador.download.PreviewLoader
import com.santyramos.mirador.download.SizeEstimator
import com.santyramos.mirador.download.Cookies
import com.santyramos.mirador.util.Format
import kotlinx.coroutines.launch

/** Qué opciones mostrar según lo que realmente tiene el enlace. */
internal fun opcionesDisponibles(info: MediaInfo): List<Preset> {
    if (info.directos.isNotEmpty()) return listOf(Preset.IMAGENES)
    val alturaMax = SizeEstimator.alturaMaxima(info)
    val hayVideo = info.tieneVideo
    return buildList {
        if (hayVideo) {
            add(Preset.MEJOR)
            if (alturaMax == 0 || alturaMax >= 720) add(Preset.P720)
            if (alturaMax == 0 || alturaMax >= 480) add(Preset.P480)
            if (alturaMax > 1080) add(Preset.MAX_4K)
        }
        add(Preset.MP3)
    }
}

private sealed interface Estado {
    data object Cargando : Estado
    data class Error(val mensaje: String) : Estado
    data class Listo(val info: MediaInfo) : Estado
}

/**
 * Hoja de descarga: vista previa (miniatura, título, duración, peso estimado de cada opción) y
 * botón para encolar. La usan «Compartir → Mirador», el campo de pegar y el botón «Descargar» del reproductor.
 */
@Composable
fun DownloadSheetContent(
    url: String,
    soloEste: Boolean = true,
    onCerrar: () -> Unit,
    onEncolada: () -> Unit,
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var estado by remember(url) { mutableStateOf<Estado>(Estado.Cargando) }
    var elegido by remember(url) { mutableStateOf(Preset.MEJOR) }
    var intento by remember(url) { mutableStateOf(0) }

    LaunchedEffect(url, intento) {
        estado = Estado.Cargando
        estado = try {
            val info = PreviewLoader.cargar(url, Cookies.archivo(contexto), soloEste)
            elegido = opcionesDisponibles(info).first()
            Estado.Listo(info)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Estado.Error(ErrorMapper.mensaje(e))
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = estado) {
            Estado.Cargando -> Row(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text("Buscando opciones de descarga…")
            }
            is Estado.Error -> {
                Text("No se puede descargar", style = MaterialTheme.typography.titleMedium)
                Text(s.mensaje, color = MaterialTheme.colorScheme.error)
                Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { intento++ }) { Text("Reintentar") }
                    OutlinedButton(onClick = onCerrar) { Text("Cerrar") }
                }
            }
            is Estado.Listo -> {
                val info = s.info
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    AsyncImage(
                        model = info.miniatura, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(132.dp).height(76.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(info.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(info.autor, info.duracionSeg?.let { Format.duracion(it) }, info.sitio).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (info.esLista) Text("Lista de ${info.cantidadLista} elementos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                val opciones = opcionesDisponibles(info)
                opciones.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { elegido = p }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = elegido == p, onClick = { elegido = p })
                        Column(Modifier.weight(1f)) {
                            Text(if (p == Preset.IMAGENES) "Descargar todo (${info.directos.size} archivos)" else p.etiqueta, style = MaterialTheme.typography.bodyLarge)
                            Text(p.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val peso = SizeEstimator.estimar(info, p)
                        Text(if (peso != null) "≈ ${Format.bytes(peso)}" else "", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (elegido == Preset.MAX_4K) {
                    Text("⚠ Los archivos 4K pesan mucho y no todos los celulares los reproducen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onCerrar, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(
                        onClick = { scope.launch { DownloadCenter.encolar(info, elegido, soloEste); onEncolada() } },
                        modifier = Modifier.weight(1f),
                    ) { Text("⬇ Descargar") }
                }
            }
        }
    }
}
