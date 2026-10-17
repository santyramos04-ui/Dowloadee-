package com.santyramos.mirador.escritorio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main(args: Array<String>) {
    // Prueba automática del CI sobre la app ya empaquetada (no abre ventanas).
    args.firstOrNull { it.startsWith("--autoprueba=") }?.let { kotlin.system.exitProcess(Autoprueba.correr(java.io.File(it.substringAfter('=')))) }
    ventana()
}

private fun ventana() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = App.nombre,
        icon = painterResource("icono.png"),
        state = rememberWindowState(size = DpSize(1180.dp, 780.dp)),
    ) {
        window.minimumSize = java.awt.Dimension(820, 560)
        TemaMirador { Raiz() }
    }
}

/** Lo que el usuario quiere descargar (abre la hoja de formatos). */
class PedidoDescarga(val url: String, val titulo: String?)

@Composable
fun Raiz() {
    var pestana by remember { mutableIntStateOf(0) }
    var pedido by remember { mutableStateOf<PedidoDescarga?>(null) }
    val activas = CentroDescargas.lista.collectAsState().value.count { it.estado in setOf(Estado.EN_COLA, Estado.DESCARGANDO, Estado.PROCESANDO) }
    Surface(Modifier.fillMaxSize(), color = P.fondo) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = P.s1, modifier = Modifier.fillMaxHeight().width(92.dp)) {
                Box(Modifier.padding(vertical = 16.dp)) {
                    androidx.compose.foundation.Image(painterResource("icono.png"), null, Modifier.size(40.dp))
                }
                val colores = NavigationRailItemDefaults.colors(
                    selectedIconColor = P.acento, selectedTextColor = P.acento, indicatorColor = P.acentoSuave,
                    unselectedIconColor = P.texto3, unselectedTextColor = P.texto3,
                )
                NavigationRailItem(pestana == 0, { pestana = 0 }, { Icon(Icons.Outlined.Home, null) }, label = { Text("Inicio") }, colors = colores)
                NavigationRailItem(
                    pestana == 1, { pestana = 1 },
                    { BadgedBox(badge = { if (activas > 0) Badge { Text("$activas") } }) { Icon(Icons.Outlined.Download, null) } },
                    label = { Text("Descargas") }, colors = colores,
                )
                NavigationRailItem(pestana == 2, { pestana = 2 }, { Icon(Icons.Outlined.Settings, null) }, label = { Text("Ajustes") }, colors = colores)
            }
            VerticalDivider(color = P.linea)
            Box(Modifier.fillMaxSize()) {
                when (pestana) {
                    0 -> InicioPantalla(onDescargar = { pedido = it })
                    1 -> DescargasPantalla(onDescargar = { pedido = it })
                    else -> AjustesPantalla()
                }
            }
        }
    }
    pedido?.let { p ->
        HojaFormato(p, onCerrar = { pedido = null }, onElegido = { f ->
            CentroDescargas.agregar(p.url, f, p.titulo)
            pedido = null
            pestana = 1
        })
    }
}
