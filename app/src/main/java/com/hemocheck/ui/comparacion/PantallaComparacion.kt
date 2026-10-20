package com.hemocheck.ui.comparacion

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.hemocheck.core.corroboracion.SeccionInforme
import com.hemocheck.datos.DatosComparacion
import com.hemocheck.datos.RegistroEncontrado
import com.hemocheck.datos.db.PaginaEntidad
import com.hemocheck.imagen.recortarZona
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.Etiqueta
import com.hemocheck.ui.comun.EtiquetaCategoria
import com.hemocheck.ui.comun.EtiquetaEstado
import com.hemocheck.ui.comun.FilaDato
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.ImagenAmpliable
import com.hemocheck.ui.comun.Seccion
import com.hemocheck.ui.comun.TextoId
import com.hemocheck.ui.comun.VisorImagen
import com.hemocheck.ui.comun.formatearFecha
import com.hemocheck.ui.comun.formatearId
import com.hemocheck.ui.comun.gravedadAviso
import com.hemocheck.ui.comun.textoAviso
import com.hemocheck.ui.comun.textoEstadoEntrada
import com.hemocheck.ui.comun.textoOrigen
import com.hemocheck.ui.comun.textoTipo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Corroboración visual: muestra a la vez la etiqueta, el recorte de su número y los recortes
 * de las hojas de serología con sus resultados, para que el usuario compare con sus propios ojos.
 */
@Composable
fun PantallaComparacion(
    contenedor: Contenedor,
    verificacionId: Long,
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

    Scaffold(topBar = { Barra(stringResource(R.string.comparacion_titulo), onAtras = onAtras) }) { relleno ->
        val d = datos
        if (d == null) {
            Column(Modifier.fillMaxSize().padding(relleno).padding(16.dp)) {
                if (!cargado) CircularProgressIndicator() else Aviso(stringResource(R.string.comparacion_no_disponible), Gravedad.ADVERTENCIA)
            }
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(relleno)) {
            val ancho = maxWidth >= 640.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).testTag("pantalla_comparacion"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Encabezado(d, revisada)
                Aviso(stringResource(R.string.limite_clinico_largo), Gravedad.INFO, titulo = stringResource(R.string.limite_clinico_titulo))
                EstadosGlobales(d)
                if (ancho) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) { TarjetaEtiqueta(contenedor, d, imagenes) }
                        Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            d.informe.secciones.forEach { s -> TarjetaSeccion(contenedor, d, s, imagenes, onAbrirEntrada) }
                        }
                    }
                } else {
                    TarjetaEtiqueta(contenedor, d, imagenes)
                    d.informe.secciones.forEach { s -> TarjetaSeccion(contenedor, d, s, imagenes, onAbrirEntrada) }
                }
                TablaDeterminaciones(d)
                if (d.informe.avisos.isNotEmpty()) {
                    Seccion(stringResource(R.string.comparacion_avisos)) {
                        d.informe.avisos.distinct().forEach { Aviso(textoAviso(it), gravedadAviso(it)) }
                    }
                }
                if (d.correcciones.isNotEmpty()) {
                    Seccion(stringResource(R.string.comparacion_correcciones)) {
                        d.correcciones.forEach {
                            Text(stringResource(R.string.correccion_linea, formatearFecha(it.fecha), it.campo, it.valorAnterior ?: "—", it.valorNuevo ?: "—"))
                        }
                    }
                }
                if (revisada == null) {
                    Button(onClick = {
                        alcance.launch(Dispatchers.Main) {
                            contenedor.repositorio.marcarRevisada(verificacionId)
                            revisada = contenedor.repositorio.ahora()
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("boton_marcar_revisada")) { Text(stringResource(R.string.comparacion_marcar_revisada)) }
                }
                OutlinedButton(onClick = onNuevaVerificacion, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.comparacion_nueva)) }
                Aviso(stringResource(R.string.limite_clinico_pie), Gravedad.INFO)
            }
        }
    }
}

@Composable
private fun Encabezado(d: DatosComparacion, revisada: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.comparacion_id_etiqueta), style = MaterialTheme.typography.labelLarge)
        TextoId(d.informe.idEtiqueta, grande = true)
        Text(stringResource(R.string.comparacion_fecha, formatearFecha(d.verificacion.creado)), style = MaterialTheme.typography.bodySmall)
        Text(
            if (revisada != null) stringResource(R.string.comparacion_revisada_el, formatearFecha(revisada)) else stringResource(R.string.comparacion_sin_revision_visual),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EstadosGlobales(d: DatosComparacion) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        d.informe.estados.forEach { EtiquetaEstado(it) }
    }
}

