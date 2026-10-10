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

/** Rojo de la marca (#C11720): el del logo y los botones principales. */
val AcentoMarca = Color(0xFFC11720)

/** Paleta completa de un tema. Rejilla de 4 dp, sin sombras: la profundidad sale de tonos de superficie y líneas finas.
 *  `acento` es para texto e íconos destacados; `relleno` es el fondo de botones y casillas marcadas, con `sobreRelleno` encima. */
@androidx.compose.runtime.Immutable
data class Colores(
    val fondo: Color, val s1: Color, val s2: Color, val s3: Color, val linea: Color,
    val texto1: Color, val texto2: Color, val texto3: Color,
    val acento: Color, val acentoSuave: Color, val acentoBorde: Color, val sobreAcento: Color,
    val relleno: Color, val sobreRelleno: Color,
    val aviso: Color, val error: Color, val errorSuave: Color,
)

/** Oscuro «Marino»: azul marino, crema y rojo. El rojo no se lee como texto sobre el marino (2.2:1),
 *  así que solo rellena botones (blanco encima, 6.2:1); el texto destacado va en azul claro (7.9:1). */
val ColoresOscuros = Colores(
    fondo = Color(0xFF08202F), s1 = Color(0xFF0C324A), s2 = Color(0xFF123E5A), s3 = Color(0xFF1A4A69), linea = Color(0xFF1D4C6B),
    texto1 = Color(0xFFFEF1D5), texto2 = Color(0xFFA9C3D6), texto3 = Color(0xFF8DAFC6), // 14.9, 9.1 y 7.2:1 sobre el fondo
    acento = Color(0xFF8DB8D3), acentoSuave = Color(0xFF17435F), acentoBorde = Color(0xFF3E6F90), sobreAcento = Color(0xFF08202F),
    relleno = AcentoMarca, sobreRelleno = Color(0xFFFFFFFF),
    aviso = Color(0xFFF2B544), error = Color(0xFFFF8A80), errorSuave = Color(0xFF3A1A22),
)

/** Claro «Marino»: crema de fondo, texto azul marino y rojo para lo destacado (5.5:1 sobre la crema). */
val ColoresClaros = Colores(
    fondo = Color(0xFFFEF1D5), s1 = Color(0xFFFFF9EC), s2 = Color(0xFFF5E6C4), s3 = Color(0xFFEDDBB3), linea = Color(0xFFE6D2A8),
    texto1 = Color(0xFF0C324A), texto2 = Color(0xFF2F5670), texto3 = Color(0xFF4A6B80),
    acento = AcentoMarca, acentoSuave = Color(0xFFFADFD9), acentoBorde = Color(0xFFE3A39B), sobreAcento = Color(0xFFFFFFFF),
    relleno = AcentoMarca, sobreRelleno = Color(0xFFFFFFFF),
    aviso = Color(0xFF8A5A00), error = Color(0xFFB3261E), errorSuave = Color(0xFFFBE0DC),
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
    val Relleno: Color @Composable @ReadOnlyComposable get() = LocalColores.current.relleno
    val SobreRelleno: Color @Composable @ReadOnlyComposable get() = LocalColores.current.sobreRelleno
    val Aviso: Color @Composable @ReadOnlyComposable get() = LocalColores.current.aviso
    val Error: Color @Composable @ReadOnlyComposable get() = LocalColores.current.error
    val ErrorSuave: Color @Composable @ReadOnlyComposable get() = LocalColores.current.errorSuave
}

/** Color de acento del tema actual para texto e íconos (azul claro en oscuro, rojo de la marca en claro). */
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
        outline = if (oscuro) Color(0xFF3A6482) else Color(0xFFB9A57E), outlineVariant = c.linea,
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
