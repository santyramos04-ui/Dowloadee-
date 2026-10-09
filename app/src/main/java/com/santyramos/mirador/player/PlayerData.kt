package com.santyramos.mirador.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.santyramos.mirador.net.Http
import com.santyramos.mirador.net.NewPipeDownloader
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cómo se piden los datos de video a YouTube (misma lógica que usa NewPipe):
 *  - El User-Agent debe coincidir con el cliente que generó la URL (c=VISIONOS).
 *  - YouTube pide los trozos con un parámetro `range=inicio-fin` en la URL en vez de la
 *    cabecera Range, y un contador `rn=` que sube en cada petición.
 */
object PlayerData {
    private val cliente: OkHttpClient by lazy {
        Http.client.newBuilder().addInterceptor(Interceptor { chain ->
            val req = chain.request()
            val ua = if (esGoogleVideo(req.url.host)) {
                if (req.url.queryParameter("c") == "VISIONOS") YoutubeParsingHelper.getVisionOsUserAgent(null)
                else NewPipeDownloader.USER_AGENT
            } else null
            chain.proceed(if (ua != null) req.newBuilder().header("User-Agent", ua).build() else req)
        }).build()
    }

    fun esGoogleVideo(host: String?) = host != null && (host.endsWith(".googlevideo.com") || host == "googlevideo.com")

    private fun base(): DataSource.Factory = OkHttpDataSource.Factory(cliente).setUserAgent(NewPipeDownloader.USER_AGENT)

    /** Para streams unidos por manifiestos DASH: el rango va en la URL. */
    fun fabricaDash(): DataSource.Factory = ResolvingDataSource.Factory(base(), YoutubeResolver(usarRango = true))

    /** Para archivos progresivos con audio y video juntos: rango normal en la cabecera. */
    fun fabricaProgresiva(): DataSource.Factory = ResolvingDataSource.Factory(base(), YoutubeResolver(usarRango = false))

    /** Para HLS y cualquier otra URL. */
    fun fabricaGenerica(): DataSource.Factory = base()

    internal class YoutubeResolver(private val usarRango: Boolean) : ResolvingDataSource.Resolver {
        private val contador = AtomicInteger(0)

        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val uri = dataSpec.uri
            if (!esGoogleVideo(uri.host) || uri.path?.startsWith("/videoplayback") != true) return dataSpec
            var url = uri.toString()
            if (uri.getQueryParameter("rn") == null) url += "&rn=" + contador.getAndIncrement()
            val rango = if (usarRango && uri.getQueryParameter("range") == null) {
                construirRango(dataSpec.position, dataSpec.length)
            } else null
            if (rango == null) return dataSpec.buildUpon().setUri(Uri.parse(url)).build()
            return dataSpec.buildUpon()
                .setUri(Uri.parse(url + rango))
                // El servidor ya devuelve solo ese trozo: no hay que volver a pedir un rango.
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()
        }
    }

    /** `&range=inicio-fin`, o null si se pide el archivo completo. */
    internal fun construirRango(posicion: Long, longitud: Long): String? {
        if (posicion == 0L && longitud == C.LENGTH_UNSET.toLong()) return null
        return "&range=" + posicion + "-" + (if (longitud != C.LENGTH_UNSET.toLong()) (posicion + longitud - 1).toString() else "")
    }
}
