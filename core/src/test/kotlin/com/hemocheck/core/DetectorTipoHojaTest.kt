package com.hemocheck.core

import com.hemocheck.core.analisis.DetectorTipoHoja
import com.hemocheck.core.resultado.TipoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Encabezados con el formato del servicio; números ficticios. */
class DetectorTipoHojaTest {
    @Test
    fun `planilla de biologia molecular`() {
        val p = pagina {
            linea("SEROLOGÍA", 1500, 100)
            linea("Derivante : Laboratorio Hemoterapia (Interno)", 1200, 140)
            linea("Sección : Biología molecular", 1300, 180)
            linea("Referencia Estado HIV NAT HBV NAT HCV NAT", 60, 300)
            linea("NAT NAT NAT", 400, 340)
            linea("900101 Apto NO REACTIVO NO REACTIVO NO REACTIVO", 60, 420)
        }
        assertEquals(TipoRegistro.MOLECULAR, DetectorTipoHoja.detectar(p))
    }

    @Test
    fun `planilla de serologia convencional`() {
        val p = pagina {
            linea("Derivante : Laboratorio Hemoterapia (Interno)", 1200, 140)
            linea("Sección : Hemoterapia", 1300, 180)
            linea("Referencia Estado HIV Ag/Ac Anti-HTLV I+II Anti-HVC AgHBs Anti-HBc Chagas EIA SIFILIS Brucelosis", 60, 300)
            linea("CMIA CMIA CMIA CMIA CMIA EIA CMIA R. Bengala", 400, 340)
            linea("900101 Apto NO REACTIVO NO REACTIVO", 60, 420)
        }
        assertEquals(TipoRegistro.CONVENCIONAL, DetectorTipoHoja.detectar(p))
    }

    @Test
    fun `sin encabezados no decide`() {
        assertNull(DetectorTipoHoja.detectar(pagina { linea("900101 Apto NO REACTIVO", 60, 420) }))
    }
}
