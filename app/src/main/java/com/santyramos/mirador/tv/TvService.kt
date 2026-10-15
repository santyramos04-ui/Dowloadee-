package com.santyramos.mirador.tv

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.santyramos.mirador.R
import com.santyramos.mirador.download.Notifications

/**
 * Mantiene viva la app mientras el televisor reproduce (sobre todo si lee una descarga desde el
 * celular), aunque bloquees la pantalla. Muestra una notificación con «Detener».
 */
class TvService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_DETENER) {
            Tv.detener(this)
            return START_NOT_STICKY
        }
        val nombre = Tv.enTv.value?.tv?.nombre ?: "el televisor"
        val detener = PendingIntent.getService(
            this, 1, Intent(this, TvService::class.java).setAction(ACCION_DETENER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val abrir = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(this, Notifications.CANAL_REPRODUCCION)
            .setSmallIcon(R.drawable.ic_stat_mirador)
            .setContentTitle("Reproduciendo en $nombre")
            .setContentText(Tv.enTv.value?.titulo.orEmpty())
            .setOngoing(true)
            .setContentIntent(abrir)
            .addAction(0, "Detener", detener)
            .build()
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, 41, n, tipo)
        return START_NOT_STICKY
    }

    companion object {
        private const val ACCION_DETENER = "com.santyramos.mirador.tv.DETENER"
        fun iniciar(context: Context) {
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, TvService::class.java)) }
        }
        fun detener(context: Context) { context.stopService(Intent(context, TvService::class.java)) }
    }
}
