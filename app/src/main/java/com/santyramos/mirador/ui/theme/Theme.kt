package com.santyramos.mirador.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santyramos.mirador.R

/** Buganvilla (#E85A8C): el color de la marca, tomado de las flores de los balcones (miradores) colombianos. */
val AcentoMarca = Color(0xFFE85A8C)

/** Paleta completa de un tema. Rejilla de 4 dp, sin sombras: la profundidad sale de tonos de superficie y líneas finas. */
@androidx.compose.runtime.Immutable
data class Colores(
    val fondo: Color, val s1: Color, val s2: Color, val s3: Color, val linea: Color,
    val texto1: Color, val texto2: Color, val texto3: Color,
    val acento: Color, val acentoSuave: Color, val acentoBorde: Color, val sobreAcento: Color,
    val aviso: Color, val error: Color, val errorSuave: Color,
)

/** Oscuro: azul de noche (como la vista desde un mirador al anochecer), blanco de cal y buganvilla. */
val ColoresOscuros = Colores(
    fondo = Color(0xFF0F1420), s1 = Color(0xFF182031), s2 = Color(0xFF212A3D), s3 = Color(0xFF2A344A), linea = Color(0xFF2C374D),
    texto1 = Color(0xFFF1EEE6), texto2 = Color(0xFFA5AEC2), texto3 = Color(0xFF8C96AB), // 8.4:1 y 5.9:1 sobre el fondo
    acento = AcentoMarca, acentoSuave = Color(0xFF3A1B2B), acentoBorde = Color(0xFF7A2E4E), sobreAcento = Color(0xFF14090F),
    aviso = Color(0xFFF2B544), error = Color(0xFFFF7A7A), errorSuave = Color(0xFF3A1A1E),
)

/** Claro: gris niebla frío y blanco; la buganvilla se oscurece para que texto e íconos tengan contraste (≥ 4.5:1). */
val ColoresClaros = Colores(
    fondo = Color(0xFFF4F5F8), s1 = Color(0xFFFFFFFF), s2 = Color(0xFFEAECF1), s3 = Color(0xFFDFE2E9), linea = Color(0xFFD6DAE3),
    texto1 = Color(0xFF161A24), texto2 = Color(0xFF4A5263), texto3 = Color(0xFF5F6778),
    acento = Color(0xFFC22A63), acentoSuave = Color(0xFFF8DCE7), acentoBorde = Color(0xFFE4A3BC), sobreAcento = Color(0xFFFFFFFF),
    aviso = Color(0xFF8A5A00), error = Color(0xFFC62828), errorSuave = Color(0xFFFBE4E4),
)

val LocalColores = androidx.compose.runtime.staticCompositionLocalOf { ColoresOscuros }

/** Los colores del tema actual. Se usan igual que antes (`Paleta.S1`), pero cambian solos entre claro y oscuro. */
object Paleta {
    val Negro: Color @Composable @ReadOnlyComposable get() = LocalColores.current.fondo
    val S1: Color @Composable @ReadOnlyComposable get() = LocalColores.current.s1
    val S2: Color @Composable @ReadOnlyComposable get() = LocalColores.current.s2
    val S3: Color @Composable @ReadOnlyComposable get() = LocalColores.current.s3
    val Linea: Color @Composable @ReadOnlyComposable get() = LocalColores.current.linea
    val Texto1: Color @Composable @ReadOnlyComposable get() = LocalColores.current.texto1
    val Texto2: Color @Composable @ReadOnlyComposable get() = LocalColores.current.texto2
    val Texto3: Color @Composable @ReadOnlyComposable get() = LocalColores.current.texto3
    val AcentoSuave: Color @Composable @ReadOnlyComposable get() = LocalColores.current.acentoSuave
    val AcentoBorde: Color @Composable @ReadOnlyComposable get() = LocalColores.current.acentoBorde
    val SobreAcento: Color @Composable @ReadOnlyComposable get() = LocalColores.current.sobreAcento
    val Aviso: Color @Composable @ReadOnlyComposable get() = LocalColores.current.aviso
    val Error: Color @Composable @ReadOnlyComposable get() = LocalColores.current.error
    val ErrorSuave: Color @Composable @ReadOnlyComposable get() = LocalColores.current.errorSuave
}

