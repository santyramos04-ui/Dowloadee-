package com.santyramos.mirador.download

/** Lo que el usuario puede pedir descargar. */
enum class Preset(
    val etiqueta: String,
    val descripcion: String,
    val altura: Int = 0,
    val esAudio: Boolean = false,
    /** Formato de salida de audio (para yt-dlp --audio-format). */
    val formatoAudio: String? = null,
    /** Calidad para ffmpeg: "0" = la mejor, o una tasa como "320K". */
    val calidadAudio: String = "0",
    /** Bits por segundo aproximados, para estimar el peso antes de descargar. */
    val bitsPorSeg: Long = 0,
    /** Opus y FLAC no admiten portada con las herramientas incluidas; MP3 y M4A sí. */
    val portada: Boolean = true,
) {
    MEJOR("Mejor calidad", "Hasta 1080p · MP4 (H.264 + AAC), se ve en cualquier celular", 1080),
    P720("720p", "MP4, ocupa menos espacio", 720),
    P480("480p", "MP4, el más liviano", 480),
    MAX_4K("Máx. 4K", "Pesa mucho y no todos los celulares lo reproducen", 2160),
    MP3("Audio MP3", "192 kbps, compatible con todo, con portada y título", esAudio = true, formatoAudio = "mp3", calidadAudio = "192K", bitsPorSeg = 192_000),
    MP3_320("Audio MP3 320", "320 kbps, la máxima calidad de MP3, con portada y título", esAudio = true, formatoAudio = "mp3", calidadAudio = "320K", bitsPorSeg = 320_000),
    M4A("Audio M4A (AAC)", "Calidad alta y pesa poco; con portada. Se guarda sin recomprimir cuando el sitio ya lo ofrece", esAudio = true, formatoAudio = "m4a", bitsPorSeg = 140_000),
    OPUS("Audio Opus", "El mejor sonido por tamaño; sin portada. Lo reproducen la mayoría de apps de música", esAudio = true, formatoAudio = "opus", bitsPorSeg = 160_000, portada = false),
    FLAC("Audio FLAC", "Sin pérdida al guardar, pero el original ya viene comprimido: pesa mucho más sin sonar mejor", esAudio = true, formatoAudio = "flac", bitsPorSeg = 900_000, portada = false),
    IMAGENES("Fotos y videos del post", "Descarga todas las imágenes y videos del post");

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: MEJOR
    }
}
