package com.hemocheck.ui.etiqueta

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.core.etiqueta.AvisoEtiqueta
import com.hemocheck.core.identificador.IdUnidad
import com.hemocheck.datos.ArchivosCifrados
import com.hemocheck.imagen.decodificarReducido
import com.hemocheck.imagen.recortarZona
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.ImagenAmpliable
import com.hemocheck.ui.comun.TextoId
import com.hemocheck.ui.comun.formatearId
import com.hemocheck.ui.comun.textoAvisoEtiqueta
import com.hemocheck.ui.comun.textoCalidad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Revisión del número de unidad leído en la etiqueta. La búsqueda solo empieza cuando el
 * usuario confirma explícitamente el identificador comparándolo con la etiqueta física.
 */
@Composable
fun PantallaRevisionEtiqueta(contenedor: Contenedor, onRepetir: () -> Unit, onVerificado: (Long) -> Unit, onAtras: () -> Unit) {
    val estado by contenedor.borradores.etiqueta.collectAsStateWithLifecycle()
    val pagina = estado.pagina
    val lectura = estado.lectura
    var texto by remember(lectura) { mutableStateOf(lectura?.propuesto.orEmpty()) }
    var confirmar by remember { mutableStateOf(false) }
    var ilegible by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val alcance = rememberCoroutineScope()

    val imagen by produceState<Bitmap?>(null, pagina?.uid) {
        value = pagina?.let { p -> withContext(Dispatchers.Default) { decodificarReducido(p.jpegProcesada, 2400) } }
    }

    fun verificar(id: String?) {
        trabajando = true
        alcance.launch(Dispatchers.Main) {
            try {
                val vid = contenedor.repositorio.crearVerificacion(pagina, lectura, id)
                contenedor.borradores.descartarEtiqueta()
                onVerificado(vid)
            } catch (e: ArchivosCifrados.SinEspacioException) {
                error = R.string.error_sin_espacio
            } catch (e: Exception) {
                error = R.string.error_guardar
            } finally {
                trabajando = false
            }
        }
    }

    Scaffold(topBar = { Barra(stringResource(R.string.etiqueta_titulo), onAtras = onAtras) }) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pagina == null || lectura == null) {
                Aviso(stringResource(R.string.etiqueta_sin_captura), Gravedad.ADVERTENCIA)
                Button(onClick = onRepetir, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.etiqueta_fotografiar)) }
                return@Column
            }
            imagen?.let { bmp ->
                ImagenAmpliable(bmp, stringResource(R.string.etiqueta_foto), alturaMax = 240)
                val caja = lectura.candidatos.firstOrNull { it.normalizado == texto || it.sugerido == texto }?.caja
                    ?: lectura.candidatos.firstOrNull()?.caja
                val recorte = remember(bmp, caja) { recortarZona(bmp, caja, pagina.anchoProcesada, pagina.altoProcesada, 0.8f) }
                if (recorte != null) {
                    Text(stringResource(R.string.etiqueta_recorte_id), style = MaterialTheme.typography.titleSmall)
                    ImagenAmpliable(recorte, stringResource(R.string.etiqueta_recorte_id), alturaMax = 120)
                } else {
                    Aviso(stringResource(R.string.etiqueta_sin_recorte), Gravedad.ADVERTENCIA)
                }
            }
            if (pagina.errorOcr) Aviso(stringResource(R.string.revision_error_ocr), Gravedad.PELIGRO)
            pagina.avisos.forEach { Aviso(stringResource(textoCalidad(it)), Gravedad.ADVERTENCIA) }
            lectura.avisos.forEach {
                Aviso(stringResource(textoAvisoEtiqueta(it)), if (it == AvisoEtiqueta.BARRAS_CONCORDANTE) Gravedad.CORRECTO else Gravedad.ADVERTENCIA)
            }
            lectura.idCodigoBarras?.let { Text(stringResource(R.string.etiqueta_codigo_barras, formatearId(it)), style = MaterialTheme.typography.bodySmall) }

            if (lectura.candidatos.size > 1 || lectura.candidatos.any { it.sugerido != null }) {
                Text(stringResource(R.string.etiqueta_candidatos), style = MaterialTheme.typography.titleSmall)
                lectura.candidatos.forEach { c ->
                    val valor = c.sugerido ?: c.normalizado
                    AssistChip(onClick = { texto = valor }, label = {
                        Text(
                            if (c.sugerido != null) stringResource(R.string.etiqueta_candidato_sugerido, c.textoCrudo, formatearId(valor))
                            else formatearId(valor)
                        )
                    })
                }
            }

            OutlinedTextField(
                value = texto,
                onValueChange = { texto = IdUnidad.normalizar(it).take(32) },
                label = { Text(stringResource(R.string.etiqueta_campo_id)) },
                supportingText = { Text(stringResource(R.string.etiqueta_campo_ayuda)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().testTag("campo_id_etiqueta"),
            )
            error?.let { Aviso(stringResource(it), Gravedad.PELIGRO) }
            if (trabajando) CircularProgressIndicator()
            Button(
                onClick = { confirmar = true },
                enabled = texto.isNotBlank() && !trabajando,
                modifier = Modifier.fillMaxWidth().testTag("boton_confirmar_id"),
            ) { Text(stringResource(R.string.etiqueta_confirmar_buscar)) }
            OutlinedButton(onClick = onRepetir, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.etiqueta_repetir)) }
            TextButton(onClick = { ilegible = true }, enabled = !trabajando, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.etiqueta_ilegible_boton))
            }
        }
    }

    if (confirmar) {
        val canon = contenedor.repositorio.canonizar(texto)
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text(stringResource(R.string.etiqueta_confirmar_titulo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextoId(canon, grande = true)
                    Text(stringResource(R.string.etiqueta_confirmar_texto))
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmar = false; verificar(texto) }, modifier = Modifier.testTag("boton_confirmar_busqueda")) {
                    Text(stringResource(R.string.etiqueta_confirmar_si))
                }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text(stringResource(R.string.etiqueta_confirmar_no)) } },
        )
    }
    if (ilegible) {
        AlertDialog(
            onDismissRequest = { ilegible = false },
            title = { Text(stringResource(R.string.etiqueta_ilegible_titulo)) },
            text = { Text(stringResource(R.string.etiqueta_ilegible_texto)) },
            confirmButton = { TextButton(onClick = { ilegible = false; verificar(null) }) { Text(stringResource(R.string.etiqueta_ilegible_registrar)) } },
            dismissButton = { TextButton(onClick = { ilegible = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}
