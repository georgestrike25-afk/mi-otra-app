package com.hemocheck.ui.historial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.core.corroboracion.EstadoCorroboracion
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.core.identificador.IdUnidad
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.EtiquetaEstado
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.TextoId
import com.hemocheck.ui.comun.formatearFecha
import com.hemocheck.ui.comun.textoEstadoEntrada
import com.hemocheck.ui.comun.textoTipo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaHistorial(
    contenedor: Contenedor,
    onAbrirVerificacion: (Long) -> Unit,
    onAbrirEntrada: (Long) -> Unit,
    onAtras: () -> Unit,
) {
    var pestana by remember { mutableIntStateOf(0) }
    var busqueda by remember { mutableStateOf("") }
    var soloPendientes by remember { mutableStateOf(false) }
    val flujoVerif = remember { contenedor.repositorio.observarVerificaciones() }
    val flujoEntradas = remember(soloPendientes) { contenedor.repositorio.observarEntradas(soloPendientes) }
    val verificaciones by flujoVerif.collectAsStateWithLifecycle(emptyList())
    val entradas by flujoEntradas.collectAsStateWithLifecycle(emptyList())
    // Búsqueda EXACTA por identificador canonizado (sin coincidencias aproximadas)
    val idBuscado = busqueda.takeIf { it.isNotBlank() }?.let { contenedor.repositorio.canonizar(it) }

    Scaffold(topBar = { Barra(stringResource(R.string.historial_titulo), onAtras = onAtras) }) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = IdUnidad.normalizar(it).take(32) },
                label = { Text(stringResource(R.string.historial_buscar)) },
                supportingText = { Text(stringResource(R.string.historial_buscar_ayuda)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("buscar_historial"),
            )
            TabRow(selectedTabIndex = pestana) {
                Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text(stringResource(R.string.historial_corroboraciones)) })
                Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text(stringResource(R.string.historial_registros)) })
            }
            Aviso(stringResource(R.string.historial_retencion), Gravedad.INFO, modifier = Modifier.padding(12.dp))
            if (pestana == 0) {
                val lista = verificaciones.filter { idBuscado == null || it.idEtiqueta == idBuscado }
                if (lista.isEmpty()) Text(stringResource(R.string.historial_vacio), Modifier.padding(16.dp))
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lista, key = { it.id }) { v ->
                        Card(Modifier.fillMaxWidth().clickable { onAbrirVerificacion(v.id) }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextoId(v.idEtiqueta)
                                Text(stringResource(R.string.historial_fecha, formatearFecha(v.creado)), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (v.revisadaEn != null) stringResource(R.string.comparacion_revisada_el, formatearFecha(v.revisadaEn))
                                    else stringResource(R.string.comparacion_sin_revision_visual),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    v.estados.split(",").filter { it.isNotBlank() }
                                        .mapNotNull { runCatching { EstadoCorroboracion.valueOf(it) }.getOrNull() }
                                        .forEach { EtiquetaEstado(it) }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = soloPendientes, onClick = { soloPendientes = !soloPendientes }, label = { Text(stringResource(R.string.historial_solo_pendientes)) })
                }
                val lista = entradas.filter { idBuscado == null || it.idNormalizado == idBuscado }
                if (lista.isEmpty()) Text(stringResource(R.string.historial_vacio), Modifier.padding(16.dp))
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lista, key = { it.id }) { e ->
                        Card(Modifier.fillMaxWidth().clickable { onAbrirEntrada(e.id) }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextoId(e.idNormalizado)
                                Text("${stringResource(textoTipo(e.tipo))} · ${stringResource(textoEstadoEntrada(e.estado))}", style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.historial_incorporado, formatearFecha(e.creado)), style = MaterialTheme.typography.bodySmall)
                                if (e.estado == EstadoEntrada.PENDIENTE_REVISION) {
                                    EtiquetaEstado(EstadoCorroboracion.PENDIENTE_REVISION)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
