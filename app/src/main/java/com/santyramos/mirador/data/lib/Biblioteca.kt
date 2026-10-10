package com.santyramos.mirador.data.lib

import android.content.Context
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.extractor.bestUrl
import com.santyramos.mirador.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.channel.ChannelInfo

/** Punto único de acceso a la biblioteca personal: suscripciones, historial, ver más tarde y listas. */
object Biblioteca {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: BibliotecaDb
    val dao get() = db.dao()

    fun init(context: Context) {
        db = BibliotecaDb.get(context)
    }

    // ------------------------------------------------------------------ Suscripciones

    sealed interface EstadoFeed {
        data object Quieto : EstadoFeed
        data class Actualizando(val hechos: Int, val total: Int) : EstadoFeed
        data class Listo(val conError: Int, val total: Int) : EstadoFeed
    }

    private val _feed = MutableStateFlow<EstadoFeed>(EstadoFeed.Quieto)
    val estadoFeed: StateFlow<EstadoFeed> = _feed.asStateFlow()
    @Volatile private var ultimaActualizacion = 0L

    fun suscribir(s: Suscripcion) = scope.launch {
        dao.suscribir(s)
        refrescarCanal(s)
    }

    fun anularSuscripcion(canalId: String) = scope.launch {
        dao.anularSuscripcion(canalId)
        dao.borrarNovedadesDe(canalId)
    }

    /** Devuelve cuántos canales eran nuevos. */
    suspend fun importar(canales: List<Suscripcion>): Int {
        val antes = dao.suscripciones().size
        dao.suscribirVarias(canales)
        val despues = dao.suscripciones().size
        actualizarFeed(forzar = true)
        return despues - antes
    }

    /** El canal puede venir como /channel/UC…, /@usuario, /c/nombre… Se resuelve el identificador una sola vez. */
    suspend fun suscribirPorUrl(url: String): Suscripcion? = withContext(Dispatchers.IO) {
        Importar.canalIdDeUrl(url)?.let { id -> return@withContext Suscripcion(id, id, Importar.urlDeCanal(id)) }
        runCatching {
            val info = ChannelInfo.getInfo(org.schabi.newpipe.extractor.ServiceList.YouTube, url)
            Suscripcion(info.id, info.name.orEmpty(), Importar.urlDeCanal(info.id), info.avatars.bestUrl())
        }.getOrNull()
    }

    /** El feed RSS de YouTube es lo más ligero, pero a veces responde 404; si falla se usa el extractor, que siempre funciona. */
    @Volatile private var rssCaido = false

    private suspend fun novedadesPorRss(s: Suscripcion): List<Novedad> {
        val xml = withContext(Dispatchers.IO) {
            Http.client.newCall(Request.Builder().url(FeedRss.url(s.canalId)).header("User-Agent", "Mozilla/5.0").build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body.string()
            }
        }
        return FeedRss.parsear(xml, s.canalId)
    }

