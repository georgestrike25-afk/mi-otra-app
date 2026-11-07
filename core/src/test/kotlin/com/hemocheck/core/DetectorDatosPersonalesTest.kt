package com.hemocheck.core

import com.hemocheck.core.etiqueta.DetectorDatosPersonales
import com.hemocheck.core.etiqueta.TipoDatoPersonal
import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.ocr.recortada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textos de etiqueta ficticios; los nombres y números de paciente son inventados. */
class DetectorDatosPersonalesTest {
    private fun detectar(vararg lineas: String, id: String? = "900123") =
        DetectorDatosPersonales.detectar(pagina { lineas.forEachIndexed { i, t -> linea(t, 100, 100 + i * 60) } }.lineas, id)

    @Test
    fun `una etiqueta normal no se bloquea`() {
        assertNull(
            detectar(
                "Hospital Ficticio - F.Ext:6/10/2026", "Globulos Rojos Sedimentados", "Unidad:900123G", "*900123G*",
                "A POS Ext:900123 - Hc:90012", "SEROLOGIA NO REACTIVA", "ChEIA,ChCMIA,Sifilis CMIA,R. Bengala",
                "HIV - AG/AC,HBsAg CMIA,HBc CMIA,VHC - CMIA", "F. Venc: 17/11/2026", "Vol 426ml - Cons: 4C +/- 2C",
            ),
        )
    }

    @Test
    fun `historia clinica y fecha de nacimiento bloquean`() {
        assertEquals(TipoDatoPersonal.NUMERO_HC, detectar("Unidad:900123G", "HC: 345443")!!.tipo)
        assertEquals(TipoDatoPersonal.FECHA_NACIMIENTO, detectar("Unidad:900123G", "FN: 6/7/76")!!.tipo)
        assertEquals(TipoDatoPersonal.PALABRA_CLAVE, detectar("Unidad:900123G", "Paciente: X")!!.tipo)
        assertTrue(TipoDatoPersonal.NUMERO_HC.duro)
    }

    @Test
    fun `el Hc que repite el numero de la unidad es de la etiqueta`() {
        assertNull(detectar("Unidad:900123G", "Ext:900123G - Hc:90012"))
        assertNull(detectar("Unidad:900123G", "Ext:900123G - Hc:900123"))
    }

    @Test
    fun `nombre y apellido se sospechan pero se pueden confirmar`() {
        val h = detectar("Unidad:900123G", "PEREZ LOPEZ")
        assertNotNull(h)
        assertEquals(TipoDatoPersonal.POSIBLE_NOMBRE, h!!.tipo)
        assertEquals(false, h.tipo.duro)
        // Apellido y nombre en líneas separadas
        assertEquals(TipoDatoPersonal.POSIBLE_NOMBRE, detectar("Unidad:900123G", "PEREZ", "MARTINA")!!.tipo)
    }

    @Test
    fun `el recorte del texto traslada las cajas y deja afuera lo de otra etiqueta`() {
        val p = pagina(1200, 1600) {
            linea("Unidad:900123G", 300, 700, altoLetra = 40)
            linea("PEREZ LOPEZ", 900, 700, altoLetra = 40) // otra etiqueta, a la derecha
        }
        val zona = Caja(250, 650, 700, 800)
        val r = p.recortada(zona)
        assertEquals(zona.ancho, r.ancho)
        assertEquals(1, r.lineas.size)
        assertEquals(50, r.lineas.single().caja!!.izq)
        assertNull(detectar(*r.lineas.map { it.texto }.toTypedArray()))
    }
}
