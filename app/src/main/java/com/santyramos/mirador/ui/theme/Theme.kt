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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Verde de Mirador (#3DDC97). */
val Acento = Color(0xFF3DDC97)

/** Colores extra que Material 3 no trae. */
object Paleta {
    val Aviso = Color(0xFFFFC857)
    val Error = Color(0xFFFF8A80)
    val ExitoSuave = Color(0xFF1E3B2F)
    val Fondo = Color(0xFF000000)
}

private val Oscuro = darkColorScheme(
    primary = Acento,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF17463A),
    onPrimaryContainer = Color(0xFFB8F5D8),
    secondary = Color(0xFF9FD3BC),
    onSecondary = Color(0xFF053828),
    secondaryContainer = Color(0xFF22443A),
    onSecondaryContainer = Color(0xFFCDEFE0),
    tertiary = Paleta.Aviso,
    background = Paleta.Fondo,
    onBackground = Color(0xFFE3EBE6),
    surface = Paleta.Fondo,
    onSurface = Color(0xFFE3EBE6),
    surfaceVariant = Color(0xFF18201B),
    onSurfaceVariant = Color(0xFFA7B6AE),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C110E),
    surfaceContainer = Color(0xFF111713),
    surfaceContainerHigh = Color(0xFF18201B),
    surfaceContainerHighest = Color(0xFF212B25),
    error = Paleta.Error,
    outline = Color(0xFF5C6E64),
    outlineVariant = Color(0xFF2B3932),
)

private val Claro = lightColorScheme(
    primary = Color(0xFF006C4B),
    primaryContainer = Color(0xFFB8F5D8),
    background = Color(0xFFF5FBF7),
    surface = Color(0xFFF5FBF7),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val base = FontFamily.Default
private val Tipografia = Typography(
    headlineMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
)

/** Tema oscuro por defecto, acento verde #3DDC97. */
@Composable
fun MiradorTheme(oscuro: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, typography = Tipografia, shapes = Formas, content = content)
}
