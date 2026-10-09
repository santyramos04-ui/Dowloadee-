package com.santyramos.mirador.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.santyramos.mirador.extractor.SeleccionStreams
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.ByteArrayInputStream

/** Convierte los streams que da NewPipeExtractor en fuentes de Media3/ExoPlayer. */
object PlayerSources {

    fun metadatos(info: StreamInfo): MediaMetadata = MediaMetadata.Builder()
        .setTitle(info.name)
        .setArtist(info.uploaderName)
        .setArtworkUri(info.thumbnails.maxByOrNull { it.width }?.url?.let(Uri::parse))
        .build()

    /** Fuente para un directo o un video con la selección elegida. */
    fun construir(info: StreamInfo, seleccion: SeleccionStreams?): MediaSource {
        val meta = metadatos(info)
        val esDirecto = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (esDirecto || seleccion == null) {
            val hls = info.hlsUrl
            if (!hls.isNullOrEmpty()) {
                return HlsMediaSource.Factory(PlayerData.fabricaGenerica())
                    .createMediaSource(MediaItem.Builder().setUri(hls).setMediaMetadata(meta).build())
            }
            error("Este contenido no se puede reproducir (no hay streams disponibles)")
        }
        val video = fuente(info, seleccion.video, meta)
        val audio = seleccion.audio?.let { fuente(info, it, meta) }
        return if (audio != null) MergingMediaSource(video, audio) else video
    }

    /** Solo audio (modo "escuchar" con la pantalla apagada y poco consumo de datos). */
    fun construirSoloAudio(info: StreamInfo, audio: AudioStream): MediaSource = fuente(info, audio, metadatos(info))

    private fun fuente(info: StreamInfo, stream: Stream, meta: MediaMetadata): MediaSource {
        val item = MediaItem.Builder().setUri(Uri.parse(stream.content)).setMediaMetadata(meta).build()
        val soloUnTipo = stream is AudioStream || (stream is VideoStream && stream.isVideoOnly)
        return when (stream.deliveryMethod) {
            DeliveryMethod.PROGRESSIVE_HTTP -> {
                if (soloUnTipo) {
                    // Los streams "solo video" y "solo audio" de YouTube se reproducen mejor con un
                    // manifiesto DASH generado al vuelo.
                    runCatching {
                        val xml = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, stream.itagItem!!, info.duration)
                        dash(xml, stream, item)
                    }.getOrElse {
                        ProgressiveMediaSource.Factory(PlayerData.fabricaProgresiva()).createMediaSource(item)
                    }
                } else {
                    ProgressiveMediaSource.Factory(PlayerData.fabricaProgresiva()).createMediaSource(item)
                }
            }
            DeliveryMethod.DASH -> {
                if (stream.isUrl) {
                    DashMediaSource.Factory(PlayerData.fabricaDash()).createMediaSource(item)
                } else {
                    val xml = YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(stream.content, stream.itagItem!!, info.duration)
                    dash(xml, stream, item)
                }
            }
            DeliveryMethod.HLS -> HlsMediaSource.Factory(PlayerData.fabricaGenerica()).createMediaSource(item)
            else -> error("Tipo de entrega no compatible: ${stream.deliveryMethod}")
        }
    }

    private fun dash(xml: String, stream: Stream, item: MediaItem): MediaSource {
        val uri = Uri.parse(stream.manifestUrl ?: stream.content)
        val manifest = DashManifestParser().parse(uri, ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        return DashMediaSource.Factory(PlayerData.fabricaDash()).createMediaSource(manifest, item)
    }
}
