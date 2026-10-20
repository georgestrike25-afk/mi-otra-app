package com.hemocheck.core

import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.analisis.AvisoPagina
import com.hemocheck.core.analisis.Disposicion
import com.hemocheck.core.analisis.Problema
import com.hemocheck.core.identificador.ConfiguracionIdentificadores
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.ClasificadorResultado
import com.hemocheck.core.resultado.TipoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hojas sintéticas con datos ficticios. */
class AnalizadorHojaTest {
    private val analizador = AnalizadorHoja(ConfiguracionIdentificadores().formatos())

    /** Columnas: identificador en x=100, determinaciones a partir de x=700 cada 160 px. */
    private val columnas = listOf("HIV", "HBsAg", "Anti-HBc", "HCV", "Chagas", "VDRL", "HTLV", "Brucelosis")
    private fun x(col: Int) = 700 + col * 160

    private fun tablaConvencional(filas: List<Pair<String, List<String>>>, yInicio: Int = 400, paso: Int = 80): PaginaOcr = pagina {
        linea("Serología convencional - Fecha: 15/10/2026", 100, 150)
        linea("Unidad", 100, 300)
        columnas.forEachIndexed { i, c -> linea(c, x(i), 300, altoLetra = 26) }
        filas.forEachIndexed { f, (id, res) ->
            val y = yInicio + f * paso
            linea(id, 100, y)
            res.forEachIndexed { i, r -> if (r.isNotEmpty()) linea(r, x(i), y, altoLetra = 26) }
        }
    }

    private val nr8 = List(8) { "NR" }

    @Test
    fun `tabla con varias unidades asocia cada fila a su identificador`() {
        val hoja = analizador.analizar(
            tablaConvencional(
                listOf(
                    "X9999 26 000101" to nr8,
                    "X9999 26 000102" to listOf("NR", "NR", "R", "NR", "NR", "NR", "NR", "NR"),
                    "X9999 26 000103" to nr8,
                )
            ),
            TipoRegistro.CONVENCIONAL,
        )
        assertEquals(Disposicion.TABLA, hoja.disposicion)
        assertEquals(3, hoja.entradas.size)
        val segunda = hoja.entradas.single { it.ocurrencia.normalizado == "X999926000102" }
        assertTrue(segunda.problemas.toString(), segunda.listaParaConfirmar)
        assertEquals(8, segunda.resultados.size)
        val hbc = segunda.resultados.single { it.clave == "ANTI_HBC" }
        assertEquals(CategoriaResultado.REACTIVO, hbc.categoria)
        assertTrue(segunda.resultados.filter { it.clave != "ANTI_HBC" }.all { it.categoria == CategoriaResultado.NO_REACTIVO })
        assertEquals("2026-10-15", hoja.fechaPagina)
    }

    @Test
    fun `resultado entre dos columnas queda sin asignar y la fila pendiente`() {
        val p = pagina {
            linea("Unidad", 100, 300)
            linea("HIV", 700, 300)
            linea("HCV", 900, 300)
            linea("X9999 26 000201", 100, 400)
            // Centro justo entre HIV (≈727) y HCV (≈927)
            linea("NR", 810, 400)
            linea("X9999 26 000202", 100, 480)
            linea("NR", 705, 480)
            linea("NR", 905, 480)
        }
        val hoja = analizador.analizar(p, TipoRegistro.CONVENCIONAL)
        val ambigua = hoja.entradas.single { it.ocurrencia.normalizado == "X999926000201" }
        assertTrue(Problema.RESULTADO_AMBIGUO_COLUMNAS in ambigua.problemas)
        assertFalse(ambigua.listaParaConfirmar)
        assertTrue(ambigua.resultados.isEmpty())
        val clara = hoja.entradas.single { it.ocurrencia.normalizado == "X999926000202" }
        assertTrue(clara.listaParaConfirmar)
        assertEquals(setOf("HIV", "HCV"), clara.resultados.map { it.clave }.toSet())
    }

    @Test
    fun `filas demasiado juntas no asocian resultados dudosos`() {
        val p = pagina {
            linea("Unidad", 100, 300)
            linea("HIV", 700, 300)
            linea("X9999 26 000301", 100, 400)
            linea("X9999 26 000302", 100, 430)
            // A mitad de camino entre las dos filas
            linea("R", 710, 415)
        }
        val hoja = analizador.analizar(p, TipoRegistro.CONVENCIONAL)
        hoja.entradas.forEach { e ->
            assertTrue(e.resultados.none { it.categoria == CategoriaResultado.REACTIVO })
            assertFalse(e.listaParaConfirmar)
        }
    }

