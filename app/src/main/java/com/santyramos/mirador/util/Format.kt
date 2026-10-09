package com.santyramos.mirador.util

import java.util.Locale

object Format {
    fun bytes(n: Long?): String {
        if (n == null || n <= 0) return "—"
        val unidades = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = n.toDouble()
        var i = 0
        while (v >= 1024 && i < unidades.lastIndex) { v /= 1024; i++ }
        return if (i == 0) "${n} B" else String.format(Locale.US, "%.1f %s", v, unidades[i])
    }

    fun velocidad(bps: Long): String = if (bps <= 0) "—" else bytes(bps) + "/s"

    fun duracion(seg: Long?): String {
        if (seg == null || seg < 0) return ""
        val h = seg / 3600
        val m = (seg % 3600) / 60
        val s = seg % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun eta(seg: Long): String = if (seg <= 0) "—" else duracion(seg)

    /** 1234567 -> "1,2 M" (visitas, suscriptores). */
    fun contar(n: Long): String = when {
        n < 0 -> ""
        n < 1_000 -> n.toString()
        n < 1_000_000 -> String.format(Locale("es"), "%.1f mil", n / 1_000.0).replace(",0", "")
        n < 1_000_000_000 -> String.format(Locale("es"), "%.1f M", n / 1_000_000.0).replace(",0", "")
        else -> String.format(Locale("es"), "%.1f mil M", n / 1_000_000_000.0).replace(",0", "")
    }
}
