package com.santyramos.mirador.download

/** Línea de progreso que imprimimos con --progress-template (ver [YtDlpEngine]). */
data class Avance(
    val descargados: Long,
    val totales: Long,
    val velocidadBps: Long,
    val etaSeg: Long,
) {
    val porcentaje: Float get() = if (totales > 0) (descargados * 100f / totales).coerceIn(0f, 100f) else -1f
}

object ProgressParser {
    const val PREFIJO = "MIR|"
    const val PLANTILLA = "download:" + PREFIJO +
        "%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|" +
        "%(progress.speed)s|%(progress.eta)s"

    private fun num(s: String): Long? = s.trim().takeIf { it != "NA" && it != "None" && it.isNotEmpty() }
        ?.toDoubleOrNull()?.toLong()

    /** null si la línea no es de progreso. */
    fun parsear(linea: String): Avance? {
        val i = linea.indexOf(PREFIJO)
        if (i < 0) return null
        val p = linea.substring(i + PREFIJO.length).split('|')
        if (p.size < 5) return null
        val descargados = num(p[0]) ?: 0L
        val total = num(p[1]) ?: num(p[2]) ?: 0L
        return Avance(descargados, total, num(p[3]) ?: 0L, num(p[4]) ?: 0L)
    }

    /** ¿yt-dlp está procesando (uniendo audio y video, convirtiendo)? */
    fun esProcesando(linea: String): Boolean {
        val l = linea.trimStart()
        return l.startsWith("[Merger]") || l.startsWith("[ExtractAudio]") || l.startsWith("[VideoRemuxer]") ||
            l.startsWith("[VideoConvertor]") || l.startsWith("[EmbedThumbnail]") ||
            l.startsWith("[Metadata]") || l.startsWith("[ThumbnailsConvertor]") || l.startsWith("[FixupM4a]")
    }
}
