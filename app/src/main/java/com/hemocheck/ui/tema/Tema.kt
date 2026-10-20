package com.hemocheck.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val Claro = lightColorScheme(
    primary = Color(0xFF9A1B1B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF37474F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE4E8),
    onSecondaryContainer = Color(0xFF1B262B),
    background = Color(0xFFFCFCFC),
    surface = Color(0xFFFCFCFC),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF680003),
    primaryContainer = Color(0xFF8C1D18),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFB5C4CC),
    onSecondary = Color(0xFF1F2F36),
)

/** Colores de estado. Siempre se usan junto con un ícono y un texto, nunca solos. */
@Immutable
data class ColoresEstado(
    val correcto: Color, val sobreCorrecto: Color,
    val peligro: Color, val sobrePeligro: Color,
    val advertencia: Color, val sobreAdvertencia: Color,
    val neutro: Color, val sobreNeutro: Color,
)

private val EstadosClaro = ColoresEstado(
    correcto = Color(0xFFD7F0DA), sobreCorrecto = Color(0xFF0B3D14),
    peligro = Color(0xFFFFD9D6), sobrePeligro = Color(0xFF5F0A06),
    advertencia = Color(0xFFFFE9B8), sobreAdvertencia = Color(0xFF4A3300),
    neutro = Color(0xFFE6E9EB), sobreNeutro = Color(0xFF263238),
)

private val EstadosOscuro = ColoresEstado(
    correcto = Color(0xFF1E4D26), sobreCorrecto = Color(0xFFD7F0DA),
    peligro = Color(0xFF6B1A14), sobrePeligro = Color(0xFFFFD9D6),
    advertencia = Color(0xFF5A4300), sobreAdvertencia = Color(0xFFFFE9B8),
    neutro = Color(0xFF37474F), sobreNeutro = Color(0xFFE6E9EB),
)

val LocalColoresEstado = staticCompositionLocalOf { EstadosClaro }

@Composable
fun TemaHemoCheck(contenido: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalColoresEstado provides if (oscuro) EstadosOscuro else EstadosClaro) {
        MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, content = contenido)
    }
}
