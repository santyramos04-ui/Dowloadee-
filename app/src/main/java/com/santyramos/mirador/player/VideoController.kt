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
)

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

    /** Videos que van a continuación (una lista de reproducción, tus listas, Ver más tarde…). Vacía = se usan los relacionados. */
    private var cola: List<String> = emptyList()
    private val reproducidos = LinkedHashSet<String>()
    private val _autoSiguiente = MutableStateFlow(true)
    val autoSiguiente: StateFlow<Boolean> = _autoSiguiente.asStateFlow()

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
                if (playbackState == Player.STATE_ENDED && _autoSiguiente.value && _video.value?.info != null) siguiente()
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
            }
        }
    }

    private fun guardarPosicionActual() {
        val v = _video.value ?: return
        if (v.info == null) return
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
    fun definirCola(urls: List<String>) { cola = urls }

    fun cambiarAutoSiguiente(v: Boolean) {
        _autoSiguiente.value = v
        scope.launch { Settings(app).setAutoSiguiente(v) }
    }

    /** El siguiente de la cola; si no hay cola (o se acabó), el primer relacionado que aún no viste. */
    fun urlSiguiente(): String? {
        val actual = _video.value ?: return null
        val i = cola.indexOf(actual.urlOriginal)
        if (i >= 0 && i + 1 < cola.size) return cola[i + 1]
        if (i >= 0) return null // era el último de la lista: se termina ahí
        val relacionados = actual.info?.relatedItems.orEmpty()
            .mapNotNull { Youtube.aElemento(it) }.filterIsInstance<Elemento.Video>()
            .filter { !it.esDirecto && it.url != actual.urlOriginal && it.url !in reproducidos }
        return relacionados.firstOrNull()?.url
    }

    fun haySiguiente(): Boolean = urlSiguiente() != null

    fun siguiente() { urlSiguiente()?.let { abrir(it) } }

    /** Abre un video. Si es el mismo que ya está sonando, no lo reinicia. */
    fun abrir(url: String, posicionInicialMs: Long = 0) {
        val actual = _video.value
        if (actual != null && actual.urlOriginal == url && actual.info != null && player.mediaItemCount > 0) return
        trabajo?.cancel()
        // Si el video no pertenece a la cola actual, se descarta (y se vuelve a los relacionados).
        if (url !in cola) cola = emptyList()
        reproducidos.add(url)
        if (reproducidos.size > 200) reproducidos.remove(reproducidos.first())
        _video.value = VideoActual(urlOriginal = url)
        trabajo = scope.launch {
            try {
                val ajustes = Settings(app)
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

    fun reintentar() { _video.value?.urlOriginal?.let { u -> _video.value = null; abrir(u) } }

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
        _video.value = null
    }
}
