package com.hemocheck.core

import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.analisis.Disposicion
import com.hemocheck.core.analisis.Problema
import com.hemocheck.core.identificador.ConfiguracionIdentificadores
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Planillas con el formato del servicio (columnas «Referencia», «Estado» y determinaciones,
 * filas de metodología y lotes bajo el encabezado). Todos los números son ficticios.
 */
class PlanillaHospitalTest {
    private val analizador = AnalizadorHoja(ConfiguracionIdentificadores().formatos())

    private val convencional = listOf(
        "HIV Ag/Ac", "Anti-HTLV I+II", "Anti-HVC", "AgHBs", "Anti-HBc",
        "Chagas EIA", "Chagas CMIA", "SIFILIS", "Brucelosis",
    )
    private fun x(i: Int) = 560 + i * 270

    private fun planilla(columnas: List<String>, filas: List<Triple<String, String, Map<Int, String>>>) = pagina(3200, 2300) {
        linea("SEROLOGIA Fecha: 20/10/2026", 2400, 60)
        linea("Referencia", 60, 200, altoLetra = 24)
        linea("Estado", 300, 200, altoLetra = 24)
        columnas.forEachIndexed { i, c -> linea(c, x(i), 200, altoLetra = 22) }
        // Filas de metodología bajo el encabezado (marca, lote y vencimiento)
        columnas.indices.forEach { i ->
            linea("CMIA", x(i), 250, altoLetra = 22)
            linea("ABBOTT", x(i), 290, altoLetra = 22)
            linea(if (i == 5) "777001" else "8${i}523BE00", x(i), 330, altoLetra = 22) // un lote de 6 dígitos
            linea("17/12/2027", x(i), 370, altoLetra = 22)
        }
        filas.forEachIndexed { f, (id, estado, especiales) ->
            val y = 440 + f * 46
            linea(id, 60, y, altoLetra = 24)
            linea(estado, 300, y, altoLetra = 22)
            columnas.indices.forEach { i -> linea(especiales[i] ?: "NO REACTIVO", x(i), y, altoLetra = 22) }
        }
    }

    @Test
    fun `planilla convencional del servicio`() {
        val filas = (1..20).map { n ->
            val id = "9001%02d".format(n)
            when (n) {
                6 -> Triple(id, "No Apto", mapOf(1 to "REACTIVO")) // Anti-HTLV reactivo
                12 -> Triple(id, "No Apto", mapOf(2 to "REACTIVO")) // Anti-HVC reactivo
                else -> Triple(id, "Apto", emptyMap())
            }
        }
        val hoja = analizador.analizar(planilla(convencional, filas), TipoRegistro.CONVENCIONAL)
        assertEquals(Disposicion.TABLA, hoja.disposicion)
        // El lote de 6 dígitos no se toma como unidad
        assertFalse(hoja.entradas.any { it.ocurrencia.normalizado == "777001" })
        assertEquals(20, hoja.entradas.size)
        hoja.entradas.forEach { e ->
            assertTrue("${e.ocurrencia.normalizado}: ${e.problemas}", e.listaParaConfirmar)
            assertEquals(9, e.resultados.size)
        }
        val n6 = hoja.entradas.single { it.ocurrencia.normalizado == "900106" }
        assertEquals(CategoriaResultado.REACTIVO, n6.resultados.single { it.clave == "HTLV" }.categoria)
        val n12 = hoja.entradas.single { it.ocurrencia.normalizado == "900112" }
        assertEquals(CategoriaResultado.REACTIVO, n12.resultados.single { it.clave == "HCV" }.categoria)
        // Las dos columnas de Chagas se conservan por separado
        assertEquals(listOf("Chagas EIA", "Chagas CMIA"), n12.resultados.filter { it.clave == "CHAGAS" }.map { it.textoEncabezado })
        assertEquals("2026-10-20", hoja.fechaPagina)
    }

    @Test
    fun `planilla molecular del servicio`() {
        val filas = (1..15).map { n -> Triple("9001%02d".format(n), "Apto", emptyMap<Int, String>()) }
        val hoja = analizador.analizar(planilla(listOf("HIV NAT", "HBV NAT", "HCV NAT"), filas), TipoRegistro.MOLECULAR)
        assertEquals(15, hoja.entradas.size)
        hoja.entradas.forEach { e ->
            assertEquals(setOf("HIV", "HBV", "HCV"), e.resultados.map { it.clave }.toSet())
            assertTrue(e.resultados.all { it.categoria == CategoriaResultado.NO_REACTIVO })
        }
    }

