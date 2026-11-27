package com.santyramos.mirador.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.view.View
import android.widget.RemoteViews
import com.santyramos.mirador.MainActivity
import com.santyramos.mirador.R
import com.santyramos.mirador.data.Settings
import com.santyramos.mirador.data.db.DownloadStatus
import com.santyramos.mirador.data.lib.Biblioteca
import com.santyramos.mirador.download.DownloadCenter
import com.santyramos.mirador.extractor.Elemento
import com.santyramos.mirador.extractor.Letra
import com.santyramos.mirador.extractor.Letras
import com.santyramos.mirador.player.Repetir
import com.santyramos.mirador.player.Ritmo
import com.santyramos.mirador.player.VideoActual
import com.santyramos.mirador.player.VideoController
import com.santyramos.mirador.ui.WidgetRacq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Una recomendación guardada para el widget «Para ti» (así se ve aunque la app esté cerrada). */
@Serializable
data class RecomendacionWidget(val url: String, val titulo: String, val miniatura: String? = null)

/**
 * Dibuja y mantiene al día todos los widgets de RACQ.
 * - Lo que suena (título, pausa, mapache, cola, letra): cuando cambia, y el avance cada segundo con la pantalla encendida.
 * - Descargas y novedades: cuando cambian en la base de datos.
 * El mapache baila con dibujos que pasan solos (como un folioscopio), a uno de tres tempos según [Ritmo].
 */
