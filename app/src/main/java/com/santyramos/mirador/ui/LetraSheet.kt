package com.santyramos.mirador.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.extractor.Letra
import com.santyramos.mirador.extractor.Letras
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.theme.Acento
import com.santyramos.mirador.ui.theme.Paleta

private sealed interface EstadoLetra {
    data object Buscando : EstadoLetra
    data object NoHay : EstadoLetra
    data class Lista(val letra: Letra) : EstadoLetra
}

/** Letra de lo que suena. Si viene sincronizada, se resalta la línea actual y se puede tocar para ir a ella. */
@Composable
fun LetraSheetContent() {
    val video by VideoController.video.collectAsState()
    val estado by VideoController.estado.collectAsState()
    val titulo = video?.titulo.orEmpty()
    val autor = video?.autor
    val resultado by produceState<EstadoLetra>(EstadoLetra.Buscando, titulo) {
        value = EstadoLetra.Buscando
        val l = runCatching { Letras.buscar(titulo, autor, estado.duracionMs / 1000) }.getOrNull()
        value = if (l == null) EstadoLetra.NoHay else EstadoLetra.Lista(l)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text("Letra", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(titulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        when (val r = resultado) {
            EstadoLetra.Buscando -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Acento) }
            EstadoLetra.NoHay -> Text(
                "No encontré la letra de esta canción.", style = MaterialTheme.typography.bodyMedium, color = Paleta.Texto3,
                modifier = Modifier.padding(20.dp),
            )
            is EstadoLetra.Lista -> if (r.letra.sincronizada) {
                val lineas = r.letra.lineas
                val lista = rememberLazyListState()
                val actual = lineas.indexOfLast { it.ms <= estado.posicionMs + 250 }
                LaunchedEffect(actual) { if (actual >= 0) lista.animateScrollToItem((actual - 2).coerceAtLeast(0)) }
                LazyColumn(Modifier.fillMaxWidth().height(460.dp), state = lista, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(lineas) { i, l ->
                        Text(
                            l.texto.ifBlank { "♪" },
                            style = if (i == actual) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            fontWeight = if (i == actual) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == actual) Acento else if (i < actual) Paleta.Texto3 else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Ir a esta parte") { VideoController.moverA(l.ms) },
                        )
                    }
                }
            } else {
                Text(
                    r.letra.texto.orEmpty(), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().height(460.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        Text("Letras de LRCLIB (lrclib.net)", style = MaterialTheme.typography.labelSmall, color = Paleta.Texto3, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    }
}
