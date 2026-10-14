package com.santyramos.mirador.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import com.santyramos.mirador.player.VideoController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lo que está sonando en el televisor. */
data class EnTv(val tv: Televisor, val titulo: String, val pausado: Boolean = false)

/**
 * Ver en el televisor: duplicar la pantalla (Miracast, lo hace Android) o enviar el video a un
 * Smart TV por DLNA (el televisor lo reproduce solo y el celular queda libre, incluso bloqueado).
 */
object Tv {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _televisores = MutableStateFlow<List<Televisor>>(emptyList())
    val televisores: StateFlow<List<Televisor>> = _televisores.asStateFlow()
    private val _buscando = MutableStateFlow(false)
    val buscando: StateFlow<Boolean> = _buscando.asStateFlow()
    private val _enTv = MutableStateFlow<EnTv?>(null)
    val enTv: StateFlow<EnTv?> = _enTv.asStateFlow()
    private val _aviso = MutableStateFlow<String?>(null)
    /** Último problema (para mostrarlo en la hoja). */
    val aviso: StateFlow<String?> = _aviso.asStateFlow()
    private var servidor: ServidorLocal? = null

    fun buscar(context: Context) {
        if (_buscando.value) return
        _buscando.value = true; _aviso.value = null
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val candado = wifi.createMulticastLock("mirador-tv").apply { setReferenceCounted(false) }
        scope.launch {
            try {
                candado.acquire()
                _televisores.value = Dlna.buscar()
                if (_televisores.value.isEmpty()) _aviso.value = "No encontré televisores. Revisa que estén encendidos y en el mismo Wi-Fi que el celular."
            } catch (e: Throwable) {
                _aviso.value = "No pude buscar televisores. ¿Estás conectado a un Wi-Fi?"
            } finally {
                runCatching { candado.release() }
                _buscando.value = false
            }
        }
    }

    /** Manda lo que está sonando al televisor elegido y pausa el celular. */
    fun enviar(context: Context, tv: Televisor) {
        val v = VideoController.video.value ?: run { _aviso.value = "No hay nada sonando."; return }
        _aviso.value = null
        scope.launch {
            try {
                cerrarServidor()
                val (url, mime) = when {
                    v.archivoUri?.startsWith("http") == true -> v.archivoUri!! to (v.archivoMime ?: "video/mp4")
                    v.archivoUri != null -> {
                        val s = ServidorLocal(context, Uri.parse(v.archivoUri), v.archivoMime ?: "video/mp4")
                        val u = s.iniciar() ?: throw IllegalStateException("Conéctate a un Wi-Fi para enviar tus descargas al televisor.")
                        servidor = s
                        u to (v.archivoMime ?: "video/mp4")
                    }
                    else -> {
                        val info = v.info ?: throw IllegalStateException("Espera a que el video cargue.")
                        // Los televisores necesitan un archivo con imagen y sonido juntos (las versiones separadas no las entienden).
                        val mezclado = info.videoStreams.filter { !it.isVideoOnly && it.isUrl }
                            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { it.height }.thenByDescending { it.format?.suffix == "mp4" })
                            .firstOrNull()
                        when {
                            mezclado != null -> mezclado.content to (mezclado.format?.mimeType ?: "video/mp4")
                            !info.hlsUrl.isNullOrEmpty() -> info.hlsUrl to "application/vnd.apple.mpegurl"
                            else -> throw IllegalStateException("Este video no se puede enviar al televisor. Prueba duplicando la pantalla.")
                        }
                    }
                }
                Dlna.enviar(tv, url, v.titulo.ifBlank { "Video" }, mime)
                VideoController.player.pause()
                _enTv.value = EnTv(tv, v.titulo)
                TvService.iniciar(context)
            } catch (e: Throwable) {
                cerrarServidor()
                _aviso.value = e.message?.takeIf { !it.startsWith("El televisor") && e is IllegalStateException }
                    ?: "El televisor no aceptó el video. Prueba duplicando la pantalla."
            }
        }
    }

    fun alternarPausa() {
        val actual = _enTv.value ?: return
        scope.launch {
            runCatching { if (actual.pausado) Dlna.reanudar(actual.tv) else Dlna.pausar(actual.tv) }
                .onSuccess { _enTv.value = actual.copy(pausado = !actual.pausado) }
                .onFailure { _aviso.value = "El televisor no respondió." }
        }
    }

    fun detener(context: Context) {
        val actual = _enTv.value
        _enTv.value = null
        scope.launch { actual?.let { Dlna.detener(it.tv) }; cerrarServidor() }
        TvService.detener(context)
    }

    private fun cerrarServidor() { servidor?.detener(); servidor = null }

    /** Abre la opción del sistema para duplicar la pantalla (Miracast / «Proyección inalámbrica» / «Transmitir»). */
    fun abrirDuplicarPantalla(context: Context) {
        val opciones = listOf(
            Intent("android.settings.CAST_SETTINGS"),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        )
        for (i in opciones) {
            val ok = runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) {
                if (i.action == Settings.ACTION_WIRELESS_SETTINGS) android.widget.Toast.makeText(context, "Busca «Proyección» o «Transmitir pantalla».", android.widget.Toast.LENGTH_LONG).show()
                return
            }
        }
    }
}
