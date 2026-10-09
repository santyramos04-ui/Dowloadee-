package com.santyramos.mirador.download

/** Traduce los errores técnicos de yt-dlp y de la red a mensajes claros en español. */
object ErrorMapper {
    private data class Regla(val patrones: List<String>, val mensaje: String)

    private val reglas = listOf(
        Regla(
            listOf("no space left", "enospc", "not enough space", "disk full"),
            "No hay espacio suficiente en el celular. Libera espacio e inténtalo otra vez.",
        ),
        Regla(
            listOf("unable to resolve host", "name or service not known", "network is unreachable",
                "temporary failure in name resolution", "no address associated", "getaddrinfo",
                "failed to resolve", "unknownhostexception", "connection reset", "timed out",
                "timeout", "connection refused", "software caused connection abort",
                "unable to connect", "failed to connect", "sockettimeoutexception", "econnreset"),
            "No hay conexión a internet (o es muy inestable). Revisa tu Wi‑Fi o tus datos y reintenta.",
        ),
        Regla(
            listOf("private video", "this video is private", "is private", "private account",
                "this account is private", "video privado"),
            "El contenido es privado. Solo su dueño (o sus seguidores) puede verlo.",
        ),
        Regla(
            listOf("confirm your age", "age-restricted", "age restricted", "age verification",
                "nsfw", "adult content", "sensitive content", "may contain sensitive"),
            "Contenido +18 o sensible: pide iniciar sesión. Puedes activar «Usar mi sesión» en Ajustes (opcional).",
        ),
        Regla(
            listOf("not a bot", "confirm you’re not a bot", "confirm you're not a bot"),
            "YouTube pidió comprobar que no eres un robot. Espera unos minutos, cambia de red o actualiza el motor en Ajustes.",
        ),
        Regla(
            listOf("login required", "rate-limit reached or login required", "requires authentication",
                "sign in to", "log in to", "you need to log in", "cookies", "this content isn't available",
                "empty media response", "no csrf"),
            "Este sitio pide iniciar sesión para ver el contenido. Puedes activar «Usar mi sesión» en Ajustes (opcional).",
        ),
        Regla(
            listOf("unsupported url"),
            "Este enlace no es compatible o no contiene un video que se pueda descargar.",
        ),
        Regla(
            listOf("no video could be found", "no video formats found", "there is no video",
                "no media found", "no video in"),
            "Este enlace no tiene un video descargable (¿será solo texto o fotos?).",
        ),
        Regla(
            listOf("requested format is not available", "requested format not available"),
            "Esa calidad no está disponible para este contenido. Prueba con otra opción.",
        ),
        Regla(
            listOf("video unavailable", "this video is unavailable", "has been removed", "been deleted",
                "no longer available", "not available in your country", "blocked it in your country",
                "http error 404", "404 not found", "does not exist", "this video has been",
                "content isn't available", "post is unavailable", "page isn't available"),
            "El contenido ya no está disponible (lo borraron o está bloqueado en tu país).",
        ),
        Regla(
            listOf("too many requests", "http error 429", "rate limit", "rate-limit"),
            "El sitio recibió demasiadas solicitudes. Espera unos minutos e inténtalo de nuevo.",
        ),
        Regla(
            listOf("http error 403", "forbidden"),
            "El sitio rechazó la descarga (403). Actualiza el motor en Ajustes y reintenta.",
        ),
        Regla(
            listOf("ffmpeg", "postprocessing", "conversion failed", "audioconvertor"),
            "Falló la conversión del archivo (ffmpeg). Prueba con otra calidad.",
        ),
    )

    fun mensaje(error: Throwable?): String {
        val texto = buildString {
            var e: Throwable? = error
            var n = 0
            while (e != null && n < 5) { append(e.javaClass.simpleName).append(": ").append(e.message).append('\n'); e = e.cause; n++ }
        }
        return mensaje(texto)
    }

    fun mensaje(texto: String?): String {
        val bajo = texto.orEmpty().lowercase()
        for (r in reglas) if (r.patrones.any { bajo.contains(it) }) return r.mensaje
        val linea = texto.orEmpty().lines().lastOrNull { it.contains("ERROR", ignoreCase = true) }
            ?: texto.orEmpty().lines().firstOrNull { it.isNotBlank() }.orEmpty()
        val corto = linea.replace(Regex("""^.*?ERROR:\s*""", RegexOption.IGNORE_CASE), "").take(140)
        return if (corto.isBlank()) "No se pudo descargar. Inténtalo de nuevo."
        else "No se pudo descargar: $corto"
    }

    /** Errores de NewPipeExtractor / reproducción, en español. */
    fun mensajeVer(error: Throwable?): String {
        var e: Throwable? = error
        var n = 0
        while (e != null && n < 6) {
            val nombre = e.javaClass.simpleName
            val msg = e.message.orEmpty().lowercase()
            when {
                nombre.contains("AgeRestricted") -> return "Este video es +18. Sin cuenta de Google no se puede ver aquí."
                nombre.contains("PrivateContent") -> return "El video es privado."
                nombre.contains("GeographicRestriction") -> return "Este video no está disponible en tu país."
                nombre.contains("Paid") || nombre.contains("Premium") -> return "Este contenido es de pago o solo para miembros."
                nombre.contains("SignInConfirmNotBot") || msg.contains("not a bot") || msg.contains("sign in to confirm") ->
                    return "YouTube pidió comprobar que no eres un robot. Prueba de nuevo en unos minutos o cambia de red."
                nombre.contains("ContentNotAvailable") || nombre.contains("NotAvailable") -> return "El video no está disponible (lo borraron o es privado)."
                nombre.contains("ReCaptcha") -> return "YouTube está limitando las solicitudes. Espera unos minutos."
                nombre.contains("UnknownHost") || nombre.contains("SocketTimeout") || nombre.contains("ConnectException") ->
                    return "No hay conexión a internet. Revisa tu Wi‑Fi o tus datos."
            }
            e = e.cause
            n++
        }
        val m = mensaje(error)
        return if (m.startsWith("No se pudo descargar")) "No se pudo cargar el video. Si pasa seguido, YouTube cambió algo: mira «Si algo deja de funcionar» en el LEEME." else m
    }

    fun mensajeReproduccion(error: Throwable?): String {
        val m = error?.message.orEmpty().lowercase()
        return when {
            m.contains("403") -> "YouTube rechazó el video (403). Toca «Reintentar»; si sigue, cambia la calidad."
            m.contains("source error") || m.contains("io_error") || m.contains("unable to connect") || m.contains("timeout") ->
                "Se cortó la conexión mientras reproducía. Toca «Reintentar»."
            else -> "No se pudo reproducir el video (${error?.javaClass?.simpleName ?: "error"}). Toca «Reintentar»."
        }
    }
}
