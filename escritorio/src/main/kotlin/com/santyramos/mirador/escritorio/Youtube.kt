package com.santyramos.mirador.escritorio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit

object Red {
    val cliente: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }
}

/** Puente entre NewPipeExtractor y OkHttp (NewPipeExtractor no hace peticiones por sí mismo). */
private class DescargadorNewPipe : Downloader() {
    override fun execute(request: Request): Response {
        val cuerpo = request.dataToSend()?.toRequestBody()
        val b = okhttp3.Request.Builder().method(request.httpMethod(), cuerpo).url(request.url()).addHeader("User-Agent", AGENTE)
        request.headers().forEach { (nombre, valores) -> b.removeHeader(nombre); valores.forEach { b.addHeader(nombre, it) } }
        Red.cliente.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("YouTube pidió comprobar que no eres un robot", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body.string(), r.request.url.toString())
        }
    }

    companion object { const val AGENTE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0" }
}

data class Video(
    val titulo: String, val url: String, val miniatura: String?, val autor: String?,
    val duracionSeg: Long, val vistas: Long, val fecha: String?, val esDirecto: Boolean,
)

class Pagina(val videos: List<Video>, val siguiente: Page?)

/** Búsqueda de YouTube y direcciones de reproducción con NewPipeExtractor (sin anuncios, sin cuenta). */
object Youtube {
    @Volatile private var iniciado = false
    private fun iniciar() {
        if (iniciado) return
        NewPipe.init(DescargadorNewPipe(), Localization("es", "CO"), ContentCountry("CO"))
        iniciado = true
    }
    private val servicio get() = ServiceList.YouTube
    private fun qh(q: String) = servicio.searchQHFactory.fromQuery(q, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")

    private fun aVideo(i: StreamInfoItem) = Video(
        i.name.orEmpty(), i.url, i.thumbnails.maxByOrNull { it.width }?.url, i.uploaderName,
        i.duration, i.viewCount, i.textualUploadDate, i.streamType.name.contains("LIVE"),
    )

    suspend fun buscar(q: String): Pagina = withContext(Dispatchers.IO) {
        iniciar()
        val info = SearchInfo.getInfo(servicio, qh(q))
        Pagina(info.relatedItems.filterIsInstance<StreamInfoItem>().map(::aVideo), info.nextPage)
    }

    suspend fun mas(q: String, p: Page): Pagina = withContext(Dispatchers.IO) {
        iniciar()
        val r = SearchInfo.getMoreItems(servicio, qh(q), p)
        Pagina(r.items.filterIsInstance<StreamInfoItem>().map(::aVideo), r.nextPage)
    }

    suspend fun info(url: String): StreamInfo = withContext(Dispatchers.IO) { iniciar(); StreamInfo.getInfo(servicio, url) }

    fun esYoutube(url: String) = Regex("^https?://([a-z0-9-]+\\.)?(youtube\\.com|youtu\\.be)/", RegexOption.IGNORE_CASE).containsMatchIn(url)
}

/** Elige qué streams reproducir (misma lógica que en el celular). */
object Seleccion {
    /** Audio original primero: YouTube agrega doblajes automáticos a muchos videos. */
    fun rangoPista(a: AudioStream): Int = when (a.audioTrackType) {
        AudioTrackType.ORIGINAL -> 3; null -> 2; AudioTrackType.SECONDARY -> 1; AudioTrackType.DUBBED -> 0; AudioTrackType.DESCRIPTIVE -> -1
    }

    fun audio(audios: List<AudioStream>): AudioStream? = audios.filter { it.isUrl }
        .maxWithOrNull(compareBy<AudioStream> { rangoPista(it) }.thenBy { it.averageBitrate })

    /** Video hasta [tope] de alto: primero el más alto; a igual alto, H.264 (el que menos batería gasta). */
    fun video(videos: List<VideoStream>, tope: Int): VideoStream? = videos.filter { it.isUrl && it.height in 1..tope }
        .maxWithOrNull(compareBy<VideoStream> { it.height }.thenBy { if ((it.codec ?: "").startsWith("avc1")) 1 else 0 }.thenBy { it.bitrate })
}
