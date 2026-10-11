package com.santyramos.mirador.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.santyramos.mirador.data.PlayerQuality
import com.santyramos.mirador.data.lib.Biblioteca
import com.santyramos.mirador.data.lib.Visto
import com.santyramos.mirador.extractor.bestUrl
import com.santyramos.mirador.data.Settings
import com.santyramos.mirador.download.ErrorMapper
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.StreamSelector
import com.santyramos.mirador.extractor.Youtube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Lo que la pantalla del reproductor necesita saber del video actual. */
data class VideoActual(
    val urlOriginal: String,
    val info: StreamInfo? = null,
    val cargando: Boolean = true,
    val error: String? = null,
    val alturas: List<Int> = emptyList(),
    val calidad: PlayerQuality = PlayerQuality.AUTO,
    val alturaReproduciendo: Int = 0,
    val titulo: String = "",
    val autor: String? = null,
    val miniatura: String? = null,
    /** Suena un archivo descargado (sin conexión), no un video de internet. */
    val local: Boolean = false,
    val esAudio: Boolean = false,
) {
    /** Ya está listo para sonar (cargado y sin error). */
    val listo: Boolean get() = !cargando && error == null && (info != null || local)
}

data class EstadoReproduccion(
    val reproduciendo: Boolean = false,
    val posicionMs: Long = 0,
    val duracionMs: Long = 0,
    val bufferMs: Long = 0,
    val almacenando: Boolean = false,
    val velocidad: Float = 1f,
    val hayMedia: Boolean = false,
)

/**
 * Carga un video con NewPipeExtractor, elige los streams según la calidad y se los pasa al
 * reproductor único de la app.
 */
