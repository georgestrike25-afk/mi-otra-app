package com.hemocheck.core.ocr

import com.hemocheck.core.geometria.Caja
import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Texto de una línea de OCR con la correspondencia entre cada carácter y la palabra de origen,
 * para poder ubicar en la imagen el fragmento que coincide con una expresión regular.
 */
class TextoLinea(val linea: LineaOcr, val indiceLinea: Int) {

    private data class Tramo(val inicio: Int, val fin: Int, val palabra: PalabraOcr)

    /** Texto original de la línea (palabras unidas por un espacio). */
    val texto: String

    /** Texto en minúsculas y sin tildes, con la misma longitud que [texto]. */
    val textoNormalizado: String

    private val tramos: List<Tramo>

    init {
        val palabras = linea.palabras.ifEmpty {
            listOf(PalabraOcr(linea.texto, linea.caja, linea.confianza))
        }
        val sb = StringBuilder()
        val lista = mutableListOf<Tramo>()
        palabras.forEachIndexed { i, p ->
            if (i > 0) sb.append(' ')
            val inicio = sb.length
            sb.append(p.texto)
            lista += Tramo(inicio, sb.length, p)
        }
        texto = sb.toString()
        textoNormalizado = normalizarConservandoLongitud(texto)
        tramos = lista
    }

    /** Fragmento de la línea ubicado en la imagen. */
    data class Fragmento(
        val inicio: Int,
        val fin: Int,
        val texto: String,
        val caja: Caja?,
        /** Menor confianza entre las palabras que forman el fragmento. */
        val confianza: Float?,
        val indiceLinea: Int,
    )

    /** Busca [regex] sobre el texto normalizado y ubica cada coincidencia. */
    fun buscarNormalizado(regex: Regex, grupo: Int = 0): List<Fragmento> =
        regex.findAll(textoNormalizado).mapNotNull { m ->
            val r = if (grupo == 0) m.range else m.groups[grupo]?.range ?: return@mapNotNull null
            fragmento(r.first, r.last + 1)
        }.toList()

    /** Busca [regex] sobre el texto original (respetando mayúsculas). */
    fun buscarOriginal(regex: Regex): List<Pair<MatchResult, Fragmento>> =
        regex.findAll(texto).map { m -> m to fragmento(m.range.first, m.range.last + 1) }.toList()

    fun fragmento(inicio: Int, fin: Int): Fragmento {
        val involucrados = tramos.filter { it.inicio < fin && inicio < it.fin }
        val cajas = involucrados.mapNotNull { t -> cajaParcial(t, inicio, fin) }
        val confianzas = involucrados.mapNotNull { it.palabra.confianza }
        return Fragmento(
            inicio = inicio,
            fin = fin,
            texto = texto.substring(inicio, fin),
            caja = Caja.unionDe(cajas) ?: linea.caja,
            confianza = confianzas.minOrNull() ?: linea.confianza,
            indiceLinea = indiceLinea,
        )
    }

    /** Fragmentos de texto (palabras) que no se solapan con ninguno de los rangos dados. */
    fun palabrasFuera(rangos: List<IntRange>): List<Fragmento> =
        tramos.filter { t -> rangos.none { r -> t.inicio <= r.last && r.first < t.fin } }
            .map { fragmento(it.inicio, it.fin) }

    /**
     * Si el fragmento ocupa solo parte de una palabra, estima la porción horizontal
     * proporcionalmente a la cantidad de caracteres.
     */
    private fun cajaParcial(t: Tramo, inicio: Int, fin: Int): Caja? {
        val caja = t.palabra.caja ?: return null
        val largo = t.fin - t.inicio
        if (largo <= 0) return caja
        val desde = (maxOf(inicio, t.inicio) - t.inicio).toFloat() / largo
        val hasta = (minOf(fin, t.fin) - t.inicio).toFloat() / largo
        if (desde <= 0f && hasta >= 1f) return caja
        val izq = caja.izq + (caja.ancho * desde).roundToInt()
        val der = caja.izq + (caja.ancho * hasta).roundToInt()
        return Caja(izq, caja.arriba, maxOf(der, izq + 1), caja.abajo)
    }

    companion object {
        fun de(pagina: PaginaOcr): List<TextoLinea> =
            pagina.lineas.mapIndexed { i, l -> TextoLinea(l, i) }

        /** Minúsculas y sin diacríticos, carácter por carácter (la longitud no cambia). */
        fun normalizarConservandoLongitud(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s) {
                val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                    .firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() } ?: c
                sb.append(base.lowercaseChar())
            }
            return sb.toString()
        }
    }
}