@Composable
private fun TarjetaEtiqueta(contenedor: Contenedor, d: DatosComparacion, imagenes: Map<Long, Bitmap>) {
    val pagina = d.paginaEtiqueta
    Seccion(stringResource(R.string.comparacion_etiqueta)) {
        val bmp = pagina?.let { imagenes[it.id] }
        if (pagina == null) {
            Aviso(stringResource(R.string.comparacion_sin_foto_etiqueta), Gravedad.ADVERTENCIA)
        } else if (bmp != null) {
            val recorte = remember(bmp) { recortarZona(bmp, d.verificacion.cajaIdEtiqueta, pagina.anchoProcesada, pagina.altoProcesada, 0.8f) }
            if (recorte != null) {
                Text(stringResource(R.string.comparacion_recorte_numero_etiqueta), style = MaterialTheme.typography.titleSmall)
                ImagenAmpliable(recorte, stringResource(R.string.comparacion_recorte_numero_etiqueta), alturaMax = 120)
            } else {
                Aviso(stringResource(R.string.comparacion_sin_recorte_etiqueta), Gravedad.ADVERTENCIA)
            }
            Text(stringResource(R.string.comparacion_foto_etiqueta), style = MaterialTheme.typography.titleSmall)
            ImagenAmpliable(bmp, stringResource(R.string.comparacion_foto_etiqueta), alturaMax = 260)
            BotonOriginal(contenedor, pagina)
        } else {
            CircularProgressIndicator()
        }
        FilaDato(stringResource(R.string.comparacion_leido_ocr), d.verificacion.idOcr ?: "—")
        FilaDato(stringResource(R.string.comparacion_codigo_barras), d.verificacion.idCodigoBarras?.let { formatearId(it) } ?: "—")
        FilaDato(stringResource(R.string.comparacion_origen_id), stringResource(textoOrigen(d.verificacion.origenId)))
    }
}

@Composable
private fun TarjetaSeccion(
    contenedor: Contenedor,
    d: DatosComparacion,
    s: SeccionInforme,
    imagenes: Map<Long, Bitmap>,
    onAbrirEntrada: (Long) -> Unit,
) {
    Seccion(stringResource(R.string.comparacion_seccion, stringResource(textoTipo(s.tipo)))) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.estados.forEach { EtiquetaEstado(it) }
        }
        val registros = d.registros.filter { it.entrada.id in s.confirmadas || it.entrada.id in s.pendientes }
        if (registros.isEmpty() && (s.confirmadas + s.pendientes).isNotEmpty()) {
            Aviso(stringResource(R.string.comparacion_registro_vencido), Gravedad.ADVERTENCIA)
        }
        registros.forEach { r ->
            HorizontalDivider()
            TarjetaRegistro(contenedor, d.informe.idEtiqueta, r, r.pagina?.let { imagenes[it.id] }, onAbrirEntrada)
        }
    }
}

