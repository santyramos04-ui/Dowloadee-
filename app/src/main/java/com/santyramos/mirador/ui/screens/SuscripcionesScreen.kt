package com.santyramos.mirador.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.data.lib.Biblioteca
import com.santyramos.mirador.data.lib.Importar
import com.santyramos.mirador.data.lib.aElemento
import com.santyramos.mirador.ui.BotonIcono
import com.santyramos.mirador.ui.BotonPrimario
import com.santyramos.mirador.ui.BotonSecundario
import com.santyramos.mirador.ui.Encabezado
import com.santyramos.mirador.ui.EstadoVacio
import com.santyramos.mirador.ui.theme.Paleta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SuscripcionesScreen(
    onAbrirVideo: (String) -> Unit,
    onDescargar: (String) -> Unit,
    onAbrirCanal: (String) -> Unit,
    onBuscar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val canales by Biblioteca.dao.observarSuscripciones().collectAsState(initial = emptyList())
    val novedades by Biblioteca.dao.observarNovedades().collectAsState(initial = emptyList())
    val estado by Biblioteca.estadoFeed.collectAsState()
    var ayuda by remember { mutableStateOf(false) }

    LaunchedEffect(canales.size) { if (canales.isNotEmpty()) Biblioteca.actualizarFeed() }

    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val resultado = withContext(Dispatchers.IO) {
                runCatching { contexto.contentResolver.openInputStream(uri)!!.use { Importar.deFlujo(it) } }
            }
            val lista = resultado.getOrNull()
            when {
                lista == null -> Toast.makeText(contexto, "No pude leer ese archivo.", Toast.LENGTH_LONG).show()
                lista.isEmpty() -> Toast.makeText(contexto, "No encontré canales en ese archivo. Toca «¿Cómo lo obtengo?» para ver los pasos.", Toast.LENGTH_LONG).show()
                else -> {
                    val nuevos = Biblioteca.importar(lista)
                    Toast.makeText(contexto, "Listo: $nuevos canales nuevos (${lista.size} en el archivo).", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val importar = { selector.launch(arrayOf("*/*")) }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Encabezado("Suscripciones", if (canales.isEmpty()) "Sin cuenta de Google" else "${canales.size} canales", Modifier.weight(1f))
            if (canales.isNotEmpty()) {
                BotonIcono(Icons.Outlined.Refresh, "Actualizar", { scope.launch { Biblioteca.actualizarFeed(forzar = true) } })
                BotonIcono(Icons.Outlined.FileUpload, "Importar canales", importar)
            }
        }

        if (canales.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(
                    Icons.Outlined.Subscriptions,
                    "Tus canales, sin cuenta de Google",
                    "Sigue canales desde su página con «Suscribirme», o trae los que ya tienes en YouTube con un archivo de Google Takeout.",
                ) {
                    BotonPrimario("Importar mis suscripciones", importar, Modifier.fillMaxWidth())
                    BotonSecundario("¿Cómo lo obtengo?", { ayuda = true }, Modifier.fillMaxWidth())
                    TextButton(onClick = onBuscar) { Text("Buscar un canal", color = MaterialTheme.colorScheme.primary) }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        items(canales, key = { it.canalId }) { c ->
                            Column(Modifier.width(68.dp).clickable { onAbrirCanal(c.urlCanal) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(c.avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(CircleShape).background(Paleta.S2))
                                Text(c.nombre, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    (estado as? Biblioteca.EstadoFeed.Actualizando)?.let { a ->
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            LinearProgressIndicator(progress = { a.hechos / a.total.toFloat() }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = Paleta.S3)
                            Text("Buscando novedades… ${a.hechos}/${a.total}", style = MaterialTheme.typography.labelSmall, color = Paleta.Texto3, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    (estado as? Biblioteca.EstadoFeed.Listo)?.let { l ->
                        if (l.conError > 0) Text("No se pudo leer ${l.conError} de ${l.total} canales. Toca actualizar para reintentar.", style = MaterialTheme.typography.labelSmall, color = Paleta.Aviso, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    }
                }
                if (novedades.isEmpty() && estado !is Biblioteca.EstadoFeed.Actualizando) {
                    item { Text("Todavía no hay videos para mostrar.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(novedades, key = { it.url }) { n ->
                    val v = n.aElemento()
                    TarjetaVideo(v, onClick = { onAbrirVideo(v.url) }, onDescargar = { onDescargar(v.url) })
                }
            }
        }
    }

    if (ayuda) {
        AlertDialog(
            onDismissRequest = { ayuda = false },
            title = { Text("Cómo traer tus suscripciones") },
            text = {
                Text(
                    "1. En el navegador entra a takeout.google.com.\n" +
                        "2. Toca «Anular la selección de todo» y marca solo «YouTube y YouTube Music».\n" +
                        "3. En «Todos los datos de YouTube incluidos» deja marcado solo «suscripciones».\n" +
                        "4. Sigue hasta «Crear exportación» y descarga el archivo ZIP.\n" +
                        "5. Vuelve aquí, toca «Importar mis suscripciones» y elige ese ZIP (no hace falta descomprimirlo).\n\n" +
                        "También sirve un respaldo de NewPipe (.json) o el archivo suscripciones.csv suelto.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { ayuda = false; importar() }) { Text("Elegir archivo") } },
            dismissButton = { TextButton(onClick = { ayuda = false }) { Text("Cerrar") } },
        )
    }
}
