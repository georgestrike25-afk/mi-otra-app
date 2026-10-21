package com.hemocheck.core

import com.hemocheck.core.corroboracion.EstadoCorroboracion
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.core.corroboracion.MotorCorroboracion
import com.hemocheck.core.corroboracion.RegistroAlmacenado
import com.hemocheck.core.corroboracion.ResultadoAlmacenado
import com.hemocheck.core.corroboracion.SolicitudCorroboracion
import com.hemocheck.core.corroboracion.TipoAviso
import com.hemocheck.core.etiqueta.AnalizadorEtiqueta
import com.hemocheck.core.etiqueta.AvisoEtiqueta
import com.hemocheck.core.identificador.ConfiguracionIdentificadores
import com.hemocheck.core.identificador.IdUnidad
import com.hemocheck.core.imagen.DetectorHoja
import com.hemocheck.core.imagen.ImagenGris
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etiquetas con número local de extracción y letra de componente (formato de una bolsa real,
 * pero con números, textos y datos totalmente ficticios).
 */
class EtiquetaLocalTest {
    private val formatos = ConfiguracionIdentificadores().formatos()
    private val etiqueta = AnalizadorEtiqueta(formatos)

    /** Bolsa ficticia: varias etiquetas con el número, fechas, lote y otros números. */
    private fun bolsa(componente: String = "G") = pagina(1100, 2000) {
        linea("Hospital Ficticio - F.Ext:6/10/2026", 200, 650)
        linea("Globulos Rojos Sedimentados", 200, 710)
        linea("Unidad:900123$componente", 200, 760, altoLetra = 44)
        linea("*900123$componente*", 300, 860)
        linea("Fecha de Extraccion", 650, 880)
        linea("16880705", 650, 910) // número escrito a mano, no es la unidad
        linea("A POS Ext:900123 - Hc:90012", 220, 1000)
        linea("SEROLOGIA NO REACTIVA", 220, 1070)
        linea("F. Venc: 17/11/2026", 220, 1180)
        linea("LABEL: 1000068330", 700, 1220)
        linea("Ext:900123$componente - Hc:90012", 200, 1350)
        linea("HC: 345443", 650, 1320) // historia clínica ficticia, no es la unidad
    }

    @Test
    fun `prioriza el numero que sigue a Unidad o Ext`() {
        val l = etiqueta.analizar(bolsa(), listOf("900123G"))
        assertEquals("900123", l.propuesto)
        assertEquals("G", l.componente)
        assertFalse(AvisoEtiqueta.VARIOS_IDENTIFICADORES in l.avisos)
        assertTrue(AvisoEtiqueta.BARRAS_CONCORDANTE in l.avisos)
    }

    @Test
    fun `plaquetas y plasma comparten el numero de la donacion`() {
        assertEquals("900123", etiqueta.analizar(bolsa("P")).propuesto)
        assertEquals("900123", etiqueta.analizar(bolsa("PF")).propuesto)
        assertEquals("900123", IdUnidad.canonizar("900123G", formatos))
        assertEquals("900123", IdUnidad.canonizar("*900123G*", formatos))
    }

    @Test
    fun `codigo de barras Code 39 con asteriscos`() {
        val l = etiqueta.analizar(pagina { }, listOf("*900123G*"))
        assertEquals("900123", l.idCodigoBarras)
        assertEquals("900123", l.propuesto)
        assertTrue(AvisoEtiqueta.SOLO_BARRAS in l.avisos)
    }

    @Test
    fun `codigo de barras distinto del texto se avisa`() {
        val l = etiqueta.analizar(bolsa(), listOf("*900124G*"))
        assertTrue(AvisoEtiqueta.BARRAS_DISCORDANTE in l.avisos)
    }

    @Test
    fun `sin palabra clave y con varios numeros no propone ninguno`() {
        val p = pagina(1100, 1000) {
            linea("900123", 100, 100)
            linea("345443", 100, 300)
        }
        val l = etiqueta.analizar(p)
        assertNull(l.propuesto)
        assertTrue(AvisoEtiqueta.VARIOS_IDENTIFICADORES in l.avisos)
    }

    @Test
    fun `lectura automatica cuenta como coincidencia sin aviso de pendiente`() {
        val reg = RegistroAlmacenado(1, TipoRegistro.CONVENCIONAL, "900123", EstadoEntrada.AUTOMATICA,
            listOf(ResultadoAlmacenado("HIV", "NR", CategoriaResultado.NO_REACTIVO)), true, true)
        val inf = MotorCorroboracion().corroborar(SolicitudCorroboracion("900123"), listOf(reg))
        val s = inf.secciones.single { it.tipo == TipoRegistro.CONVENCIONAL }
        assertTrue(EstadoCorroboracion.COINCIDENCIA_EXACTA in s.estados)
        assertFalse(EstadoCorroboracion.PENDIENTE_REVISION in inf.estados)
        assertFalse(inf.avisos.any { it.tipo == TipoAviso.LECTURA_AUTOMATICA })
    }

    private fun imagen(w: Int, h: Int, f: (Int, Int) -> Int) =
        ImagenGris(w, h, ByteArray(w * h) { i -> f(i % w, i / w).coerceIn(0, 255).toByte() })

    @Test
    fun `sin fondo contrastado no se corrige la perspectiva`() {
        // Hoja blanca sobre una mesa clara: poco contraste
        val img = imagen(800, 1000) { x, y -> if (x in 100 until 700 && y in 150 until 900) 225 else 195 }
        assertNull(DetectorHoja.detectar(img).esquinas)
    }

    @Test
    fun `forma que no es una hoja no se corrige`() {
        // Región clara en forma de triángulo grande
        val img = imagen(800, 1000) { x, y -> if (y in 100 until 900 && x in 100 until 100 + (y - 100) * 600 / 800) 230 else 30 }
        assertNull(DetectorHoja.detectar(img).esquinas)
    }
}
