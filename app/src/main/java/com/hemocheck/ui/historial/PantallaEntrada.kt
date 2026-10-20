package com.hemocheck.ui.historial

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.captura.ResultadoEditable
import com.hemocheck.core.analisis.Problema
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.core.identificador.IdUnidad
import com.hemocheck.datos.DetalleEntrada
import com.hemocheck.imagen.recortarZona
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.FilaDato
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.ImagenAmpliable
import com.hemocheck.ui.comun.Seccion
import com.hemocheck.ui.comun.VisorImagen
import com.hemocheck.ui.comun.formatearFecha
import com.hemocheck.ui.comun.textoEstadoEntrada
import com.hemocheck.ui.comun.textoOrigen
import com.hemocheck.ui.comun.textoProblema
import com.hemocheck.ui.comun.textoTipo
import com.hemocheck.ui.serologia.EditorResultados
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Detalle de un registro de serología guardado, con su imagen de origen y sus correcciones. */
@Composable
fun PantallaEntrada(contenedor: Contenedor, entradaId: Long, onAtras: () -> Unit) {
    var detalle by remember { mutableStateOf<DetalleEntrada?>(null) }
    var cargado by remember { mutableStateOf(false) }
    var imagen by remember { mutableStateOf<Bitmap?>(null) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var editando by remember { mutableStateOf(false) }
    var idTexto by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<ResultadoEditable>>(emptyList()) }
    var quitados by remember { mutableStateOf<List<ResultadoEditable>>(emptyList()) }
    var confirmar by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<Int?>(null) }
    val alcance = rememberCoroutineScope()

    LaunchedEffect(entradaId, recarga) {
        val d = contenedor.repositorio.detalleEntrada(entradaId)
        detalle = d
        cargado = true
        d?.registro?.pagina?.let { imagen = contenedor.repositorio.imagen(it, procesada = true, ladoMax = 3200) }
        d?.let { det ->
            idTexto = det.registro.entrada.idNormalizado
            resultados = det.registro.resultados.map {
                ResultadoEditable(clave = it.clave, nombre = it.nombre, texto = it.texto, textoOcr = it.textoOcr, categoria = it.categoria, origen = it.origen, caja = it.caja, cajaEncabezado = it.cajaEncabezado, confianza = it.confianza)
            }
            quitados = emptyList()
            confirmar = det.registro.entrada.estado == EstadoEntrada.CONFIRMADA
        }
    }

    Scaffold(topBar = { Barra(stringResource(R.string.entrada_titulo), onAtras = onAtras) }) { relleno ->
        val d = detalle
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (d == null) {
                if (!cargado) CircularProgressIndicator() else Aviso(stringResource(R.string.entrada_no_disponible), Gravedad.ADVERTENCIA)
                return@Column
            }
            val e = d.registro.entrada
            val pagina = d.registro.pagina
            mensaje?.let { Aviso(stringResource(it), Gravedad.CORRECTO) }
            Seccion(stringResource(textoTipo(e.tipo))) {
                Text(com.hemocheck.ui.comun.formatearId(e.idNormalizado), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineSmall)
                FilaDato(stringResource(R.string.dato_estado), stringResource(textoEstadoEntrada(e.estado)))
                FilaDato(stringResource(R.string.dato_leido_ocr), e.idOcr ?: "—")
                FilaDato(stringResource(R.string.dato_origen_id), stringResource(textoOrigen(e.origenId)))
                FilaDato(stringResource(R.string.dato_fecha_documento), e.fecha ?: "—")
                FilaDato(stringResource(R.string.dato_validacion), e.validacion ?: stringResource(R.string.no_documentado))
                FilaDato(stringResource(R.string.dato_procedencia), d.registro.lote?.procedencia?.ifBlank { null } ?: "—")
                FilaDato(stringResource(R.string.dato_pagina), pagina?.numero?.toString() ?: "—")
                FilaDato(stringResource(R.string.dato_incorporado), formatearFecha(e.creado))
                FilaDato(stringResource(R.string.dato_vence), formatearFecha(e.vence))
                e.problemas.split(",").filter { it.isNotBlank() }
                    .mapNotNull { runCatching { Problema.valueOf(it) }.getOrNull() }
                    .forEach { Aviso(stringResource(textoProblema(it)), Gravedad.ADVERTENCIA) }
            }
            val bmp = imagen
            if (pagina != null && bmp != null) {
                val fila = remember(bmp) { recortarZona(bmp, e.cajaFila ?: e.cajaId, pagina.anchoProcesada, pagina.altoProcesada) }
                if (fila != null) ImagenAmpliable(fila, stringResource(R.string.recorte_fila), alturaMax = 160)
                else Aviso(stringResource(R.string.comparacion_sin_recorte_registro), Gravedad.ADVERTENCIA)
                ImagenAmpliable(bmp, stringResource(R.string.comparacion_pagina_completa), alturaMax = 300)
                TextButton(onClick = { alcance.launch(Dispatchers.Main) { original = contenedor.repositorio.imagen(pagina, procesada = false) } }) {
                    Text(stringResource(R.string.ver_foto_original))
                }
            } else if (pagina == null) {
                Aviso(stringResource(R.string.comparacion_pagina_no_disponible), Gravedad.ADVERTENCIA)
            }

            if (!editando) {
                Seccion(stringResource(R.string.resultados_titulo)) {
                    d.registro.resultados.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.nombre, modifier = Modifier.weight(1f))
                            Text("«${r.texto}»", modifier = Modifier.weight(1f))
                            com.hemocheck.ui.comun.EtiquetaCategoria(r.categoria)
                        }
                    }
                    if (d.registro.resultados.isEmpty()) Text(stringResource(R.string.resultados_ninguno))
                }
                Button(onClick = { editando = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (e.estado == EstadoEntrada.PENDIENTE_REVISION) R.string.entrada_revisar else R.string.entrada_corregir))
                }
            } else {
                Seccion(stringResource(R.string.entrada_editar)) {
                    OutlinedTextField(
                        value = idTexto,
                        onValueChange = { idTexto = IdUnidad.normalizar(it).take(32) },
                        label = { Text(stringResource(R.string.revision_identificador)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EditorResultados(
                        tipo = e.tipo,
                        resultados = resultados,
                        onCambio = { resultados = it },
                        onQuitar = { r -> resultados = resultados - r; quitados = quitados + r },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmar, onCheckedChange = { confirmar = it })
                        Text(stringResource(R.string.revision_confirmar_fila))
                    }
                    Button(onClick = {
                        alcance.launch(Dispatchers.Main) {
                            if (contenedor.repositorio.actualizarEntrada(entradaId, idTexto, resultados, quitados, confirmar)) {
                                mensaje = R.string.entrada_guardada
                                editando = false
                                recarga++
                            }
                        }
                    }, enabled = idTexto.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guardar)) }
                    TextButton(onClick = { editando = false; recarga++ }) { Text(stringResource(R.string.cancelar)) }
                }
            }

            Seccion(stringResource(R.string.comparacion_correcciones)) {
                if (d.correcciones.isEmpty()) Text(stringResource(R.string.entrada_sin_correcciones))
                d.correcciones.forEach {
                    Text(stringResource(R.string.correccion_linea, formatearFecha(it.fecha), it.campo, it.valorAnterior ?: "—", it.valorNuevo ?: "—"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    original?.let { VisorImagen(it, stringResource(R.string.ver_foto_original)) { original = null } }
}
