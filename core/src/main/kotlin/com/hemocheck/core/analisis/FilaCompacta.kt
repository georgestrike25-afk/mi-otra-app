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

    /** Zona del número de la etiqueta con su código de barras debajo. */
    fun zonaEtiqueta(cajaId: Caja): Caja =
        Caja(
            cajaId.izq - cajaId.ancho / 2, cajaId.arriba - cajaId.alto * 2,
            cajaId.der + cajaId.ancho / 4, cajaId.abajo + cajaId.alto * 3,
        )
}
