package com.hemocheck.ui.comparacion

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.core.analisis.EstadoPlanilla
import com.hemocheck.core.analisis.FilaCompacta
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.datos.DatosComparacion
import com.hemocheck.datos.RegistroEncontrado
import com.hemocheck.imagen.componerFila
import com.hemocheck.imagen.recortarConMarca
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.Etiqueta
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.ImagenAmpliable
import com.hemocheck.ui.comun.TextoId
import com.hemocheck.ui.comun.VisorImagen
import com.hemocheck.ui.comun.formatearFecha
import com.hemocheck.ui.comun.formatearId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Collage de corroboración: tres imágenes grandes, a todo el ancho y una debajo de la otra
 * (planilla convencional, planilla molecular y etiqueta), para mirar el número y el estado
 * con los propios ojos. Los detalles van debajo.
 */
@Composable
fun PantallaCollage(
    contenedor: Contenedor,
    verificacionId: Long,
    onDetalle: () -> Unit,
    onAbrirEntrada: (Long) -> Unit,
    onAtras: () -> Unit,
    onNuevaVerificacion: () -> Unit,
) {
    var datos by remember { mutableStateOf<DatosComparacion?>(null) }
    var cargado by remember { mutableStateOf(false) }
    val imagenes = remember { mutableStateMapOf<Long, Bitmap>() }
    var revisada by remember { mutableStateOf<Long?>(null) }
    val alcance = rememberCoroutineScope()

    LaunchedEffect(verificacionId) {
        val d = contenedor.repositorio.comparacion(verificacionId)
        datos = d
        cargado = true
        revisada = d?.verificacion?.revisadaEn
        if (d != null) {
            val paginas = listOfNotNull(d.paginaEtiqueta) + d.registros.mapNotNull { it.pagina }
            for (p in paginas.distinctBy { it.id }) {
                contenedor.repositorio.imagen(p, procesada = true, ladoMax = 3200)?.let { imagenes[p.id] = it }
            }
        }
    }

    Scaffold(topBar = { Barra(stringResource(R.string.collage_titulo), onAtras = onAtras) }) { relleno ->
        val d = datos
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).testTag("pantalla_collage"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (d == null) {
                Box(Modifier.padding(16.dp)) {
                    if (!cargado) CircularProgressIndicator() else Aviso(stringResource(R.string.comparacion_no_disponible), Gravedad.ADVERTENCIA)
                }
                return@Column
            }
            val lecturas = remember(d) { d.registros.associate { it.entrada.id to leer(it) } }
            val conv = d.registros.filter { it.entrada.tipo == TipoRegistro.CONVENCIONAL }
            val mol = d.registros.filter { it.entrada.tipo == TipoRegistro.MOLECULAR }

            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.comparacion_id_etiqueta), style = MaterialTheme.typography.labelLarge)
                TextoId(d.informe.idEtiqueta, grande = true)
            }
            if (d.registros.any { lecturas[it.entrada.id]?.alerta == true }) {
                Aviso(
                    stringResource(R.string.collage_alerta_texto), Gravedad.PELIGRO,
                    modifier = Modifier.padding(horizontal = 12.dp), titulo = stringResource(R.string.collage_alerta_titulo),
                )
            }

            // Las tres imágenes, grandes y pegadas
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.background(Color.Black)) {
                ImagenesDeTipo(conv, stringResource(R.string.tipo_convencional), d, imagenes, lecturas)
                ImagenesDeTipo(mol, stringResource(R.string.tipo_molecular), d, imagenes, lecturas)
                ImagenEtiqueta(d, imagenes)
            }

            // Detalles debajo
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetalleTipo(stringResource(R.string.tipo_convencional), conv, d, imagenes, lecturas, onAbrirEntrada)
                DetalleTipo(stringResource(R.string.tipo_molecular), mol, d, imagenes, lecturas, onAbrirEntrada)
                Aviso(stringResource(R.string.collage_ayuda), Gravedad.INFO)
                if (revisada == null) {
                    Button(onClick = {
                        alcance.launch(Dispatchers.Main) {
                            contenedor.repositorio.marcarRevisada(verificacionId)
                            revisada = contenedor.repositorio.ahora()
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("boton_marcar_revisada")) { Text(stringResource(R.string.comparacion_marcar_revisada)) }
                } else {
                    Aviso(stringResource(R.string.comparacion_revisada_el, formatearFecha(revisada!!)), Gravedad.CORRECTO)
                }
                OutlinedButton(onClick = onDetalle, modifier = Modifier.fillMaxWidth().testTag("boton_detalle")) { Text(stringResource(R.string.collage_ver_detalle)) }
                OutlinedButton(onClick = onNuevaVerificacion, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.comparacion_nueva)) }
                Aviso(stringResource(R.string.limite_clinico_pie), Gravedad.INFO)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Lo que se lee de la planilla para una fila: su estado y los reactivos leídos por OCR. */
private class Lectura(val estado: EstadoPlanilla?, val ocr: PaginaOcr?, val reactivos: List<String>) {
    /**
     * La alerta roja la da el estado escrito en la planilla («No Apto»). Si no se encontró la
     * columna de estado, se usan los resultados leídos («REACTIVO»).
     */
    val alerta: Boolean get() = estado?.noApto ?: reactivos.isNotEmpty()

    /** Se leyó «REACTIVO» pero la planilla dice «Apto»: probable error de lectura, a verificar. */
    val discrepancia: Boolean get() = estado != null && !estado.noApto && reactivos.isNotEmpty()
}

