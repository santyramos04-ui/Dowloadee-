package com.santyramos.mirador.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.santyramos.mirador.MainActivity
import com.santyramos.mirador.R
import com.santyramos.mirador.data.db.DownloadEntity

object Notifications {
    const val CANAL_PROGRESO = "descargas_progreso"
    const val CANAL_RESULTADO = "descargas_resultado"
    const val CANAL_REPRODUCCION = "reproduccion"
    const val CANAL_ACTUALIZACION = "actualizaciones"
    const val ID_SERVICIO = 1001
    private const val BASE_RESULTADO = 20000

    fun crearCanales(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_PROGRESO, "Descargas en curso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progreso de las descargas"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CANAL_RESULTADO, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisa cuando una descarga está lista o falló"
        })
        nm.createNotificationChannel(NotificationChannel(CANAL_REPRODUCCION, "Reproducción", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Controles de reproducción en segundo plano"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CANAL_ACTUALIZACION, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisa cuando hay una versión nueva de Mirador"
        })
    }

    fun puedeNotificar(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun abrirApp(context: Context, destino: String = "descargas"): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_DESTINO, destino),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Notificación fija del servicio de descargas (progreso general). */
    fun servicio(context: Context, activas: List<DownloadEntity>, enCola: Int): Notification {
        val b = NotificationCompat.Builder(context, CANAL_PROGRESO)
            .setSmallIcon(R.drawable.ic_stat_mirador)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(abrirApp(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (activas.isEmpty()) {
            b.setContentTitle("Mirador").setContentText(if (enCola > 0) "$enCola en cola" else "Preparando descarga…")
                .setProgress(0, 0, true)
        } else {
            val a = activas.first()
            val titulo = if (activas.size == 1) a.titulo else "Descargando ${activas.size} archivos"
            val pct = if (a.progreso >= 0) a.progreso.toInt() else 0
            val detalle = buildString {
                if (activas.size == 1) {
                    append(if (a.progreso >= 0) "$pct%" else "Procesando…")
                    if (a.bytesTotales > 0) append(" · ${com.santyramos.mirador.util.Format.bytes(a.bytesDescargados)} de ${com.santyramos.mirador.util.Format.bytes(a.bytesTotales)}")
                    if (a.velocidadBps > 0) append(" · ${com.santyramos.mirador.util.Format.velocidad(a.velocidadBps)}")
                    if (a.etaSeg > 0) append(" · quedan ${com.santyramos.mirador.util.Format.eta(a.etaSeg)}")
                } else {
                    append(activas.joinToString(" · ") { "${if (it.progreso >= 0) it.progreso.toInt() else 0}%" })
                    if (enCola > 0) append(" · $enCola en cola")
                }
            }
            b.setContentTitle(titulo).setContentText(detalle)
                .setProgress(100, pct, a.progreso < 0 && activas.size == 1)
        }
        return b.build()
    }

    fun actualizarServicio(context: Context, activas: List<DownloadEntity>, enCola: Int) {
        if (!puedeNotificar(context)) return
        NotificationManagerCompat.from(context).notify(ID_SERVICIO, servicio(context, activas, enCola))
    }

    fun lista(context: Context, d: DownloadEntity, uri: Uri, mime: String) {
        if (!puedeNotificar(context)) return
        val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, d.id.toInt(), Intent.createChooser(ver, "Abrir con").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CANAL_RESULTADO)
            .setSmallIcon(R.drawable.ic_stat_mirador)
            .setContentTitle("✅ Lista")
            .setContentText(d.titulo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(d.titulo + "\nToca para abrir. Guardado en Descargas/Mirador"))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(BASE_RESULTADO + d.id.toInt(), n)
    }

    fun error(context: Context, d: DownloadEntity, motivo: String) {
        if (!puedeNotificar(context)) return
        val n = NotificationCompat.Builder(context, CANAL_RESULTADO)
            .setSmallIcon(R.drawable.ic_stat_mirador)
            .setContentTitle("❌ Error")
            .setContentText(motivo)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${d.titulo}\n$motivo"))
            .setContentIntent(abrirApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(BASE_RESULTADO + d.id.toInt(), n)
    }

    fun resumenGrupo(context: Context, grupo: Long, nombre: String, ok: Int, errores: Int) {
        if (!puedeNotificar(context)) return
        val texto = if (errores == 0) "Se descargaron $ok archivos" else "Se descargaron $ok; $errores con error (míralos en Descargas)"
        val n = NotificationCompat.Builder(context, CANAL_RESULTADO)
            .setSmallIcon(R.drawable.ic_stat_mirador)
            .setContentTitle(if (errores == 0) "✅ Lista: $nombre" else "⚠ Terminó: $nombre")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$texto\nGuardados en Descargas/Mirador"))
            .setContentIntent(abrirApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(BASE_RESULTADO + 5000 + (grupo % 4000).toInt(), n)
    }

    fun cancelarResultado(context: Context, id: Long) {
        NotificationManagerCompat.from(context).cancel(BASE_RESULTADO + id.toInt())
    }
}
