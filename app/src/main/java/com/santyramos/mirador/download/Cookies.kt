package com.santyramos.mirador.download

import android.content.Context
import android.webkit.CookieManager
import java.io.File

/**
 * Sesión opcional: si inicias sesión en un sitio desde el navegador de Mirador, sus cookies se pasan a yt-dlp
 * (formato Netscape) para poder bajar contenido privado, +18 o de cuentas que sigues. Todo queda en el
 * almacenamiento privado de la app; nunca sale del celular salvo hacia el propio sitio al descargar.
 */
object Cookies {
    class Sitio(val nombre: String, val urlInicio: String, val dominios: List<String>, val cookieDeSesion: String)

    /** YouTube/Google no se incluye: Google bloquea el inicio de sesión dentro de navegadores incrustados. */
    val SITIOS = listOf(
        Sitio("Instagram", "https://www.instagram.com/accounts/login/", listOf("instagram.com"), "sessionid"),
        Sitio("X", "https://x.com/i/flow/login", listOf("x.com", "twitter.com"), "auth_token"),
        Sitio("TikTok", "https://www.tiktok.com/login", listOf("tiktok.com"), "sessionid"),
        Sitio("Facebook", "https://m.facebook.com/login", listOf("facebook.com"), "c_user"),
        Sitio("Reddit", "https://www.reddit.com/login", listOf("reddit.com"), "reddit_session"),
    )

    private const val NOMBRE = "cookies.txt"
    private fun prefs(c: Context) = c.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    fun activadas(c: Context): Boolean = prefs(c).getBoolean("usar_cookies", false)
    fun activar(c: Context, v: Boolean) { prefs(c).edit().putBoolean("usar_cookies", v).apply() }

    /** Líneas en formato Netscape para las cookies de [dominio] (cabecera «a=1; b=2»). */
    internal fun lineasNetscape(dominio: String, cabecera: String, expira: Long): List<String> =
        cabecera.split(";").map { it.trim() }.filter { it.contains('=') }.mapNotNull { par ->
            val nombre = par.substringBefore('=').trim()
            val valor = par.substringAfter('=').trim()
            if (nombre.isEmpty() || nombre.any { it == '\t' || it == '\n' } || valor.any { it == '\t' || it == '\n' }) null
            else ".$dominio\tTRUE\t/\tTRUE\t$expira\t$nombre\t$valor"
        }

    private fun cabecera(dominio: String): String =
        runCatching { CookieManager.getInstance().getCookie("https://www.$dominio") }.getOrNull().orEmpty()

    /** Sitios en los que hay una sesión iniciada (se mira la cookie propia de cada sitio). */
    fun sitiosConSesion(): List<Sitio> = SITIOS.filter { s ->
        s.dominios.any { d -> cabecera(d).split(";").any { it.trim().startsWith(s.cookieDeSesion + "=") } }
    }

    /** Escribe cookies.txt con lo que hay en el navegador de la app. Devuelve cuántas cookies guardó. */
    fun exportar(c: Context): Int {
        val expira = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val lineas = SITIOS.flatMap { it.dominios }.flatMap { lineasNetscape(it, cabecera(it), expira) }
        val f = File(c.filesDir, NOMBRE)
        if (lineas.isEmpty()) { f.delete(); return 0 }
        f.writeText("# Netscape HTTP Cookie File\n" + lineas.joinToString("\n") + "\n")
        return lineas.size
    }

    /** El archivo que se le pasa a yt-dlp, o null si la sesión está desactivada o no hay cookies. */
    fun archivo(context: Context): File? {
        if (!activadas(context)) return null
        exportar(context)
        val f = File(context.filesDir, NOMBRE)
        return if (f.exists() && f.length() > 0) f else null
    }

    fun cerrarSesiones(c: Context) {
        runCatching { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
        File(c.filesDir, NOMBRE).delete()
    }
}
