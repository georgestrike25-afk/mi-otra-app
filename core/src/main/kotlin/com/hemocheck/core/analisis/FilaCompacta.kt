package com.hemocheck.core.analisis

import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.ocr.TextoLinea
import kotlin.math.abs
import kotlin.math.max

/** Texto de la columna «Estado» de la planilla para una fila (p. ej. «Apto» o «No Apto»). */
data class EstadoPlanilla(val texto: String, val noApto: Boolean, val caja: Caja?)

/**
 * Ubica, a partir del OCR de la página, el estado documentado de una fila y la zona compacta
 * para el collage: el número de la unidad, su estado y una fila vecina arriba y abajo.
 * El estado se transcribe tal como figura; HemoCheck no lo interpreta como decisión propia.
 */
object FilaCompacta {
    private val REGEX_ESTADO = Regex("(?<![\\p{L}])(no\\s*apto|apto)(?![\\p{L}])")

    private fun mismaFila(a: Caja, b: Caja) = abs(a.centroY - b.centroY) <= 0.6f * max(a.alto, b.alto)

    /** Estado escrito a la derecha del número, en la misma fila. Null si no hay. */
    fun estado(pagina: PaginaOcr, cajaId: Caja): EstadoPlanilla? =
        TextoLinea.de(pagina).flatMap { l -> l.buscarNormalizado(REGEX_ESTADO) }
            .filter { f -> f.caja?.let { mismaFila(it, cajaId) && it.izq >= cajaId.der - cajaId.ancho / 4 && it.izq - cajaId.der <= cajaId.ancho * 6 } == true }
            .minByOrNull { it.caja!!.izq - cajaId.der }
            ?.let { f -> EstadoPlanilla(f.texto.trim(), f.texto.trim().lowercase().startsWith("no"), f.caja) }

    /**
     * Zona compacta del collage: desde el número hasta su estado (o unas columnas a la derecha),
     * y en altura desde la fila de arriba hasta la de abajo (las líneas alineadas con el número).
     */
    fun zona(pagina: PaginaOcr, cajaId: Caja, estado: EstadoPlanilla?): Caja {
        val h = cajaId.alto
        val enColumna = pagina.lineas.mapNotNull { it.caja }
            .filter { c -> c.superposicionHorizontal(cajaId) > cajaId.ancho / 2 && !mismaFila(c, cajaId) }
        val arriba = enColumna.filter { it.centroY < cajaId.centroY }.maxByOrNull { it.centroY }
            ?.takeIf { cajaId.centroY - it.centroY <= h * 4 }
        val abajo = enColumna.filter { it.centroY > cajaId.centroY }.minByOrNull { it.centroY }
            ?.takeIf { it.centroY - cajaId.centroY <= h * 4 }
        val margenX = cajaId.ancho / 4
        val derecha = estado?.caja?.der ?: (cajaId.der + cajaId.ancho * 3)
        return Caja(
            cajaId.izq - margenX,
            (arriba?.arriba ?: (cajaId.arriba - (h * 1.8f).toInt())) - h / 3,
            derecha + margenX,
            (abajo?.abajo ?: (cajaId.abajo + (h * 1.8f).toInt())) + h / 3,
        )
    }

    /** Zona de la fila marcada (número y estado), para dibujar el recuadro. */
    fun marca(cajaId: Caja, estado: EstadoPlanilla?): Caja {
        val pad = cajaId.alto / 4
        return Caja.unionDe(listOf(cajaId, estado?.caja))!!.expandir(pad, pad)
    }

    /** Zona mínima del número de la etiqueta con su código de barras debajo. */
    fun zonaEtiqueta(cajaId: Caja): Caja =
        Caja(
            cajaId.izq - cajaId.ancho / 2, cajaId.arriba - cajaId.alto * 2,
            cajaId.der + cajaId.ancho / 4, cajaId.abajo + cajaId.alto * 3,
        )

    /**
     * Zona de la etiqueta pegada en la bolsa: el texto agrupado alrededor del número (líneas
     * cercanas entre sí), sin alejarse más que el tamaño típico de una etiqueta. Así se ve la
     * etiqueta completa pero no toda la bolsa.
     */
    fun zonaEtiqueta(pagina: PaginaOcr?, cajaId: Caja): Caja {
        val minima = zonaEtiqueta(cajaId)
        if (pagina == null) return minima
        val h = cajaId.alto.coerceAtLeast(1)
        val limite = Caja(
            cajaId.izq - cajaId.ancho * 2, cajaId.arriba - h * 10,
            cajaId.der + cajaId.ancho * 2, cajaId.abajo + h * 12,
        )
        val dentro = pagina.lineas.mapNotNull { it.caja }.filter { it.esValida && contiene(limite, it) }
        var grupo = minima
        var pendientes = dentro
        val salto = h * 2
        while (true) {
            val cercanas = pendientes.filter { distancia(grupo, it) <= salto }
            if (cercanas.isEmpty()) break
            grupo = Caja.unionDe(cercanas + grupo)!!
            pendientes = pendientes - cercanas.toSet()
        }
        return grupo.expandir(h / 2, h / 2)
    }

    /** Ubica el número en el OCR de la etiqueta si no se guardó su posición (p. ej. lectura por código de barras). */
    fun buscarEnEtiqueta(pagina: PaginaOcr, id: String): Caja? =
        pagina.lineas.filter { l -> l.caja != null && l.texto.filter { it.isLetterOrDigit() }.uppercase().contains(id.uppercase()) }
            .maxByOrNull { it.caja!!.alto }?.caja

    private fun contiene(a: Caja, b: Caja) = b.izq >= a.izq && b.der <= a.der && b.arriba >= a.arriba && b.abajo <= a.abajo

    private fun distancia(a: Caja, b: Caja): Int {
        val dx = max(0, max(a.izq - b.der, b.izq - a.der))
        val dy = max(0, max(a.arriba - b.abajo, b.arriba - a.abajo))
        return max(dx, dy)
    }
}
