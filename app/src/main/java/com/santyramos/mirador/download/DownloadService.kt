package com.santyramos.mirador.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.santyramos.mirador.data.db.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano: mantiene vivas las descargas con la pantalla apagada
 * (bloqueo de CPU y de Wi‑Fi mientras haya trabajo) y muestra el progreso.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observador: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.crearCanales(this)
        ServiceCompat.startForeground(
            this, Notifications.ID_SERVICIO, Notifications.servicio(this, emptyList(), 0),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        tomarBloqueos()
        DownloadCenter.bombear()
        if (observador == null) {
            observador = scope.launch {
                var vacioDesde = 0L
                DownloadCenter.observarTodas().collectLatest { todas ->
                    val activas = todas.filter { it.status == DownloadStatus.RUNNING }
                    val enCola = todas.count { it.status == DownloadStatus.QUEUED }
                    Notifications.actualizarServicio(this@DownloadService, activas, enCola)
                    if (enCola > 0) DownloadCenter.bombear()
                    if (activas.isEmpty() && enCola == 0 && !DownloadCenter.hayTrabajo()) {
                        if (vacioDesde == 0L) vacioDesde = System.currentTimeMillis()
                        delay(2500)
                        if (System.currentTimeMillis() - vacioDesde >= 2400) {
                            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    } else vacioDesde = 0L
                }
            }
        }
        return START_STICKY
    }

    private fun tomarBloqueos() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mirador:descargas").apply {
                setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L)
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val modo = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(modo, "Mirador:wifi").apply { setReferenceCounted(false); acquire() }
        }
    }

    override fun onDestroy() {
        observador?.cancel()
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        scope.cancel()
        super.onDestroy()
    }
}
