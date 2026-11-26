package com.santyramos.mirador.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.santyramos.mirador.R

/** Los widgets de RACQ: cada uno con su dibujo (layout) y si se mueve con lo que suena. */
enum class TipoWidget(val layout: Int, val conMusica: Boolean) {
    MARINA(R.layout.widget_racq, true),
    CREMA(R.layout.w_crema, true),
    VINILO(R.layout.w_vinilo, true),
    KARAOKE(R.layout.w_karaoke, true),
    SOLO(R.layout.w_solo, true),
    MAPACHE(R.layout.w_mapache, true),
    BUSQUEDA(R.layout.w_busqueda, true),
    COLA(R.layout.w_cola, true),
    PARA_TI(R.layout.w_parati, true),
    DESCARGAS(R.layout.w_descargas, false),
    DESCARGAS_CUADRO(R.layout.w_descargas_cuadro, false),
    PEGAR(R.layout.w_pegar, false),
    NUEVO(R.layout.w_nuevo, false),
    SHORTS(R.layout.w_atajo_shorts, false),
    SORPRESA(R.layout.w_atajo_sorpresa, false),
    ATAJO_DESCARGAS(R.layout.w_atajo_descargas, false),
    DORMIR(R.layout.w_atajo_dormir, false),
}

/** Base de todos: Android pide dibujarlos y aquí se delega en [Widgets]. */
abstract class WidgetBase(private val tipo: TipoWidget) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendiente = goAsync()
        Widgets.dibujar(context, listOf(tipo)) { pendiente.finish() }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, opciones: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }
}

class WidgetCrema : WidgetBase(TipoWidget.CREMA)
class WidgetVinilo : WidgetBase(TipoWidget.VINILO)
class WidgetKaraoke : WidgetBase(TipoWidget.KARAOKE)
class WidgetSoloMapache : WidgetBase(TipoWidget.SOLO)
class WidgetMapache : WidgetBase(TipoWidget.MAPACHE)
class WidgetBusqueda : WidgetBase(TipoWidget.BUSQUEDA)
class WidgetCola : WidgetBase(TipoWidget.COLA)
class WidgetParaTi : WidgetBase(TipoWidget.PARA_TI)
class WidgetDescargas : WidgetBase(TipoWidget.DESCARGAS)
class WidgetDescargasCuadro : WidgetBase(TipoWidget.DESCARGAS_CUADRO)
class WidgetPegar : WidgetBase(TipoWidget.PEGAR)
class WidgetNuevo : WidgetBase(TipoWidget.NUEVO)
class WidgetShorts : WidgetBase(TipoWidget.SHORTS)
class WidgetSorpresa : WidgetBase(TipoWidget.SORPRESA)
class WidgetAtajoDescargas : WidgetBase(TipoWidget.ATAJO_DESCARGAS)
class WidgetDormir : WidgetBase(TipoWidget.DORMIR)
