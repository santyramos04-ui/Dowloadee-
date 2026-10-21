package com.santyramos.mirador.servidor

import com.santyramos.mirador.player.VideoController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Le cuenta a tu servidor por dónde vas (al empezar, cada 10 s, al pausar y al cambiar de video),
 * así «Continuar viendo» funciona igual en el celular, el televisor o la computadora.
 */
object Seguimiento {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var servidor: ServidorJf? = null
    private var vigilancia: Job? = null

    /** Se llama al reproducir algo de [s]: desde ahí se va informando el avance. */
    fun usar(s: ServidorJf) {
        servidor = s
        if (vigilancia?.isActive == true) return
        vigilancia = scope.launch {
            var actual: String? = null
            var ultimaPos = 0L
            launch {
                // Cada 10 s (o al pausar) se manda la posición del video del servidor que está sonando.
                var pausadoAntes = false
                while (isActive) {
                    delay(2000)
                    val id = actual ?: continue
                    val srv = servidor ?: continue
                    val e = VideoController.estado.value
                    ultimaPos = e.posicionMs
                    val pausado = !e.reproduciendo
                    if (pausado != pausadoAntes || (!pausado && (System.currentTimeMillis() / 2000) % 5 == 0L)) {
                        Jellyfin.avance(srv, id, e.posicionMs, pausado)
                    }
                    pausadoAntes = pausado
                }
            }
            VideoController.video.map { it?.urlOriginal }.distinctUntilChanged().collect { url ->
                val srv = servidor ?: return@collect
                actual?.let { Jellyfin.termino(srv, it, ultimaPos) }
                actual = url?.let { Jellyfin.idDePagina(srv, it) }
                actual?.let { Jellyfin.empezo(srv, it, VideoController.estado.value.posicionMs) }
            }
        }
    }
}
