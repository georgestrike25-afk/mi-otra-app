package com.hemocheck.ui.comparacion

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color
import com.hemocheck.core.analisis.EstadoPlanilla
import com.hemocheck.core.analisis.FilaCompacta
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.imagen.recortarConMarca
import com.hemocheck.ui.comun.Etiqueta
import com.hemocheck.ui.tema.LocalColoresEstado
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.datos.DatosComparacion
import com.hemocheck.datos.RegistroEncontrado
import com.hemocheck.imagen.componerFila
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.EtiquetaEstado
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.ImagenAmpliable
import com.hemocheck.ui.comun.TextoId
import com.hemocheck.ui.comun.formatearFecha
import com.hemocheck.ui.comun.formatearId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Collage de corroboración: tres filas con las imágenes ampliadas para que el usuario mire por
 * sí mismo el número y los resultados:
 * 1) la fila de la planilla de serología convencional (con su encabezado),
 * 2) la fila de la planilla de serología molecular,
 * 3) la etiqueta de la unidad.
 */
@OptIn(ExperimentalLayoutApi::class)
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
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)
                .testTag("pantalla_collage"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (d == null) {
                if (!cargado) CircularProgressIndicator() else Aviso(stringResource(R.string.comparacion_no_disponible), Gravedad.ADVERTENCIA)
                return@Column
            }
            Text(stringResource(R.string.comparacion_id_etiqueta), style = MaterialTheme.typography.labelLarge)
            TextoId(d.informe.idEtiqueta, grande = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.informe.estados.forEach { EtiquetaEstado(it) }
            }

            val alertaConv = d.registros.any { it.entrada.tipo == TipoRegistro.CONVENCIONAL && alertaDe(it) }
            val alertaMol = d.registros.any { it.entrada.tipo == TipoRegistro.MOLECULAR && alertaDe(it) }
            if (alertaConv || alertaMol) {
                Aviso(stringResource(R.string.collage_alerta_texto), Gravedad.PELIGRO, titulo = stringResource(R.string.collage_alerta_titulo))
            }

            FilaCollage(1, stringResource(R.string.tipo_convencional), alertaConv) {
                RegistrosDeTipo(d, TipoRegistro.CONVENCIONAL, imagenes, onAbrirEntrada)
            }
            FilaCollage(2, stringResource(R.string.tipo_molecular), alertaMol) {
                RegistrosDeTipo(d, TipoRegistro.MOLECULAR, imagenes, onAbrirEntrada)
            }
            FilaCollage(3, stringResource(R.string.comparacion_etiqueta), false) {
                val pagina = d.paginaEtiqueta
                val bmp = pagina?.let { imagenes[it.id] }
                when {
                    pagina == null -> Aviso(stringResource(R.string.comparacion_sin_foto_etiqueta), Gravedad.ADVERTENCIA)
                    bmp == null -> CircularProgressIndicator()
                    else -> {
                        val numero = remember(bmp) {
                            d.verificacion.cajaIdEtiqueta?.let { c ->
                                recortarConMarca(bmp, FilaCompacta.zonaEtiqueta(c), null, pagina.anchoProcesada, alerta = false)
                            }
                        }
                        numero?.let { ImagenAmpliable(it, stringResource(R.string.comparacion_recorte_numero_etiqueta), alturaMax = 140) }
                        ImagenAmpliable(bmp, stringResource(R.string.comparacion_foto_etiqueta), alturaMax = 520)
                    }
                }
            }

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
        }
    }
}

@Composable
private fun FilaCollage(numero: Int, titulo: String, alerta: Boolean, contenido: @Composable () -> Unit) {
    val fondo = if (alerta) LocalColoresEstado.current.peligro else MaterialTheme.colorScheme.surfaceVariant
    Card(
        Modifier.fillMaxWidth().then(if (alerta) Modifier.border(3.dp, Color(0xFFD32F2F), RoundedCornerShape(12.dp)) else Modifier),
        colors = CardDefaults.cardColors(containerColor = fondo),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$numero · $titulo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            contenido()
        }
    }
}

