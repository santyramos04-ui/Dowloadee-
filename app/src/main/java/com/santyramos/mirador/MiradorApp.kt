package com.santyramos.mirador

import android.app.Application
import com.santyramos.mirador.data.lib.Biblioteca
import com.santyramos.mirador.download.DownloadCenter
import com.santyramos.mirador.download.Notifications
import com.santyramos.mirador.download.YtDlpEngine
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.update.YtdlpUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MiradorApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.crearCanales(this)
        Youtube.iniciar()
        DownloadCenter.init(this)
        Biblioteca.init(this)
        YtdlpUpdater.programar(this)
        scope.launch(Dispatchers.IO) {
            // Descomprime Python, ffmpeg y QuickJS la primera vez (tarda unos segundos).
            YtDlpEngine.iniciar(this@MiradorApp)
            YtdlpUpdater.actualizarSiToca(this@MiradorApp)
        }
    }
}
