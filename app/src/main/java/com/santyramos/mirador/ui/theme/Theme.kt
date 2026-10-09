package com.santyramos.mirador.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Acento = Color(0xFF3DDC97)
private val Fondo = Color(0xFF0E1512)
private val Superficie = Color(0xFF16201B)
private val SuperficieAlta = Color(0xFF1E2B24)

private val Oscuro = darkColorScheme(
    primary = Acento,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1F4A38),
    onPrimaryContainer = Color(0xFFB8F5D8),
    secondary = Color(0xFF9FD3BC),
    background = Fondo,
    onBackground = Color(0xFFE2EAE5),
    surface = Fondo,
    onSurface = Color(0xFFE2EAE5),
    surfaceVariant = Superficie,
    onSurfaceVariant = Color(0xFFA9B8B0),
    surfaceContainer = Superficie,
    surfaceContainerHigh = SuperficieAlta,
    surfaceContainerHighest = Color(0xFF283830),
    error = Color(0xFFFF8A80),
    outline = Color(0xFF55675D),
)

private val Claro = lightColorScheme(
    primary = Color(0xFF006C4B),
    primaryContainer = Color(0xFFB8F5D8),
    background = Color(0xFFF5FBF7),
    surface = Color(0xFFF5FBF7),
)

/** Tema oscuro por defecto (el acento verde #3DDC97 es el de Mirador). */
@Composable
fun MiradorTheme(oscuro: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, content = content)
}

@Suppress("unused")
@Composable
fun sistemaOscuro() = isSystemInDarkTheme()