    @Test
    fun `dos unidades en la misma fila requieren asociacion manual`() {
        val p = pagina {
            linea("HIV", 1300, 300)
            linea("X9999 26 000401", 100, 400)
            linea("X9999 26 000402", 700, 400)
            linea("NR", 1300, 400)
        }
        val hoja = analizador.analizar(p, TipoRegistro.CONVENCIONAL)
        assertEquals(2, hoja.entradas.size)
        hoja.entradas.forEach {
            assertTrue(Problema.VARIAS_UNIDADES_MISMA_FILA in it.problemas)
            assertTrue(it.resultados.isEmpty())
        }
    }

    @Test
    fun `hoja tipo formulario con una sola unidad`() {
        val p = pagina {
            linea("Serología molecular (NAT)", 100, 100)
            linea("Donación: X9999 26 000501", 100, 200)
            linea("HIV NAT", 100, 400); linea("No detectado", 900, 400)
            linea("HCV NAT", 100, 480); linea("No detectado", 900, 480)
            linea("HBV NAT", 100, 560); linea("Detectado", 900, 560)
            linea("Validado por: Bioq. Ficticio", 100, 800)
        }
        val hoja = analizador.analizar(p, TipoRegistro.MOLECULAR)
        assertEquals(Disposicion.FORMULARIO, hoja.disposicion)
        val e = hoja.entradas.single()
        assertEquals("X999926000501", e.ocurrencia.normalizado)
        val porClave = e.resultados.associate { it.clave to it.categoria }
        assertEquals(CategoriaResultado.NO_REACTIVO, porClave["HIV"])
        assertEquals(CategoriaResultado.NO_REACTIVO, porClave["HCV"])
        assertEquals(CategoriaResultado.REACTIVO, porClave["HBV"])
        assertTrue(e.listaParaConfirmar)
        assertTrue(hoja.validacionPagina.any { "Validado" in it })
    }

    @Test
    fun `formulario con dos identificadores no asocia automaticamente`() {
        val p = pagina {
            linea("Donación: X9999 26 000601", 100, 200)
            linea("Donación: X9999 26 000602", 100, 1500)
            linea("HIV", 100, 700); linea("Negativo", 900, 700)
        }
        val hoja = analizador.analizar(p, TipoRegistro.CONVENCIONAL)
        assertEquals(2, hoja.entradas.size)
        hoja.entradas.forEach { assertTrue(it.resultados.isEmpty()); assertFalse(it.listaParaConfirmar) }
        assertTrue(AvisoPagina.RESULTADOS_SIN_ASOCIAR in hoja.avisos)
        assertEquals(1, hoja.resultadosSinAsociar.size)
    }

    @Test
    fun `identificador repetido en dos filas se marca`() {
        val hoja = analizador.analizar(
            tablaConvencional(listOf("X9999 26 000701" to nr8, "X9999 26 000702" to nr8, "X9999 26 000701" to nr8)),
            TipoRegistro.CONVENCIONAL,
        )
        val repetidas = hoja.entradas.filter { it.ocurrencia.normalizado == "X999926000701" }
        assertEquals(2, repetidas.size)
        repetidas.forEach { assertTrue(Problema.ID_REPETIDO_EN_PAGINA in it.problemas) }
    }

    @Test
    fun `texto borroso con baja confianza pide revision`() {
        val p = pagina {
            linea("Unidad", 100, 300)
            linea("HIV", 700, 300)
            linea("X9999 26 000801", 100, 400, confianza = 0.42f)
            linea("NR", 705, 400, confianza = 0.5f)
        }
        val e = analizador.analizar(p, TipoRegistro.CONVENCIONAL).entradas.single()
        assertTrue(Problema.ID_BAJA_CONFIANZA in e.problemas)
        assertTrue(Problema.RESULTADO_BAJA_CONFIANZA in e.problemas)
        assertFalse(e.listaParaConfirmar)
    }

    @Test
    fun `identificador incompleto con caracter dudoso`() {
        val p = pagina {
            linea("HIV", 700, 300)
            linea("X9999 26 00O9O1", 100, 400)
            linea("NR", 705, 400)
        }
        val e = analizador.analizar(p, TipoRegistro.CONVENCIONAL).entradas.single()
        assertTrue(Problema.ID_CARACTERES_DUDOSOS in e.problemas)
        assertEquals("X999926000901", e.ocurrencia.sugerido)
    }

