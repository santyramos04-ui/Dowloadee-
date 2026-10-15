package com.santyramos.mirador.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.santyramos.mirador.tv.Tv
import com.santyramos.mirador.ui.theme.Paleta

/** Hoja «Ver en el televisor»: duplicar la pantalla (Miracast) o enviar a un Smart TV (DLNA). */
@Composable
fun TvSheetContent(onCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val televisores by Tv.televisores.collectAsState()
    val buscando by Tv.buscando.collectAsState()
    val enTv by Tv.enTv.collectAsState()
    val aviso by Tv.aviso.collectAsState()
    LaunchedEffect(Unit) { if (enTv == null) Tv.buscar(contexto) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Ver en el televisor", style = MaterialTheme.typography.titleLarge)

        enTv?.let { e ->
            Surface(shape = RoundedCornerShape(18.dp), color = Paleta.AcentoSuave, border = BorderStroke(1.dp, Paleta.AcentoBorde), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reproduciendo en ${e.tv.nombre}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(e.titulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Puedes bloquear el celular: el televisor sigue reproduciendo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotonSecundario(if (e.pausado) "Reanudar" else "Pausar", { Tv.alternarPausa() }, Modifier.weight(1f), icono = if (e.pausado) Icons.Outlined.PlayArrow else Icons.Outlined.Pause)
                        BotonPrimario("Detener", { Tv.detener(contexto) }, Modifier.weight(1f), icono = Icons.Outlined.Stop)
                    }
                }
            }
        }

        Text("Enviar a un Smart TV", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        Text(
            "El televisor reproduce el video solo (debe estar en el mismo Wi-Fi). El celular queda libre y lo puedes bloquear.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val alta by Tv.altaCalidad.collectAsState()
        Text("Calidad para videos de YouTube", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filtro("Alta (hasta 1080p)", alta) { Tv.cambiarCalidad(true) }
            Filtro("Compatible (360p)", !alta) { Tv.cambiarCalidad(false) }
        }
        if (alta) Text(
            "El celular une imagen y sonido para el televisor, así que debe seguir conectado al Wi-Fi. Si tu TV no lo reproduce, elige «Compatible».",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        televisores.forEach { t ->
            FilaTv(Icons.Outlined.Tv, t.nombre, if (enTv?.tv == t) "Conectado" else "Toca para enviar") { Tv.enviar(contexto, t) }
        }
        if (buscando) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            Text("Buscando televisores…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else BotonSecundario("Buscar de nuevo", { Tv.buscar(contexto) }, Modifier.fillMaxWidth(), icono = Icons.Outlined.Refresh)
        aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        Text("Duplicar la pantalla", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
        FilaTv(Icons.Outlined.ScreenShare, "Proyección inalámbrica (Miracast)", "Muestra todo el celular en la TV. Si bloqueas el celular, se corta.") {
            Tv.abrirDuplicarPantalla(contexto)
        }
        FilaTv(Icons.Outlined.Cast, "Chromecast o Google TV", "Abre la app Google Home y toca «Transmitir pantalla».") {
            val i = contexto.packageManager.getLaunchIntentForPackage("com.google.android.apps.chromecast.app")
            if (i != null) contexto.startActivity(i) else Tv.abrirDuplicarPantalla(contexto)
        }
        BotonSecundario("Listo", onCerrar, Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

@Composable
private fun FilaTv(icono: ImageVector, titulo: String, detalle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Paleta.S2, border = BorderStroke(1.dp, Paleta.Linea), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
