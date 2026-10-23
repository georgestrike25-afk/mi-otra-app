package com.hemocheck.core

import com.hemocheck.core.analisis.FilaCompacta
import com.hemocheck.core.geometria.Caja
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Columna Referencia/Estado con números ficticios. */
class FilaCompactaTest {
    private val p = pagina(3000, 2000) {
        linea("900155", 60, 400); linea("Apto", 300, 400)
        linea("900156", 60, 446); linea("No Apto", 300, 446)
        linea("900157", 60, 492); linea("Apto", 300, 492)
        linea("NO REACTIVO", 600, 446)
    }
    private fun cajaDe(texto: String): Caja = p.lineas.first { it.texto == texto }.caja!!

    @Test
    fun `estado de la fila y alerta de No Apto`() {
        val e = FilaCompacta.estado(p, cajaDe("900156"))!!
        assertEquals("No Apto", e.texto)
        assertTrue(e.noApto)
        assertFalse(FilaCompacta.estado(p, cajaDe("900155"))!!.noApto)
    }

    @Test
    fun `zona compacta incluye las filas vecinas y el estado`() {
        val id = cajaDe("900156")
        val z = FilaCompacta.zona(p, id, FilaCompacta.estado(p, id))
        assertTrue(z.arriba <= cajaDe("900155").arriba)
        assertTrue(z.abajo >= cajaDe("900157").abajo)
        assertTrue(z.der >= cajaDe("No Apto").der)
        assertTrue(z.der < cajaDe("NO REACTIVO").izq)
    }

    @Test
    fun `sin columna de estado`() {
        val q = pagina { linea("900200", 60, 400) }
        assertNull(FilaCompacta.estado(q, q.lineas[0].caja!!))
    }

    @Test
    fun `zona de la etiqueta toma solo la etiqueta adhesiva`() {
        // Etiqueta ficticia con el formato local; al costado, texto impreso en la bolsa,
        // y debajo otra etiqueta
        val e = pagina(1200, 2000) {
            linea("Hospital Ficticio - F.Ext:1/1/2026", 260, 680, altoLetra = 26)
            linea("Globulos Rojos Sedimentados", 260, 720, altoLetra = 28)
            linea("Unidad:900123G", 260, 760, altoLetra = 36)
            linea("*900123G*", 340, 850, altoLetra = 20)
            linea("CPD-OPTISOL 450 mL", 820, 730, altoLetra = 22)
            linea("A POS Ext:900123", 240, 1000, altoLetra = 34)
        }
        val linea = e.lineas.first { it.texto.startsWith("Unidad") }.caja!!
        val id = Caja(linea.der - 7 * 22, linea.arriba, linea.der, linea.abajo)
        val z = FilaCompacta.zonaEtiqueta(e, id)
        fun c(t: String) = e.lineas.first { it.texto.startsWith(t) }.caja!!
        assertTrue(z.arriba <= c("Hospital").arriba && z.abajo >= c("*900123G*").abajo)
        assertTrue(z.izq <= linea.izq)
        assertTrue(z.der < c("CPD").izq)
        assertTrue(z.abajo < c("A POS").arriba)
        assertEquals(linea, FilaCompacta.buscarEnEtiqueta(e, "900123"))
    }
}
