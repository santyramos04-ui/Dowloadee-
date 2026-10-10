package com.santyramos.mirador.util

import com.santyramos.mirador.BuildConfig

/** Nombre visible de la app. Sale de `version.properties` (línea `nombre=`): cambiarlo ahí lo cambia en todas partes. */
object Marca {
    val NOMBRE: String = BuildConfig.NOMBRE_APP
}
