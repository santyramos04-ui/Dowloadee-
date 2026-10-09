package com.santyramos.mirador.player

import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.santyramos.mirador.R

/**
 * Mantiene el audio sonando con la pantalla apagada y muestra los controles en la
 * notificación y en la pantalla de bloqueo.
 */
class PlaybackService : MediaSessionService() {
    private var sesion: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_mirador) },
        )
        sesion = MediaSession.Builder(this, PlayerHolder.player(this)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = sesion

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si no está sonando nada, el servicio no tiene razón de seguir vivo.
        val p = sesion?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        sesion?.release()
        sesion = null
        super.onDestroy()
    }
}