object VideoController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: Context
    private var trabajo: Job? = null
    private var sondeo: Job? = null

    private val _video = MutableStateFlow<VideoActual?>(null)
    val video: StateFlow<VideoActual?> = _video.asStateFlow()

    /** La lista desde la que se abrió el video (una lista de reproducción, tus listas, Ver más tarde…). Vacía = se usan los relacionados. */
    private var contexto: List<String> = emptyList()
    private var indiceContexto = -1

    private val _cola = MutableStateFlow<List<ElementoCola>>(emptyList())
    /** La cola de reproducción: lo que tú añadiste para que suene a continuación. Va antes que la lista y los relacionados. */
    val cola: StateFlow<List<ElementoCola>> = _cola.asStateFlow()
    private val reproducidos = LinkedHashSet<String>()
    private val _autoSiguiente = MutableStateFlow(true)
    val autoSiguiente: StateFlow<Boolean> = _autoSiguiente.asStateFlow()

    @Volatile private var segmentos: List<Segmento> = emptyList()
    private val saltados = HashSet<Segmento>()
    private val _aviso = MutableStateFlow<String?>(null)
    /** Mensaje corto cuando se salta un tramo («Se saltó: Patrocinio»). */
    val aviso: StateFlow<String?> = _aviso.asStateFlow()
    private var avisoJob: Job? = null

    private val _estado = MutableStateFlow(EstadoReproduccion())
    val estado: StateFlow<EstadoReproduccion> = _estado.asStateFlow()

    val player: ExoPlayer get() = PlayerHolder.player(app)

    fun init(context: Context) {
        app = context.applicationContext
        scope.launch { _autoSiguiente.value = Settings(app).autoSiguiente.first() }
        PlayerHolder.conectarServicio(app)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                publicar()
                if (playbackState == Player.STATE_ENDED && _autoSiguiente.value && _video.value?.listo == true) siguiente()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { publicar(); if (!isPlaying) guardarPosicionActual() }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _video.value = _video.value?.copy(cargando = false, error = ErrorMapper.mensajeReproduccion(error))
            }
        })
        sondeo?.cancel()
        sondeo = scope.launch {
            var vueltas = 0
            while (isActive) {
                publicar(); delay(400)
                // Cada ~4 s de reproducción se guarda el punto donde vas (para retomar después).
                if (++vueltas % 10 == 0 && player.isPlaying) guardarPosicionActual()
                if (player.isPlaying) saltarSiCorresponde()
            }
        }
    }

    private fun saltarSiCorresponde() {
        val lista = segmentos
        if (lista.isEmpty()) return
        val pos = player.currentPosition
        val s = lista.firstOrNull { pos >= it.inicioMs && pos < it.finMs - 400 && it !in saltados } ?: return
        saltados.add(s)
        player.seekTo(s.finMs)
        avisoJob?.cancel()
        _aviso.value = "Se saltó: " + (SponsorBlock.CATEGORIAS[s.categoria] ?: "un tramo")
        avisoJob = scope.launch { delay(3000); _aviso.value = null }
    }

    private fun cargarSegmentos(url: String) {
        segmentos = emptyList(); saltados.clear()
        scope.launch {
            val ajustes = Settings(app)
            if (!ajustes.sponsorBlock.first()) return@launch
            val lista = SponsorBlock.segmentos(url, ajustes.sbCategorias.first())
            if (_video.value?.urlOriginal == url) segmentos = lista
        }
    }

    private fun guardarPosicionActual() {
        val v = _video.value ?: return
        if (!v.listo) return
        Biblioteca.guardarPosicion(v.urlOriginal, player.currentPosition.coerceAtLeast(0))
    }

    private fun publicar() {
        val p = player
        _estado.value = EstadoReproduccion(
            reproduciendo = p.isPlaying,
            posicionMs = p.currentPosition.coerceAtLeast(0),
            duracionMs = if (p.duration == C.TIME_UNSET) 0 else p.duration,
            bufferMs = p.bufferedPosition,
            almacenando = p.playbackState == Player.STATE_BUFFERING,
            velocidad = p.playbackParameters.speed,
            hayMedia = p.mediaItemCount > 0,
        )
    }

    private fun conWifi(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return !cm.isActiveNetworkMetered || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Define lo que sigue después: al abrir un video de esta lista, el siguiente será el que viene en ella. */
    fun definirCola(urls: List<String>) { contexto = urls }

    fun cambiarAutoSiguiente(v: Boolean) {
        _autoSiguiente.value = v
        scope.launch { Settings(app).setAutoSiguiente(v) }
    }

    // ---------- Cola de reproducción ----------

    /** Añade al final de la cola. Si no está sonando nada, empieza a sonar ya. */
    fun agregarACola(e: ElementoCola) {
        if (_video.value == null) { abrirElemento(e); return }
        _cola.value = ColaLogica.agregar(_cola.value, e)
    }

    /** Suena justo después del actual. */
    fun reproducirDespues(e: ElementoCola) {
        if (_video.value == null) { abrirElemento(e); return }
        _cola.value = ColaLogica.aContinuacion(_cola.value, e)
    }

    fun quitarDeCola(i: Int) { _cola.value = ColaLogica.quitar(_cola.value, i) }
    fun moverEnCola(i: Int, delta: Int) { _cola.value = ColaLogica.mover(_cola.value, i, delta) }
    fun vaciarCola() { _cola.value = emptyList() }

    /** Salta a un elemento de la cola (los anteriores se quitan). */
    fun reproducirDeCola(i: Int) {
        val (e, resto) = ColaLogica.saltarA(_cola.value, i) ?: return
        _cola.value = resto
        abrirElemento(e, mantenerContexto = true)
    }

    /** Reproduce una lista completa desde [desde]: lo demás queda en la cola. */
    fun reproducirLista(lista: List<ElementoCola>, desde: Int = 0) {
        val (e, resto) = ColaLogica.saltarA(lista, desde) ?: return
        contexto = emptyList(); indiceContexto = -1
        _cola.value = resto
        abrirElemento(e, mantenerContexto = true)
    }

    /** El siguiente: primero la cola; luego la lista de origen; si no hay, el primer relacionado que aún no viste. */
    fun urlSiguiente(): String? {
        _cola.value.firstOrNull()?.let { return it.url }
        val actual = _video.value ?: return null
        if (indiceContexto >= 0) return contexto.getOrNull(indiceContexto + 1) // al terminar la lista, se termina ahí
        if (actual.local) return null
        val relacionados = actual.info?.relatedItems.orEmpty()
            .mapNotNull { Youtube.aElemento(it) }.filterIsInstance<Elemento.Video>()
            .filter { !it.esDirecto && it.url != actual.urlOriginal && it.url !in reproducidos }
        return relacionados.firstOrNull()?.url
    }

    fun haySiguiente(): Boolean = urlSiguiente() != null

    fun siguiente() {
        val primero = _cola.value.firstOrNull()
        if (primero != null) {
            _cola.value = _cola.value.drop(1)
            abrirElemento(primero, mantenerContexto = true)
            return
        }
        urlSiguiente()?.let { abrir(it) }
    }

    /** Abre algo de la cola: el archivo si ya está descargado, o el video de internet. */
    fun abrirElemento(e: ElementoCola, mantenerContexto: Boolean = false) {
        if (e.esLocal) abrirArchivo(e, mantenerContexto) else abrir(e.url, previo = e, mantenerContexto = mantenerContexto)
    }

    private fun prepararApertura(url: String, mantenerContexto: Boolean) {
        trabajo?.cancel()
        val i = contexto.indexOf(url)
        if (i >= 0) indiceContexto = i
        // Si el video no pertenece a la lista de origen, se descarta (y se vuelve a los relacionados).
        // Desde la cola se conserva: al acabarse la cola, sigue la lista donde iba.
        else if (!mantenerContexto) { contexto = emptyList(); indiceContexto = -1 }
        reproducidos.add(url)
        if (reproducidos.size > 200) reproducidos.remove(reproducidos.first())
    }

    /** Reproduce un archivo descargado, sin internet. */
    fun abrirArchivo(e: ElementoCola, mantenerContexto: Boolean = false) {
        val uri = e.archivoUri ?: return
        prepararApertura(e.url, mantenerContexto)
        _video.value = VideoActual(
            urlOriginal = e.url, cargando = false, titulo = e.titulo, autor = e.autor, miniatura = e.miniatura,
            local = true, esAudio = e.esAudio,
        )
        segmentos = emptyList(); saltados.clear()
        trabajo = scope.launch {
            val desde = Biblioteca.posicionParaRetomar(e.url)
            val meta = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(e.titulo).setArtist(e.autor)
                .setArtworkUri(e.miniatura?.let(android.net.Uri::parse))
                .build()
            val item = androidx.media3.common.MediaItem.Builder()
                .setUri(android.net.Uri.parse(uri)).setMimeType(e.archivoMime).setMediaMetadata(meta).build()
            val p = player
            p.setMediaItem(item, desde)
            p.prepare()
            p.playWhenReady = true
            p.setPlaybackSpeed(Settings(app).velocidad.first())
        }
    }

    /** Abre un video. Si es el mismo que ya está sonando, no lo reinicia. */
    fun abrir(url: String, posicionInicialMs: Long = 0, previo: ElementoCola? = null, mantenerContexto: Boolean = false) {
        val actual = _video.value
        if (actual != null && actual.urlOriginal == url && actual.listo && player.mediaItemCount > 0) return
        prepararApertura(url, mantenerContexto)
        _video.value = VideoActual(urlOriginal = url, titulo = previo?.titulo.orEmpty(), autor = previo?.autor, miniatura = previo?.miniatura)
        trabajo = scope.launch {
            try {
                val ajustes = Settings(app)
                // Sin conexión (o si ya lo tienes descargado y no hay internet): suena el archivo.
                val sinConexion = ajustes.sinConexion.first() || !hayInternet()
                if (sinConexion) {
                    val d = com.santyramos.mirador.download.DownloadCenter.archivoDe(url)
                    if (d != null) { abrirArchivo(d.aCola(), mantenerContexto = true); return@launch }
                    _video.value = _video.value?.copy(
                        cargando = false,
                        error = if (ajustes.sinConexion.first()) "Estás en modo sin conexión y este video no está descargado."
                        else "No hay conexión a internet y este video no está descargado.",
                    )
                    return@launch
                }
                val calidad = ajustes.calidad.first()
                val velocidad = ajustes.velocidad.first()
                val info = withContext(Dispatchers.IO) { Youtube.video(url) }
                val desde = if (posicionInicialMs > 0) posicionInicialMs else Biblioteca.posicionParaRetomar(url)
                Biblioteca.registrarVisto(
                    Visto(
                        url = url, titulo = info.name.orEmpty(), miniatura = info.thumbnails.bestUrl(), autor = info.uploaderName,
                        urlAutor = info.uploaderUrl, duracionSeg = info.duration,
                    ),
                )
                cargarSegmentos(url)
                val alturas = StreamSelector.alturasDisponibles(info.videoStreams + info.videoOnlyStreams)
                reproducir(info, calidad, alturas, desde)
                player.setPlaybackSpeed(velocidad)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _video.value = _video.value?.copy(cargando = false, error = ErrorMapper.mensajeVer(e))
            }
        }
    }

    private fun hayInternet(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun reproducir(info: StreamInfo, calidad: PlayerQuality, alturas: List<Int>, posMs: Long) {
        val seleccion = StreamSelector.seleccionar(info.videoStreams, info.videoOnlyStreams, info.audioStreams, calidad, conWifi())
        val fuente = withContext(Dispatchers.IO) { PlayerSources.construir(info, seleccion) }
        val p = player
        p.setMediaSource(fuente, posMs)
        p.prepare()
        p.playWhenReady = true
        _video.value = _video.value?.copy(
            info = info, cargando = false, error = null, alturas = alturas, calidad = calidad,
            alturaReproduciendo = seleccion?.altura ?: 0,
            titulo = info.name.orEmpty(), autor = info.uploaderName, miniatura = info.thumbnails.bestUrl(), local = false, esAudio = false,
        )
    }

    /** Cambia la calidad sin perder el punto en el que vas. */
    fun cambiarCalidad(calidad: PlayerQuality) {
        val v = _video.value ?: return
        val info = v.info ?: return
        val pos = player.currentPosition
        trabajo?.cancel()
        trabajo = scope.launch {
            try {
                Settings(app).setCalidad(calidad)
                reproducir(info, calidad, v.alturas, pos)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _video.value = _video.value?.copy(error = ErrorMapper.mensajeVer(e))
            }
        }
    }

    fun velocidad(v: Float) {
        player.setPlaybackSpeed(v)
        scope.launch { Settings(app).setVelocidad(v) }
    }

    fun reintentar() {
        val v = _video.value ?: return
        _video.value = null
        abrir(v.urlOriginal, previo = ElementoCola(v.urlOriginal, v.titulo, v.autor, v.miniatura), mantenerContexto = true)
    }

    fun alternar() { val p = player; if (p.isPlaying) p.pause() else p.play() }
    fun moverA(ms: Long) = player.seekTo(ms.coerceAtLeast(0))
    fun saltar(deltaMs: Long) {
        val p = player
        val dur = if (p.duration == C.TIME_UNSET) Long.MAX_VALUE else p.duration
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0, dur))
    }

    /** Cierra el reproductor y quita la notificación. */
    fun cerrar() {
        guardarPosicionActual()
        trabajo?.cancel()
        PlayerHolder.detener()
        _cola.value = emptyList()
        _video.value = null
    }
}
