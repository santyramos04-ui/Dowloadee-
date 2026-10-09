package com.santyramos.mirador.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Estado compartido entre MainActivity (Picture-in-Picture) y la pantalla del reproductor. */
object PipState {
    /** true mientras la ventana está en modo imagen en imagen. */
    var activo by mutableStateOf(false)

    /** true cuando conviene entrar a PiP al salir de la app (reproductor abierto y sonando). */
    var permitido by mutableStateOf(false)
}
