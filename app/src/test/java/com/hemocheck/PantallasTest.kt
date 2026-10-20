package com.hemocheck

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hemocheck.seguridad.GestorPin
import com.hemocheck.ui.bloqueo.PantallaBloqueo
import com.hemocheck.ui.camara.PermisoCamara
import com.hemocheck.ui.tema.TemaHemoCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class PantallasTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun texto(id: Int) = context.getString(id)

    @Test
    fun `permiso de camara denegado muestra explicacion y acceso a ajustes`() {
        var ajustes = false
        compose.setContent { TemaHemoCheck { PermisoCamara(denegado = true, onPedir = {}, onAjustes = { ajustes = true }, onCancelar = {}) } }
        compose.onNodeWithText(texto(R.string.permiso_camara_denegado)).assertIsDisplayed()
        compose.onNodeWithText(texto(R.string.permiso_camara_ajustes)).performClick()
        assertTrue(ajustes)
    }

    @Test
    fun `PIN incorrecto no desbloquea y muestra el error`() {
        val prefs = context.getSharedPreferences("prueba_pantalla_pin", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val pin = GestorPin(prefs, ClavesFalsas(), { 0L }, iteraciones = 1_000)
        pin.establecer("482916".toCharArray())
        var desbloqueada = false
        compose.setContent {
            TemaHemoCheck {
                PantallaBloqueo(pin, biometriaDisponible = false, avisoAceptado = true, onAceptarAviso = {}, onPedirBiometria = {}, onDesbloqueada = { desbloqueada = true })
            }
        }
        compose.onNodeWithTag("pin_ingreso").performTextInput("111222")
        compose.onNodeWithTag("boton_desbloquear").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextContaining(texto(R.string.pin_incorrecto)) }
        assertFalse(desbloqueada)
        compose.onNodeWithTag("pin_ingreso").performTextInput("482916")
        compose.onNodeWithTag("boton_desbloquear").performScrollTo().performClick()
        compose.waitUntil(5_000) { desbloqueada }
    }

    @Test
    fun `primer uso pide crear PIN y aceptar el alcance`() {
        val prefs = context.getSharedPreferences("prueba_primer_uso", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val pin = GestorPin(prefs, ClavesFalsas(), { 0L }, iteraciones = 1_000)
        compose.setContent {
            TemaHemoCheck {
                PantallaBloqueo(pin, biometriaDisponible = false, avisoAceptado = false, onAceptarAviso = {}, onPedirBiometria = {}, onDesbloqueada = {})
            }
        }
        compose.onNodeWithText(texto(R.string.crear_pin_titulo)).assertIsDisplayed()
        compose.onNodeWithTag("pin_nuevo").performTextInput("482916")
        compose.onNodeWithTag("pin_repetir").performTextInput("482916")
        compose.onNodeWithTag("boton_crear_pin").performScrollTo().performClick()
        compose.waitUntil(5_000) { pin.configurado }
        compose.waitUntil(5_000) { compose.onAllNodesWithTextContaining(texto(R.string.aviso_entiendo)) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextContaining(t: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty()
}