private fun leer(r: RegistroEncontrado): Lectura {
    val ocr = r.pagina?.ocrJson?.let { runCatching { PaginaOcr.desdeJson(it) }.getOrNull() }
    val estado = r.entrada.cajaId?.let { c -> ocr?.let { FilaCompacta.estado(it, c) } }
    val reactivos = r.resultados.filter { it.categoria == CategoriaResultado.REACTIVO }.map { it.nombre }.distinct()
    return Lectura(estado, ocr, reactivos)
}

/** Imagen a todo el ancho, respetando su proporción; al tocarla se abre ampliable. */
@Composable
private fun ImagenAncha(bmp: Bitmap, descripcion: String) {
    var abierta by remember { mutableStateOf(false) }
    val imagen = remember(bmp) { bmp.asImageBitmap() }
    Image(
        imagen, contentDescription = descripcion, contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1)).clickable { abierta = true },
    )
    if (abierta) VisorImagen(bmp, descripcion) { abierta = false }
}

@Composable
private fun Faltante(texto: String) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(texto, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ImagenesDeTipo(
    registros: List<RegistroEncontrado>,
    tipo: String,
    d: DatosComparacion,
    imagenes: Map<Long, Bitmap>,
    lecturas: Map<Long, Lectura>,
) {
    if (registros.isEmpty()) {
        Faltante(stringResource(R.string.collage_falta_tipo, tipo, formatearId(d.informe.idEtiqueta ?: "")))
        return
    }
    registros.forEach { r ->
        val pagina = r.pagina
        val bmp = pagina?.let { imagenes[it.id] }
        val lectura = lecturas[r.entrada.id]
        val e = r.entrada
        when {
            pagina == null -> Faltante(stringResource(R.string.comparacion_pagina_no_disponible))
            bmp == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val recorte = remember(bmp, e.id) {
                    val c = e.cajaId
                    val ocr = lectura?.ocr
                    when {
                        c != null && ocr != null -> recortarConMarca(
                            bmp, FilaCompacta.zona(ocr, c, lectura.estado), FilaCompacta.marca(c, lectura.estado),
                            pagina.anchoProcesada, lectura.alerta,
                        )
                        else -> componerFila(bmp, e.cajaEncabezados, e.cajaFila ?: e.cajaId, pagina.anchoProcesada, pagina.altoProcesada)
                    }
                }
                ImagenAncha(recorte ?: bmp, stringResource(R.string.collage_recorte_compacto))
            }
        }
    }
}

@Composable
private fun ImagenEtiqueta(d: DatosComparacion, imagenes: Map<Long, Bitmap>) {
    val pagina = d.paginaEtiqueta
    val bmp = pagina?.let { imagenes[it.id] }
    when {
        pagina == null -> Faltante(stringResource(R.string.comparacion_sin_foto_etiqueta))
        bmp == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> {
            val recorte = remember(bmp) {
                d.verificacion.cajaIdEtiqueta?.let { c -> recortarConMarca(bmp, FilaCompacta.zonaEtiqueta(c), null, pagina.anchoProcesada, alerta = false) }
            }
            ImagenAncha(recorte ?: bmp, stringResource(R.string.comparacion_foto_etiqueta))
        }
    }
}

/** Detalle textual de un tipo de planilla, debajo de las imágenes. */
@Composable
private fun DetalleTipo(
    tipo: String,
    registros: List<RegistroEncontrado>,
    d: DatosComparacion,
    imagenes: Map<Long, Bitmap>,
    lecturas: Map<Long, Lectura>,
    onAbrirEntrada: (Long) -> Unit,
) {
    if (registros.isEmpty()) return
    Text(tipo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    if (registros.size > 1) Aviso(stringResource(R.string.collage_varios, registros.size), Gravedad.PELIGRO)
    registros.forEach { r ->
        val l = lecturas[r.entrada.id]
        val estado = l?.estado
        when {
            estado != null && estado.noApto ->
                Etiqueta(stringResource(R.string.collage_planilla_dice, estado.texto), Icons.Filled.ErrorOutline, Gravedad.PELIGRO)
            estado != null -> Etiqueta(stringResource(R.string.collage_planilla_dice, estado.texto), Icons.Filled.Info, Gravedad.INFO)
            l != null && l.reactivos.isNotEmpty() ->
                Etiqueta(stringResource(R.string.collage_reactivo, l.reactivos.joinToString(", ")), Icons.Filled.ErrorOutline, Gravedad.PELIGRO)
            else -> Text(stringResource(R.string.collage_sin_estado), style = MaterialTheme.typography.bodySmall)
        }
        if (l?.discrepancia == true) {
            Etiqueta(stringResource(R.string.collage_discrepancia, l.reactivos.joinToString(", ")), Icons.Filled.WarningAmber, Gravedad.ADVERTENCIA)
        }
        var verFila by remember(r.entrada.id) { mutableStateOf(false) }
        val pagina = r.pagina
        val bmp = pagina?.let { imagenes[it.id] }
        if (bmp != null && pagina != null) {
            TextButton(onClick = { verFila = !verFila }) {
                Text(stringResource(if (verFila) R.string.collage_ocultar_fila else R.string.collage_ver_fila))
            }
            if (verFila) {
                val fila = remember(bmp, r.entrada.id) {
                    componerFila(bmp, r.entrada.cajaEncabezados, r.entrada.cajaFila ?: r.entrada.cajaId, pagina.anchoProcesada, pagina.altoProcesada)
                }
                ImagenAmpliable(fila ?: bmp, stringResource(R.string.recorte_fila), alturaMax = 300)
            }
        }
        TextButton(onClick = { onAbrirEntrada(r.entrada.id) }) { Text(stringResource(R.string.collage_ver_pagina)) }
    }
}