@Composable
private fun TarjetaRegistro(
    contenedor: Contenedor,
    idEtiqueta: String?,
    r: RegistroEncontrado,
    bmp: Bitmap?,
    onAbrirEntrada: (Long) -> Unit,
) {
    val e = r.entrada
    val pagina = r.pagina
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("registro_${e.id}")) {
        Etiqueta(
            stringResource(textoEstadoEntrada(e.estado)),
            if (e.estado == EstadoEntrada.CONFIRMADA) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            if (e.estado == EstadoEntrada.CONFIRMADA) Gravedad.CORRECTO else Gravedad.ADVERTENCIA,
        )
        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.comparacion_en_etiqueta), style = MaterialTheme.typography.labelMedium)
                TextoId(idEtiqueta)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.comparacion_en_registro), style = MaterialTheme.typography.labelMedium)
                TextoId(e.idNormalizado)
            }
        }
        if (idEtiqueta == e.idNormalizado) {
            Etiqueta(stringResource(R.string.comparacion_ids_identicos), Icons.Filled.CheckCircle, Gravedad.CORRECTO)
        } else {
            Etiqueta(stringResource(R.string.comparacion_ids_distintos), Icons.Filled.ErrorOutline, Gravedad.PELIGRO)
        }

        if (pagina == null) {
            Aviso(stringResource(R.string.comparacion_pagina_no_disponible), Gravedad.ADVERTENCIA)
        } else if (bmp == null) {
            CircularProgressIndicator()
        } else {
            val encabezado = remember(bmp, e.cajaEncabezados) { recortarZona(bmp, e.cajaEncabezados, pagina.anchoProcesada, pagina.altoProcesada, 0.6f) }
            val fila = remember(bmp, e.cajaFila) { recortarZona(bmp, e.cajaFila ?: e.cajaId, pagina.anchoProcesada, pagina.altoProcesada) }
            if (fila != null) {
                encabezado?.let { ImagenAmpliable(it, stringResource(R.string.recorte_encabezado), alturaMax = 90) }
                ImagenAmpliable(fila, stringResource(R.string.recorte_fila), alturaMax = 160)
                Text(stringResource(R.string.comparacion_pagina_completa), style = MaterialTheme.typography.labelMedium)
                ImagenAmpliable(bmp, stringResource(R.string.comparacion_pagina_completa), alturaMax = 160)
            } else {
                // Sin ubicación precisa: se muestra la página completa en lugar de un recorte inventado
                Aviso(stringResource(R.string.comparacion_sin_recorte_registro), Gravedad.ADVERTENCIA)
                ImagenAmpliable(bmp, stringResource(R.string.comparacion_pagina_completa), alturaMax = 360)
            }
            BotonOriginal(contenedor, pagina)
        }

        Text(stringResource(R.string.resultados_titulo), fontWeight = FontWeight.Bold)
        if (r.resultados.isEmpty()) Aviso(stringResource(R.string.comparacion_sin_resultados), Gravedad.ADVERTENCIA)
        r.resultados.forEach { res ->
            Column {
                Row {
                    Text(res.nombre, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("«${res.texto}»", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EtiquetaCategoria(res.categoria)
                    Text(stringResource(textoOrigen(res.origen)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        FilaDato(stringResource(R.string.dato_leido_ocr), e.idOcr ?: "—")
        FilaDato(stringResource(R.string.dato_origen_id), stringResource(textoOrigen(e.origenId)))
        FilaDato(stringResource(R.string.dato_fecha_documento), e.fecha ?: "—")
        FilaDato(stringResource(R.string.dato_validacion), e.validacion ?: stringResource(R.string.no_documentado))
        FilaDato(stringResource(R.string.dato_procedencia), r.lote?.procedencia?.ifBlank { null } ?: "—")
        FilaDato(stringResource(R.string.dato_pagina), pagina?.numero?.toString() ?: "—")
        FilaDato(stringResource(R.string.dato_incorporado), formatearFecha(e.creado))
        FilaDato(stringResource(R.string.dato_vence), formatearFecha(e.vence))
        TextButton(onClick = { onAbrirEntrada(e.id) }) { Text(stringResource(R.string.comparacion_abrir_registro)) }
    }
}

@Composable
private fun BotonOriginal(contenedor: Contenedor, pagina: PaginaEntidad) {
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var cargando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    TextButton(onClick = {
        cargando = true
        alcance.launch(Dispatchers.Main) {
            original = contenedor.repositorio.imagen(pagina, procesada = false)
            cargando = false
        }
    }, enabled = !cargando) { Text(stringResource(R.string.ver_foto_original)) }
    original?.let { VisorImagen(it, stringResource(R.string.ver_foto_original)) { original = null } }
}

@Composable
private fun TablaDeterminaciones(d: DatosComparacion) {
    if (d.informe.determinaciones.isEmpty()) return
    Seccion(stringResource(R.string.comparacion_determinaciones)) {
        d.informe.determinaciones.groupBy { it.tipoRegistro }.forEach { (tipo, filas) ->
            Text(stringResource(textoTipo(tipo)), fontWeight = FontWeight.Bold)
            filas.forEach { f ->
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(f.nombre, fontWeight = FontWeight.Medium)
                    when {
                        f.ausente -> Aviso(stringResource(R.string.comparacion_determinacion_ausente), Gravedad.ADVERTENCIA)
                        else -> {
                            Text(f.textos.joinToString(" · ") { "«$it»" }, style = MaterialTheme.typography.bodySmall)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { f.categorias.distinct().forEach { EtiquetaCategoria(it) } }
                            if (f.contradictoria) Aviso(stringResource(R.string.comparacion_contradictoria), Gravedad.PELIGRO)
                        }
                    }
                }
            }
        }
    }
}
