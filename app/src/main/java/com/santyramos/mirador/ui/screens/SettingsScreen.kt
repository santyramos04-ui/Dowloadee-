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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.santyramos.mirador.data.lib.Respaldo
import com.santyramos.mirador.download.Cookies
import com.santyramos.mirador.player.SponsorBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.santyramos.mirador.BuildConfig
import com.santyramos.mirador.data.PlayerQuality
import com.santyramos.mirador.data.Settings
import com.santyramos.mirador.data.YtdlpChannel
import com.santyramos.mirador.ui.BotonIcono
import com.santyramos.mirador.ui.BotonPrimario
import com.santyramos.mirador.ui.BotonSecundario
import com.santyramos.mirador.ui.Filtro
import com.santyramos.mirador.ui.theme.Paleta
import com.santyramos.mirador.update.AppUpdater
import com.santyramos.mirador.update.YtdlpUpdater
import com.santyramos.mirador.util.Format
import kotlinx.coroutines.launch

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) = com.santyramos.mirador.ui.TarjetaSeccion(titulo) { contenido() }

@Composable
private fun colorInterruptor() = SwitchDefaults.colors(
    checkedThumbColor = Paleta.SobreAcento, checkedTrackColor = MaterialTheme.colorScheme.primary, checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = Paleta.Texto2, uncheckedTrackColor = Paleta.S3, uncheckedBorderColor = Paleta.Linea,
)

