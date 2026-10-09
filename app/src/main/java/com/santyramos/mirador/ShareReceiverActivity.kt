package com.santyramos.mirador

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.download.UrlTools
import com.santyramos.mirador.ui.DownloadSheetContent
import com.santyramos.mirador.ui.theme.MiradorTheme

/**
 * «Compartir → Mirador»: se abre como una hoja inferior encima de la app de origen (X, Instagram,
 * TikTok, Facebook, el navegador…), sin sacarte de ella.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texto = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else intent?.dataString
        val enlaces = UrlTools.todosEnlaces(texto).ifEmpty { UrlTools.todosEnlaces(intent?.getStringExtra(Intent.EXTRA_SUBJECT)) }
        val url = enlaces.firstOrNull()
        setContent {
            MiradorTheme {
                if (url == null) {
                    Toast.makeText(this, "No encontré un enlace en lo que compartiste", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    HojaCompartir(enlaces)
                }
            }
        }
    }

    @Composable
    private fun HojaCompartir(enlaces: List<String>) {
        val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { finish() }, sheetState = estado) {
            Column(Modifier.padding(top = 0.dp)) {
                Text("Descargar con Mirador", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                if (enlaces.size > 1) {
                    com.santyramos.mirador.ui.LoteSheetContent(urls = enlaces, onCerrar = { finish() }, onEncolada = { finish() })
                } else {
                    DownloadSheetContent(
                        url = enlaces.first(),
                        onCerrar = { finish() },
                        onEncolada = {
                            Toast.makeText(this@ShareReceiverActivity, "Descarga iniciada ⬇", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                    )
                }
            }
        }
    }
}
