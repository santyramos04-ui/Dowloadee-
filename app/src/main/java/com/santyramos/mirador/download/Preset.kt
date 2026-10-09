package com.santyramos.mirador.download

/** Lo que el usuario puede pedir descargar. */
enum class Preset(
    val etiqueta: String,
    val descripcion: String,
    val altura: Int = 0,
    val esAudio: Boolean = false,
) {
    MEJOR("Mejor calidad", "Hasta 1080p · MP4 (H.264 + AAC), se ve en cualquier celular", 1080),
    P720("720p", "MP4, ocupa menos espacio", 720),
    P480("480p", "MP4, el más liviano", 480),
    MAX_4K("Máx. 4K", "Pesa mucho y no todos los celulares lo reproducen", 2160),
    MP3("Audio MP3", "192 kbps, con portada y título incrustados", esAudio = true),
    IMAGENES("Fotos y videos del post", "Descarga todas las imágenes y videos del post");

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: MEJOR
    }
}
