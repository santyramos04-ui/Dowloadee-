package com.santyramos.mirador.data.lib

import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.Youtube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Recomendaciones hechas en el celular, sin cuenta ni envío de datos: se miran los videos que viste
 * (y los canales que sigues) y se juntan los «relacionados» de cada uno. Sale primero lo que más se repite
 * y lo de canales que ya ves o sigues. Nada de lo ya visto vuelve a aparecer.
 */
object Recomendador {
    private const val VIGENCIA_MS = 30 * 60_000L
    private var guardado: List<Elemento.Video>? = null
    private var calculadoEn = 0L

    /** Cuánto pesa cada señal al ordenar (público para poder probarlo). */
    internal data class Puntos(val repeticion: Int = 2, val canalVisto: Int = 3, val canalSeguido: Int = 4, val novedadSeguida: Int = 5)

    internal fun ordenar(
        relacionadosPorSemilla: List<List<Elemento.Video>>,
        novedades: List<Novedad>,
        urlsVistas: Set<String>,
        autoresVistos: Map<String, Int>,
        autoresSeguidos: Set<String>,
        p: Puntos = Puntos(),
        limite: Int = 40,
    ): List<Elemento.Video> {
        val puntos = HashMap<String, Int>()
        val datos = LinkedHashMap<String, Elemento.Video>()
        relacionadosPorSemilla.forEach { lista ->
            lista.forEachIndexed { pos, v ->
                if (v.url in urlsVistas || v.esDirecto) return@forEachIndexed
                datos.putIfAbsent(v.url, v)
                val autor = v.autor.orEmpty()
                var s = p.repeticion + maxOf(0, 5 - pos / 3) // los primeros relacionados de cada video pesan más
                if (autor in autoresSeguidos) s += p.canalSeguido
                s += minOf(autoresVistos[autor] ?: 0, 3) * p.canalVisto / 3
                puntos[v.url] = (puntos[v.url] ?: 0) + s
            }
        }
        val ahora = System.currentTimeMillis()
        novedades.filter { it.url !in urlsVistas && ahora - it.publicadoMs < 4 * 86_400_000L }.forEach { n ->
            datos.putIfAbsent(n.url, Elemento.Video(n.titulo, n.url, n.miniatura, n.canalNombre, null, 0, n.vistas, null, false, false, n.publicadoMs))
            puntos[n.url] = (puntos[n.url] ?: 0) + p.novedadSeguida
        }
        return datos.values.sortedByDescending { puntos[it.url] ?: 0 }.take(limite)
    }

    /** @return null si todavía no hay con qué recomendar (sin historial ni suscripciones). */
    suspend fun recomendar(forzar: Boolean = false): List<Elemento.Video>? = withContext(Dispatchers.IO) {
        val previo = guardado
        if (!forzar && previo != null && System.currentTimeMillis() - calculadoEn < VIGENCIA_MS) return@withContext previo
        val dao = Biblioteca.dao
        val historial = dao.observarHistorial().first()
        val suscripciones = dao.suscripciones()
        val novedades = dao.observarNovedades().first()
        if (historial.isEmpty() && suscripciones.isEmpty()) return@withContext null

        // Semillas: los últimos videos vistos, de canales distintos para variar.
        val semillas = historial.distinctBy { it.urlAutor ?: it.autor ?: it.url }.take(5)
        val cupo = Semaphore(3)
        val relacionados = coroutineScope {
            semillas.map { s ->
                async {
                    cupo.withPermit {
                        runCatching {
                            Youtube.video(s.url).relatedItems.mapNotNull { Youtube.aElemento(it) }.filterIsInstance<Elemento.Video>()
                        }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll()
        }
        val resultado = ordenar(
            relacionados, novedades,
            urlsVistas = historial.map { it.url }.toSet(),
            autoresVistos = historial.mapNotNull { it.autor }.groupingBy { it }.eachCount(),
            autoresSeguidos = suscripciones.map { it.nombre }.toSet(),
        )
        if (resultado.isNotEmpty()) { guardado = resultado; calculadoEn = System.currentTimeMillis() }
        resultado
    }
}
