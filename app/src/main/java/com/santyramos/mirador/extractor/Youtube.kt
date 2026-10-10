package com.santyramos.mirador.extractor

import com.santyramos.mirador.net.NewPipeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Qué se muestra en las listas (resultados de búsqueda, videos de un canal...). */
sealed interface Elemento {
    val titulo: String
    val url: String
    val miniatura: String?

    data class Video(
        override val titulo: String, override val url: String, override val miniatura: String?,
        val autor: String?, val urlAutor: String?, val duracionSeg: Long, val vistas: Long,
        val fecha: String?, val esShort: Boolean, val esDirecto: Boolean,
    ) : Elemento

    data class Canal(
        override val titulo: String, override val url: String, override val miniatura: String?,
        val suscriptores: Long, val descripcion: String?,
    ) : Elemento

    data class Lista(
        override val titulo: String, override val url: String, override val miniatura: String?,
        val autor: String?, val cantidad: Long,
    ) : Elemento
}

data class Pagina<T>(val elementos: List<T>, val siguiente: Page?)

enum class FiltroBusqueda(val etiqueta: String, val clave: String) {
    TODO("Todo", YoutubeSearchQueryHandlerFactory.ALL),
    VIDEOS("Videos", YoutubeSearchQueryHandlerFactory.VIDEOS),
    CANALES("Canales", YoutubeSearchQueryHandlerFactory.CHANNELS),
    LISTAS("Listas", YoutubeSearchQueryHandlerFactory.PLAYLISTS),
}

/** Acceso a YouTube a través de NewPipeExtractor (sin cuenta de Google, sin anuncios). */
object Youtube {
    @Volatile private var iniciado = false

    @Synchronized
    fun iniciar(downloader: NewPipeDownloader = NewPipeDownloader()) {
        if (iniciado) return
        NewPipe.init(downloader, Localization("es", "CO"), ContentCountry("CO"))
        iniciado = true
    }

    private val servicio get() = ServiceList.YouTube

    fun aElemento(item: InfoItem): Elemento? = when (item) {
        is StreamInfoItem -> Elemento.Video(
            titulo = item.name.orEmpty(), url = item.url, miniatura = item.thumbnails.bestUrl(),
            autor = item.uploaderName, urlAutor = item.uploaderUrl, duracionSeg = item.duration,
            vistas = item.viewCount, fecha = item.textualUploadDate, esShort = item.isShortFormContent,
            esDirecto = item.streamType.name.contains("LIVE"),
        )
        is ChannelInfoItem -> Elemento.Canal(item.name.orEmpty(), item.url, item.thumbnails.bestUrl(), item.subscriberCount, item.description)
        is PlaylistInfoItem -> Elemento.Lista(item.name.orEmpty(), item.url, item.thumbnails.bestUrl(), item.uploaderName, item.streamCount)
        else -> null
    }

    suspend fun buscar(consulta: String, filtro: FiltroBusqueda = FiltroBusqueda.TODO): Pagina<Elemento> =
        withContext(Dispatchers.IO) {
            iniciar()
            val qh = servicio.searchQHFactory.fromQuery(consulta, listOf(filtro.clave), "")
            val info = SearchInfo.getInfo(servicio, qh)
            Pagina(info.relatedItems.mapNotNull(::aElemento), info.nextPage)
        }

    suspend fun masBusqueda(consulta: String, filtro: FiltroBusqueda, pagina: Page): Pagina<Elemento> =
        withContext(Dispatchers.IO) {
            iniciar()
            val qh = servicio.searchQHFactory.fromQuery(consulta, listOf(filtro.clave), "")
            val p = SearchInfo.getMoreItems(servicio, qh, pagina)
            Pagina(p.items.mapNotNull(::aElemento), p.nextPage)
        }

    suspend fun sugerencias(consulta: String): List<String> = withContext(Dispatchers.IO) {
        iniciar()
        runCatching { servicio.suggestionExtractor.suggestionList(consulta) }.getOrDefault(emptyList())
    }

    suspend fun video(url: String): StreamInfo = withContext(Dispatchers.IO) {
        iniciar()
        StreamInfo.getInfo(servicio, url)
    }

