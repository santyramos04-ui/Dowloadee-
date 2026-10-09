package com.santyramos.mirador

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import com.santyramos.mirador.data.Settings
import com.santyramos.mirador.download.UrlTools
import com.santyramos.mirador.extractor.Youtube
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.AppRaiz
import com.santyramos.mirador.ui.Entrada
import com.santyramos.mirador.ui.PipState
import com.santyramos.mirador.ui.theme.MiradorTheme
import com.santyramos.mirador.update.AppUpdater
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        VideoController.init(this)
        manejarIntent(intent)

        setContent {
            MiradorTheme {
                val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                AppRaiz(
                    onEntrarPip = { entrarPip() },
                    onPortapapelesVisto = { u -> lifecycleScope.launch { Settings(this@MainActivity).marcarPortapapelesVisto(u) } },
                )
            }
        }

        // Entrar a imagen en imagen automáticamente al salir de la app mientras suena un video.
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                androidx.compose.runtime.snapshotFlow { PipState.permitido },
                VideoController.estado,
            ) { permitido, estado -> permitido && estado.reproduciendo }.collectLatest { auto ->
                if (Build.VERSION.SDK_INT >= 31) {
                    runCatching {
                        setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setAutoEnterEnabled(auto).build())
                    }
                }
            }
        }

        // Aviso de versión nueva (como mucho cada 12 horas)
        lifecycleScope.launch {
            val ajustes = Settings(this@MainActivity)
            val ultima = ajustes.appUltimaRevision()
            if (System.currentTimeMillis() - ultima > 12L * 3600 * 1000) {
                ajustes.marcarRevisionApp(System.currentTimeMillis())
                AppUpdater.buscar(this@MainActivity, silencioso = true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        manejarIntent(intent)
    }

    private fun manejarIntent(i: Intent?) {
        if (i == null) return
        i.getStringExtra(EXTRA_DESTINO)?.let { if (it == "descargas") Entrada.pestana = 2 }
        val url = i.dataString ?: return
        if (i.action != Intent.ACTION_VIEW) return
        val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
        when {
            path.startsWith("/playlist") -> Entrada.abrirLista = url
            path.startsWith("/@") || path.startsWith("/channel") || path.startsWith("/c/") -> Entrada.abrirCanal = url
            else -> Entrada.abrirVideo = url
        }
    }

    fun entrarPip() {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android < 12 no tiene entrada automática: se entra a mano.
        if (Build.VERSION.SDK_INT < 31 && PipState.permitido && VideoController.estado.value.reproduciendo) entrarPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.activo = isInPictureInPictureMode
    }

    /** Al volver a la app: si hay un enlace en el portapapeles, ofrece descargarlo. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        lifecycleScope.launch {
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val texto = runCatching { cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() }.getOrNull()
            val enlace = UrlTools.primerEnlace(texto) ?: return@launch
            if (enlace != Settings(this@MainActivity).portapapelesVisto() && Entrada.enlacePortapapeles == null) Entrada.enlacePortapapeles = enlace
        }
    }

    companion object {
        const val EXTRA_DESTINO = "destino"
    }

    @Suppress("unused")
    private fun yt() = Youtube
}
