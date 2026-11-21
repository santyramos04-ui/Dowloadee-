package com.santyramos.mirador.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.santyramos.mirador.MainActivity
import com.santyramos.mirador.R
import com.santyramos.mirador.player.VideoController

/** Widget de la pantalla de inicio: lo que suena y los botones anterior / reproducir-pausar / siguiente. */
class WidgetRacq : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = actualizar(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val accion = intent.action ?: return
        if (accion !in setOf(ANTERIOR, ALTERNAR, SIGUIENTE)) return
        // Si la app no está abierta (o no hay nada sonando), el botón la abre.
        if (!VideoController.iniciado || VideoController.video.value == null) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        when (accion) {
            ANTERIOR -> VideoController.anterior()
            ALTERNAR -> VideoController.alternar()
            SIGUIENTE -> VideoController.siguiente()
        }
    }

    companion object {
        private const val ANTERIOR = "com.santyramos.mirador.widget.ANTERIOR"
        private const val ALTERNAR = "com.santyramos.mirador.widget.ALTERNAR"
        private const val SIGUIENTE = "com.santyramos.mirador.widget.SIGUIENTE"

        private fun boton(context: Context, accion: String, codigo: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, codigo, Intent(context, WidgetRacq::class.java).setAction(accion),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Redibuja todos los widgets con lo que suena ahora. */
        fun actualizar(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WidgetRacq::class.java))
            if (ids.isEmpty()) return
            val v = if (VideoController.iniciado) VideoController.video.value else null
            val sonando = VideoController.iniciado && VideoController.estado.value.reproduciendo
            val vistas = RemoteViews(context.packageName, R.layout.widget_racq).apply {
                setTextViewText(R.id.w_titulo, v?.titulo?.takeIf { it.isNotBlank() } ?: "Nada sonando")
                setTextViewText(R.id.w_autor, v?.autor ?: "Toca para abrir RACQ")
                setImageViewResource(R.id.w_alternar, if (sonando) R.drawable.ic_w_pause else R.drawable.ic_w_play)
                setOnClickPendingIntent(R.id.w_anterior, boton(context, ANTERIOR, 1))
                setOnClickPendingIntent(R.id.w_alternar, boton(context, ALTERNAR, 2))
                setOnClickPendingIntent(R.id.w_siguiente, boton(context, SIGUIENTE, 3))
                setOnClickPendingIntent(
                    R.id.w_raiz,
                    PendingIntent.getActivity(context, 4, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                )
            }
            manager.updateAppWidget(ids, vistas)
        }
    }
}
