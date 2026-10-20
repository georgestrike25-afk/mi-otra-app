package com.hemocheck.core

import com.hemocheck.core.corroboracion.ConcordanciaBarras
import com.hemocheck.core.corroboracion.EstadoCorroboracion as E
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.core.corroboracion.MotorCorroboracion
import com.hemocheck.core.corroboracion.RegistroAlmacenado
import com.hemocheck.core.corroboracion.ResultadoAlmacenado
import com.hemocheck.core.corroboracion.SolicitudCorroboracion
import com.hemocheck.core.corroboracion.TipoAviso
import com.hemocheck.core.etiqueta.AnalizadorEtiqueta
import com.hemocheck.core.etiqueta.AvisoEtiqueta
import com.hemocheck.core.identificador.ConfiguracionIdentificadores
import com.hemocheck.core.identificador.Mod37_2
import com.hemocheck.core.resultado.CategoriaResultado as C
import com.hemocheck.core.resultado.TipoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorroboracionTest {
    private val motor = MotorCorroboracion()
    private val id = "X999926000123" // ficticio

    private val todasNr = listOf("HIV", "HBSAG", "ANTI_HBC", "HCV", "CHAGAS", "SIFILIS", "HTLV", "BRUCELOSIS")
        .map { ResultadoAlmacenado(it, "NR", C.NO_REACTIVO) }
    private val natNr = listOf("HIV", "HCV", "HBV").map { ResultadoAlmacenado(it, "No detectado", C.NO_REACTIVO) }

    private fun reg(
        n: Long, tipo: TipoRegistro, res: List<ResultadoAlmacenado>, idReg: String = id,
        estado: EstadoEntrada = EstadoEntrada.CONFIRMADA, ubicacion: Boolean = true, imagen: Boolean = true,
    ) = RegistroAlmacenado(n, tipo, idReg, estado, res, ubicacion, imagen)

    private fun corroborar(vararg r: RegistroAlmacenado, sol: SolicitudCorroboracion = SolicitudCorroboracion(id)) =
        motor.corroborar(sol, r.toList())

    @Test
    fun `coincidencia exacta en ambos registros`() {
        val inf = corroborar(reg(1, TipoRegistro.CONVENCIONAL, todasNr), reg(2, TipoRegistro.MOLECULAR, natNr))
        assertTrue(E.COINCIDENCIA_EXACTA in inf.estados)
        assertTrue(E.NO_REACTIVO_DOCUMENTADO in inf.estados)
        assertFalse(E.REGISTRO_NO_ENCONTRADO in inf.estados)
        assertFalse(E.REGISTRO_INCOMPLETO in inf.estados)
        assertTrue(inf.secciones.all { E.COINCIDENCIA_EXACTA in it.estados })
    }

    @Test
    fun `identificadores diferentes no coinciden aunque sean parecidos`() {
        val otro = reg(1, TipoRegistro.CONVENCIONAL, todasNr, idReg = "X999926000124")
        val inf = corroborar(otro)
        assertFalse(E.COINCIDENCIA_EXACTA in inf.estados)
        assertTrue(E.REGISTRO_NO_ENCONTRADO in inf.estados)
    }

    @Test
    fun `registro similar por OCR se avisa pero no se usa`() {
        val similar = reg(9, TipoRegistro.CONVENCIONAL, todasNr, idReg = "X99992600O123")
        val inf = motor.corroborar(SolicitudCorroboracion(id), emptyList(), listOf(similar))
        assertTrue(E.ERROR_OCR in inf.estados)
        assertTrue(inf.avisos.any { it.tipo == TipoAviso.ID_SIMILAR && it.detalle == "X99992600O123" })
        assertTrue(inf.secciones.all { it.confirmadas.isEmpty() })
    }

    @Test
    fun `falta el registro convencional`() {
        val inf = corroborar(reg(2, TipoRegistro.MOLECULAR, natNr))
        val conv = inf.secciones.single { it.tipo == TipoRegistro.CONVENCIONAL }
        assertEquals(listOf(E.REGISTRO_NO_ENCONTRADO), conv.estados)
        val mol = inf.secciones.single { it.tipo == TipoRegistro.MOLECULAR }
        assertTrue(E.COINCIDENCIA_EXACTA in mol.estados)
    }

    @Test
    fun `falta el registro molecular`() {
        val inf = corroborar(reg(1, TipoRegistro.CONVENCIONAL, todasNr))
        assertEquals(listOf(E.REGISTRO_NO_ENCONTRADO), inf.secciones.single { it.tipo == TipoRegistro.MOLECULAR }.estados)
    }

    @Test
    fun `duplicados con resultados contradictorios`() {
        val a = reg(1, TipoRegistro.CONVENCIONAL, todasNr)
        val b = reg(3, TipoRegistro.CONVENCIONAL, todasNr.map { if (it.clave == "HCV") it.copy(textoCrudo = "R", categoria = C.REACTIVO) else it })
        val inf = corroborar(a, b, reg(2, TipoRegistro.MOLECULAR, natNr))
        assertTrue(E.DUPLICADO_AMBIGUO in inf.estados)
        assertTrue(E.RESULTADOS_CONTRADICTORIOS in inf.estados)
        assertTrue(E.REACTIVO_DOCUMENTADO in inf.estados)
        val hcv = inf.determinaciones.single { it.tipoRegistro == TipoRegistro.CONVENCIONAL && it.clave == "HCV" }
        assertTrue(hcv.contradictoria)
        assertEquals(listOf("NR", "R"), hcv.textos)
    }

    @Test
    fun `determinacion ausente es registro incompleto y no se interpreta como negativa`() {
        val sinChagas = todasNr.filter { it.clave != "CHAGAS" }
        val inf = corroborar(reg(1, TipoRegistro.CONVENCIONAL, sinChagas), reg(2, TipoRegistro.MOLECULAR, natNr))
        assertTrue(E.REGISTRO_INCOMPLETO in inf.estados)
        val chagas = inf.determinaciones.single { it.clave == "CHAGAS" }
        assertTrue(chagas.ausente)
        assertTrue(chagas.categorias.isEmpty())
    }

    @Test
    fun `multiplex NAT cubre HIV HCV y HBV`() {
        val inf = corroborar(
            reg(1, TipoRegistro.CONVENCIONAL, todasNr),
            reg(2, TipoRegistro.MOLECULAR, listOf(ResultadoAlmacenado("NAT_MULTIPLEX", "No reactivo", C.NO_REACTIVO))),
        )
        assertFalse(E.REGISTRO_INCOMPLETO in inf.estados)
    }

    @Test
    fun `solo pendiente de revision no cuenta como coincidencia`() {
        val inf = corroborar(reg(1, TipoRegistro.CONVENCIONAL, todasNr, estado = EstadoEntrada.PENDIENTE_REVISION))
        val conv = inf.secciones.single { it.tipo == TipoRegistro.CONVENCIONAL }
        assertTrue(E.PENDIENTE_REVISION in conv.estados)
        assertTrue(E.REGISTRO_NO_ENCONTRADO in conv.estados)
        assertFalse(E.COINCIDENCIA_EXACTA in conv.estados)
    }

    @Test
    fun `indeterminado y pendiente documentados`() {
        val res = todasNr.map {
            when (it.clave) {
                "CHAGAS" -> it.copy(textoCrudo = "Indeterminado", categoria = C.INDETERMINADO)
                "HTLV" -> it.copy(textoCrudo = "Pendiente", categoria = C.PENDIENTE)
                else -> it
            }
        }
        val inf = corroborar(reg(1, TipoRegistro.CONVENCIONAL, res))
        assertTrue(E.INDETERMINADO_DOCUMENTADO in inf.estados)
        assertTrue(E.PENDIENTE_DOCUMENTADO in inf.estados)
    }

    @Test
    fun `sin recorte o imagen es no verificable`() {
        val inf = corroborar(reg(1, TipoRegistro.CONVENCIONAL, todasNr, ubicacion = false), reg(2, TipoRegistro.MOLECULAR, natNr, imagen = false))
        assertTrue(inf.avisos.any { it.tipo == TipoAviso.SIN_UBICACION })
        assertTrue(inf.avisos.any { it.tipo == TipoAviso.SIN_IMAGEN })
        assertTrue(E.NO_VERIFICABLE in inf.estados)
    }

    @Test
    fun `etiqueta ilegible`() {
        val inf = motor.corroborar(SolicitudCorroboracion(null, errorOcrEtiqueta = true), emptyList())
        assertTrue(E.IDENTIFICADOR_ILEGIBLE in inf.estados)
        assertTrue(E.ERROR_OCR in inf.estados)
        assertFalse(E.COINCIDENCIA_EXACTA in inf.estados)
    }

    @Test
    fun `codigo de barras discordante y correccion manual se informan`() {
        val inf = corroborar(
            reg(1, TipoRegistro.CONVENCIONAL, todasNr),
            sol = SolicitudCorroboracion(id, idCorregidoManualmente = true, concordanciaBarras = ConcordanciaBarras.DISCORDANTE),
        )
        assertTrue(inf.avisos.any { it.tipo == TipoAviso.BARRAS_DISCORDANTE })
        assertTrue(inf.avisos.any { it.tipo == TipoAviso.ID_CORREGIDO })
    }

    @Test
    fun `el motor descarta registros con otro identificador aunque se los pasen`() {
        val inf = corroborar(reg(1, TipoRegistro.CONVENCIONAL, todasNr, idReg = "OTRO123456"))
        assertTrue(inf.secciones.all { it.confirmadas.isEmpty() })
    }

    // ---- Etiqueta ----

    private val etiqueta = AnalizadorEtiqueta(ConfiguracionIdentificadores().formatos())

    @Test
    fun `etiqueta ISBT con control y codigo de barras concordante`() {
        val c = Mod37_2.calcular(id)
        val p = pagina(1200, 800) {
            linea("BANCO FICTICIO DE PRUEBA", 50, 50)
            linea("X9999 26 000123 00 $c", 50, 200, altoLetra = 60)
            linea("Vence 15/11/2026 Lote 4455667", 50, 500)
        }
        val l = etiqueta.analizar(p, listOf("=X99992600012300"))
        assertEquals(id, l.propuesto)
        assertTrue(AvisoEtiqueta.BARRAS_CONCORDANTE in l.avisos)
        assertFalse(l.requiereAtencion)
    }

    @Test
    fun `etiqueta con dos DIN distintos no propone ninguno`() {
        val p = pagina(1200, 800) {
            linea("X9999 26 000123", 50, 200)
            linea("X9999 26 000999", 50, 400)
        }
        val l = etiqueta.analizar(p)
        assertNull(l.propuesto)
        assertTrue(AvisoEtiqueta.VARIOS_IDENTIFICADORES in l.avisos)
    }

    @Test
    fun `etiqueta con caracteres dudosos sugiere pero no propone`() {
        val l = etiqueta.analizar(pagina(1200, 800) { linea("X9999 26 OOO123", 50, 200) })
        assertNull(l.propuesto)
        assertTrue(AvisoEtiqueta.CARACTERES_DUDOSOS in l.avisos)
        assertEquals("X999926000123", l.candidatos.single().sugerido)
    }

    @Test
    fun `codigo de barras discordante`() {
        val l = etiqueta.analizar(pagina(1200, 800) { linea("X9999 26 000123", 50, 200) }, listOf("=X99992600012400"))
        assertTrue(AvisoEtiqueta.BARRAS_DISCORDANTE in l.avisos)
        assertTrue(l.requiereAtencion)
    }

    @Test
    fun `etiqueta sin texto`() {
        val l = etiqueta.analizar(pagina { })
        assertTrue(AvisoEtiqueta.SIN_IDENTIFICADOR in l.avisos)
        assertNull(l.propuesto)
    }
}
