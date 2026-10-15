package com.santyramos.mirador.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.ui.theme.Paleta

/** Una sugerencia del buscador: algo que ya buscaste, una página que visitaste o lo que propone el buscador. */
data class Sugerencia(val texto: String, val tipo: Tipo, val detalle: String? = null, val url: String? = null) {
    enum class Tipo { HISTORIAL, SITIO, SUGERIDA }
}

/** Mezcla historial y sugerencias de internet: primero lo tuyo que coincide, luego lo nuevo, sin repetir. */
object Sugerir {
    fun combinar(texto: String, historial: List<String>, remotas: List<String>, maxHistorial: Int = 4, max: Int = 10): List<Sugerencia> {
        val t = texto.trim()
        val propias = (if (t.isEmpty()) historial else historial.filter { it.contains(t, ignoreCase = true) })
            .take(if (t.isEmpty()) max else maxHistorial).map { Sugerencia(it, Sugerencia.Tipo.HISTORIAL) }
        if (t.isEmpty()) return propias
        val vistas = propias.map { it.texto.lowercase() }.toMutableSet()
        val nuevas = remotas.filter { vistas.add(it.lowercase()) }.map { Sugerencia(it, Sugerencia.Tipo.SUGERIDA) }
        return (propias + nuevas).take(max)
    }
}

/**
 * Lista de sugerencias para debajo de un buscador. Tocar = usarla; la flecha ↖ pasa el texto al
 * buscador para seguir escribiendo; la ✕ quita esa entrada del historial.
 */
fun LazyListScope.listaSugerencias(
    items: List<Sugerencia>,
    onElegir: (Sugerencia) -> Unit,
    onCompletar: (Sugerencia) -> Unit,
    onQuitar: (Sugerencia) -> Unit,
    onBorrarTodo: (() -> Unit)?,
) {
    items(items, key = { "${it.tipo} ${it.url ?: it.texto}" }) { s ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = { onElegir(s) }).padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (s.tipo) { Sugerencia.Tipo.HISTORIAL -> Icons.Outlined.History; Sugerencia.Tipo.SITIO -> Icons.Outlined.Language; Sugerencia.Tipo.SUGERIDA -> Icons.Outlined.Search },
                null, tint = Paleta.Texto3,
            )
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(s.texto, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.detalle != null) Text(s.detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (s.tipo != Sugerencia.Tipo.SUGERIDA) BotonIcono(Icons.Outlined.Close, "Quitar del historial", { onQuitar(s) })
            BotonIcono(Icons.Outlined.NorthWest, "Usar «${s.texto}» y seguir escribiendo", { onCompletar(s) })
        }
    }
    if (onBorrarTodo != null && items.any { it.tipo != Sugerencia.Tipo.SUGERIDA }) item {
        TextButton(onClick = onBorrarTodo, modifier = Modifier.padding(start = 8.dp)) { Text("Borrar historial", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
