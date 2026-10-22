package com.hemocheck

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hemocheck.captura.Borradores
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.seguridad.GestorPin
import com.hemocheck.seguridad.Sesion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class SeguridadAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var ahora = 1_000_000L
    private val prefs = context.getSharedPreferences("prueba_pin", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    private val pin = GestorPin(prefs, ClavesFalsas(), { ahora }, iteraciones = 1_000)

    @Test
    fun `el PIN no se guarda en texto plano`() {
        assertEquals(null, pin.establecer("482916".toCharArray()))
        val todo = prefs.all.values.joinToString()
        assertFalse(todo.contains("482916"))
        assertTrue(pin.configurado)
    }

    @Test
    fun `PIN incorrecto y espera tras varios intentos`() {
        pin.establecer("482916".toCharArray())
        repeat(4) { assertTrue(pin.verificar("111222".toCharArray()) is GestorPin.Verificacion.Incorrecto) }
        assertTrue(pin.verificar("111222".toCharArray()) is GestorPin.Verificacion.EnEspera)
        // Aun con el PIN correcto, hay que esperar
        assertTrue(pin.verificar("482916".toCharArray()) is GestorPin.Verificacion.EnEspera)
        ahora += 31_000
        assertEquals(GestorPin.Verificacion.Correcto, pin.verificar("482916".toCharArray()))
    }

    @Test
    fun `PIN trivial rechazado`() {
        assertTrue(pin.establecer("123456".toCharArray()) != null)
        assertFalse(pin.configurado)
    }

    @Test
    fun `bloquear descarta los borradores en memoria`() {
        val borradores = Borradores()
        val sesion = Sesion { 3 }
        sesion.alBloquearse { borradores.descartarTodo() }
        sesion.desbloquear()
        borradores.iniciarSerologia(TipoRegistro.MOLECULAR, "x")
        borradores.agregarPagina(paginaFicticia(), null)
        assertEquals(1, borradores.serologia.value.paginas.size)
        sesion.bloquear()
        assertFalse(sesion.desbloqueada.value)
        assertTrue(borradores.serologia.value.paginas.isEmpty())
    }

    @Test
    fun `salir a segundo plano bloquea salvo una salida breve para elegir un PDF`() {
        val sesion = Sesion { 3 }
        sesion.desbloquear()
        sesion.alSalir()
        assertFalse(sesion.desbloqueada.value)

        sesion.desbloquear()
        sesion.permitirSalidaBreve()
        sesion.alSalir()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(30))
        sesion.alVolver()
        assertTrue(sesion.desbloqueada.value)

        // El permiso es de una sola vez
        sesion.alSalir()
        assertFalse(sesion.desbloqueada.value)

        // Si la ausencia es larga, se bloquea al volver
        sesion.desbloquear()
        sesion.permitirSalidaBreve()
        sesion.alSalir()
        ShadowSystemClock.advanceBy(Duration.ofMillis(Sesion.SALIDA_BREVE_MS + 1_000))
        sesion.alVolver()
        assertFalse(sesion.desbloqueada.value)
    }
}
