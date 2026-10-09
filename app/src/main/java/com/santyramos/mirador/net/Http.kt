package com.santyramos.mirador.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Un único cliente HTTP para toda la app (comparte conexiones y caché de DNS). */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
