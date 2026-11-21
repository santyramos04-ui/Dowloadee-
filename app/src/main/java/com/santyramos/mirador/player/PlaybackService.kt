package com.santyramos.mirador.player

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.santyramos.mirador.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mantiene el audio sonando con la pantalla apagada y muestra los controles en la
 * notificación y en la pantalla de bloqueo. También es la «biblioteca» que ve Android Auto.
 */
class PlaybackService : MediaLibraryService() {
    private var sesion: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Si lo abre el carro sin que la app esté abierta, se prepara aquí.
        if (!VideoController.iniciado) VideoController.init(this)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_mirador) },
        )
        sesion = MediaLibrarySession.Builder(this, ControlesSesion(PlayerHolder.player(this)), Biblioteca()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = sesion

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si no está sonando nada, el servicio no tiene razón de seguir vivo.
        val p = sesion?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        sesion?.release()
        sesion = null
        super.onDestroy()
    }

    private fun <T> enSegundoPlano(bloque: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        scope.launch { runCatching { bloque() }.onSuccess { f.set(it) }.onFailure { f.setException(it) } }
        return f
    }

    private inner class Biblioteca : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(BibliotecaAuto.raiz(), params))

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = enSegundoPlano {
            val item = if (mediaId == BibliotecaAuto.RAIZ) BibliotecaAuto.raiz()
            else BibliotecaAuto.carpetas().firstOrNull { it.mediaId == mediaId }
                ?: BibliotecaAuto.resolver(this@PlaybackService, MediaItem.Builder().setMediaId(mediaId).build())
            if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = enSegundoPlano {
            val todos = BibliotecaAuto.hijos(this@PlaybackService, parentId)
            val desde = (page.toLong() * pageSize).coerceAtMost(todos.size.toLong()).toInt()
            val hasta = (desde.toLong() + pageSize).coerceAtMost(todos.size.toLong()).toInt()
            LibraryResult.ofItemList(ImmutableList.copyOf(todos.subList(desde, hasta)), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = enSegundoPlano {
            mediaItems.mapNotNull { BibliotecaAuto.resolver(this@PlaybackService, it) }.toMutableList()
        }
    }
}
