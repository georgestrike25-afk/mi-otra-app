package com.hemocheck.core

import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.ocr.LineaOcr
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.ocr.PalabraOcr

/**
 * Generador de páginas de OCR sintéticas para las pruebas. TODOS los identificadores y
 * resultados usados en las pruebas son ficticios.
 */
class PaginaSintetica(private val ancho: Int = 2000, private val alto: Int = 2800) {
    private val lineas = mutableListOf<LineaOcr>()

    /**
     * Agrega una línea en (x, y) con letras de [altoLetra] px; cada carácter ocupa 0,6 × alto.
     * [confianza] se aplica a todas las palabras (o se puede indicar por palabra con [confianzas]).
     */
    fun linea(
        texto: String, x: Int, y: Int, altoLetra: Int = 30, confianza: Float? = 0.95f,
        confianzas: List<Float?>? = null, angulo: Float? = 0f,
    ): PaginaSintetica {
        val anchoChar = (altoLetra * 0.6f)
        val palabras = mutableListOf<PalabraOcr>()
        var cursor = x.toFloat()
        texto.split(" ").filter { it.isNotEmpty() }.forEachIndexed { i, p ->
            val w = p.length * anchoChar
            palabras += PalabraOcr(p, Caja(cursor.toInt(), y, (cursor + w).toInt(), y + altoLetra), confianzas?.getOrNull(i) ?: confianza)
            cursor += w + anchoChar
        }
        lineas += LineaOcr(
            texto = palabras.joinToString(" ") { it.texto },
            caja = Caja.unionDe(palabras.map { it.caja }),
            confianza = palabras.mapNotNull { it.confianza }.minOrNull(),
            angulo = angulo,
            palabras = palabras,
        )
        return this
    }

    fun construir() = PaginaOcr(ancho, alto, lineas.toList())
}

fun pagina(ancho: Int = 2000, alto: Int = 2800, bloque: PaginaSintetica.() -> Unit): PaginaOcr =
    PaginaSintetica(ancho, alto).apply(bloque).construir()