/** Verde de acento del tema actual (el de la marca en oscuro; uno más profundo en claro). */
val Acento: Color @Composable @ReadOnlyComposable get() = LocalColores.current.acento

private fun esquema(c: Colores, oscuro: Boolean): androidx.compose.material3.ColorScheme {
    val base = if (oscuro) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.acento, onPrimary = c.sobreAcento, primaryContainer = c.acentoSuave, onPrimaryContainer = c.acento,
        secondary = c.texto2, onSecondary = c.fondo, secondaryContainer = c.acentoSuave, onSecondaryContainer = c.acento,
        tertiary = c.aviso,
        background = c.fondo, onBackground = c.texto1, surface = c.fondo, onSurface = c.texto1,
        surfaceVariant = c.s2, onSurfaceVariant = c.texto2,
        surfaceContainerLowest = c.fondo, surfaceContainerLow = c.s1, surfaceContainer = c.s1,
        surfaceContainerHigh = c.s2, surfaceContainerHighest = c.s3,
        error = c.error, onError = c.fondo, errorContainer = c.errorSuave, onErrorContainer = c.error,
        outline = if (oscuro) Color(0xFF3D4A63) else Color(0xFFB3B9C6), outlineVariant = c.linea,
        scrim = Color(0xCC000000),
    )
}

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

// Tipografías incluidas (licencia OFL):
// - Bricolage Grotesque, algo condensada, para títulos y la marca: tiene carácter sin perder claridad.
// - Atkinson Hyperlegible Next para el texto: hecha para leerse bien, incluso con baja visión.
// Archivos estáticos (uno por grosor): las fuentes variables no aplicaban el grosor en Android.
val Titulos = FontFamily(
    Font(R.font.titulos_semibold, FontWeight.SemiBold),
    Font(R.font.titulos_bold, FontWeight.Bold),
)
val Texto = FontFamily(
    Font(R.font.texto_regular, FontWeight.Normal),
    Font(R.font.texto_medium, FontWeight.Medium),
    Font(R.font.texto_semibold, FontWeight.SemiBold),
    Font(R.font.texto_bold, FontWeight.Bold),
)

private val Tipografia = Typography(
    displaySmall = TextStyle(fontFamily = Titulos, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Titulos, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Titulos, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Titulos, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Texto, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Texto, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Texto, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Texto, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Texto, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp),
)

/** ¿Hay que usar el tema oscuro? Según el ajuste (Automático sigue al celular). */
@Composable
fun temaOscuro(): Boolean {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val tema by remember(contexto) { com.santyramos.mirador.data.Settings(contexto).tema }
        .collectAsState(initial = com.santyramos.mirador.data.Tema.AUTO)
    return when (tema) {
        com.santyramos.mirador.data.Tema.AUTO -> androidx.compose.foundation.isSystemInDarkTheme()
        com.santyramos.mirador.data.Tema.CLARO -> false
        com.santyramos.mirador.data.Tema.OSCURO -> true
    }
}

/** Tema de Mirador: oscuro (negro OLED) o claro. */
@Composable
fun MiradorTheme(oscuro: Boolean = temaOscuro(), content: @Composable () -> Unit) {
    val colores = if (oscuro) ColoresOscuros else ColoresClaros
    // Íconos de la barra de estado y de navegación: oscuros sobre fondo claro y claros sobre fondo oscuro.
    val vista = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val ventana = (vista.context as? android.app.Activity)?.window ?: return@SideEffect
        val control = androidx.core.view.WindowCompat.getInsetsController(ventana, vista)
        control.isAppearanceLightStatusBars = !oscuro
        control.isAppearanceLightNavigationBars = !oscuro
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalColores provides colores) {
        MaterialTheme(colorScheme = esquema(colores, oscuro), typography = Tipografia, shapes = Formas, content = content)
    }
}
