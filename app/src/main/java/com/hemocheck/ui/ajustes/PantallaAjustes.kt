package com.hemocheck.ui.ajustes

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.core.identificador.FormatoPersonalizado
import com.hemocheck.core.resultado.CatalogoDeterminaciones
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.core.retencion.ResultadoLimpieza
import com.hemocheck.core.seguridad.ValidadorPin
import com.hemocheck.seguridad.GestorPin
import com.hemocheck.ui.bloqueo.CampoPin
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.Seccion
import com.hemocheck.ui.comun.formatearFecha
import com.hemocheck.ui.comun.textoTipo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaAjustes(contenedor: Contenedor, onAyuda: () -> Unit, onAtras: () -> Unit) {
    val ajustes = contenedor.ajustes
    val version by ajustes.version.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val alcance = rememberCoroutineScope()
    var cambiarPin by remember { mutableStateOf(false) }
    var borrarTodo by remember { mutableStateOf(false) }
    var resultadoLimpieza by remember { mutableStateOf<ResultadoLimpieza?>(null) }
    val biometriaPosible = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    // Lee los valores actuales (version fuerza la recomposición al cambiar)
    val ids = remember(version) { ajustes.identificadores }

    Scaffold(topBar = { Barra(stringResource(R.string.ajustes_titulo), onAtras = onAtras) }) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Seccion(stringResource(R.string.ajustes_seguridad)) {
                Button(onClick = { cambiarPin = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ajustes_cambiar_pin)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ajustes_biometria), modifier = Modifier.weight(1f))
                    Switch(checked = ajustes.biometria && biometriaPosible, enabled = biometriaPosible, onCheckedChange = { ajustes.biometria = it })
                }
                if (!biometriaPosible) Text(stringResource(R.string.ajustes_biometria_no_disponible), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ajustes_bloqueo_inactividad))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 5, 10).forEach { m ->
                        FilterChip(selected = ajustes.minutosBloqueo == m, onClick = { ajustes.minutosBloqueo = m }, label = { Text(stringResource(R.string.minutos, m)) })
                    }
                }
                Text(stringResource(R.string.ajustes_bloqueo_fondo), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ajustes_capturas), modifier = Modifier.weight(1f))
                    Switch(checked = ajustes.permitirCapturas, onCheckedChange = { ajustes.permitirCapturas = it })
                }
                if (ajustes.permitirCapturas) Aviso(stringResource(R.string.ajustes_capturas_aviso), Gravedad.ADVERTENCIA)
                OutlinedButton(onClick = { contenedor.sesion.bloquear() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bloquear_ahora)) }
            }

            Seccion(stringResource(R.string.ajustes_identificadores)) {
                Text(stringResource(R.string.ajustes_identificadores_explicacion), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ajustes_isbt), modifier = Modifier.weight(1f))
                    Switch(checked = ids.isbt128, onCheckedChange = { ajustes.identificadores = ids.copy(isbt128 = it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ajustes_numerico), modifier = Modifier.weight(1f))
                    Switch(checked = ids.numerico, onCheckedChange = { ajustes.identificadores = ids.copy(numerico = it) })
                }
                if (ids.numerico) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoNumero(stringResource(R.string.ajustes_minimo), ids.numericoMin, Modifier.weight(1f)) {
                            ajustes.identificadores = ids.copy(numericoMin = it.coerceIn(3, 20), numericoMax = maxOf(ids.numericoMax, it.coerceIn(3, 20)))
                        }
                        CampoNumero(stringResource(R.string.ajustes_maximo), ids.numericoMax, Modifier.weight(1f)) {
                            ajustes.identificadores = ids.copy(numericoMax = it.coerceIn(ids.numericoMin, 24))
                        }
                    }
                }
                var patron by remember(version) { mutableStateOf(ids.patronPersonalizado.orEmpty()) }
                val valido = patron.isBlank() || FormatoPersonalizado.crear(patron) != null
                OutlinedTextField(
                    value = patron,
                    onValueChange = { patron = it },
                    label = { Text(stringResource(R.string.ajustes_patron)) },
                    isError = !valido,
                    supportingText = { Text(stringResource(if (valido) R.string.ajustes_patron_ayuda else R.string.ajustes_patron_invalido)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(enabled = valido, onClick = { ajustes.identificadores = ids.copy(patronPersonalizado = patron.ifBlank { null }) }) {
                    Text(stringResource(R.string.ajustes_guardar_patron))
                }
                if (!ids.isbt128 && !ids.numerico && ids.patronPersonalizado.isNullOrBlank()) {
                    Aviso(stringResource(R.string.ajustes_sin_formatos), Gravedad.PELIGRO)
                }
            }

            Seccion(stringResource(R.string.ajustes_esperadas)) {
                Text(stringResource(R.string.ajustes_esperadas_explicacion), style = MaterialTheme.typography.bodySmall)
                TipoRegistro.entries.forEach { tipo ->
                    val esperadas = remember(version, tipo) { ajustes.esperadas(tipo) }
                    Text(stringResource(textoTipo(tipo)), style = MaterialTheme.typography.titleSmall)
                    CatalogoDeterminaciones.de(tipo).filter { it.cubre == setOf(it.clave) }.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = d.clave in esperadas, onCheckedChange = { marcado ->
                                ajustes.fijarEsperadas(tipo, if (marcado) esperadas + d.clave else esperadas - d.clave)
                            })
                            Text(d.nombre)
                        }
                    }
                }
            }

            Seccion(stringResource(R.string.ajustes_retencion)) {
                Text(stringResource(R.string.ajustes_retencion_texto))
                val ultima = ajustes.ultimaLimpieza
                Text(
                    if (ultima == 0L) stringResource(R.string.ajustes_limpieza_nunca)
                    else stringResource(
                        if (ajustes.ultimaLimpiezaCompleta) R.string.ajustes_limpieza_ultima else R.string.ajustes_limpieza_incompleta,
                        formatearFecha(ultima),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                resultadoLimpieza?.let {
                    Aviso(stringResource(R.string.ajustes_limpieza_resultado, it.filasEliminadas, it.archivosEliminados + it.huerfanosEliminados, it.archivosConError), if (it.completa) Gravedad.CORRECTO else Gravedad.ADVERTENCIA)
                }
                OutlinedButton(onClick = { alcance.launch { resultadoLimpieza = contenedor.limpieza.ejecutar() } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ajustes_limpiar_ahora))
                }
                Text(stringResource(R.string.ajustes_espacio, contenedor.repositorio.bytesImagenes() / (1024 * 1024)), style = MaterialTheme.typography.bodySmall)
            }

            Seccion(stringResource(R.string.ajustes_datos)) {
                Text(stringResource(R.string.ajustes_borrar_explicacion), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { borrarTodo = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ajustes_borrar_todo)) }
            }
            OutlinedButton(onClick = onAyuda, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ayuda)) }
        }
    }

    if (cambiarPin) DialogoCambiarPin(contenedor.pin) { cambiarPin = false }
    if (borrarTodo) DialogoBorrarTodo(contenedor.pin, onBorrar = {
        alcance.launch {
            contenedor.repositorio.borrarTodo()
            borrarTodo = false
        }
    }, onCancelar = { borrarTodo = false })
}

