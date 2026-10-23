package com.hemocheck.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta HemoCheck: celeste turquesa (acciones), azul institucional (paneles), amarillo de
// acento (detalles no críticos) y carbón profundo (fondo oscuro). El rojo queda solo para alertas.
val Turquesa = Color(0xFF08B5D6)
val AzulInstitucional = Color(0xFF123F68)
val AmarilloAcento = Color(0xFFFFC928)
val Carbon = Color(0xFF10151D)

private val Claro = lightColorScheme(
    primary = Turquesa,
    onPrimary = Carbon,
    primaryContainer = Color(0xFFCCF1F8),
    onPrimaryContainer = Color(0xFF003A45),
    secondary = AzulInstitucional,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1ECF6),
    onSecondaryContainer = AzulInstitucional,
    tertiary = AmarilloAcento,
    onTertiary = Carbon,
    background = Color(0xFFF3F7FA),
    onBackground = Carbon,
    surface = Color(0xFFF3F7FA),
    onSurface = Carbon,
    surfaceVariant = Color(0xFFE1E8EE),
    onSurfaceVariant = Color(0xFF33414F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEEF3F7),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFF6B7C8C),
)

private val Oscuro = darkColorScheme(
    primary = Turquesa,
    onPrimary = Carbon,
    primaryContainer = Color(0xFF0A4F5E),
    onPrimaryContainer = Color(0xFFBFF2FB),
    secondary = Color(0xFF8FB8DE),
    onSecondary = Color(0xFF0B2540),
    secondaryContainer = AzulInstitucional,
    onSecondaryContainer = Color(0xFFDCEBFA),
    tertiary = AmarilloAcento,
    onTertiary = Carbon,
    background = Carbon,
    onBackground = Color(0xFFE6EDF3),
    surface = Carbon,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1F2A37),
    onSurfaceVariant = Color(0xFF9FB0C0),
    surfaceContainerLowest = Color(0xFF0C1016),
    surfaceContainerLow = Color(0xFF18212C),
    surfaceContainer = Color(0xFF151C25),
    surfaceContainerHigh = Color(0xFF18212C),
    surfaceContainerHighest = Color(0xFF1F2A37),
    outline = Color(0xFF3A4A5A),
)

/** Barra superior: azul institucional en modo claro, carbón en modo oscuro. */
@Immutable
data class ColoresBarra(val fondo: Color, val frente: Color, val iconos: Color)

val LocalColoresBarra = staticCompositionLocalOf { ColoresBarra(AzulInstitucional, Color.White, Color.White) }
private val BarraOscura = ColoresBarra(Carbon, Color(0xFFE6EDF3), Turquesa)

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
    advertencia = Color(0xFFFFF1C2), sobreAdvertencia = Color(0xFF4A3300),
    neutro = Color(0xFFE1ECF6), sobreNeutro = AzulInstitucional,
)

private val EstadosOscuro = ColoresEstado(
    correcto = Color(0xFF1E4D26), sobreCorrecto = Color(0xFFD7F0DA),
    peligro = Color(0xFF6B1A14), sobrePeligro = Color(0xFFFFD9D6),
    advertencia = Color(0xFF3A3110), sobreAdvertencia = Color(0xFFFFE79A),
    neutro = AzulInstitucional, sobreNeutro = Color(0xFFDCEBFA),
)

val LocalColoresEstado = staticCompositionLocalOf { EstadosClaro }

@Composable
fun TemaHemoCheck(contenido: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalColoresEstado provides if (oscuro) EstadosOscuro else EstadosClaro,
        LocalColoresBarra provides if (oscuro) BarraOscura else ColoresBarra(AzulInstitucional, Color.White, Color.White),
    ) {
        MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, content = contenido)
    }
}
