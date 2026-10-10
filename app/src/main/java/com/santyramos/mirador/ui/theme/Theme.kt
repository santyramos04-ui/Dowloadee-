package com.santyramos.mirador.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.santyramos.mirador.R

/** Verde de Mirador (#3DDC97): el único color de acento de toda la app. */
val Acento = Color(0xFF3DDC97)

/**
 * Sistema de diseño de Mirador: negro OLED + neutros grafito + un solo acento.
 * Rejilla de 4 dp. Sin sombras: la profundidad se logra con tonos de superficie y líneas finas.
 */
object Paleta {
    val Negro = Color(0xFF000000)
    val S1 = Color(0xFF0D0D0F)        // paneles
    val S2 = Color(0xFF151518)        // campos y botones secundarios
    val S3 = Color(0xFF1D1D21)        // pistas, etiquetas
    val Linea = Color(0xFF27272C)     // bordes de 1 dp
    val Texto1 = Color(0xFFF5F5F7)
    val Texto2 = Color(0xFFA3A3AD)    // contraste 7:1 sobre negro
    val Texto3 = Color(0xFF6E6E78)
    val AcentoSuave = Color(0xFF0F2B20)   // fondo de elementos seleccionados
    val AcentoBorde = Color(0xFF1D5A40)
    val SobreAcento = Color(0xFF04281A)
    val Aviso = Color(0xFFF5C451)
    val Error = Color(0xFFFF6B6B)
    val ErrorSuave = Color(0xFF2A1313)
}

private val Oscuro = darkColorScheme(
    primary = Acento,
    onPrimary = Paleta.SobreAcento,
    primaryContainer = Paleta.AcentoSuave,
    onPrimaryContainer = Acento,
    secondary = Paleta.Texto2,
    onSecondary = Paleta.Negro,
    secondaryContainer = Paleta.AcentoSuave,
    onSecondaryContainer = Acento,
    tertiary = Paleta.Aviso,
    background = Paleta.Negro,
    onBackground = Paleta.Texto1,
    surface = Paleta.Negro,
    onSurface = Paleta.Texto1,
    surfaceVariant = Paleta.S2,
    onSurfaceVariant = Paleta.Texto2,
    surfaceContainerLowest = Paleta.Negro,
    surfaceContainerLow = Paleta.S1,
    surfaceContainer = Paleta.S1,
    surfaceContainerHigh = Paleta.S2,
    surfaceContainerHighest = Paleta.S3,
    error = Paleta.Error,
    onError = Paleta.Negro,
    errorContainer = Paleta.ErrorSuave,
    onErrorContainer = Paleta.Error,
    outline = Color(0xFF3A3A41),
    outlineVariant = Paleta.Linea,
    scrim = Color(0xCC000000),
)

private val Claro = lightColorScheme(
    primary = Color(0xFF006C4B),
    primaryContainer = Color(0xFFB8F5D8),
    background = Color(0xFFF5FBF7),
    surface = Color(0xFFF5FBF7),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

// Tipografías incluidas en la app (licencia OFL): Outfit para títulos y marca, Inter para el texto.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, peso: Int) = Font(
    resId = res,
    weight = FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
)

val Outfit = FontFamily(
    variable(R.font.outfit_variable, 500),
    variable(R.font.outfit_variable, 600),
    variable(R.font.outfit_variable, 700),
)
val Inter = FontFamily(
    variable(R.font.inter_variable, 400),
    variable(R.font.inter_variable, 500),
    variable(R.font.inter_variable, 600),
    variable(R.font.inter_variable, 700),
)

private val Tipografia = Typography(
    displaySmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp),
)

/** Tema oscuro (OLED) por defecto. */
@Composable
fun MiradorTheme(oscuro: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, typography = Tipografia, shapes = Formas, content = content)
}