@OptIn(FlowPreview::class)
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val candado = Mutex()
    private lateinit var app: Context
    @Volatile private var vigilando = false
    private val json = Json { ignoreUnknownKeys = true }

    const val EXTRA_WIDGET = "widget"
    private const val PREFIJO = "com.santyramos.mirador.widget."

    private fun clase(t: TipoWidget): Class<*> = when (t) {
        TipoWidget.MARINA -> WidgetRacq::class.java
        TipoWidget.CREMA -> WidgetCrema::class.java
        TipoWidget.VINILO -> WidgetVinilo::class.java
        TipoWidget.KARAOKE -> WidgetKaraoke::class.java
        TipoWidget.SOLO -> WidgetSoloMapache::class.java
        TipoWidget.MAPACHE -> WidgetMapache::class.java
        TipoWidget.COLA -> WidgetCola::class.java
        TipoWidget.PARA_TI -> WidgetParaTi::class.java
        TipoWidget.DESCARGAS -> WidgetDescargas::class.java
        TipoWidget.DESCARGAS_CUADRO -> WidgetDescargasCuadro::class.java
        TipoWidget.PEGAR -> WidgetPegar::class.java
        TipoWidget.NUEVO -> WidgetNuevo::class.java
    }

    private fun ids(c: Context, t: TipoWidget): IntArray =
        runCatching { AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, clase(t))) }.getOrDefault(IntArray(0))

    private fun hay(c: Context, t: TipoWidget) = ids(c, t).isNotEmpty()

    // ------------------------------------------------------------------ arranque y vigilancia

    /** Desde [com.santyramos.mirador.MiradorApp]: vigila descargas y novedades (no necesita la app abierta). */
    fun iniciar(context: Context) {
        app = context.applicationContext
        scope.launch {
            runCatching {
                DownloadCenter.observarTodas().debounce(800).collect { dibujar(app, listOf(TipoWidget.DESCARGAS, TipoWidget.DESCARGAS_CUADRO)) }
            }
        }
        scope.launch {
            // Durante una pausa de YouTube la cuenta regresiva se refresca cada 30 s.
            while (true) {
                delay(30_000)
                if (DownloadCenter.segundosDePausa() > 0 || DownloadCenter.esperandoWifi) dibujar(app, listOf(TipoWidget.DESCARGAS, TipoWidget.DESCARGAS_CUADRO))
            }
        }
        scope.launch {
            runCatching {
                Biblioteca.dao.observarNovedades().map { it.firstOrNull()?.url }.distinctUntilChanged().collect { dibujar(app, listOf(TipoWidget.NUEVO)) }
            }
        }
    }

    /** Desde [VideoController.init]: redibuja al cambiar lo que suena y lleva el avance y la letra. */
    fun vigilarReproduccion(context: Context) {
        if (vigilando) return
        vigilando = true
        app = context.applicationContext
        val musica = TipoWidget.entries.filter { it.conMusica }
        scope.launch {
            merge(
                VideoController.video.map { Triple(it?.titulo, it?.autor, it?.miniatura) }.distinctUntilChanged(),
                VideoController.estado.map { it.reproduciendo }.distinctUntilChanged(),
                VideoController.cola, VideoController.repetir, VideoController.aleatorio, VideoController.temporizador,
                Ritmo.bpm.map { tempo(it) }.distinctUntilChanged(),
            ).debounce(250).collect { dibujar(app, musica) }
        }
        scope.launch {
            var vuelta = 0
            var lineaVista = Int.MIN_VALUE
            while (true) {
                delay(500)
                vuelta++
                if (!VideoController.estado.value.reproduciendo || !pantallaEncendida()) continue
                if (vuelta % 2 == 0) actualizarAvance()
                if (hay(app, TipoWidget.KARAOKE)) {
                    val i = lineaActual()
                    if (i != lineaVista) { lineaVista = i; actualizarLetra() }
                }
            }
        }
    }

    private fun pantallaEncendida(): Boolean =
        (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    /** Guarda las recomendaciones de «Para ti» para el widget (se llama al calcularlas en la app). */
    fun guardarParaTi(lista: List<Elemento.Video>) {
        if (!::app.isInitialized || lista.isEmpty()) return
        scope.launch {
            runCatching {
                val datos = lista.distinctBy { it.url }.take(16).map { RecomendacionWidget(it.url, it.titulo, it.miniatura) }
                File(app.filesDir, "widget_parati.json").writeText(json.encodeToString(datos))
            }
            dibujar(app, listOf(TipoWidget.PARA_TI))
        }
    }

    private fun paraTi(): List<RecomendacionWidget> =
        runCatching { json.decodeFromString<List<RecomendacionWidget>>(File(app.filesDir, "widget_parati.json").readText()) }.getOrDefault(emptyList())

    fun otrasRecomendaciones(context: Context) {
        val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        prefs.edit().putInt("pagina_parati", prefs.getInt("pagina_parati", 0) + 1).apply()
        dibujar(context, listOf(TipoWidget.PARA_TI))
    }

    // ------------------------------------------------------------------ dibujo

    /** Redibuja los widgets de esos tipos que estén puestos en la pantalla. */
    fun dibujar(context: Context, tipos: Collection<TipoWidget>, alTerminar: (() -> Unit)? = null) {
        val c = context.applicationContext
        if (!::app.isInitialized) app = c
        scope.launch {
            try {
                candado.withLock {
                    val manager = AppWidgetManager.getInstance(c)
                    for (t in tipos) {
                        val lista = ids(c, t)
                        if (lista.isEmpty()) continue
                        val vista = runCatching { vista(c, t) }.getOrNull() ?: continue
                        runCatching { manager.updateAppWidget(lista, vista) }
                    }
                }
            } finally {
                alTerminar?.invoke()
            }
        }
    }

    private data class Ahora(val v: VideoActual?, val sonando: Boolean, val posMs: Long, val durMs: Long)

    private fun ahora(): Ahora {
        if (!VideoController.iniciado) return Ahora(null, false, 0, 0)
        val e = VideoController.estado.value
        return Ahora(VideoController.video.value, e.reproduciendo, e.posicionMs, e.duracionMs)
    }

    /** 0 = dormido, 1 = lento, 2 = medio, 3 = rápido. */
    private fun tempo(bpm: Float): Int = when {
        bpm <= 0f -> 2
        bpm < 92f -> 1
        bpm < 126f -> 2
        else -> 3
    }

    private fun mapache(rv: RemoteViews, sonando: Boolean) {
        val cual = if (sonando) tempo(Ritmo.bpm.value) else 0
        rv.setViewVisibility(R.id.rac_dormido, if (cual == 0) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.rac_lento, if (cual == 1) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.rac_medio, if (cual == 2) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.rac_rapido, if (cual == 3) View.VISIBLE else View.GONE)
    }

    private fun avance(a: Ahora): Int = if (a.durMs > 0) (a.posMs * 1000 / a.durMs).toInt().coerceIn(0, 1000) else 0

    private suspend fun vista(c: Context, t: TipoWidget): RemoteViews {
        val rv = RemoteViews(c.packageName, t.layout)
        val a = ahora()
        val densidad = c.resources.displayMetrics.density
        fun px(dp: Int) = (dp * densidad).toInt()
        when (t) {
            TipoWidget.MARINA, TipoWidget.CREMA -> {
                reproductor(c, rv, a)
                rv.setProgressBar(R.id.w_avance, 1000, avance(a), false)
                rv.setViewVisibility(R.id.w_avance, if (a.v != null && a.durMs > 0) View.VISIBLE else View.INVISIBLE)
            }
            TipoWidget.VINILO -> {
                reproductor(c, rv, a)
                rv.setViewVisibility(R.id.w_vinilo, if (a.sonando) View.VISIBLE else View.GONE)
                rv.setViewVisibility(R.id.w_vinilo_quieto, if (a.sonando) View.GONE else View.VISIBLE)
                val portada = Portadas.cargar(c, a.v?.miniatura, px(58), px(58), 0f, circulo = true)
                if (portada != null) rv.setImageViewBitmap(R.id.w_portada, portada) else rv.setImageViewResource(R.id.w_portada, R.drawable.wc_rojo)
                val tm = if (VideoController.iniciado) VideoController.temporizador.value else null
                val textoTm = when {
                    tm == null -> null
                    tm.alTerminar -> "Se apaga al terminar"
                    tm.finMs != null -> "Duerme en " + ((tm.finMs - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1) + " min"
                    else -> null
                }
                rv.setViewVisibility(R.id.w_temporizador, if (textoTm != null) View.VISIBLE else View.GONE)
                rv.setTextViewText(R.id.w_temporizador, textoTm ?: "")
                rv.setOnClickPendingIntent(R.id.w_lista, abrir(c, "guardar"))
                rv.setOnClickPendingIntent(R.id.w_dormir, boton(c, "DORMIR"))
            }
            TipoWidget.KARAOKE -> {
                reproductor(c, rv, a)
                val v = a.v
                rv.setTextViewText(R.id.w_titulo, if (v == null) "Nada sonando" else listOfNotNull(v.titulo, v.autor).joinToString(" · "))
                if (v != null) asegurarLetra(v, a.durMs)
                letraEn(rv)
            }
            TipoWidget.SOLO -> reproductor(c, rv, a)
            TipoWidget.MAPACHE -> {
                reproductor(c, rv, a)
                rv.setTextViewText(R.id.w_titulo, if (a.v == null) "Nada sonando" else if (a.sonando) a.v.titulo else "En pausa")
                rv.setViewVisibility(R.id.w_zz, if (a.sonando) View.GONE else View.VISIBLE)
            }
            TipoWidget.COLA -> cola(c, rv, a, px(44))
            TipoWidget.PARA_TI -> paraTi(c, rv, a, px(80), px(72))
            TipoWidget.DESCARGAS, TipoWidget.DESCARGAS_CUADRO -> descargas(c, rv, t)
            TipoWidget.PEGAR -> rv.setOnClickPendingIntent(R.id.w_raiz, abrir(c, "pegar"))
            TipoWidget.NUEVO -> nuevo(c, rv, px(96), px(56))
        }
        return rv
    }

    /** Lo común a los reproductores: textos, mapache y los botones. */
    private fun reproductor(c: Context, rv: RemoteViews, a: Ahora) {
        val v = a.v
        rv.setTextViewText(R.id.w_titulo, v?.titulo?.takeIf { it.isNotBlank() } ?: "Nada sonando")
        rv.setTextViewText(R.id.w_autor, if (v == null) "Toca para abrir RACQ" else v.autor ?: "")
        rv.setImageViewResource(R.id.w_alternar, if (a.sonando) R.drawable.wi_pausa else R.drawable.wi_play)
        rv.setContentDescription(R.id.w_alternar, if (a.sonando) "Pausar" else "Reproducir")
        mapache(rv, a.sonando)
        rv.setOnClickPendingIntent(R.id.w_raiz, abrir(c, "reproductor"))
        rv.setOnClickPendingIntent(R.id.w_anterior, boton(c, "ANTERIOR"))
        rv.setOnClickPendingIntent(R.id.w_alternar, boton(c, "ALTERNAR"))
        rv.setOnClickPendingIntent(R.id.w_siguiente, boton(c, "SIGUIENTE"))
    }

    private suspend fun cola(c: Context, rv: RemoteViews, a: Ahora, lado: Int) {
        reproductor(c, rv, a)
        rv.setTextViewText(R.id.w_etiqueta, if (a.v == null) "RACQ" else if (a.sonando) "SONANDO" else "EN PAUSA")
        rv.setProgressBar(R.id.w_avance, 1000, avance(a), false)
        val cola = if (VideoController.iniciado) VideoController.cola.value else emptyList()
        val filas = listOf(
            Fila(R.id.w_fila0, R.id.w_fila0_img, R.id.w_fila0_titulo, R.id.w_fila0_autor, R.id.w_fila0_bajada),
            Fila(R.id.w_fila1, R.id.w_fila1_img, R.id.w_fila1_titulo, R.id.w_fila1_autor, R.id.w_fila1_bajada),
            Fila(R.id.w_fila2, R.id.w_fila2_img, R.id.w_fila2_titulo, R.id.w_fila2_autor, R.id.w_fila2_bajada),
        )
        filas.forEachIndexed { i, f ->
            val e = cola.getOrNull(i)
            rv.setViewVisibility(f.raiz, if (e != null) View.VISIBLE else View.GONE)
            if (e == null) return@forEachIndexed
            rv.setTextViewText(f.titulo, e.titulo)
            val dur = if (e.duracionSeg > 0) " · " + com.santyramos.mirador.util.Format.duracion(e.duracionSeg) else ""
            rv.setTextViewText(f.autor, (e.autor ?: "") + dur)
            rv.setViewVisibility(f.bajada, if (e.esDescarga) View.VISIBLE else View.GONE)
            val img = Portadas.cargar(c, e.miniatura, lado, lado, lado * 0.22f)
            if (img != null) rv.setImageViewBitmap(f.img, img) else rv.setImageViewResource(f.img, R.drawable.wf_miniatura)
            rv.setOnClickPendingIntent(f.raiz, boton(c, "SALTAR", i))
        }
        rv.setViewVisibility(R.id.w_vacia, if (cola.isEmpty()) View.VISIBLE else View.GONE)
        val repetir = if (VideoController.iniciado) VideoController.repetir.value else Repetir.NO
        val mezcla = VideoController.iniciado && VideoController.aleatorio.value
        rv.setInt(R.id.w_repetir, "setColorFilter", if (repetir != Repetir.NO) 0xFFFEF1D5.toInt() else 0xFF5F86A1.toInt())
        rv.setContentDescription(R.id.w_repetir, repetir.etiqueta)
        rv.setInt(R.id.w_mezclar, "setColorFilter", if (mezcla) 0xFFFEF1D5.toInt() else 0xFF5F86A1.toInt())
        rv.setOnClickPendingIntent(R.id.w_mezclar, boton(c, "MEZCLAR"))
        rv.setOnClickPendingIntent(R.id.w_repetir, boton(c, "REPETIR"))
    }

    private data class Fila(val raiz: Int, val img: Int, val titulo: Int, val autor: Int, val bajada: Int)

    private suspend fun paraTi(c: Context, rv: RemoteViews, a: Ahora, ancho: Int, alto: Int) {
        mapache(rv, a.sonando)
        val todas = paraTi()
        rv.setViewVisibility(R.id.w_vacia, if (todas.isEmpty()) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.w_recs, if (todas.isEmpty()) View.GONE else View.VISIBLE)
        rv.setOnClickPendingIntent(R.id.w_raiz, abrir(c, null))
        rv.setOnClickPendingIntent(R.id.w_otras, boton(c, "OTRAS"))
        if (todas.isEmpty()) return
        val paginas = ((todas.size + 3) / 4).coerceAtLeast(1)
        val pagina = c.getSharedPreferences("widgets", Context.MODE_PRIVATE).getInt("pagina_parati", 0) % paginas
        val celdas = listOf(
            Triple(R.id.w_rec0, R.id.w_rec0_img, R.id.w_rec0_titulo), Triple(R.id.w_rec1, R.id.w_rec1_img, R.id.w_rec1_titulo),
            Triple(R.id.w_rec2, R.id.w_rec2_img, R.id.w_rec2_titulo), Triple(R.id.w_rec3, R.id.w_rec3_img, R.id.w_rec3_titulo),
        )
        celdas.forEachIndexed { i, (raiz, img, titulo) ->
            val r = todas.getOrNull(pagina * 4 + i) ?: todas.getOrNull(i)
            rv.setViewVisibility(raiz, if (r != null) View.VISIBLE else View.INVISIBLE)
            if (r == null) return@forEachIndexed
            rv.setTextViewText(titulo, r.titulo)
            rv.setContentDescription(raiz, r.titulo)
            val b = Portadas.cargar(c, r.miniatura, ancho, alto, ancho * 0.15f)
            if (b != null) rv.setImageViewBitmap(img, b) else rv.setImageViewResource(img, R.drawable.wf_miniatura)
            rv.setOnClickPendingIntent(raiz, abrirVideo(c, r.url))
        }
    }

    private suspend fun nuevo(c: Context, rv: RemoteViews, ancho: Int, alto: Int) {
        val n = runCatching { Biblioteca.dao.observarNovedades().first().firstOrNull() }.getOrNull()
        if (n == null) {
            rv.setOnClickPendingIntent(R.id.w_raiz, abrir(c, null))
            return
        }
        val horas = ((System.currentTimeMillis() - n.publicadoMs) / 3_600_000).coerceAtLeast(0)
        val cuando = when {
            horas < 1 -> "NUEVO · hace un momento"
            horas < 24 -> "NUEVO · hace $horas h"
            horas < 48 -> "AYER"
            else -> "HACE ${horas / 24} DÍAS"
        }
        rv.setTextViewText(R.id.w_cuando, cuando)
        rv.setTextViewText(R.id.w_titulo, n.titulo)
        rv.setTextViewText(R.id.w_canal, n.canalNombre)
        Portadas.cargar(c, n.miniatura, ancho, alto, ancho * 0.12f)?.let { rv.setImageViewBitmap(R.id.w_img, it) }
        rv.setOnClickPendingIntent(R.id.w_raiz, abrirVideo(c, n.url))
    }

    private suspend fun descargas(c: Context, rv: RemoteViews, t: TipoWidget) {
        val todas = runCatching { DownloadCenter.observarTodas().first() }.getOrDefault(emptyList())
        val enCurso = todas.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }
        val pausadas = todas.filter { it.status == DownloadStatus.PAUSED }
        val actual = enCurso.firstOrNull { it.status == DownloadStatus.RUNNING } ?: enCurso.firstOrNull()
        val pausaMin = ((DownloadCenter.segundosDePausa() + 59) / 60).toInt()
        val esperandoWifi = DownloadCenter.esperandoWifi
        val soloWifi = runCatching { Settings(c).soloWifi.first() }.getOrDefault(false)
        val pct = ((actual?.progreso ?: 0f).coerceIn(0f, 100f) * 10).toInt()
        rv.setOnClickPendingIntent(R.id.w_raiz, abrirDescargas(c))
        if (t == TipoWidget.DESCARGAS) {
            when {
                actual != null -> {
                    rv.setTextViewText(R.id.w_titulo, actual.titulo)
                    rv.setTextViewText(R.id.w_porcentaje, if (actual.status == DownloadStatus.RUNNING) "${pct / 10} %" else "")
                    rv.setProgressBar(R.id.w_barra, 1000, pct, false)
                    val resto = enCurso.size - 1
                    rv.setTextViewText(R.id.w_detalle, when {
                        esperandoWifi -> "Esperando Wi‑Fi para seguir"
                        pausaMin > 0 -> "YouTube pidió una pausa: sigue en $pausaMin min"
                        resto > 0 -> "$resto más en cola"
                        else -> "Descargando"
                    })
                    rv.setViewVisibility(R.id.w_pausar, View.VISIBLE)
                    rv.setImageViewResource(R.id.w_pausar, R.drawable.wi_pausa)
                    rv.setContentDescription(R.id.w_pausar, "Pausar las descargas")
                    rv.setOnClickPendingIntent(R.id.w_pausar, boton(c, "PAUSAR_DESCARGAS"))
                }
                pausadas.isNotEmpty() -> {
                    rv.setTextViewText(R.id.w_titulo, if (pausadas.size == 1) "1 descarga en pausa" else "${pausadas.size} descargas en pausa")
                    rv.setTextViewText(R.id.w_porcentaje, "")
                    rv.setProgressBar(R.id.w_barra, 1000, ((pausadas.first().progreso.coerceIn(0f, 100f)) * 10).toInt(), false)
                    rv.setTextViewText(R.id.w_detalle, "Toca ▶ para seguir")
                    rv.setViewVisibility(R.id.w_pausar, View.VISIBLE)
                    rv.setImageViewResource(R.id.w_pausar, R.drawable.wi_play)
                    rv.setContentDescription(R.id.w_pausar, "Seguir las descargas")
                    rv.setOnClickPendingIntent(R.id.w_pausar, boton(c, "SEGUIR_DESCARGAS"))
                }
                else -> {
                    val ultima = todas.firstOrNull { it.status == DownloadStatus.DONE }
                    rv.setTextViewText(R.id.w_titulo, "Sin descargas pendientes")
                    rv.setTextViewText(R.id.w_porcentaje, "")
                    rv.setProgressBar(R.id.w_barra, 1000, if (ultima != null) 1000 else 0, false)
                    rv.setTextViewText(R.id.w_detalle, ultima?.let { "Última: ${it.titulo}" } ?: "Toca para descargar algo")
                    rv.setViewVisibility(R.id.w_pausar, View.GONE)
                }
            }
            return
        }
        // Cuadro 2 × 2
        rv.setViewVisibility(R.id.w_wifi, if (soloWifi) View.VISIBLE else View.GONE)
        val recientes = todas.count { it.status == DownloadStatus.DONE && (it.terminadaEn ?: 0) > System.currentTimeMillis() - 6 * 3_600_000L }
        fun numero(n: Int?) {
            rv.setViewVisibility(R.id.w_numero, if (n != null) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.w_icono, if (n == null) View.VISIBLE else View.GONE)
            if (n != null) rv.setTextViewText(R.id.w_numero, n.toString())
        }
        when {
            enCurso.isNotEmpty() && (pausaMin > 0 || esperandoWifi) -> {
                numero(null)
                rv.setImageViewResource(R.id.w_icono, if (esperandoWifi) R.drawable.wi_wifi else R.drawable.wi_reloj)
                rv.setTextViewText(R.id.w_mensaje, if (esperandoWifi) "Esperando Wi‑Fi para seguir" else "YouTube pidió una pausa. Sigue sola en $pausaMin min.")
                rv.setTextViewText(R.id.w_detalle, "${enCurso.size} en cola")
                rv.setViewVisibility(R.id.w_barra, View.GONE)
            }
            enCurso.isNotEmpty() -> {
                numero(enCurso.size)
                rv.setTextViewText(R.id.w_mensaje, if (enCurso.size == 1) "descarga en cola" else "descargas en cola")
                rv.setTextViewText(R.id.w_detalle, actual?.let { it.titulo + if (it.status == DownloadStatus.RUNNING) " · ${pct / 10} %" else "" } ?: "")
                rv.setViewVisibility(R.id.w_barra, View.VISIBLE)
                rv.setProgressBar(R.id.w_barra, 1000, pct, false)
            }
            recientes > 0 -> {
                numero(null)
                rv.setImageViewResource(R.id.w_icono, R.drawable.wi_listo)
                rv.setTextViewText(R.id.w_mensaje, if (recientes == 1) "Listo: 1 descarga terminada" else "Listo: $recientes descargas terminadas")
                rv.setTextViewText(R.id.w_detalle, "Toca para verlas")
                rv.setViewVisibility(R.id.w_barra, View.GONE)
            }
            else -> {
                numero(pausadas.size)
                rv.setTextViewText(R.id.w_mensaje, if (pausadas.isNotEmpty()) "en pausa" else "Sin descargas pendientes")
                rv.setTextViewText(R.id.w_detalle, "Toca para descargar algo")
                rv.setViewVisibility(R.id.w_barra, View.GONE)
            }
        }
    }

    // ------------------------------------------------------------------ actualizaciones rápidas (solo lo que cambia)

    private fun actualizarAvance() {
        val a = ahora()
        val manager = AppWidgetManager.getInstance(app)
        for (t in listOf(TipoWidget.MARINA, TipoWidget.COLA)) {
            val lista = ids(app, t)
            if (lista.isEmpty()) continue
            val rv = RemoteViews(app.packageName, t.layout)
            rv.setProgressBar(R.id.w_avance, 1000, avance(a), false)
            runCatching { manager.partiallyUpdateAppWidget(lista, rv) }
        }
    }

    // ------------------------------------------------------------------ letra (karaoke)

    @Volatile private var letraDe: String? = null
    @Volatile private var letra: Letra? = null
    @Volatile private var buscandoLetra: String? = null

    private fun asegurarLetra(v: VideoActual, durMs: Long) {
        val url = v.urlOriginal
        if (letraDe == url || buscandoLetra == url || v.titulo.isBlank()) return
        buscandoLetra = url
        scope.launch(Dispatchers.IO) {
            val l = runCatching { Letras.buscar(v.titulo, v.autor, durMs / 1000) }.getOrNull()
            letra = l; letraDe = url; buscandoLetra = null
            dibujar(app, listOf(TipoWidget.KARAOKE))
        }
    }

    private fun lineaActual(): Int {
        val l = letra ?: return -1
        if (letraDe != VideoController.video.value?.urlOriginal) return -1
        val pos = VideoController.estado.value.posicionMs + 300
        return l.lineas.indexOfLast { it.ms <= pos }
    }

    private fun letraEn(rv: RemoteViews) {
        val v = VideoController.video.value
        val l = letra.takeIf { v != null && letraDe == v.urlOriginal }
        val lineas = l?.lineas.orEmpty()
        val (antes, ahora, despues) = when {
            v == null -> Triple("", "La letra sale aquí mientras suena", "")
            buscandoLetra == v.urlOriginal || (letraDe != v.urlOriginal) -> Triple("", "Buscando la letra…", "")
            l == null -> Triple("", "Esta canción no tiene letra", "")
            lineas.isEmpty() -> Triple("", l.texto?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Letra sin tiempos", "Ábrela en la app para leerla completa")
            else -> {
                val i = lineaActual()
                if (i < 0) Triple("", "♪", lineas.first().texto)
                else Triple(lineas.getOrNull(i - 1)?.texto.orEmpty(), lineas[i].texto.ifBlank { "♪" }, lineas.getOrNull(i + 1)?.texto.orEmpty())
            }
        }
        rv.setTextViewText(R.id.w_antes, antes)
        rv.setTextViewText(R.id.w_linea, ahora)
        rv.setTextViewText(R.id.w_despues, despues)
    }

    private fun actualizarLetra() {
        val lista = ids(app, TipoWidget.KARAOKE)
        if (lista.isEmpty()) return
        val rv = RemoteViews(app.packageName, TipoWidget.KARAOKE.layout)
        letraEn(rv)
        runCatching { AppWidgetManager.getInstance(app).partiallyUpdateAppWidget(lista, rv) }
    }

    // ------------------------------------------------------------------ toques

    /** Botón que actúa sin abrir la app (si la app no está lista, la abre). */
    private fun boton(c: Context, accion: String, i: Int = 0): PendingIntent {
        if (!VideoController.iniciado && accion !in setOf("OTRAS", "PAUSAR_DESCARGAS", "SEGUIR_DESCARGAS")) return abrir(c, null)
        val intent = Intent(c, AccionWidget::class.java).setAction(PREFIJO + accion).putExtra("i", i)
        return PendingIntent.getBroadcast(c, accion.hashCode() * 31 + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun accionDe(intent: Intent): String? = intent.action?.takeIf { it.startsWith(PREFIJO) }?.removePrefix(PREFIJO)

    /** Abre la app; [que] le dice qué hacer al abrir (buscar, pegar, shorts…). */
    fun abrir(c: Context, que: String?): PendingIntent {
        val intent = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (que != null) intent.putExtra(EXTRA_WIDGET, que)
        return PendingIntent.getActivity(c, 5000 + (que?.hashCode() ?: 0) % 1000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun abrirDescargas(c: Context): PendingIntent {
        val intent = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_DESTINO, "descargas")
        return PendingIntent.getActivity(c, 7000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun abrirVideo(c: Context, url: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url), c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(c, 8000 + (url.hashCode() and 0xFFFF), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
