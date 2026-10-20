package com.hemocheck.ui.bloqueo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hemocheck.R
import com.hemocheck.core.seguridad.ValidadorPin
import com.hemocheck.seguridad.GestorPin
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Gravedad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bienvenida y desbloqueo. La primera vez pide crear el PIN y aceptar el alcance de la
 * herramienta; luego pide el PIN (o la huella, si está habilitada).
 */
@Composable
fun PantallaBloqueo(
    pin: GestorPin,
    biometriaDisponible: Boolean,
    avisoAceptado: Boolean,
    onAceptarAviso: () -> Unit,
    onPedirBiometria: () -> Unit,
    onDesbloqueada: () -> Unit,
) {
    var configurado by remember { mutableStateOf(pin.configurado) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.bienvenida_subtitulo), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        when {
            !configurado -> CrearPin(pin) { configurado = true }
            !avisoAceptado -> AceptarAviso(onAceptarAviso)
            else -> IngresarPin(pin, biometriaDisponible, onPedirBiometria, onDesbloqueada)
        }
    }
}

@Composable
private fun CrearPin(pin: GestorPin, onCreado: () -> Unit) {
    var primero by remember { mutableStateOf("") }
    var segundo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    Text(stringResource(R.string.crear_pin_titulo), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.crear_pin_explicacion), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    CampoPin(primero, { primero = it; error = null }, stringResource(R.string.pin_nuevo), "pin_nuevo")
    CampoPin(segundo, { segundo = it; error = null }, stringResource(R.string.pin_repetir), "pin_repetir")
    error?.let { Aviso(stringResource(it), Gravedad.PELIGRO) }
    Button(
        enabled = !trabajando && primero.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().testTag("boton_crear_pin"),
        onClick = {
            if (primero != segundo) {
                error = R.string.pin_no_coincide
                return@Button
            }
            trabajando = true
            alcance.launch {
                val e = withContext(Dispatchers.Default) { pin.establecer(primero.toCharArray()) }
                trabajando = false
                when (e) {
                    null -> onCreado()
                    ValidadorPin.Error.LARGO, ValidadorPin.Error.NO_NUMERICO -> error = R.string.pin_largo
                    ValidadorPin.Error.TRIVIAL -> error = R.string.pin_trivial
                }
                primero = ""
                segundo = ""
            }
        },
    ) { Text(stringResource(R.string.crear_pin_boton)) }
}

@Composable
private fun AceptarAviso(onAceptar: () -> Unit) {
    var marcado by remember { mutableStateOf(false) }
    Aviso(stringResource(R.string.aviso_clinico_texto), Gravedad.ADVERTENCIA, titulo = stringResource(R.string.aviso_clinico_titulo))
    Aviso(stringResource(R.string.aviso_privacidad_texto), Gravedad.INFO, titulo = stringResource(R.string.aviso_privacidad_titulo))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = marcado, onCheckedChange = { marcado = it }, modifier = Modifier.testTag("check_aviso"))
        Text(stringResource(R.string.aviso_entiendo))
    }
    Button(enabled = marcado, onClick = onAceptar, modifier = Modifier.fillMaxWidth().testTag("boton_aceptar_aviso")) {
        Text(stringResource(R.string.continuar))
    }
}

@Composable
private fun IngresarPin(pin: GestorPin, biometriaDisponible: Boolean, onPedirBiometria: () -> Unit, onDesbloqueada: () -> Unit) {
    var valor by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var espera by remember { mutableLongStateOf(pin.esperaRestante()) }
    var trabajando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    val textoIncorrecto = stringResource(R.string.pin_incorrecto)
    val textoQuedan = stringResource(R.string.pin_intentos_restantes)

    LaunchedEffect(Unit) { if (biometriaDisponible) onPedirBiometria() }
    LaunchedEffect(espera > 0) {
        while (espera > 0) {
            delay(1000)
            espera = pin.esperaRestante()
        }
    }

    fun intentar() {
        if (valor.isEmpty() || trabajando) return
        trabajando = true
        val ingresado = valor.toCharArray()
        valor = ""
        alcance.launch {
            val r = withContext(Dispatchers.Default) { pin.verificar(ingresado) }
            trabajando = false
            when (r) {
                GestorPin.Verificacion.Correcto -> onDesbloqueada()
                is GestorPin.Verificacion.Incorrecto -> {
                    mensaje = if (r.intentosAntesDeEspera > 0) "$textoIncorrecto ${textoQuedan.format(r.intentosAntesDeEspera)}" else textoIncorrecto
                }
                is GestorPin.Verificacion.EnEspera -> {
                    mensaje = textoIncorrecto
                    espera = r.milisegundos
                }
            }
        }
    }

    Text(stringResource(R.string.ingresar_pin), style = MaterialTheme.typography.titleMedium)
    CampoPin(valor, { valor = it; mensaje = null }, stringResource(R.string.pin), "pin_ingreso", onListo = { intentar() })
    mensaje?.let { Aviso(it, Gravedad.PELIGRO) }
    if (espera > 0) Aviso(stringResource(R.string.pin_espera, (espera + 999) / 1000), Gravedad.ADVERTENCIA)
    Button(
        onClick = { intentar() },
        enabled = espera <= 0 && !trabajando && valor.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().testTag("boton_desbloquear"),
    ) {
        if (trabajando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.desbloquear))
    }
    if (biometriaDisponible) {
        OutlinedButton(onClick = onPedirBiometria, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Fingerprint, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.usar_biometria))
        }
    }
}

@Composable
fun CampoPin(valor: String, onCambio: (String) -> Unit, etiqueta: String, tag: String, onListo: (() -> Unit)? = null) {
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo -> onCambio(nuevo.filter { it.isDigit() }.take(ValidadorPin.LARGO_MAX)) },
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = if (onListo != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onListo?.invoke() }),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