    @Test
    fun `valor numerico sin categoria queda como no interpretado`() {
        val p = pagina {
            linea("Unidad", 100, 300)
            linea("HIV", 700, 300); linea("HCV", 900, 300)
            linea("X9999 26 000901", 100, 400)
            linea("NR", 705, 400); linea("0,21", 905, 400)
        }
        val e = analizador.analizar(p, TipoRegistro.CONVENCIONAL).entradas.single()
        val hcv = e.resultados.single { it.clave == "HCV" }
        assertEquals(CategoriaResultado.NO_INTERPRETADO, hcv.categoria)
        assertEquals("0,21", hcv.textoCrudo)
    }

    @Test
    fun `pagina sin texto o sin identificadores`() {
        val vacia = analizador.analizar(pagina { }, TipoRegistro.CONVENCIONAL)
        assertTrue(AvisoPagina.SIN_TEXTO in vacia.avisos)
        val sinId = analizador.analizar(pagina { linea("HIV", 700, 300); linea("NR", 705, 400) }, TipoRegistro.CONVENCIONAL)
        assertTrue(AvisoPagina.SIN_IDENTIFICADORES in sinId.avisos)
        assertTrue(sinId.entradas.isEmpty())
    }

    @Test
    fun `fila sin resultados queda pendiente`() {
        val hoja = analizador.analizar(tablaConvencional(listOf("X9999 26 001001" to emptyList())), TipoRegistro.CONVENCIONAL)
        val e = hoja.entradas.single()
        assertTrue(Problema.SIN_RESULTADOS in e.problemas)
        assertFalse(e.listaParaConfirmar)
    }

    @Test
    fun `pagina inclinada usa el angulo de las lineas`() {
        // Inclinación de 3° en sentido horario: la y crece 0,0524 px por px hacia la derecha
        val t = 0.0524f
        val p = pagina {
            linea("Unidad", 100, 300 + (100 * t).toInt(), angulo = 3f)
            linea("HIV", 700, 300 + (700 * t).toInt(), angulo = 3f)
            linea("HCV", 1500, 300 + (1500 * t).toInt(), angulo = 3f)
            linea("X9999 26 001101", 100, 400 + (100 * t).toInt(), angulo = 3f)
            linea("NR", 705, 400 + (705 * t).toInt(), angulo = 3f)
            linea("R", 1505, 400 + (1505 * t).toInt(), angulo = 3f)
            linea("X9999 26 001102", 100, 450 + (100 * t).toInt(), angulo = 3f)
            linea("NR", 705, 450 + (705 * t).toInt(), angulo = 3f)
            linea("NR", 1505, 450 + (1505 * t).toInt(), angulo = 3f)
        }
        val hoja = analizador.analizar(p, TipoRegistro.CONVENCIONAL)
        val a = hoja.entradas.single { it.ocurrencia.normalizado == "X999926001101" }
        assertEquals(CategoriaResultado.REACTIVO, a.resultados.single { it.clave == "HCV" }.categoria)
        val b = hoja.entradas.single { it.ocurrencia.normalizado == "X999926001102" }
        assertEquals(CategoriaResultado.NO_REACTIVO, b.resultados.single { it.clave == "HCV" }.categoria)
    }

    @Test
    fun `clasificador distingue negaciones`() {
        assertEquals(CategoriaResultado.NO_REACTIVO, ClasificadorResultado.clasificar("No reactivo"))
        assertEquals(CategoriaResultado.NO_REACTIVO, ClasificadorResultado.clasificar("NO REACTIVA"))
        assertEquals(CategoriaResultado.NO_REACTIVO, ClasificadorResultado.clasificar("N/R"))
        assertEquals(CategoriaResultado.NO_REACTIVO, ClasificadorResultado.clasificar("no detectado"))
        assertEquals(CategoriaResultado.REACTIVO, ClasificadorResultado.clasificar("Reactivo"))
        assertEquals(CategoriaResultado.REACTIVO, ClasificadorResultado.clasificar("R"))
        assertEquals(CategoriaResultado.REACTIVO, ClasificadorResultado.clasificar("Detectado"))
        assertEquals(CategoriaResultado.INDETERMINADO, ClasificadorResultado.clasificar("Indeterminado"))
        assertEquals(CategoriaResultado.PENDIENTE, ClasificadorResultado.clasificar("Pendiente"))
        assertNull(ClasificadorResultado.clasificar("ilegible"))
        assertNull(ClasificadorResultado.clasificar(""))
    }
}