@Composable
fun SettingsScreen(onAtras: () -> Unit, onAbrirNavegador: (String) -> Unit = {}) {
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
    val tema by ajustes.tema.collectAsState(initial = com.santyramos.mirador.data.Tema.AUTO)
    val sb by ajustes.sponsorBlock.collectAsState(initial = true)
    val sbCats by ajustes.sbCategorias.collectAsState(initial = com.santyramos.mirador.data.SB_CATEGORIAS_INICIALES)
    var sesionActiva by remember { mutableStateOf(Cookies.activadas(contexto)) }
    var conSesion by remember { mutableStateOf(emptyList<String>()) }
    var confirmarCierre by remember { mutableStateOf(false) }
    LaunchedEffect(sesionActiva) { conSesion = Cookies.sitiosConSesion().map { it.nombre } }
    val crearRespaldo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val texto = Respaldo.aTexto(Respaldo.crear(contexto))
                withContext(Dispatchers.IO) { contexto.contentResolver.openOutputStream(uri)!!.use { it.write(texto.toByteArray()) } }
            }.isSuccess
            Toast.makeText(contexto, if (ok) "Respaldo guardado." else "No pude guardar el respaldo.", Toast.LENGTH_LONG).show()
        }
    }
    val abrirRespaldo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = runCatching {
                val texto = withContext(Dispatchers.IO) { contexto.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
                Respaldo.restaurar(contexto, Respaldo.deTexto(texto))
            }
            Toast.makeText(
                contexto,
                r.getOrNull()?.let { "Restaurado: ${it.suscripciones} canales, ${it.historial} del historial, ${it.guardados} guardados y ${it.listas} listas." }
                    ?: "Ese archivo no es un respaldo de ${com.santyramos.mirador.util.Marca.NOMBRE}.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    LaunchedEffect(Unit) { YtdlpUpdater.refrescarVersion(contexto) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, top = 6.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonIcono(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onAtras)
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Seccion("Apariencia") {
                Text("Tema", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.santyramos.mirador.data.Tema.entries.forEach { t -> Filtro(t.etiqueta, tema == t) { scope.launch { ajustes.setTema(t) } } }
                }
                Text("«Automático» sigue el modo claro u oscuro de tu celular.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Seccion("Descargas") {
                Text("Descargas simultáneas: $simultaneas", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { n -> Filtro("$n", simultaneas == n) { scope.launch { ajustes.setSimultaneas(n) } } }
                }
            }
            Seccion("Reproductor") {
                Text("Calidad predeterminada", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerQuality.entries.forEach { q -> Filtro(q.etiqueta, calidad == q) { scope.launch { ajustes.setCalidad(q) } } }
                }
                Text("«Auto» usa hasta 1080p con Wi‑Fi y 720p con datos móviles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.santyramos.mirador.ui.FilaInterruptor(
                    "Saltar patrocinios (SponsorBlock)", sb, { v -> scope.launch { ajustes.setSponsorBlock(v) } },
                    "Usa la base de datos de la comunidad (sponsor.ajay.app). Se consulta con un código parcial del video, nunca con tus datos.",
                )
                if (sb) {
                    Text("Qué saltar", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SponsorBlock.CATEGORIAS.forEach { (clave, nombre) ->
                            Filtro(nombre, clave in sbCats) { scope.launch { ajustes.setSbCategorias(if (clave in sbCats) sbCats - clave else sbCats + clave) } }
                        }
                    }
                }
            }
            Seccion("Motor de descargas (yt-dlp)") {
                Text("Versión instalada: ${versionYt ?: "…"}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "YouTube y los demás sitios cambian seguido. El motor se actualiza solo, sin esperar una versión nueva de la app.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.santyramos.mirador.ui.FilaInterruptor("Actualizar automáticamente una vez al día", auto, { v -> scope.launch { ajustes.setYtdlpAuto(v) } })
                YtdlpChannel.entries.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = canal == c, role = androidx.compose.ui.semantics.Role.RadioButton) { scope.launch { ajustes.setYtdlpCanal(c) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = canal == c, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = Paleta.Texto3),
                        )
                        Text(c.etiqueta, style = MaterialTheme.typography.bodySmall)
                    }
                }
                BotonPrimario(
                    if (estadoYt is YtdlpUpdater.Estado.Actualizando) "Actualizando…" else "Actualizar motor ahora",
                    { scope.launch { YtdlpUpdater.actualizarAhora(contexto) } },
                    Modifier.fillMaxWidth(), enabled = estadoYt !is YtdlpUpdater.Estado.Actualizando,
                )
                if (estadoYt is YtdlpUpdater.Estado.Actualizando) LinearProgressIndicator(Modifier.fillMaxWidth())
                (estadoYt as? YtdlpUpdater.Estado.Resultado)?.let {
                    Text(it.mensaje, color = if (it.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Seccion("Aplicación") {
                Text("${com.santyramos.mirador.util.Marca.NOMBRE} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                when (val e = estadoApp) {
                    AppUpdater.Estado.Nada -> {}
                    AppUpdater.Estado.Buscando -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is AppUpdater.Estado.AlDia -> Text("Tienes la última versión.", color = MaterialTheme.colorScheme.primary)
                    is AppUpdater.Estado.Error -> Text(e.mensaje, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is AppUpdater.Estado.Disponible -> {
                        Text("Hay una versión nueva: ${e.nueva.version} (${Format.bytes(e.nueva.tamano)})", color = MaterialTheme.colorScheme.primary)
                        if (e.nueva.notas.isNotBlank()) Text(e.nueva.notas.take(400), style = MaterialTheme.typography.bodySmall)
                        BotonPrimario("Descargar", { scope.launch { AppUpdater.descargar(contexto, e.nueva) } }, Modifier.fillMaxWidth())
                    }
                    is AppUpdater.Estado.Descargando -> {
                        Text("Descargando ${e.nueva.version}… ${(e.fraccion * 100).toInt()}%")
                        LinearProgressIndicator(progress = { e.fraccion }, modifier = Modifier.fillMaxWidth())
                    }
                    is AppUpdater.Estado.ListaParaInstalar -> BotonPrimario("Instalar ${e.nueva.version}", { AppUpdater.instalar(contexto, e.archivo) }, Modifier.fillMaxWidth())
                }
                BotonSecundario("Buscar actualización", { scope.launch { AppUpdater.buscar(contexto) } }, Modifier.fillMaxWidth())
            }
            Seccion("Sesiones en los sitios (opcional)") {
                Text(
                    "Para bajar contenido privado, +18 o de cuentas que sigues (Instagram, X, TikTok, Facebook, Reddit), inicia sesión aquí. " +
                        "La sesión se guarda solo en este celular y únicamente se usa al descargar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.santyramos.mirador.ui.FilaInterruptor("Usar mi sesión al descargar", sesionActiva, { Cookies.activar(contexto, it); sesionActiva = it })
                Cookies.SITIOS.forEach { sitio ->
                    val dentro = sitio.nombre in conSesion
                    BotonSecundario(
                        if (dentro) "${sitio.nombre} · sesión iniciada" else "Iniciar sesión en ${sitio.nombre}",
                        { onAbrirNavegador(sitio.urlInicio) }, Modifier.fillMaxWidth(),
                    )
                }
                Text("YouTube no está en la lista porque Google bloquea el inicio de sesión dentro de otras apps.", style = MaterialTheme.typography.bodySmall, color = Paleta.Texto3)
                if (conSesion.isNotEmpty()) BotonSecundario("Cerrar todas las sesiones", { confirmarCierre = true }, Modifier.fillMaxWidth())
                if (confirmarCierre) androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmarCierre = false },
                    title = { Text("¿Cerrar todas las sesiones?") },
                    text = { Text("Se cierra la sesión de ${conSesion.joinToString(", ")} dentro de ${com.santyramos.mirador.util.Marca.NOMBRE}. Para volver a bajar contenido privado tendrás que iniciar sesión otra vez.") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { Cookies.cerrarSesiones(contexto); conSesion = emptyList(); confirmarCierre = false }) { Text("Cerrar sesiones") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmarCierre = false }) { Text("Cancelar") } },
                )
            }
            Seccion("Respaldo") {
                Text(
                    "Guarda en un archivo tus suscripciones, historial, «Ver más tarde», listas y ajustes. Al restaurar se suma a lo que ya tienes; no se borra nada.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BotonPrimario("Guardar respaldo", { crearRespaldo.launch("mirador-respaldo-${java.time.LocalDate.now()}.json") }, Modifier.fillMaxWidth())
                BotonSecundario("Restaurar desde un archivo", { abrirRespaldo.launch(arrayOf("application/json", "*/*")) }, Modifier.fillMaxWidth())
            }
            Seccion("Batería (importante en Xiaomi / HyperOS)") {
                Text(
                    "Para que la música en segundo plano y las descargas no se corten con la pantalla apagada, pon ${com.santyramos.mirador.util.Marca.NOMBRE} en «Sin restricciones» (Ajustes del sistema → Aplicaciones → ${com.santyramos.mirador.util.Marca.NOMBRE} → Ahorro de batería) y activa «Inicio automático».",
                    style = MaterialTheme.typography.bodySmall,
                )
                BotonSecundario("Quitar restricción de batería", {
                    val pm = contexto.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                    val intent = if (!pm.isIgnoringBatteryOptimizations(contexto.packageName))
                        Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${contexto.packageName}"))
                    else Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    runCatching { contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Modifier.fillMaxWidth())
                BotonSecundario("Abrir información de la app", {
                    runCatching {
                        contexto.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${contexto.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }, Modifier.fillMaxWidth())
            }
            Seccion("Acerca de") {
                Text("${com.santyramos.mirador.util.Marca.NOMBRE} es software libre (GPLv3). Usa NewPipeExtractor para ver YouTube sin anuncios y yt-dlp para descargar. Todo queda en tu celular.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
