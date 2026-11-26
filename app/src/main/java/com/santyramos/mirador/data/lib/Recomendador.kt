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
    private const val VIGENCIA_MS = 10 * 60_000L
    private var guardado: List<Elemento.Video>? = null
    private var calculadoEn = 0L
    /** Último video visto cuando se calcularon: si ves otro, se recalcula aunque no hayan pasado 10 min. */
    private var ultimoVisto: String? = null
    /** Lo que ya se mostró arriba en las últimas vueltas: baja de puesto para que la lista se renueve. */
    private val yaMostrados = ArrayDeque<String>()
    /** Videos de los que se sacarán más relacionados al bajar por el feed (así nunca se acaba). */
    private val semillasPendientes = ArrayDeque<String>()

    /** Un tema de la fila de arriba: «Todo» (consulta null), una búsqueda o los directos. */
    data class Tema(val nombre: String, val consulta: String?, val enVivo: Boolean = false)

    private val palabrasVacias = setOf(
        "para", "como", "with", "from", "this", "that", "your", "official", "video", "videos", "oficial", "lyric", "lyrics",
        "letra", "audio", "full", "live", "vivo", "parte", "part", "episodio", "episode", "capitulo", "capítulo", "nuevo",
        "nueva", "mejor", "mejores", "todo", "todos", "esto", "este", "esta", "pero", "porque", "cuando", "donde", "sobre",
        "entre", "hasta", "desde", "the", "and", "feat", "remix", "version", "versión", "minutos", "hora", "horas",
    )

    /** Temas a partir de tu historial: «Todo», «Música», «En vivo», los canales que más ves y palabras que se repiten. */
    internal fun armarTemas(historial: List<Visto>): List<Tema> {
        val fijos = listOf(Tema("Todo", null), Tema("Música", "música"), Tema("En vivo", "en vivo", enVivo = true))
        val canales = historial.mapNotNull { it.autor?.trim()?.takeIf { a -> a.isNotEmpty() } }
            .groupingBy { it }.eachCount().filter { it.value >= 2 }.entries.sortedByDescending { it.value }.take(3).map { Tema(it.key, it.key) }
        val palabras = historial.flatMap { v -> Regex("\\p{L}{4,}").findAll(v.titulo.lowercase()).map { it.value }.toSet() }
            .filter { it !in palabrasVacias && canales.none { c -> c.nombre.lowercase().contains(it) } }
            .groupingBy { it }.eachCount().filter { it.value >= 2 }.entries.sortedByDescending { it.value }.take(3)
            .map { Tema(it.key.replaceFirstChar { c -> c.uppercase() }, it.key) }
        return (fijos + canales + palabras).distinctBy { it.nombre.lowercase() }
    }

    suspend fun temas(): List<Tema> = withContext(Dispatchers.IO) { armarTemas(Biblioteca.dao.observarHistorial().first()) }

    private suspend fun relacionadosDe(urls: List<String>): List<List<Elemento.Video>> = coroutineScope {
        val cupo = Semaphore(3)
        urls.map { u ->
            async {
                cupo.withPermit {
                    runCatching { Youtube.video(u).relatedItems.mapNotNull { Youtube.aElemento(it) }.filterIsInstance<Elemento.Video>() }
                        .getOrDefault(emptyList())
                }
            }
        }.awaitAll()
    }

    /**
     * Más recomendaciones para seguir bajando por el feed: relacionados de los siguientes videos de tu historial y
     * de lo que ya se recomendó (por eso no se acaba). [yaEnFeed] no se repite.
     */
    suspend fun mas(yaEnFeed: Set<String>): List<Elemento.Video> = withContext(Dispatchers.IO) {
        val dao = Biblioteca.dao
        val historial = dao.observarHistorial().first()
        val semillas = buildList { repeat(3) { semillasPendientes.removeFirstOrNull()?.let(::add) } }
        if (semillas.isEmpty()) return@withContext emptyList()
        val res = ordenar(
            relacionadosDe(semillas), emptyList(),
            urlsVistas = historial.map { it.url }.toSet() + yaEnFeed,
            autoresVistos = historial.mapNotNull { it.autor }.groupingBy { it }.eachCount(),
            autoresSeguidos = seguidosActivos(historial, dao.suscripciones().map { it.nombre }),
            limite = 20, azar = kotlin.random.Random.Default,
        )
        res.shuffled().take(4).forEach { semillasPendientes.addLast(it.url) }
        res
    }

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
        mostradosAntes: Set<String> = emptySet(),
        azar: kotlin.random.Random? = null,
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
            // Lo nuevo de un canal que sigues pesa solo si lo ves de verdad últimamente.
            puntos[n.url] = (puntos[n.url] ?: 0) + if (n.canalNombre in autoresSeguidos) p.novedadSeguida else 1
        }
        // Lo ya mostrado baja un poco y una pizca de azar desempata, así cada vuelta trae algo distinto.
        mostradosAntes.forEach { u -> puntos[u]?.let { puntos[u] = it - 4 } }
        if (azar != null) puntos.keys.toList().forEach { u -> puntos[u] = puntos[u]!! + azar.nextInt(0, 3) }
        return diversificar(datos.values.sortedByDescending { puntos[it.url] ?: 0 }).take(limite)
    }

    /**
     * Variedad: se intercalan los canales (primero lo mejor de cada canal, luego el segundo de cada uno…),
     * así un solo canal no ocupa los primeros puestos.
     */
    internal fun diversificar(ordenados: List<Elemento.Video>): List<Elemento.Video> {
        val porCanal = LinkedHashMap<String, ArrayDeque<Elemento.Video>>()
        ordenados.forEach { v -> porCanal.getOrPut(v.autor ?: v.url) { ArrayDeque() }.addLast(v) }
        val salida = ArrayList<Elemento.Video>(ordenados.size)
        while (porCanal.values.any { it.isNotEmpty() }) porCanal.values.forEach { q -> q.removeFirstOrNull()?.let(salida::add) }
        return salida
    }

    /** Canales que sigues y además viste en tus últimos 100 videos (los «seguidos activos»). */
    private fun seguidosActivos(historial: List<Visto>, suscripciones: List<String>): Set<String> {
        val recientes = historial.take(100).mapNotNull { it.autor }.toSet()
        return suscripciones.filter { it in recientes }.toSet()
    }

    /** @return null si todavía no hay con qué recomendar (sin historial ni suscripciones). */
    suspend fun recomendar(forzar: Boolean = false): List<Elemento.Video>? = withContext(Dispatchers.IO) {
        val dao = Biblioteca.dao
        val historial = dao.observarHistorial().first()
        val previo = guardado
        val sinCambios = historial.firstOrNull()?.url == ultimoVisto
        if (!forzar && previo != null && sinCambios && System.currentTimeMillis() - calculadoEn < VIGENCIA_MS) return@withContext previo
        val suscripciones = dao.suscripciones()
        val novedades = dao.observarNovedades().first()
        if (historial.isEmpty() && suscripciones.isEmpty()) return@withContext null

        // Semillas: los 3 últimos videos vistos (de canales distintos) y 3 más al azar de lo que viste antes,
        // para que no salga siempre lo mismo.
        val canal = { v: Visto -> v.urlAutor ?: v.autor ?: v.url }
        val recientes = historial.distinctBy(canal).take(3)
        val delPasado = historial.drop(3).take(60).filter { v -> recientes.none { canal(it) == canal(v) } }
            .distinctBy(canal).shuffled().take(3)
        val semillas = recientes + delPasado
        val relacionados = relacionadosDe(semillas.map { it.url })
        val resultado = ordenar(
            relacionados, novedades,
            urlsVistas = historial.map { it.url }.toSet(),
            autoresVistos = historial.mapNotNull { it.autor }.groupingBy { it }.eachCount(),
            autoresSeguidos = seguidosActivos(historial, suscripciones.map { it.nombre }),
            mostradosAntes = yaMostrados.toSet(),
            azar = kotlin.random.Random.Default,
        )
        if (resultado.isNotEmpty()) {
            guardado = resultado; calculadoEn = System.currentTimeMillis(); ultimoVisto = historial.firstOrNull()?.url
            com.santyramos.mirador.ui.widgets.Widgets.guardarParaTi(resultado)
            resultado.take(12).forEach { yaMostrados.addLast(it.url) }
            while (yaMostrados.size > 60) yaMostrados.removeFirst()
            // Para el feed infinito: más historial (al azar) y algunos de los recomendados.
            semillasPendientes.clear()
            historial.drop(3).take(80).filter { v -> semillas.none { it.url == v.url } }.distinctBy(canal).shuffled().take(6).forEach { semillasPendientes.addLast(it.url) }
            resultado.shuffled().take(6).forEach { semillasPendientes.addLast(it.url) }
        }
        resultado
    }
}
