package com.hemocheck

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.hemocheck.ui.AppHemoCheck
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.tema.TemaHemoCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Impide capturas de pantalla, grabaciones y la vista previa en «Recientes»
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()

        val app = application as HemoCheckApp
        setContent {
            TemaHemoCheck {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var contenedor by remember { mutableStateOf(app.contenedor) }
                    val c = contenedor
                    if (c == null) {
                        ErrorInicio(onRestablecer = {
                            app.restablecer()
                            contenedor = app.contenedor
                        })
                    } else {
                        AppHemoCheck(
                            contenedor = c,
                            biometriaDisponible = ::biometriaDisponible,
                            onPedirBiometria = { pedirBiometria { c.sesion.desbloquear() } },
                        )
                    }
                }
            }
        }

        // Revisión periódica del tiempo de inactividad
        lifecycleScope.launch {
            while (true) {
                delay(10_000)
                app.contenedor?.sesion?.revisarInactividad()
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as HemoCheckApp).contenedor?.sesion?.registrarActividad()
    }

    private fun biometriaDisponible(): Boolean {
        val c = (application as HemoCheckApp).contenedor ?: return false
        return c.ajustes.biometria && c.pin.configurado && c.ajustes.avisoInicialAceptado &&
            BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Huella o rostro (clase fuerte). El PIN siempre queda como alternativa. */
    fun pedirBiometria(onOk: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometria_titulo))
            .setSubtitle(getString(R.string.biometria_subtitulo))
            .setNegativeButtonText(getString(R.string.usar_pin))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
private fun ErrorInicio(onRestablecer: () -> Unit) {
    var confirmar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.error_inicio_titulo), style = MaterialTheme.typography.headlineSmall)
        Aviso(stringResource(R.string.error_inicio_texto), Gravedad.PELIGRO)
        if (!confirmar) {
            Button(onClick = { confirmar = true }) { Text(stringResource(R.string.error_inicio_restablecer)) }
        } else {
            Aviso(stringResource(R.string.error_inicio_confirmar), Gravedad.PELIGRO)
            Button(onClick = onRestablecer) { Text(stringResource(R.string.error_inicio_restablecer_confirmado)) }
        }
    }
}
