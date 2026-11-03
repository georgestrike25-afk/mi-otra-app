package com.hemocheck.core.analisis

import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.imagen.ImagenGris
import com.hemocheck.core.ocr.LineaOcr
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.ocr.PalabraOcr
import com.hemocheck.core.ocr.TextoLinea
import kotlin.math.abs
import kotlin.math.max

/**
 * A veces el OCR no entrega el «NO» de «NO REACTIVO» (queda pegado a la línea del cuadro o con
 * poco contraste) y solo devuelve «REACTIVO»: un falso reactivo. Si junto a un «REACTIVO» suelto
 * no hay ninguna palabra a la izquierda pero en la imagen sí hay tinta con forma de texto (no una
 * línea del cuadro), se agrega la palabra «NO» faltante para que se lea «NO REACTIVO».
 * Un «REACTIVO» real, sin nada escrito a su izquierda, no se toca.
 */
object CompletarNegaciones {
    private val REACTIVO = Regex("reactiv[oa]s?")

    /** «NO» suelto, también mal leído por estar pegado a la línea del cuadro («INO», «NC», «N», «MO»...). */
    internal val NEGACION = Regex("[il1!|\\[(]{0,2}[nmh][o0cqduv]?[|.)\\]]{0,2}")

    private fun mismaFila(a: Caja, b: Caja) = abs(a.centroY - b.centroY) <= 0.6f * max(a.alto, b.alto)

    /** [gris] es la imagen en la que se hizo el OCR (puede estar reducida: se calcula la escala). */
    fun completar(pagina: PaginaOcr, gris: ImagenGris): PaginaOcr {
        val palabras = TextoLinea.de(pagina).flatMap { it.palabrasFuera(emptyList()) }.filter { it.caja != null }
        val escala = gris.ancho.toFloat() / pagina.ancho.coerceAtLeast(1)
        val nuevas = mutableListOf<LineaOcr>()
        for (w in palabras) {
            val t = TextoLinea.normalizarConservandoLongitud(w.texto).trim().trimEnd('.')
            if (!REACTIVO.matches(t)) continue
            val c = w.caja!!
            val aLaIzquierda = palabras.any { o ->
                val oc = o.caja!!
                o !== w && mismaFila(oc, c) && oc.der <= c.izq + c.alto / 2 && c.izq - oc.der <= c.alto * 2.5f
            }
            if (aLaIzquierda || !hayTextoAIzquierda(gris, c, escala)) continue
            val caja = Caja((c.izq - 0.45f * c.ancho).toInt(), c.arriba, (c.izq - 0.06f * c.ancho).toInt(), c.abajo)
            nuevas += LineaOcr("NO", caja, 0.4f, 0f, listOf(PalabraOcr("NO", caja, 0.4f)))
        }
        return if (nuevas.isEmpty()) pagina else pagina.copy(lineas = pagina.lineas + nuevas)
    }

    /**
     * Hay trazos de letras justo a la izquierda de la palabra [c]: columnas con tinta dentro de la
     * franja de la fila que no sean una línea del cuadro (estas siguen por encima y por debajo de la fila).
     */
    internal fun hayTextoAIzquierda(g: ImagenGris, c: Caja, e: Float): Boolean {
        fun px(v: Number) = (v.toFloat() * e).toInt()
        val x0 = px(c.izq - 0.45f * c.ancho)
        val x1 = px(c.izq - 0.04f * c.ancho)
        val y0 = px(c.arriba + 0.12f * c.alto)
        val y1 = px(c.abajo - 0.12f * c.alto)
        val extra = px(0.5f * c.alto).coerceAtLeast(2)
        if (x0 < 0 || x1 > g.ancho || x1 - x0 < 6 || y0 - extra < 0 || y1 + extra >= g.alto || y1 - y0 < 4) return false

        // Tono del papel y de la tinta, medidos en la propia palabra «REACTIVO»
        val muestra = ArrayList<Int>()
        for (y in px(c.arriba) until px(c.abajo)) for (x in px(c.izq) until px(c.der)) {
            if (x in 0 until g.ancho && y in 0 until g.alto) muestra += g.valor(x, y)
        }
        if (muestra.size < 20) return false
        muestra.sort()
        val tinta = muestra[muestra.size * 5 / 100]
        val papel = muestra[muestra.size * 85 / 100]
        if (papel - tinta < 40) return false
        val umbral = papel - 0.45f * (papel - tinta)

        fun tintaEn(x: Int, desde: Int, hasta: Int): Int {
            var n = 0
            for (y in desde..hasta) if (g.valor(x, y) < umbral) n++
            return n
        }
        var columnas = 0
        for (x in x0 until x1) {
            if (tintaEn(x, y0, y1) == 0) continue
            // Una línea del cuadro sigue por encima y por debajo de la fila; las letras no
            val arriba = tintaEn(x, y0 - extra, y0 - 1)
            val abajo = tintaEn(x, y1 + 1, y1 + extra)
            if (arriba >= extra / 2 + 1 && abajo >= extra / 2 + 1) continue
            columnas++
        }
        // «NO» ocupa cerca de la cuarta parte del ancho de «NO REACTIVO»; se piden columnas con tinta en ~9 % del ancho de la palabra
        return columnas >= 0.09f * c.ancho * e
    }
}
