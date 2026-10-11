package com.santyramos.mirador.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddToQueue
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.data.Settings
import com.santyramos.mirador.download.DownloadCenter
import com.santyramos.mirador.player.ElementoCola
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.player.aCola
import com.santyramos.mirador.ui.BotonIcono
import com.santyramos.mirador.ui.BotonPrimario
import com.santyramos.mirador.ui.BotonSecundario
import com.santyramos.mirador.ui.EstadoVacio
import com.santyramos.mirador.ui.FilaElemento
import com.santyramos.mirador.ui.FilaInterruptor
import com.santyramos.mirador.ui.Panel
import kotlinx.coroutines.launch

/** Un grupo de descargas: una lista de reproducción descargada o los archivos sueltos. */
data class GrupoSinConexion(val clave: String, val nombre: String, val elementos: List<ElementoCola>)

/** Agrupa las descargas reproducibles por lista (en el orden en que se descargaron) y deja al final las sueltas. */
fun agruparSinConexion(descargas: List<com.santyramos.mirador.data.db.DownloadEntity>): List<GrupoSinConexion> {
    val listas = descargas.filter { it.grupo != null }.groupBy { it.grupo }.values.map { filas ->
        val ordenadas = filas.sortedWith(compareBy({ it.prefijo.orEmpty() }, { it.id }))
        GrupoSinConexion("g${filas.first().grupo}", filas.first().grupoNombre ?: "Lista", ordenadas.map { it.aCola() }.distinctBy { it.url })
    }.reversed() // la más reciente primero
    val sueltas = descargas.filter { it.grupo == null }.map { it.aCola() }.distinctBy { it.url }.reversed()
    return listas + if (sueltas.isEmpty()) emptyList() else listOf(GrupoSinConexion("sueltas", "Descargas sueltas", sueltas))
}

/** «Sin conexión»: el interruptor del modo y todo lo descargado, para escucharlo o verlo dentro de la app sin internet. */
@Composable
fun SinConexionScreen(onAtras: () -> Unit) {
    val contexto = LocalContext.current
    val ajustes = remember { Settings(contexto) }
    val scope = rememberCoroutineScope()
    val activo by ajustes.sinConexion.collectAsState(initial = false)
    val descargas by DownloadCenter.observarReproducibles().collectAsState(initial = emptyList())
    val grupos = remember(descargas) { agruparSinConexion(descargas) }
    fun avisar(t: String) = android.widget.Toast.makeText(contexto, t, android.widget.Toast.LENGTH_SHORT).show()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonIcono(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onAtras)
            Text("Sin conexión", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Panel(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        FilaInterruptor(
                            "Modo sin conexión", activo, { v -> scope.launch { ajustes.setSinConexion(v) } },
                            "El reproductor usa solo lo que descargaste y no gasta datos",
                        )
                        Text(
                            "Para tener una lista sin conexión: ábrela, toca «Descargar lista» y elige un formato de audio (para música) o de video. " +
                                "Cuando termine, aparece aquí.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (grupos.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        EstadoVacio(Icons.Outlined.CloudOff, "Aún no hay nada descargado", "Las canciones y videos que descargues se podrán reproducir aquí sin internet.")
                    }
                }
            }
            grupos.forEach { g ->
                item(key = "titulo ${g.clave}") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(g.nombre, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${g.elementos.size} archivos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BotonPrimario("Reproducir", { VideoController.reproducirLista(g.elementos) }, Modifier.weight(1f), icono = Icons.Outlined.PlayArrow)
                            BotonSecundario("A la cola", {
                                g.elementos.forEach { VideoController.agregarACola(it) }
                                avisar("Se añadieron ${g.elementos.size} a la cola")
                            }, Modifier.weight(1f))
                        }
                    }
                }
                itemsIndexed(g.elementos, key = { i, e -> "${g.clave} $i ${e.url}" }) { i, e ->
                    FilaElemento(e, { VideoController.reproducirLista(g.elementos, i) }) {
                        BotonIcono(Icons.Outlined.AddToQueue, "Añadir a la cola", { VideoController.agregarACola(e); avisar("Se añadió a la cola") })
                    }
                }
            }
        }
    }
}
