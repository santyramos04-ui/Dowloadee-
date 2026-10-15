package com.santyramos.mirador.tv

import com.santyramos.mirador.net.Http
import com.santyramos.mirador.net.NewPipeDownloader
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Elige imagen (H.264, hasta 1080p: la que todo televisor decodifica) y sonido (AAC) de YouTube
 * para unirlos en el celular y mandar al televisor en alta calidad.
 */
object AltaCalidad {
    const val ALTURA_MAX = 1080

    internal fun elegirVideo(videos: List<VideoStream>): VideoStream? = videos
        .filter { it.isVideoOnly && it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        .filter { (it.codec ?: "").startsWith("avc1") && it.height in 1..ALTURA_MAX }
        .maxWithOrNull(compareBy<VideoStream> { it.height }.thenBy { it.fps }.thenBy { it.bitrate })

    internal fun elegirAudio(audios: List<AudioStream>, pistaId: String? = null): AudioStream? = audios
        .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.M4A }
        .filter { pistaId == null || it.audioTrackId == pistaId }
        .maxWithOrNull(compareBy<AudioStream> { com.santyramos.mirador.extractor.StreamSelector.rangoPista(it) }.thenBy { it.averageBitrate })

    private fun agente(url: String) = if (url.contains("c=VISIONOS")) YoutubeParsingHelper.getVisionOsUserAgent(null) else NewPipeDownloader.USER_AGENT

    /** Tamaño total del archivo (lo da NewPipe; si no, se pregunta pidiendo el primer byte). */
    private fun total(s: Stream): Long {
        s.itagItem?.contentLength?.takeIf { it > 0 }?.let { return it }
        val sep = if (s.content.contains('?')) "&" else "?"
        val req = Request.Builder().url("${s.content}${sep}range=0-0").header("User-Agent", agente(s.content)).build()
        return Http.client.newCall(req).execute().use { r ->
            r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
        }
    }

    /** Imagen y sonido listos para el servidor del celular, o null si este video no los tiene (se usa el modo compatible). */
    fun elegir(info: StreamInfo, pistaId: String? = null): Pair<Recurso.Remoto, Recurso.Remoto>? {
        val v = elegirVideo(info.videoOnlyStreams) ?: return null
        val a = elegirAudio(info.audioStreams, pistaId) ?: elegirAudio(info.audioStreams) ?: return null
        val tv = runCatching { total(v) }.getOrDefault(-1L)
        val ta = runCatching { total(a) }.getOrDefault(-1L)
        if (tv <= 0 || ta <= 0) return null
        return Recurso.Remoto(v.content, agente(v.content), tv, "video/mp4") to Recurso.Remoto(a.content, agente(a.content), ta, "audio/mp4")
    }
}
