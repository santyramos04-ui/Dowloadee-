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
    val CANALES = listOf(
        CanalAnime("Crunchyroll en Español", "https://www.youtube.com/@CrunchyrollenEspanol"),
        CanalAnime("Crunchyroll", "https://www.youtube.com/@Crunchyroll"),
        CanalAnime("Muse Asia", "https://www.youtube.com/@MuseAsia"),
        CanalAnime("Ani-One Asia", "https://www.youtube.com/@AniOneAsia"),
        CanalAnime("Gundam Info", "https://www.youtube.com/@GUNDAMINFO"),
        CanalAnime("TMS Entertainment", "https://www.youtube.com/@TMSEntertainment"),
        CanalAnime("Toei Animation", "https://www.youtube.com/@ToeiAnimation"),
        CanalAnime("Anime Onegai", "https://www.youtube.com/@AnimeOnegai"),
    )

    /** Un episodio dura al menos ~15 minutos (así se dejan fuera tráileres y clips). */
    internal fun esEpisodio(v: Elemento.Video) = v.duracionSeg >= 15 * 60 && !v.esDirecto && !v.esShort

    /** Lo último de todos los canales, mezclado y del más nuevo al más viejo. */
    suspend fun recientes(): List<Elemento.Video> = coroutineScope {
        CANALES.map { c -> async { runCatching { Youtube.canal(c.url).videos.elementos }.getOrDefault(emptyList()) } }
            .awaitAll().flatten().filterIsInstance<Elemento.Video>().filter(::esEpisodio)
            .distinctBy { it.url }.sortedByDescending { it.fechaMs }
    }

    /** Busca un anime en YouTube y deja solo lo que parece un episodio completo. */
    suspend fun buscar(texto: String): List<Elemento.Video> =
        Youtube.buscar("$texto anime episodio completo", FiltroBusqueda.VIDEOS).elementos
            .filterIsInstance<Elemento.Video>().filter(::esEpisodio)
}
