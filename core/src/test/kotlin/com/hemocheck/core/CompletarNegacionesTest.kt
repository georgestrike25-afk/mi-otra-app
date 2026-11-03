package com.hemocheck.core

import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.analisis.CompletarNegaciones
import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.identificador.ConfiguracionIdentificadores
import com.hemocheck.core.imagen.ImagenGris
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El OCR no entregó el «NO» de «NO REACTIVO»; la imagen (sintética) sí tiene tinta a la izquierda. */
class CompletarNegacionesTest {
    private val analizador = AnalizadorHoja(ConfiguracionIdentificadores().formatos())

    /** Papel claro con trazos oscuros: «REACTIVO» en su caja y, opcionalmente, un «NO» y una línea del cuadro. */
    private fun imagen(p: PaginaOcr, palabra: Caja, conNo: Boolean, conLinea: Boolean): ImagenGris {
        val w = p.ancho; val h = p.alto
        val px = ByteArray(w * h) { 225.toByte() }
        fun tinta(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = 40
        }
        // «REACTIVO»: trazos verticales repartidos en su caja
        var x = palabra.izq
        while (x < palabra.der - 3) { tinta(x, palabra.arriba + 2, x + 3, palabra.abajo - 2); x += 8 }
        if (conNo) {
            // «NO»: cuatro trazos (dos montantes de la N y los lados de la O) a la izquierda
            val base = palabra.izq - (0.30f * palabra.ancho).toInt()
            for (dx in listOf(0, 5, 11, 17)) tinta(base + dx, palabra.arriba + 2, base + dx + 3, palabra.abajo - 2)
        }
        if (conLinea) {
            // Línea del cuadro: sigue por encima y por debajo de la fila
            val lx = palabra.izq - (0.30f * palabra.ancho).toInt()
            tinta(lx, palabra.arriba - 40, lx + 3, palabra.abajo + 40)
        }
        return ImagenGris(w, h, px)
    }

    private fun pagina1(): PaginaOcr = pagina(2000, 1000) {
        linea("Referencia", 60, 200); linea("Chagas EIA", 900, 200)
        linea("900155", 60, 400); linea("REACTIVO", 900, 400, altoLetra = 22) // sin el «NO»
    }

    private fun caja(p: PaginaOcr) = p.lineas.first { it.texto == "REACTIVO" }.caja!!

    @Test
    fun `con tinta de texto a la izquierda se completa el NO`() {
        val p = pagina1()
        val completa = CompletarNegaciones.completar(p, imagen(p, caja(p), conNo = true, conLinea = false))
        assertTrue(completa.lineas.any { it.texto == "NO" })
        val e = analizador.analizar(completa, TipoRegistro.CONVENCIONAL).entradas.single { it.ocurrencia.normalizado == "900155" }
        assertTrue(e.resultados.toString(), e.resultados.all { it.categoria == CategoriaResultado.NO_REACTIVO })
    }

    @Test
    fun `un REACTIVO real sin nada a la izquierda no se toca`() {
        val p = pagina1()
        assertEquals(p, CompletarNegaciones.completar(p, imagen(p, caja(p), conNo = false, conLinea = false)))
    }

    @Test
    fun `la linea del cuadro a la izquierda no cuenta como texto`() {
        val p = pagina1()
        assertEquals(p, CompletarNegaciones.completar(p, imagen(p, caja(p), conNo = false, conLinea = true)))
    }

    @Test
    fun `si el OCR ya leyo el NO no se agrega otro`() {
        val p = pagina(2000, 1000) {
            linea("Referencia", 60, 200); linea("Chagas EIA", 900, 200)
            linea("900155", 60, 400); linea("NO", 900, 400, altoLetra = 22); linea("REACTIVO", 930, 400, altoLetra = 22)
        }
        val g = imagen(p, caja(p), conNo = true, conLinea = false)
        assertEquals(p, CompletarNegaciones.completar(p, g))
    }
}