    @Test
    fun `NO separado de REACTIVO por el OCR se lee como NO REACTIVO`() {
        val p = pagina(2000, 1000) {
            linea("Referencia", 60, 200); linea("HIV Ag/Ac", 560, 200); linea("AgHBs", 830, 200)
            linea("900148", 60, 300)
            linea("NO", 560, 300); linea("REACTIVO", 560 + 3 * 18, 300) // dos líneas distintas
            linea("N0 REACTIVO", 830, 300) // cero en lugar de O
            linea("900149", 60, 360)
            linea("NO REACTIVO", 560, 360); linea("REACTIVO", 830, 360) // reactivo real
        }
        val hoja = analizador.analizar(p, TipoRegistro.CONVENCIONAL)
        val a = hoja.entradas.single { it.ocurrencia.normalizado == "900148" }
        assertTrue(a.resultados.toString(), a.resultados.all { it.categoria == CategoriaResultado.NO_REACTIVO })
        val b = hoja.entradas.single { it.ocurrencia.normalizado == "900149" }
        assertEquals(CategoriaResultado.REACTIVO, b.resultados.single { it.clave == "HBSAG" }.categoria)
    }

    @Test
    fun `NO pegado a la linea del cuadro se lee como NO REACTIVO`() {
        val p = pagina(2400, 1000) {
            linea("Referencia", 60, 200); linea("HIV NAT", 560, 200); linea("HBV NAT", 900, 200); linea("HCV NAT", 1240, 200)
            linea("900150", 60, 300)
            linea("INO REACTIVO", 560, 300); linea("NC REACTIVO", 900, 300); linea("|NO REACTIVO", 1240, 300)
            linea("900151", 60, 360)
            linea("N", 560, 360); linea("REACTIVO", 560 + 2 * 18, 360); linea("MO REACTIVO", 900, 360); linea("NO REACTIVO", 1240, 360)
            linea("900152", 60, 420)
            linea("NO REACTIVO", 560, 420); linea("REACTIVO", 900, 420); linea("NO REACTIVO", 1240, 420) // reactivo real
        }
        val hoja = analizador.analizar(p, TipoRegistro.MOLECULAR)
        for (id in listOf("900150", "900151")) {
            val e = hoja.entradas.single { it.ocurrencia.normalizado == id }
            assertTrue("$id: ${e.resultados}", e.resultados.isNotEmpty() && e.resultados.all { it.categoria == CategoriaResultado.NO_REACTIVO })
        }
        val real = hoja.entradas.single { it.ocurrencia.normalizado == "900152" }
        assertTrue(real.resultados.toString(), real.resultados.any { it.categoria == CategoriaResultado.REACTIVO })
    }

    @Test
    fun `numero leido fuera de orden se corrige por sus vecinos`() {
        // La columna va en orden; el OCR confundió un 9 con un 8 (900190 leído como 900180)
        val ids = listOf("900185", "900187", "900189", "900180", "900192", "900195")
        val hoja = analizador.analizar(planilla(convencional, ids.map { Triple(it, "Apto", emptyMap<Int, String>()) }), TipoRegistro.CONVENCIONAL)
        val leidos = hoja.entradas.map { it.ocurrencia.normalizado }
        assertFalse("900180" in leidos)
        val corregida = hoja.entradas.single { it.ocurrencia.normalizado == "900190" }
        assertTrue(Problema.ID_CORREGIDO_POR_ORDEN in corregida.problemas)
    }

    @Test
    fun `numero fuera de orden sin correccion unica se marca`() {
        val ids = listOf("900185", "900187", "900189", "900023", "900199", "900201")
        val hoja = analizador.analizar(planilla(convencional, ids.map { Triple(it, "Apto", emptyMap<Int, String>()) }), TipoRegistro.CONVENCIONAL)
        val dudosa = hoja.entradas.single { it.ocurrencia.normalizado == "900023" }
        assertTrue(Problema.ID_FUERA_DE_ORDEN in dudosa.problemas)
        assertEquals(1, hoja.entradas.count { Problema.ID_FUERA_DE_ORDEN in it.problemas })
    }
}
