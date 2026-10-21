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
}
