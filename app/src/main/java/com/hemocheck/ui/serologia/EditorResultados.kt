package com.hemocheck.ui.serologia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hemocheck.R
import com.hemocheck.captura.ResultadoEditable
import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.resultado.CatalogoDeterminaciones
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.ClasificadorResultado
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.datos.db.OrigenDato
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.EtiquetaCategoria
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.textoOrigen

/**
 * Edición de los resultados de una fila. La categoría se deduce siempre del texto escrito
 * (no se puede elegir una categoría distinta de lo documentado).
 */
@Composable
fun EditorResultados(
    tipo: TipoRegistro,
    resultados: List<ResultadoEditable>,
    onCambio: (List<ResultadoEditable>) -> Unit,
    onQuitar: (ResultadoEditable) -> Unit,
) {
    val catalogo = CatalogoDeterminaciones.de(tipo)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.resultados_titulo), fontWeight = FontWeight.Bold)
        if (resultados.isEmpty()) Text(stringResource(R.string.resultados_ninguno), style = MaterialTheme.typography.bodySmall)
        resultados.forEach { r ->
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectorDeterminacion(
                    tipo = tipo,
                    clave = r.clave,
                    nombre = r.nombre,
                    onElegir = { clave, nombre ->
                        onCambio(resultados.map { if (it.uid == r.uid) it.copy(clave = clave, nombre = nombre, origen = corregido(it.origen)) else it })
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onQuitar(r) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.resultado_quitar)) }
            }
            OutlinedTextField(
                value = r.texto,
                onValueChange = { nuevo ->
                    onCambio(resultados.map {
                        if (it.uid == r.uid) it.copy(
                            texto = nuevo,
                            categoria = ClasificadorResultado.clasificar(nuevo) ?: CategoriaResultado.NO_INTERPRETADO,
                            origen = corregido(it.origen),
                        ) else it
                    })
                },
                label = { Text(stringResource(R.string.resultado_texto_documentado)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EtiquetaCategoria(r.categoria)
                Text(stringResource(textoOrigen(r.origen)), style = MaterialTheme.typography.bodySmall)
            }
            if (r.textoOcr != null && r.textoOcr != r.texto) {
                Text(stringResource(R.string.resultado_leido_ocr, r.textoOcr), style = MaterialTheme.typography.bodySmall)
            }
            if (r.clave == AnalizadorHoja.CLAVE_DESCONOCIDA) Aviso(stringResource(R.string.resultado_sin_determinacion), Gravedad.ADVERTENCIA)
            if ((r.confianza ?: 1f) < AnalizadorHoja.UMBRAL_CONFIANZA_POR_OMISION && r.origen == OrigenDato.OCR) {
                Aviso(stringResource(R.string.resultado_baja_confianza), Gravedad.ADVERTENCIA)
            }
        }
        OutlinedButton(onClick = {
            val libre = catalogo.firstOrNull { d -> resultados.none { it.clave == d.clave } } ?: catalogo.first()
            onCambio(resultados + ResultadoEditable(clave = libre.clave, nombre = libre.nombre, texto = "", textoOcr = null, categoria = CategoriaResultado.NO_INTERPRETADO, origen = OrigenDato.MANUAL))
        }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.resultado_agregar))
        }
    }
}

private fun corregido(o: OrigenDato) = if (o == OrigenDato.OCR) OrigenDato.CORREGIDO else o

@Composable
private fun SelectorDeterminacion(tipo: TipoRegistro, clave: String, nombre: String, onElegir: (String, String) -> Unit, modifier: Modifier) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { abierto = true }) {
            Text(if (clave == AnalizadorHoja.CLAVE_DESCONOCIDA) stringResource(R.string.resultado_elegir_determinacion) else nombre, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            CatalogoDeterminaciones.de(tipo).forEach { d ->
                DropdownMenuItem(text = { Text(d.nombre) }, onClick = { abierto = false; onElegir(d.clave, d.nombre) })
            }
        }
    }
}
