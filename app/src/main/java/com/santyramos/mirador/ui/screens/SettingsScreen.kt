package com.santyramos.mirador.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.BuildConfig
import com.santyramos.mirador.data.PlayerQuality
import com.santyramos.mirador.data.Settings
import com.santyramos.mirador.data.YtdlpChannel
import com.santyramos.mirador.update.AppUpdater
import com.santyramos.mirador.update.YtdlpUpdater
import com.santyramos.mirador.util.Format
import kotlinx.coroutines.launch

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        contenido()
    }
    HorizontalDivider()
}

@Composable
fun SettingsScreen(onAtras: () -> Unit) {
    BackHandler(onBack = onAtras)
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val ajustes = Settings(contexto)
    val simultaneas by ajustes.simultaneas.collectAsState(initial = 2)
    val calidad by ajustes.calidad.collectAsState(initial = PlayerQuality.AUTO)
    val auto by ajustes.ytdlpAuto.collectAsState(initial = true)
    val canal by ajustes.ytdlpCanal.collectAsState(initial = YtdlpChannel.NIGHTLY)
    val estadoYt by YtdlpUpdater.estado.collectAsState()
    val versionYt by YtdlpUpdater.version.collectAsState()
    val estadoApp by AppUpdater.estado.collectAsState()
    LaunchedEffect(Unit) { YtdlpUpdater.refrescarVersion(contexto) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            Text("Ajustes", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Seccion("Descargas") {
                Text("Descargas simultáneas: $simultaneas", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { n -> FilterChip(selected = simultaneas == n, onClick = { scope.launch { ajustes.setSimultaneas(n) } }, label = { Text("$n") }) }
                }
            }
            Seccion("Reproductor") {
                Text("Calidad predeterminada", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerQuality.entries.forEach { q -> FilterChip(selected = calidad == q, onClick = { scope.launch { ajustes.setCalidad(q) } }, label = { Text(q.etiqueta) }) }
                }
                Text("«Auto» usa hasta 1080p con Wi‑Fi y 720p con datos móviles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Seccion("Motor de descargas (yt-dlp)") {
                Text("Versión instalada: ${versionYt ?: "…"}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "YouTube y los demás sitios cambian seguido. El motor se actualiza solo, sin esperar una versión nueva de la app.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Actualizar automáticamente una vez al día", Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { v -> scope.launch { ajustes.setYtdlpAuto(v) } })
                }
                YtdlpChannel.entries.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = canal == c, onClick = { scope.launch { ajustes.setYtdlpCanal(c) } })
                        Text(c.etiqueta, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = { scope.launch { YtdlpUpdater.actualizarAhora(contexto) } }, enabled = estadoYt !is YtdlpUpdater.Estado.Actualizando) {
                    Text(if (estadoYt is YtdlpUpdater.Estado.Actualizando) "Actualizando…" else "Actualizar motor ahora")
                }
                if (estadoYt is YtdlpUpdater.Estado.Actualizando) LinearProgressIndicator(Modifier.fillMaxWidth())
                (estadoYt as? YtdlpUpdater.Estado.Resultado)?.let {
                    Text(it.mensaje, color = if (it.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Seccion("Aplicación") {
                Text("Mirador ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                when (val e = estadoApp) {
                    AppUpdater.Estado.Nada -> {}
                    AppUpdater.Estado.Buscando -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is AppUpdater.Estado.AlDia -> Text("✅ Tienes la última versión.", color = MaterialTheme.colorScheme.primary)
                    is AppUpdater.Estado.Error -> Text(e.mensaje, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is AppUpdater.Estado.Disponible -> {
                        Text("Hay una versión nueva: ${e.nueva.version} (${Format.bytes(e.nueva.tamano)})", color = MaterialTheme.colorScheme.primary)
                        if (e.nueva.notas.isNotBlank()) Text(e.nueva.notas.take(400), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { scope.launch { AppUpdater.descargar(contexto, e.nueva) } }) { Text("Descargar") }
                    }
                    is AppUpdater.Estado.Descargando -> {
                        Text("Descargando ${e.nueva.version}… ${(e.fraccion * 100).toInt()}%")
                        LinearProgressIndicator(progress = { e.fraccion }, modifier = Modifier.fillMaxWidth())
                    }
                    is AppUpdater.Estado.ListaParaInstalar -> Button(onClick = { AppUpdater.instalar(contexto, e.archivo) }) { Text("Instalar ${e.nueva.version}") }
                }
                OutlinedButton(onClick = { scope.launch { AppUpdater.buscar(contexto) } }, enabled = estadoApp !is AppUpdater.Estado.Buscando) { Text("Buscar actualización") }
            }
            Seccion("Batería (importante en Xiaomi / HyperOS)") {
                Text(
                    "Para que la música en segundo plano y las descargas no se corten con la pantalla apagada, pon Mirador en «Sin restricciones» (Ajustes del sistema → Aplicaciones → Mirador → Ahorro de batería) y activa «Inicio automático».",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    val pm = contexto.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                    val intent = if (!pm.isIgnoringBatteryOptimizations(contexto.packageName))
                        Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${contexto.packageName}"))
                    else Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    runCatching { contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Quitar restricción de batería") }
                OutlinedButton(onClick = {
                    runCatching {
                        contexto.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${contexto.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Abrir información de la app") }
            }
            Seccion("Acerca de") {
                Text("Mirador es software libre (GPLv3). Usa NewPipeExtractor para ver YouTube sin anuncios y yt-dlp para descargar. Todo queda en tu celular.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
