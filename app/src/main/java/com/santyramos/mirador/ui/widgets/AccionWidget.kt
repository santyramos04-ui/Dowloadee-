package com.santyramos.mirador.ui.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.santyramos.mirador.MainActivity
import com.santyramos.mirador.data.db.DownloadStatus
import com.santyramos.mirador.download.DownloadCenter
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.player.aCola
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Recibe los toques de los botones de los widgets que no necesitan abrir la app. */
class AccionWidget : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accion = Widgets.accionDe(intent) ?: return
        val pendiente = goAsync()
        AccionesWidget.scope.launch {
            try {
                AccionesWidget.ejecutar(context.applicationContext, accion, intent.getIntExtra("i", 0))
            } finally {
                pendiente.finish()
            }
        }
    }
}

/** Lo que hacen los botones y los atajos (también se usa al abrir la app desde un atajo). */
object AccionesWidget {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Temporizador de dormir: cada toque pasa al siguiente (15 → 30 → 60 min → apagado). */
    private val pasosDormir = listOf(15, 30, 60)

    suspend fun ejecutar(c: Context, accion: String, i: Int) {
        when (accion) {
            "OTRAS" -> { Widgets.otrasRecomendaciones(c); return }
            "PAUSAR_DESCARGAS" -> { pausarDescargas(); return }
            "SEGUIR_DESCARGAS" -> { seguirDescargas(); return }
        }
        // Lo demás necesita el reproductor: si la app no está abierta (o no suena nada), se abre.
        if (!VideoController.iniciado || VideoController.video.value == null) {
            runCatching { c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            Widgets.dibujar(c, TipoWidget.entries.filter { it.conMusica })
            return
        }
        when (accion) {
            "ANTERIOR" -> VideoController.anterior()
            "ALTERNAR" -> VideoController.alternar()
            "SIGUIENTE" -> VideoController.siguiente()
            "SALTAR" -> VideoController.reproducirDeCola(i)
            "MEZCLAR" -> VideoController.cambiarAleatorio()
            "REPETIR" -> VideoController.cambiarRepetir()
            "DORMIR" -> {
                val actual = VideoController.temporizador.value?.minutos
                val siguiente = if (actual == null || actual == 0) pasosDormir.first() else pasosDormir.getOrNull(pasosDormir.indexOf(actual) + 1)
                VideoController.programarTemporizador(siguiente)
                Toast.makeText(c, if (siguiente == null) "Temporizador apagado" else "Se pausa en $siguiente min", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun pausarDescargas() {
        val todas = DownloadCenter.observarTodas().first()
        todas.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }.forEach { DownloadCenter.pausar(it.id) }
    }

    private suspend fun seguirDescargas() {
        val todas = DownloadCenter.observarTodas().first()
        todas.filter { it.status == DownloadStatus.PAUSED }.forEach { DownloadCenter.reanudar(it.id) }
    }

    /**
     * «Sorpréndeme»: tus descargas al azar (primero la música; si no hay, los videos). Sin internet.
     * @return false si todavía no hay nada descargado.
     */
    suspend fun sorprendeme(): Boolean {
        val todas = withContext(Dispatchers.IO) { DownloadCenter.observarReproducibles().first() }
        if (todas.isEmpty()) return false
        val musica = todas.filter { it.archivoMime?.startsWith("audio/") == true }
        val lista = (musica.ifEmpty { todas }).shuffled().map { it.aCola() }
        VideoController.reproducirLista(lista)
        return true
    }

    /** «Dormir»: si no suena nada pone tus descargas al azar; se pausa sola en 30 minutos. */
    suspend fun dormir(): Boolean {
        val hayAlgo = VideoController.video.value != null
        if (!hayAlgo && !sorprendeme()) return false
        if (hayAlgo && !VideoController.estado.value.reproduciendo) VideoController.alternar()
        VideoController.programarTemporizador(30)
        return true
    }
}
