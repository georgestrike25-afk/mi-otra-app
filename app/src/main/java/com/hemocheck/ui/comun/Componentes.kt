package com.hemocheck.ui.comun

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.hemocheck.R
import com.hemocheck.ui.tema.LocalColoresEstado

enum class Gravedad { INFO, CORRECTO, ADVERTENCIA, PELIGRO }

@Composable
fun coloresDe(g: Gravedad): Pair<Color, Color> {
    val c = LocalColoresEstado.current
    return when (g) {
        Gravedad.INFO -> c.neutro to c.sobreNeutro
        Gravedad.CORRECTO -> c.correcto to c.sobreCorrecto
        Gravedad.ADVERTENCIA -> c.advertencia to c.sobreAdvertencia
        Gravedad.PELIGRO -> c.peligro to c.sobrePeligro
    }
}

/** Etiqueta de estado con ícono y texto (no depende solo del color). */
@Composable
fun Etiqueta(texto: String, icono: ImageVector, gravedad: Gravedad, modifier: Modifier = Modifier) {
    val (fondo, frente) = coloresDe(gravedad)
    Row(
        modifier
            .background(fondo, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = frente, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, color = frente, style = MaterialTheme.typography.labelLarge)
    }
}

/** Tarjeta de aviso con ícono según gravedad. */
@Composable
fun Aviso(texto: String, gravedad: Gravedad = Gravedad.ADVERTENCIA, modifier: Modifier = Modifier, titulo: String? = null) {
    val (fondo, frente) = coloresDe(gravedad)
    val icono = when (gravedad) {
        Gravedad.INFO, Gravedad.CORRECTO -> Icons.Filled.Info
        Gravedad.ADVERTENCIA -> Icons.Filled.WarningAmber
        Gravedad.PELIGRO -> Icons.Filled.ErrorOutline
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(fondo, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icono, contentDescription = null, tint = frente)
        Spacer(Modifier.width(10.dp))
        Column {
            if (titulo != null) Text(titulo, color = frente, fontWeight = FontWeight.Bold)
            Text(texto, color = frente, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Barra(titulo: String, onAtras: (() -> Unit)?, acciones: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(titulo) },
        navigationIcon = {
            if (onAtras != null) {
                IconButton(onClick = onAtras) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.volver))
                }
            }
        },
        actions = { acciones() },
    )
}

/** Muestra un identificador en letra monoespaciada y agrupado (los DIN ISBT como «X9999 26 000123»). */
@Composable
fun TextoId(id: String?, modifier: Modifier = Modifier, grande: Boolean = false) {
    Text(
        text = id?.let { formatearId(it) } ?: stringResource(R.string.sin_identificador),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = if (grande) 26.sp else 18.sp,
        modifier = modifier.semantics { contentDescription = id?.toList()?.joinToString(" ") ?: "" },
    )
}

fun formatearId(id: String): String =
    if (Regex("^[A-Z]\\d{12}$").matches(id)) "${id.substring(0, 5)} ${id.substring(5, 7)} ${id.substring(7)}" else id

@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            contenido()
        }
    }
}

/** Miniatura que abre el visor con zoom al tocarla. */
@Composable
fun ImagenAmpliable(bitmap: Bitmap, descripcion: String, modifier: Modifier = Modifier, alturaMax: Int = 260) {
    var abierta by remember { mutableStateOf(false) }
    val imagen = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        bitmap = imagen,
        contentDescription = descripcion,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = alturaMax.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable { abierta = true },
    )
    if (abierta) VisorImagen(bitmap, descripcion) { abierta = false }
}

/** Visor a pantalla completa: pellizcar para ampliar, doble toque para restablecer. */
@Composable
fun VisorImagen(bitmap: Bitmap, descripcion: String, onCerrar: () -> Unit) {
    val imagen = remember(bitmap) { bitmap.asImageBitmap() }
    var escala by remember { mutableFloatStateOf(1f) }
    var desplazamiento by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = imagen,
                    contentDescription = descripcion,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                escala = (escala * zoom).coerceIn(1f, 8f)
                                desplazamiento = if (escala == 1f) Offset.Zero else desplazamiento + pan
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { escala = 1f; desplazamiento = Offset.Zero })
                        }
                        .graphicsLayer {
                            scaleX = escala
                            scaleY = escala
                            translationX = desplazamiento.x
                            translationY = desplazamiento.y
                        },
                )
                IconButton(onClick = onCerrar, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cerrar), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun FilaDato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}

private val formatoFecha = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun formatearFecha(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).format(formatoFecha)