    private suspend fun refrescarCanal(s: Suscripcion): Boolean {
        return try {
            var avatarListo = s.avatar != null
            var nuevas: List<Novedad> = emptyList()
            if (!rssCaido) {
                try { nuevas = novedadesPorRss(s) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { rssCaido = true }
            }
            if (nuevas.isEmpty()) {
                val d = Youtube.canal(s.urlCanal)
                val ahora = System.currentTimeMillis()
                val nombre = d.info.name.orEmpty().ifBlank { s.nombre }
                nuevas = d.videos.elementos.filterIsInstance<Elemento.Video>().take(15).mapIndexed { i, v ->
                    // Sin fecha exacta se ordena por posición dentro del canal (el más nuevo primero).
                    Novedad(v.url, s.canalId, nombre, v.titulo, v.miniatura, if (v.fechaMs > 0) v.fechaMs else ahora - (i + 1) * 3_600_000L, v.vistas)
                }
                dao.actualizarCanal(s.canalId, nombre, d.info.avatars.bestUrl())
                avatarListo = true
            }
            if (nuevas.isNotEmpty()) {
                dao.guardarNovedades(nuevas)
                val nombre = nuevas.first().canalNombre.ifBlank { s.nombre }
                if (nombre != s.nombre && avatarListo.not()) dao.actualizarCanal(s.canalId, nombre, s.avatar)
            }
            if (!avatarListo) completarAvatar(s)
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { false }
    }

    private suspend fun completarAvatar(s: Suscripcion) {
        runCatching {
            val info = ChannelInfo.getInfo(org.schabi.newpipe.extractor.ServiceList.YouTube, s.urlCanal)
            dao.actualizarCanal(s.canalId, info.name.orEmpty().ifBlank { s.nombre }, info.avatars.bestUrl())
        }
    }

    /** Baja las novedades de todos los canales (4 a la vez). Si hace poco que se hizo, no repite salvo [forzar]. */
    suspend fun actualizarFeed(forzar: Boolean = false) {
        if (_feed.value is EstadoFeed.Actualizando) return
        if (!forzar && System.currentTimeMillis() - ultimaActualizacion < 20 * 60_000) return
        val canales = dao.suscripciones()
        if (canales.isEmpty()) return
        Youtube.iniciar()
        _feed.value = EstadoFeed.Actualizando(0, canales.size)
        var hechos = 0
        var errores = 0
        val cupo = Semaphore(4)
        coroutineScope {
            canales.map { c ->
                async {
                    val ok = cupo.withPermit { refrescarCanal(c) }
                    synchronized(this@Biblioteca) { hechos++; if (!ok) errores++; _feed.value = EstadoFeed.Actualizando(hechos, canales.size) }
                }
            }.awaitAll()
        }
        dao.limpiarNovedadesViejas(System.currentTimeMillis() - 60L * 24 * 3600_000)
        ultimaActualizacion = System.currentTimeMillis()
        _feed.value = EstadoFeed.Listo(errores, canales.size)
    }

    // ------------------------------------------------------------------ Historial

    fun registrarVisto(v: Visto) = scope.launch {
        // Conserva el punto donde ibas si ya lo habías visto.
        val previo = dao.visto(v.url)
        dao.registrarVisto(v.copy(posicionMs = previo?.posicionMs ?: 0))
    }

    fun guardarPosicion(url: String, posicionMs: Long) = scope.launch {
        dao.guardarPosicion(url, posicionMs, System.currentTimeMillis())
    }

    /** Dónde retomar un video: solo si pasó de los 10 s y no está por terminar. */
    suspend fun posicionParaRetomar(url: String): Long {
        val v = dao.visto(url) ?: return 0
        val dur = v.duracionSeg * 1000
        return if (v.posicionMs > 10_000 && (dur <= 0 || v.posicionMs < dur - 15_000)) v.posicionMs else 0
    }

    fun borrarHistorial() = scope.launch { dao.borrarHistorial() }
    fun quitarDelHistorial(url: String) = scope.launch { dao.quitarDelHistorial(url) }

    // ------------------------------------------------------------------ Ver más tarde y listas

    fun guardar(v: Elemento.Video) = scope.launch { dao.guardar(v.aGuardado()) }
    fun quitarGuardado(url: String) = scope.launch { dao.quitarGuardado(url) }

    fun crearLista(nombre: String, conVideo: Elemento.Video? = null) = scope.launch {
        val id = dao.crearLista(ListaPropia(nombre = nombre.trim().ifBlank { "Mi lista" }))
        if (conVideo != null) dao.agregarALista(conVideo.aItem(id))
    }

    fun agregarALista(listaId: Long, v: Elemento.Video) = scope.launch { dao.agregarALista(v.aItem(listaId)) }
    fun quitarDeLista(listaId: Long, url: String) = scope.launch { dao.quitarDeLista(listaId, url) }
    fun renombrarLista(id: Long, nombre: String) = scope.launch { dao.renombrarLista(id, nombre.trim().ifBlank { "Mi lista" }) }
    fun borrarLista(id: Long) = scope.launch { dao.vaciarLista(id); dao.borrarLista(id) }
}
