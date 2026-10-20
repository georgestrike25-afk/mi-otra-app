package com.hemocheck.core

import com.hemocheck.core.seguridad.CifradorAesGcm
import com.hemocheck.core.seguridad.CredencialPin
import com.hemocheck.core.seguridad.DatosCorruptosException
import com.hemocheck.core.seguridad.DerivadorPin
import com.hemocheck.core.seguridad.PoliticaIntentosPin
import com.hemocheck.core.seguridad.TemporizadorInactividad
import com.hemocheck.core.seguridad.ValidadorPin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SeguridadTest {
    @Test
    fun `cifrado y descifrado AES-GCM`() {
        val clave = CifradorAesGcm.nuevaClave()
        val datos = "Imagen ficticia de prueba".toByteArray()
        val c = CifradorAesGcm.cifrar(clave, datos, "archivo1".toByteArray())
        assertFalse(String(c, Charsets.ISO_8859_1).contains("ficticia"))
        assertArrayEquals(datos, CifradorAesGcm.descifrar(clave, c, "archivo1".toByteArray()))
    }

    @Test
    fun `mismo contenido cifrado dos veces da resultados distintos`() {
        val clave = CifradorAesGcm.nuevaClave()
        val a = CifradorAesGcm.cifrar(clave, ByteArray(100))
        val b = CifradorAesGcm.cifrar(clave, ByteArray(100))
        assertFalse(a.contentEquals(b))
    }

    @Test(expected = DatosCorruptosException::class)
    fun `alteracion del contenido se detecta`() {
        val clave = CifradorAesGcm.nuevaClave()
        val c = CifradorAesGcm.cifrar(clave, ByteArray(64) { it.toByte() })
        c[c.size - 5] = (c[c.size - 5].toInt() xor 1).toByte()
        CifradorAesGcm.descifrar(clave, c)
    }

    @Test(expected = DatosCorruptosException::class)
    fun `clave incorrecta se detecta`() {
        val c = CifradorAesGcm.cifrar(CifradorAesGcm.nuevaClave(), ByteArray(10))
        CifradorAesGcm.descifrar(CifradorAesGcm.nuevaClave(), c)
    }

    @Test(expected = DatosCorruptosException::class)
    fun `archivo intercambiado (AAD distinto) se detecta`() {
        val clave = CifradorAesGcm.nuevaClave()
        val c = CifradorAesGcm.cifrar(clave, ByteArray(10), "a".toByteArray())
        CifradorAesGcm.descifrar(clave, c, "b".toByteArray())
    }

    private val claveHmac = SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256")
    private val pimienta: (ByteArray) -> ByteArray = { Mac.getInstance("HmacSHA256").apply { init(claveHmac) }.doFinal(it) }
    private val derivador = DerivadorPin(pimienta, iteraciones = 1_000)

    @Test
    fun `PIN correcto e incorrecto`() {
        val cred = derivador.crear("482916".toCharArray())
        assertTrue(derivador.verificar("482916".toCharArray(), cred))
        assertFalse(derivador.verificar("482917".toCharArray(), cred))
    }

    @Test
    fun `la credencial no contiene el PIN y se serializa`() {
        val cred = derivador.crear("482916".toCharArray())
        val s = cred.serializar()
        assertFalse(s.contains("482916"))
        val leida = CredencialPin.deserializar(s)
        assertEquals(cred, leida)
        assertTrue(derivador.verificar("482916".toCharArray(), leida!!))
        assertNull(CredencialPin.deserializar("basura"))
    }

    @Test
    fun `sin la clave de Keystore el verificador no sirve`() {
        val cred = derivador.crear("482916".toCharArray())
        val otraPimienta = DerivadorPin({ Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 9 }, "HmacSHA256")) }.doFinal(it) }, 1_000)
        assertFalse(otraPimienta.verificar("482916".toCharArray(), cred))
    }

    @Test
    fun `validacion del PIN`() {
        assertEquals(ValidadorPin.Error.LARGO, ValidadorPin.validar("1234".toCharArray()))
        assertEquals(ValidadorPin.Error.TRIVIAL, ValidadorPin.validar("123456".toCharArray()))
        assertEquals(ValidadorPin.Error.TRIVIAL, ValidadorPin.validar("000000".toCharArray()))
        assertEquals(ValidadorPin.Error.TRIVIAL, ValidadorPin.validar("987654".toCharArray()))
        assertEquals(ValidadorPin.Error.NO_NUMERICO, ValidadorPin.validar("12a456".toCharArray()))
        assertNull(ValidadorPin.validar("482916".toCharArray()))
    }

    @Test
    fun `espera creciente tras intentos fallidos`() {
        assertEquals(0L, PoliticaIntentosPin.espera(4))
        assertEquals(30_000L, PoliticaIntentosPin.espera(5))
        assertTrue(PoliticaIntentosPin.espera(9) > PoliticaIntentosPin.espera(6))
    }

    @Test
    fun `bloqueo por inactividad`() {
        val t = TemporizadorInactividad { 60_000L }
        assertFalse(t.vencido(1_000L))
        t.registrarActividad(1_000L)
        assertFalse(t.vencido(30_000L))
        assertTrue(t.vencido(61_000L))
        assertNotNull(t)
    }
}
