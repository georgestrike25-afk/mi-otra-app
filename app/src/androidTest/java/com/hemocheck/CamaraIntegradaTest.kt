package com.hemocheck

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Comprueba en un dispositivo (emulador en CI) que la vista previa y la captura ocurren
 * DENTRO de HemoCheck: en ningún momento pasa a primer plano otra aplicación de cámara.
 */
@RunWith(AndroidJUnit4::class)
class CamaraIntegradaTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val reglas: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.CAMERA)).around(compose)

    private val dispositivo = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val paquete = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private fun desbloquear() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HemoCheckApp
        val c = app.contenedor
        assertNotNull("El almacenamiento protegido debe abrirse", c)
        c!!
        if (!c.pin.configurado) c.pin.establecer("482916".toCharArray())
        c.ajustes.avisoInicialAceptado = true
        compose.runOnUiThread { c.sesion.desbloquear() }
    }

    /**
     * Ninguna app de cámara pasa a primer plano. Los diálogos del sistema propios de un emulador
     * lento («no responde», paquete «android») se cierran y no cuentan como otra app.
     */
    private fun verificarDentroDeHemoCheck() {
        var actual = dispositivo.currentPackageName
        if (actual == "android" || actual == "com.android.systemui") {
            dispositivo.findObject(By.res("android:id/aerr_wait"))?.click()
            dispositivo.waitForIdle()
            actual = dispositivo.currentPackageName
        }
        assertFalse("Se abrió una cámara externa: $actual", actual != null && actual.contains("camera", ignoreCase = true))
        assertTrue("HemoCheck no está en primer plano: $actual", actual == paquete || actual == "android" || actual == "com.android.systemui")
    }

    private fun existe(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private val transmitiendo = hasTestTag("estado_camara") and
        SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("transmitiendo"))

    @Test
    fun vistaPreviaYCapturaDentroDeLaApp() {
        desbloquear()
        compose.waitUntil(10_000) { existe("opcion_verificar") }
        compose.onNodeWithTag("opcion_verificar").performClick()

        // La vista previa de CameraX está activa dentro de HemoCheck
        compose.waitUntil(30_000) { compose.onAllNodes(transmitiendo).fetchSemanticsNodes().isNotEmpty() }
        verificarDentroDeHemoCheck()

        // Tomar la foto: se procesa y se muestra la revisión, sin abrir otra app
        compose.onNodeWithTag("boton_tomar_foto").performClick()
        compose.waitUntil(60_000) { existe("revision_foto") }
        verificarDentroDeHemoCheck()

        // Repetir vuelve a la vista previa
        compose.onNodeWithTag("boton_repetir").performClick()
        compose.waitUntil(10_000) { !existe("revision_foto") && existe("boton_tomar_foto") }
        verificarDentroDeHemoCheck()

        // Confirmar la foto lleva a la revisión del número de unidad
        compose.waitUntil(30_000) { compose.onAllNodes(transmitiendo).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("boton_tomar_foto").performClick()
        compose.waitUntil(60_000) { existe("revision_foto") }
        compose.onNodeWithTag("boton_confirmar_foto").performClick()
        compose.waitUntil(20_000) { existe("campo_id_etiqueta") }
        verificarDentroDeHemoCheck()
    }
}
