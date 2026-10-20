package com.hemocheck.core

import com.hemocheck.core.identificador.Confusiones
import com.hemocheck.core.identificador.ConfiguracionIdentificadores
import com.hemocheck.core.identificador.FormatoIsbt128
import com.hemocheck.core.identificador.FormatoNumerico
import com.hemocheck.core.identificador.FormatoPersonalizado
import com.hemocheck.core.identificador.IdUnidad
import com.hemocheck.core.identificador.Mod37_2
import com.hemocheck.core.identificador.TipoFormato
import com.hemocheck.core.ocr.TextoLinea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificadoresTest {
    private val formatos = ConfiguracionIdentificadores().formatos()

    private fun buscar(texto: String) =
        IdUnidad.buscarEnLinea(TextoLinea(pagina { linea(texto, 100, 100) }.lineas[0], 0), formatos)

    @Test
    fun `mod 37-2 detecta cualquier sustitucion de un caracter`() {
        val din = "X9999" + "26" + "000123" // ficticio
        val control = Mod37_2.calcular(din)
        assertTrue(Mod37_2.verificar(din + control))
        val alfabeto = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        for (i in din.indices) for (c in alfabeto) {
            if (c == din[i]) continue
            val alterado = din.substring(0, i) + c + din.substring(i + 1)
            assertFalse("No detectó cambio en posición $i", Mod37_2.calcular(alterado) == control)
        }
    }

    @Test
    fun `lee DIN ISBT con espacios flags y control valido`() {
        val din = "X999926000123"
        val control = Mod37_2.calcular(din)
        val o = buscar("Unidad: X9999 26 000123 00 $control").single()
        assertEquals(din, o.normalizado)
        assertTrue(o.estricto)
        assertEquals(true, o.controlValido)
        assertEquals(TipoFormato.ISBT128, o.formato)
    }

    @Test
    fun `control invalido se informa`() {
        val din = "X999926000123"
        val malo = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".first { it != Mod37_2.calcular(din) }
        val o = buscar("X9999 26 000123 00 $malo").single()
        assertEquals(false, o.controlValido)
    }

    @Test
    fun `caracteres parecidos no se corrigen solos, solo se sugieren`() {
        // O en lugar de 0 y S en lugar de 5
        val o = buscar("X9999 26 0O012S").single()
        assertFalse(o.estricto)
        assertEquals("X9999260O012S", o.normalizado)
        assertEquals("X999926000125", o.sugerido)
        // Con demasiados caracteres dudosos no se considera identificador
        assertTrue(buscar("X9999 26 OOO12S").isEmpty())
    }

    @Test
    fun `numero local numerico con guion`() {
        val o = buscar("Donación N° 2026-004512").single()
        assertEquals("2026004512", o.normalizado)
        assertEquals(TipoFormato.NUMERICO, o.formato)
    }

    @Test
    fun `numero de 4 digitos no es identificador`() {
        assertTrue(buscar("Año 2026 lote 1234").isEmpty())
    }

    @Test
    fun `el formato numerico no fragmenta un DIN`() {
        val res = buscar("X9999 26 000123")
        assertEquals(1, res.size)
        assertEquals(TipoFormato.ISBT128, res[0].formato)
    }

    @Test
    fun `varias unidades en la misma linea`() {
        val res = buscar("X9999 26 000123  X9999 26 000124")
        assertEquals(listOf("X999926000123", "X999926000124"), res.map { it.normalizado })
    }

    @Test
    fun `canonizacion del texto escrito por el usuario`() {
        assertEquals("X999926000123", IdUnidad.canonizar("x9999-26-000123 00", formatos))
        assertEquals("2026004512", IdUnidad.canonizar(" 2026 004512 ", formatos))
    }

    @Test
    fun `clave de confusion iguala O y 0`() {
        assertEquals(Confusiones.clave("X99992600O123"), Confusiones.clave("X999926000123"))
        assertFalse(Confusiones.clave("X999926000123") == Confusiones.clave("X999926000124"))
    }

    @Test
    fun `formato personalizado y patron invalido`() {
        val f = FormatoPersonalizado.crear("HC-\\d{5}")
        assertNotNull(f)
        assertEquals("HC12345", f!!.canonizar("hc-12345"))
        assertNull(FormatoPersonalizado.crear("(sin cerrar"))
        assertNull(FormatoPersonalizado.crear(".*"))
    }

    @Test
    fun `ubica el identificador dentro de la linea`() {
        val o = buscar("Unidad X9999 26 000123").single()
        val caja = o.caja!!
        // "Unidad " ocupa 7 caracteres de 18 px: el identificador empieza después
        assertTrue(caja.izq >= 100 + 6 * 18)
    }

    @Test
    fun `canonizar ISBT con barras e indicadores`() {
        assertEquals("X999926000123", FormatoIsbt128.canonizar("=X99992600012300"))
        assertNull(FormatoNumerico(6, 12).canonizar("12AB34"))
    }
}
