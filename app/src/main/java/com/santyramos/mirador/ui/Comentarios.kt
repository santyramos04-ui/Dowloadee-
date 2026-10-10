package com.santyramos.mirador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.extractor.Comentario
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.ui.theme.Paleta
import com.santyramos.mirador.util.Format
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo

/** Comentarios de un video (solo lectura, sin cuenta). Se cargan más al llegar al final. */
@Composable
fun ComentariosSheetContent(url: String) {
    val comentarios = remember(url) { mutableStateListOf<Comentario>() }
    var info by remember(url) { mutableStateOf<CommentsInfo?>(null) }
    var siguiente by remember(url) { mutableStateOf<Page?>(null) }
    var cargando by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var cargandoMas by remember(url) { mutableStateOf(false) }
    val lista = rememberLazyListState()

    LaunchedEffect(url) {
        try {
            val p = Youtube.comentarios(url)
            info = p.info; comentarios.addAll(p.comentarios); siguiente = p.siguiente
            if (p.info.isCommentsDisabled) error = "Los comentarios están desactivados en este video."
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { error = ErrorMapper.mensajeVer(e) }
        cargando = false
    }
    val alFinal by remember { derivedStateOf { val u = lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; u >= comentarios.size - 3 && comentarios.isNotEmpty() } }
    LaunchedEffect(alFinal, siguiente) {
        val s = siguiente; val i = info
        if (alFinal && s != null && i != null && !cargandoMas) {
            cargandoMas = true
            try { val (mas, sig) = Youtube.masComentarios(i, s); comentarios.addAll(mas); siguiente = sig }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { siguiente = null } finally { cargandoMas = false }
        }
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
        Text("Comentarios", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                cargando -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                comentarios.isEmpty() -> Text(error ?: "Todavía no hay comentarios.", Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(state = lista, contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(comentarios.size) { i -> FilaComentario(comentarios[i]) }
                    if (cargandoMas) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) } }
                }
            }
        }
    }
}

@Composable
private fun FilaComentario(c: Comentario) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(c.avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(CircleShape).background(Paleta.S3))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(if (c.fijado) "Fijado" else null, c.autor, c.fecha).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(c.texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
            val extra = listOfNotNull(
                if (c.megusta > 0) "${Format.contar(c.megusta.toLong())} me gusta" else null,
                if (c.respuestas > 0) "${c.respuestas} respuestas" else null,
            ).joinToString(" · ")
            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.labelSmall, color = Paleta.Texto3, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