    class DatosCanal(val info: ChannelInfo, val videos: Pagina<Elemento>)

    suspend fun canal(url: String): DatosCanal = withContext(Dispatchers.IO) {
        iniciar()
        val info = ChannelInfo.getInfo(servicio, url)
        val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
        val videos = if (tab != null) {
            val t = ChannelTabInfo.getInfo(servicio, tab)
            Pagina(t.relatedItems.mapNotNull(::aElemento), t.nextPage)
        } else Pagina(emptyList(), null)
        DatosCanal(info, videos)
    }

    suspend fun masVideosCanal(info: ChannelInfo, pagina: Page): Pagina<Elemento> = withContext(Dispatchers.IO) {
        iniciar()
        val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
            ?: return@withContext Pagina(emptyList(), null)
        val p = ChannelTabInfo.getMoreItems(servicio, tab, pagina)
        Pagina(p.items.mapNotNull(::aElemento), p.nextPage)
    }

    class DatosLista(val info: PlaylistInfo, val videos: Pagina<Elemento>)

    suspend fun lista(url: String): DatosLista = withContext(Dispatchers.IO) {
        iniciar()
        val info = PlaylistInfo.getInfo(servicio, url)
        DatosLista(info, Pagina(info.relatedItems.mapNotNull(::aElemento), info.nextPage))
    }

    suspend fun masLista(url: String, pagina: Page): Pagina<Elemento> = withContext(Dispatchers.IO) {
        iniciar()
        val p = PlaylistInfo.getMoreItems(servicio, url, pagina)
        Pagina(p.items.mapNotNull(::aElemento), p.nextPage)
    }

    /** Tendencias del país configurado (Colombia). Si YouTube ya no ofrece esa página, devuelve null. */
    suspend fun tendencias(): List<Elemento.Video>? = withContext(Dispatchers.IO) {
        iniciar()
        runCatching {
            val k = org.schabi.newpipe.extractor.kiosk.KioskInfo.getInfo(servicio, "https://www.youtube.com/feed/trending")
            k.relatedItems.mapNotNull(::aElemento).filterIsInstance<Elemento.Video>().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    class PaginaComentarios(val info: org.schabi.newpipe.extractor.comments.CommentsInfo, val comentarios: List<Comentario>, val siguiente: Page?)

    /** Los comentarios llegan con entidades HTML (&apos;, &amp;…) y <br>; se dejan como texto normal. */
    internal fun limpiarHtml(t: String): String = t
        .replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")

    private fun aComentario(c: org.schabi.newpipe.extractor.comments.CommentsInfoItem) = Comentario(
        autor = c.uploaderName.orEmpty(), texto = limpiarHtml(c.commentText?.content.orEmpty()), avatar = c.uploaderAvatars.bestUrl(),
        megusta = c.likeCount, fecha = c.textualUploadDate, fijado = c.isPinned, respuestas = c.replyCount,
    )

    suspend fun comentarios(url: String): PaginaComentarios = withContext(Dispatchers.IO) {
        iniciar()
        val info = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(servicio, url)
        PaginaComentarios(info, info.relatedItems.map(::aComentario), info.nextPage)
    }

    suspend fun masComentarios(info: org.schabi.newpipe.extractor.comments.CommentsInfo, pagina: Page): Pair<List<Comentario>, Page?> = withContext(Dispatchers.IO) {
        iniciar()
        val p = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(servicio, info, pagina)
        p.items.map(::aComentario) to p.nextPage
    }
}

data class Comentario(
    val autor: String, val texto: String, val avatar: String?, val megusta: Int, val fecha: String?, val fijado: Boolean, val respuestas: Int,
)

/** La miniatura más grande que no pase de ~720 px de ancho (ahorra datos en las listas). */
fun List<org.schabi.newpipe.extractor.Image>.bestUrl(): String? {
    if (isEmpty()) return null
    val conMedidas = filter { it.width > 0 }
    return (conMedidas.filter { it.width <= 720 }.maxByOrNull { it.width } ?: conMedidas.minByOrNull { it.width } ?: first()).url
}