@Composable
private fun CampoNumero(etiqueta: String, valor: Int, modifier: Modifier, onCambio: (Int) -> Unit) {
    var texto by remember(valor) { mutableStateOf(valor.toString()) }
    OutlinedTextField(
        value = texto,
        onValueChange = { t ->
            texto = t.filter { it.isDigit() }.take(2)
            texto.toIntOrNull()?.let(onCambio)
        },
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun DialogoCambiarPin(pin: GestorPin, onCerrar: () -> Unit) {
    var actual by remember { mutableStateOf("") }
    var nuevo by remember { mutableStateOf("") }
    var repetido by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var listo by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.ajustes_cambiar_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (listo) {
                    Aviso(stringResource(R.string.ajustes_pin_cambiado), Gravedad.CORRECTO)
                } else {
                    CampoPin(actual, { actual = it }, stringResource(R.string.pin_actual), "pin_actual")
                    CampoPin(nuevo, { nuevo = it }, stringResource(R.string.pin_nuevo), "pin_nuevo2")
                    CampoPin(repetido, { repetido = it }, stringResource(R.string.pin_repetir), "pin_repetir2")
                    error?.let { Aviso(stringResource(it), Gravedad.PELIGRO) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (listo) { onCerrar(); return@TextButton }
                if (nuevo != repetido) { error = R.string.pin_no_coincide; return@TextButton }
                alcance.launch {
                    val ok = withContext(Dispatchers.Default) { pin.verificar(actual.toCharArray()) } == GestorPin.Verificacion.Correcto
                    if (!ok) { error = R.string.pin_incorrecto; return@launch }
                    when (withContext(Dispatchers.Default) { pin.establecer(nuevo.toCharArray()) }) {
                        null -> listo = true
                        ValidadorPin.Error.TRIVIAL -> error = R.string.pin_trivial
                        else -> error = R.string.pin_largo
                    }
                }
            }) { Text(stringResource(if (listo) R.string.cerrar else R.string.guardar)) }
        },
        dismissButton = { if (!listo) TextButton(onClick = onCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

@Composable
private fun DialogoBorrarTodo(pin: GestorPin, onBorrar: () -> Unit, onCancelar: () -> Unit) {
    var valor by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.ajustes_borrar_todo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Aviso(stringResource(R.string.ajustes_borrar_confirmar), Gravedad.PELIGRO)
                CampoPin(valor, { valor = it }, stringResource(R.string.pin), "pin_borrar")
                if (error) Aviso(stringResource(R.string.pin_incorrecto), Gravedad.PELIGRO)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                alcance.launch {
                    val ok = withContext(Dispatchers.Default) { pin.verificar(valor.toCharArray()) } == GestorPin.Verificacion.Correcto
                    if (ok) onBorrar() else error = true
                }
            }) { Text(stringResource(R.string.ajustes_borrar_definitivo)) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancelar)) } },
    )
}
