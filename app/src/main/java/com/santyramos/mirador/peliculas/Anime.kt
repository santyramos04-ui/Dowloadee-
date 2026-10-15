package com.santyramos.mirador.peliculas

import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.FiltroBusqueda
import com.santyramos.mirador.extractor.Youtube
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Un canal oficial de YouTube que publica episodios completos de anime gratis. */
data class CanalAnime(val nombre: String, val url: String)

/**
 * Anime gratis y legal: episodios completos que los propios estudios y distribuidores suben a
 * YouTube. Algunos canales solo dejan ver en ciertos países; esos videos se saltan solos.
 */
object Anime {
    // Comprobados en CI: estos canales tienen episodios completos visibles fuera de Asia.
    val CANALES = listOf(
        CanalAnime("Gundam Info", "https://www.youtube.com/@GUNDAMINFO"),
        CanalAnime("RetroCrush", "https://www.youtube.com/@RetroCrush"),
        CanalAnime("Pokémon", "https://www.youtube.com/@Pokemon"),
        CanalAnime("Pokémon LATAM", "https://www.youtube.com/@PokemonLatAm"),
        CanalAnime("Crunchyroll en Español", "https://www.youtube.com/@CrunchyrollenEspanol"),
        CanalAnime("Crunchyroll", "https://www.youtube.com/@Crunchyroll"),
    )

    /** Programas que no son episodios aunque duren mucho (podcasts, radio, música, tráileres, entrevistas). */
    private val NO_EPISODIO = Regex(
        "podcast|radio|m[uú]sica|music|soundtrack|\\bost\\b|tr[aá]iler|trailer|\\bpv\\b|punto fandom|entrevista|interview|reaction|reacci[oó]n|relax|estudiar|study",
        RegexOption.IGNORE_CASE,
    )

    /** Un episodio dura al menos ~15 minutos y no es un programa de otro tipo. */
    internal fun esEpisodio(v: Elemento.Video) =
        v.duracionSeg >= 15 * 60 && !v.esDirecto && !v.esShort && !NO_EPISODIO.containsMatchIn(v.titulo)

    private fun esCanalOficial(v: Elemento.Video): Boolean {
        val autor = v.autor?.lowercase() ?: return false
        return CANALES.any { autor == it.nombre.lowercase() || autor.replace(" ", "") == it.url.substringAfter("@").lowercase() }
    }

    /** Lo último de todos los canales, mezclado y del más nuevo al más viejo. */
    suspend fun recientes(): List<Elemento.Video> = coroutineScope {
        CANALES.map { c -> async { runCatching { Youtube.canal(c.url).videos.elementos }.getOrDefault(emptyList()) } }
            .awaitAll().flatten().filterIsInstance<Elemento.Video>().filter(::esEpisodio)
            .distinctBy { it.url }.sortedByDescending { it.fechaMs }
    }

    /** Busca un anime: en lo último de los canales oficiales y en YouTube, pero solo episodios de esos canales. */
    suspend fun buscar(texto: String): List<Elemento.Video> = coroutineScope {
        val locales = async { runCatching { recientes() }.getOrDefault(emptyList()).filter { it.titulo.contains(texto, ignoreCase = true) } }
        val enYoutube = async {
            runCatching { Youtube.buscar("$texto episodio completo", FiltroBusqueda.VIDEOS).elementos }.getOrDefault(emptyList())
                .filterIsInstance<Elemento.Video>().filter { esEpisodio(it) && esCanalOficial(it) }
        }
        (locales.await() + enYoutube.await()).distinctBy { it.url }
    }
}