@Composable
private fun RegistrosDeTipo(
    d: DatosComparacion,
    tipo: TipoRegistro,
    imagenes: Map<Long, Bitmap>,
    onAbrirEntrada: (Long) -> Unit,
) {
    val registros: List<RegistroEncontrado> = d.registros.filter { it.entrada.tipo == tipo }
    if (registros.isEmpty()) {
        Aviso(stringResource(R.string.collage_no_encontrado, formatearId(d.informe.idEtiqueta ?: "")), Gravedad.PELIGRO)
        return
    }
    if (registros.size > 1) Aviso(stringResource(R.string.collage_varios, registros.size), Gravedad.PELIGRO)
    registros.forEach { r ->
        val e = r.entrada
        val pagina = r.pagina
        val bmp = pagina?.let { imagenes[it.id] }
        when {
            pagina == null -> Aviso(stringResource(R.string.comparacion_pagina_no_disponible), Gravedad.ADVERTENCIA)
            bmp == null -> CircularProgressIndicator()
            else -> {
                val ocr = remember(pagina.id) { pagina.ocrJson?.let { runCatching { PaginaOcr.desdeJson(it) }.getOrNull() } }
                val estado = remember(pagina.id, e.id) { estadoDe(r) }
                val alerta = alertaDe(r)
                val compacto = remember(bmp, e.id) {
                    val c = e.cajaId
                    if (c == null || ocr == null) null
                    else recortarConMarca(bmp, FilaCompacta.zona(ocr, c, estado), FilaCompacta.marca(c, estado), pagina.anchoProcesada, alerta)
                }
                val fila = remember(bmp, e.id) { componerFila(bmp, e.cajaEncabezados, e.cajaFila ?: e.cajaId, pagina.anchoProcesada, pagina.altoProcesada) }
                var verCompleta by remember(e.id) { mutableStateOf(false) }
                if (compacto != null) {
                    ImagenAmpliable(compacto, stringResource(R.string.collage_recorte_compacto), alturaMax = 220)
                    estado?.let {
                        if (it.noApto) Etiqueta(stringResource(R.string.collage_planilla_dice, it.texto), Icons.Filled.ErrorOutline, Gravedad.PELIGRO)
                        else Etiqueta(stringResource(R.string.collage_planilla_dice, it.texto), Icons.Filled.Info, Gravedad.INFO)
                    }
                    val reactivos = r.resultados.filter { it.categoria == CategoriaResultado.REACTIVO }.map { it.nombre }.distinct()
                    if (reactivos.isNotEmpty()) {
                        Etiqueta(stringResource(R.string.collage_reactivo, reactivos.joinToString(", ")), Icons.Filled.ErrorOutline, Gravedad.PELIGRO)
                    }
                    if (fila != null) {
                        TextButton(onClick = { verCompleta = !verCompleta }) {
                            Text(stringResource(if (verCompleta) R.string.collage_ocultar_fila else R.string.collage_ver_fila))
                        }
                        if (verCompleta) ImagenAmpliable(fila, stringResource(R.string.recorte_fila), alturaMax = 260)
                    }
                } else if (fila != null) {
                    ImagenAmpliable(fila, stringResource(R.string.recorte_fila), alturaMax = 260)
                } else {
                    // Sin ubicación precisa: la página completa, nunca un recorte inventado
                    Aviso(stringResource(R.string.comparacion_sin_recorte_registro), Gravedad.ADVERTENCIA)
                    ImagenAmpliable(bmp, stringResource(R.string.comparacion_pagina_completa), alturaMax = 420)
                }
            }
        }
        Text(stringResource(R.string.collage_pie_registro, formatearId(e.idNormalizado)), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onAbrirEntrada(e.id) }) { Text(stringResource(R.string.collage_ver_pagina)) }
    }
}

/** Estado «Apto / No Apto» que figura en la planilla para la fila del registro. */
private fun estadoDe(r: RegistroEncontrado): EstadoPlanilla? {
    val caja = r.entrada.cajaId ?: return null
    val ocr = r.pagina?.ocrJson?.let { runCatching { PaginaOcr.desdeJson(it) }.getOrNull() } ?: return null
    return FilaCompacta.estado(ocr, caja)
}

/** La planilla documenta un resultado reactivo o «No Apto» para esta fila. */
private fun alertaDe(r: RegistroEncontrado): Boolean =
    r.resultados.any { it.categoria == CategoriaResultado.REACTIVO } || estadoDe(r)?.noApto == true
