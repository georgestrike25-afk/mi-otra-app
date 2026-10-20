package com.hemocheck.ui.serologia

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.captura.FilaEditable
import com.hemocheck.captura.PaginaCapturada
import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.analisis.HojaAnalizada
import com.hemocheck.core.identificador.IdUnidad
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.datos.ArchivosCifrados
import com.hemocheck.datos.db.OrigenDato
import com.hemocheck.imagen.decodificarReducido
import com.hemocheck.imagen.recortarZona
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.ImagenAmpliable
import com.hemocheck.ui.comun.formatearId
import com.hemocheck.ui.comun.textoAvisoPagina
import com.hemocheck.ui.comun.textoCalidad
import com.hemocheck.ui.comun.textoProblema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Revisión y corrección del OCR antes de guardar el registro de serología. */
@Composable
fun PantallaRevisionSerologia(contenedor: Contenedor, onGuardado: () -> Unit, onAtras: () -> Unit) {
    val b by contenedor.borradores.serologia.collectAsStateWithLifecycle()
    var dialogo by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val alcance = rememberCoroutineScope()
    val formatos = remember { contenedor.ajustes.identificadores.formatos() }
    // Cada página se decodifica una sola vez (en memoria) y se comparte entre sus filas
    val imagenes = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(b.paginas.map { it.uid }) {
        for (p in b.paginas) {
            if (p.uid in imagenes) continue
            withContext(Dispatchers.Default) { decodificarReducido(p.jpegProcesada, 2400) }?.let { imagenes[p.uid] = it }
        }
    }

    Scaffold(
        topBar = { Barra(stringResource(R.string.revision_ocr_titulo), onAtras = onAtras) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                error?.let { Aviso(stringResource(it), Gravedad.PELIGRO) }
                Button(
                    onClick = { dialogo = true },
                    enabled = !guardando && b.paginas.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("boton_guardar_registro"),
                ) { Text(stringResource(R.string.revision_guardar)) }
            }
        },
    ) { relleno ->
        LazyColumn(
            Modifier.fillMaxSize().padding(relleno).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Aviso(stringResource(R.string.revision_ocr_explicacion), Gravedad.INFO) }
            b.paginas.forEachIndexed { i, p ->
                val hoja = b.analisis[p.uid]
                item(key = "pagina_${p.uid}") { EncabezadoPagina(i + 1, p, hoja, imagenes[p.uid]) }
                items(b.filas.filter { it.paginaUid == p.uid }, key = { it.uid }) { fila ->
                    TarjetaFila(
                        fila = fila,
                        tipo = b.tipo,
                        pagina = p,
                        imagen = imagenes[p.uid],
                        idValido = formatos.any { f -> f.canonizar(fila.idTexto) != null },
                        onCambio = { contenedor.borradores.actualizarFila(it) },
                    )
                }
                item(key = "agregar_${p.uid}") {
                    OutlinedButton(onClick = {
                        contenedor.borradores.agregarFila(
                            FilaEditable(
                                paginaUid = p.uid, idTexto = "", idOcr = null, sugerido = null, origenId = OrigenDato.MANUAL,
                                cajaId = null, cajaFila = null, cajaEncabezados = null, fecha = hoja?.fechaPagina,
                                validacion = hoja?.validacionPagina?.firstOrNull(), problemas = emptySet(), resultados = emptyList(),
                            )
                        )
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.revision_agregar_fila)) }
                }
            }
        }
    }

    if (dialogo) {
        val activas = b.filas.filter { !it.descartada && it.idTexto.isNotBlank() }
        val confirmadas = activas.count { it.confirmada && it.resultados.none { r -> r.clave == AnalizadorHoja.CLAVE_DESCONOCIDA } }
        AlertDialog(
            onDismissRequest = { dialogo = false },
            title = { Text(stringResource(R.string.revision_guardar_titulo)) },
            text = { Text(stringResource(R.string.revision_guardar_resumen, b.paginas.size, confirmadas, activas.size - confirmadas)) },
            confirmButton = {
                TextButton(onClick = {
                    dialogo = false
                    guardando = true
                    alcance.launch(Dispatchers.Main) {
                        try {
                            val id = contenedor.repositorio.guardarSerologia(b)
                            contenedor.borradores.loteGuardado(id, b.tipo)
                            onGuardado()
                        } catch (e: ArchivosCifrados.SinEspacioException) {
                            error = R.string.error_sin_espacio
                        } catch (e: Exception) {
                            error = R.string.error_guardar
                        } finally {
                            guardando = false
                        }
                    }
                }, modifier = Modifier.testTag("boton_confirmar_guardar")) { Text(stringResource(R.string.guardar)) }
            },
            dismissButton = { TextButton(onClick = { dialogo = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

@Composable
private fun EncabezadoPagina(numero: Int, p: PaginaCapturada, hoja: HojaAnalizada?, imagen: Bitmap?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pagina_n, numero), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            imagen?.let { ImagenAmpliable(it, stringResource(R.string.pagina_n, numero), alturaMax = 220) }
            if (p.errorOcr) Aviso(stringResource(R.string.revision_error_ocr), Gravedad.PELIGRO)
            p.avisos.forEach { Aviso(stringResource(textoCalidad(it)), Gravedad.ADVERTENCIA) }
            hoja?.avisos?.forEach { Aviso(stringResource(textoAvisoPagina(it)), Gravedad.ADVERTENCIA) }
            hoja?.validacionPagina?.takeIf { it.isNotEmpty() }?.let {
                Text(stringResource(R.string.revision_validacion_documentada, it.joinToString(" · ")), style = MaterialTheme.typography.bodySmall)
            }
            hoja?.resultadosSinAsociar?.takeIf { it.isNotEmpty() }?.let { sueltos ->
                Aviso(
                    stringResource(R.string.revision_sueltos, sueltos.joinToString(", ") { it.textoCrudo }),
                    Gravedad.ADVERTENCIA,
                )
            }
        }
    }
}

@Composable
private fun TarjetaFila(
    fila: FilaEditable, tipo: TipoRegistro, pagina: PaginaCapturada, imagen: Bitmap?, idValido: Boolean, onCambio: (FilaEditable) -> Unit,
) {
    val recortes = remember(imagen, fila.cajaFila, fila.cajaEncabezados) {
        if (imagen == null) null to null
        else recortarZona(imagen, fila.cajaEncabezados, pagina.anchoProcesada, pagina.altoProcesada, 0.6f) to
            recortarZona(imagen, fila.cajaFila, pagina.anchoProcesada, pagina.altoProcesada)
    }
    Card(Modifier.fillMaxWidth().alpha(if (fila.descartada) 0.5f else 1f).testTag("fila_${fila.idTexto}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (encabezado, zona) = recortes
            encabezado?.let { ImagenAmpliable(it, stringResource(R.string.recorte_encabezado), alturaMax = 90) }
            if (zona != null) ImagenAmpliable(zona, stringResource(R.string.recorte_fila), alturaMax = 140)
            else Aviso(stringResource(R.string.revision_sin_recorte), Gravedad.ADVERTENCIA)

            OutlinedTextField(
                value = fila.idTexto,
                onValueChange = { onCambio(fila.copy(idTexto = IdUnidad.normalizar(it).take(32))) },
                label = { Text(stringResource(R.string.revision_identificador)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            fila.idOcr?.let { Text(stringResource(R.string.revision_leido_ocr, it), style = MaterialTheme.typography.bodySmall) }
            if (fila.sugerido != null && fila.sugerido != fila.idTexto) {
                AssistChip(
                    onClick = { onCambio(fila.copy(idTexto = fila.sugerido)) },
                    label = { Text(stringResource(R.string.revision_usar_sugerencia, formatearId(fila.sugerido))) },
                )
            }
            if (fila.idTexto.isNotBlank() && !idValido) Aviso(stringResource(R.string.revision_formato_invalido), Gravedad.ADVERTENCIA)
            fila.problemas.forEach { Aviso(stringResource(textoProblema(it)), if (it.bloqueante) Gravedad.PELIGRO else Gravedad.ADVERTENCIA) }
            fila.fecha?.let { Text(stringResource(R.string.revision_fecha, it), style = MaterialTheme.typography.bodySmall) }
            fila.validacion?.let { Text(stringResource(R.string.revision_validacion_documentada, it), style = MaterialTheme.typography.bodySmall) }

            EditorResultados(
                tipo = tipo,
                resultados = fila.resultados,
                onCambio = { onCambio(fila.copy(resultados = it, confirmada = false)) },
                onQuitar = { r ->
                    onCambio(fila.copy(
                        resultados = fila.resultados - r,
                        resultadosQuitados = if (r.textoOcr != null) fila.resultadosQuitados + r else fila.resultadosQuitados,
                        confirmada = false,
                    ))
                },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = fila.confirmada,
                    enabled = !fila.descartada && fila.idTexto.isNotBlank(),
                    onCheckedChange = { onCambio(fila.copy(confirmada = it)) },
                    modifier = Modifier.testTag("confirmar_fila_${fila.idTexto}"),
                )
                Text(stringResource(R.string.revision_confirmar_fila), style = MaterialTheme.typography.bodyMedium)
            }
            if (fila.confirmada && fila.tieneBloqueantes) Aviso(stringResource(R.string.revision_confirmada_con_problemas), Gravedad.ADVERTENCIA)
            if (!fila.confirmada && !fila.descartada) Text(stringResource(R.string.revision_quedara_pendiente), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onCambio(fila.copy(descartada = !fila.descartada, confirmada = false)) }) {
                Text(stringResource(if (fila.descartada) R.string.revision_restaurar_fila else R.string.revision_descartar_fila))
            }
        }
    }
}
