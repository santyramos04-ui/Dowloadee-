package com.santyramos.mirador.extractor

import com.santyramos.mirador.data.PlayerQuality
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

/** Resultado: o un video con audio ya mezclado, o video + audio por separado. */
data class SeleccionStreams(val video: VideoStream, val audio: AudioStream?) {
    val altura get() = video.height
}

/**
 * Elige qué streams reproducir según la calidad pedida. Lógica pura (se prueba sin Android).
 * - AUTO: hasta 1080p con Wi‑Fi y hasta 720p con datos móviles.
 * - Se prefiere H.264 (lo decodifica el hardware de cualquier celular), luego VP9 y luego AV1.
 */
object StreamSelector {
    private fun prioridadCodec(v: VideoStream): Int {
        val c = (v.codec ?: "").lowercase()
        return when {
            c.startsWith("avc") || c.startsWith("h264") -> 3
            c.startsWith("vp9") || c.startsWith("vp09") -> 2
            c.startsWith("av01") || c.startsWith("av1") -> 1
            else -> 2
        }
    }

    fun topeAltura(calidad: PlayerQuality, conWifi: Boolean): Int = when (calidad) {
        PlayerQuality.AUTO -> if (conWifi) 1080 else 720
        else -> calidad.alturaMax
    }

    fun elegirVideo(videos: List<VideoStream>, tope: Int): VideoStream? {
        val usables = videos
        if (usables.isEmpty()) return null
        val dentro = usables.filter { it.height in 1..tope }
        val altura = (if (dentro.isNotEmpty()) dentro.maxOf { it.height } else usables.filter { it.height > 0 }.minOfOrNull { it.height }) ?: 0
        val enAltura = usables.filter { it.height == altura }.ifEmpty { usables }
        return enAltura.sortedWith(
            compareByDescending<VideoStream> { prioridadCodec(it) }
                .thenByDescending { it.bitrate }
                // Un stream ya mezclado (con audio) es más simple que unir dos.
                .thenBy { if (it.isVideoOnly) 1 else 0 },
        ).first()
    }

    fun elegirAudio(audios: List<AudioStream>): AudioStream? {
        if (audios.isEmpty()) return null
        return audios.sortedWith(
            compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                .thenByDescending { it.averageBitrate },
        ).first()
    }

    fun seleccionar(
        videoMezclado: List<VideoStream>,
        videoSolo: List<VideoStream>,
        audios: List<AudioStream>,
        calidad: PlayerQuality,
        conWifi: Boolean,
    ): SeleccionStreams? {
        val tope = topeAltura(calidad, conWifi)
        val todos = videoMezclado + videoSolo
        val v = elegirVideo(todos, tope) ?: return null
        return if (v.isVideoOnly) SeleccionStreams(v, elegirAudio(audios)) else SeleccionStreams(v, null)
    }

    /** Calidades disponibles para el menú del reproductor (solo las que existen de verdad). */
    fun alturasDisponibles(videos: List<VideoStream>): List<Int> =
        videos.map { it.height }.filter { it > 0 }.distinct().sortedDescending()
}
