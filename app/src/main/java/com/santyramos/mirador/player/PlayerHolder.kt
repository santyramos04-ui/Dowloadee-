package com.santyramos.mirador.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * El reproductor es único para toda la app. Lo "posee" este objeto y la sesión multimedia
 * (notificación y pantalla de bloqueo) lo expone a través de [PlaybackService].
 */
object PlayerHolder {
    @Volatile private var player: ExoPlayer? = null
    private var controlador: ListenableFuture<MediaController>? = null

    @Synchronized
    fun player(context: Context): ExoPlayer {
        player?.let { return it }
        val ctx = context.applicationContext
        val p = ExoPlayer.Builder(ctx)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 90_000, 1_500, 3_000)
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Mantiene CPU y Wi‑Fi despiertos mientras reproduce con la pantalla apagada.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player = p
        return p
    }

    /** Conecta la app con el servicio de reproducción (así Android lo mantiene vivo en segundo plano). */
    @Synchronized
    fun conectarServicio(context: Context) {
        if (controlador != null) return
        val ctx = context.applicationContext
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        controlador = MediaController.Builder(ctx, token).buildAsync()
    }

    @Synchronized
    fun detener() {
        player?.let { it.stop(); it.clearMediaItems() }
    }
}
