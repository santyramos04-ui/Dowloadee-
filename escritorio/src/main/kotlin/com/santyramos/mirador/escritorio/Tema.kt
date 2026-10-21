package com.santyramos.mirador.escritorio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Los mismos colores «Marino» de la app del celular (azul marino, crema y rojo). */
@Immutable
data class Colores(
    val fondo: Color, val s1: Color, val s2: Color, val s3: Color, val linea: Color,
    val texto1: Color, val texto2: Color, val texto3: Color,
    val acento: Color, val acentoSuave: Color, val acentoBorde: Color,
    val relleno: Color, val sobreRelleno: Color, val error: Color,
)

private val Rojo = Color(0xFFC11720)

val Oscuro = Colores(
    fondo = Color(0xFF08202F), s1 = Color(0xFF0C324A), s2 = Color(0xFF123E5A), s3 = Color(0xFF1A4A69), linea = Color(0xFF1D4C6B),
    texto1 = Color(0xFFFEF1D5), texto2 = Color(0xFFA9C3D6), texto3 = Color(0xFF8DAFC6),
    acento = Color(0xFF8DB8D3), acentoSuave = Color(0xFF17435F), acentoBorde = Color(0xFF3E6F90),
    relleno = Rojo, sobreRelleno = Color.White, error = Color(0xFFFF8A80),
)

val Claro = Colores(
    fondo = Color(0xFFFEF1D5), s1 = Color(0xFFFFF9EC), s2 = Color(0xFFF5E6C4), s3 = Color(0xFFEDDBB3), linea = Color(0xFFE6D2A8),
    texto1 = Color(0xFF0C324A), texto2 = Color(0xFF2F5670), texto3 = Color(0xFF4A6B80),
    acento = Rojo, acentoSuave = Color(0xFFFADFD9), acentoBorde = Color(0xFFE3A39B),
    relleno = Rojo, sobreRelleno = Color.White, error = Color(0xFFB3261E),
)

val LocalColores = staticCompositionLocalOf { Oscuro }
val P: Colores @Composable @ReadOnlyComposable get() = LocalColores.current

private val Titulos = FontFamily(
    Font("fuentes/titulos_semibold.ttf", FontWeight.SemiBold),
    Font("fuentes/titulos_bold.ttf", FontWeight.Bold),
)
private val Texto = FontFamily(
    Font("fuentes/texto_regular.ttf", FontWeight.Normal),
    Font("fuentes/texto_medium.ttf", FontWeight.Medium),
    Font("fuentes/texto_semibold.ttf", FontWeight.SemiBold),
    Font("fuentes/texto_bold.ttf", FontWeight.Bold),
)

private val Tipografia = Typography(
    headlineMedium = TextStyle(fontFamily = Titulos, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Titulos, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = Texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Texto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = Texto, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Texto, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Texto, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Texto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Texto, fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
)

@Composable
fun TemaMirador(contenido: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) Oscuro else Claro
    val base = if (c == Oscuro) darkColorScheme() else lightColorScheme()
    val esquema = base.copy(
        primary = c.acento, onPrimary = c.fondo, primaryContainer = c.acentoSuave, onPrimaryContainer = c.acento,
        background = c.fondo, onBackground = c.texto1, surface = c.fondo, onSurface = c.texto1,
        surfaceVariant = c.s2, onSurfaceVariant = c.texto2, surfaceContainer = c.s1, surfaceContainerHigh = c.s2,
        surfaceContainerHighest = c.s3, outline = c.acentoBorde, outlineVariant = c.linea, error = c.error,
    )
    CompositionLocalProvider(LocalColores provides c) {
        MaterialTheme(
            colorScheme = esquema, typography = Tipografia,
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp)),
            content = contenido,
        )
    }
}
