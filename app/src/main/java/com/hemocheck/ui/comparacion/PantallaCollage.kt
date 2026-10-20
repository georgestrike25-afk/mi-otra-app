package com.hemocheck.ui.comparacion

import android.graphics.Bitmap
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
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.datos.DatosComparacion
import com.hemocheck.datos.RegistroEncontrado
import com.hemocheck.imagen.componerFila
import com.hemocheck.imagen.recortarZona
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.EtiquetaEstado
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.ImagenAmpliable
import com.hemocheck.ui.comun.TextoId
import com.hemocheck.ui.comun.formatearFecha
import com.hemocheck.ui.comun.formatearId
import com.hemocheck.ui.comun.textoEstadoEntrada
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

            FilaCollage(1, stringResource(R.string.tipo_convencional)) {
                RegistrosDeTipo(d, TipoRegistro.CONVENCIONAL, imagenes, onAbrirEntrada)
            }
            FilaCollage(2, stringResource(R.string.tipo_molecular)) {
                RegistrosDeTipo(d, TipoRegistro.MOLECULAR, imagenes, onAbrirEntrada)
            }
            FilaCollage(3, stringResource(R.string.comparacion_etiqueta)) {
                val pagina = d.paginaEtiqueta
                val bmp = pagina?.let { imagenes[it.id] }
                when {
                    pagina == null -> Aviso(stringResource(R.string.comparacion_sin_foto_etiqueta), Gravedad.ADVERTENCIA)
                    bmp == null -> CircularProgressIndicator()
                    else -> {
                        val numero = remember(bmp) { recortarZona(bmp, d.verificacion.cajaIdEtiqueta, pagina.anchoProcesada, pagina.altoProcesada, 0.8f) }
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
private fun FilaCollage(numero: Int, titulo: String, contenido: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
                val fila = remember(bmp, e.id) { componerFila(bmp, e.cajaEncabezados, e.cajaFila ?: e.cajaId, pagina.anchoProcesada, pagina.altoProcesada) }
                if (fila != null) {
                    ImagenAmpliable(fila, stringResource(R.string.recorte_fila), alturaMax = 260)
                } else {
                    // Sin ubicación precisa: la página completa, nunca un recorte inventado
                    Aviso(stringResource(R.string.comparacion_sin_recorte_registro), Gravedad.ADVERTENCIA)
                    ImagenAmpliable(bmp, stringResource(R.string.comparacion_pagina_completa), alturaMax = 420)
                }
            }
        }
        Text(
            stringResource(R.string.collage_pie_registro, formatearId(e.idNormalizado), stringResource(textoEstadoEntrada(e.estado))),
            style = MaterialTheme.typography.bodySmall,
        )
        if (e.estado == EstadoEntrada.PENDIENTE_REVISION) Aviso(stringResource(R.string.collage_pendiente), Gravedad.ADVERTENCIA)
        TextButton(onClick = { onAbrirEntrada(e.id) }) { Text(stringResource(R.string.collage_ver_pagina)) }
    }
}
