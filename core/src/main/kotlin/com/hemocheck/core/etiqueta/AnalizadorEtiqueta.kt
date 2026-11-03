package com.hemocheck.core.etiqueta

import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.identificador.FormatoIdentificador
import com.hemocheck.core.identificador.FormatoIsbt128
import com.hemocheck.core.identificador.IdUnidad
import com.hemocheck.core.identificador.OcurrenciaId
import com.hemocheck.core.identificador.TipoFormato
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.ocr.TextoLinea

enum class AvisoEtiqueta {
    SIN_TEXTO,
    SIN_IDENTIFICADOR,
    VARIOS_IDENTIFICADORES,
    BAJA_CONFIANZA,
    CARACTERES_DUDOSOS,
    CONTROL_INVALIDO,
    BARRAS_DISCORDANTE,
    BARRAS_CONCORDANTE,
    SOLO_BARRAS,
    /** El número termina en 0 y la etiqueta es de plaquetas: la «Q» final pudo leerse como un cero. */
    POSIBLE_Q_COMO_CERO,
}

data class LecturaEtiqueta(
    /** Candidatos ordenados del más al menos probable. */
    val candidatos: List<OcurrenciaId>,
    /** Identificador propuesto para que el usuario lo revise; null si no hay uno claro. */
    val propuesto: String?,
    /** DIN leído del código de barras ISBT 128, si se detectó. */
    val idCodigoBarras: String?,
    val avisos: Set<AvisoEtiqueta>,
    /** Letra de componente leída (p. ej. «G»), solo informativa. */
    val componente: String? = null,
) {
    /** La lectura necesita atención especial antes de confirmar (además de la confirmación habitual). */
    val requiereAtencion: Boolean
        get() = propuesto == null || avisos.any { it != AvisoEtiqueta.BARRAS_CONCORDANTE }
}

/**
 * Propone el identificador de unidad de una etiqueta a partir del OCR y, si existe, del código
 * de barras. Nunca confirma: el usuario siempre revisa y confirma el identificador antes de buscar.
 */
class AnalizadorEtiqueta(
    private val formatos: List<FormatoIdentificador>,
    private val umbralConfianza: Float = AnalizadorHoja.UMBRAL_CONFIANZA_POR_OMISION,
) {
    fun analizar(pagina: PaginaOcr, codigosBarras: List<String> = emptyList()): LecturaEtiqueta {
        val avisos = mutableSetOf<AvisoEtiqueta>()
        if (pagina.vacia) avisos += AvisoEtiqueta.SIN_TEXTO
        val candidatosLeidos = TextoLinea.de(pagina)
            .flatMap { IdUnidad.buscarEnLinea(it, formatos) }
            .sortedWith(
                compareByDescending<OcurrenciaId> { it.controlValido == true }
                    .thenByDescending { it.estricto }
                    .thenByDescending { it.anclado }
                    .thenByDescending { it.formato == TipoFormato.ISBT128 }
                    .thenByDescending { it.caja?.alto ?: 0 }
                    .thenByDescending { it.confianza ?: 0f }
            )
        // La letra del componente (Q, G, P, U) a veces se lee como un dígito pegado al número
        val candidatos = corregirLetraLeidaComoDigito(candidatosLeidos, codigosBarras)
            // Un mismo identificador puede aparecer varias veces en la etiqueta
            .distinctBy { it.sugerido ?: it.normalizado }

        val barras = codigosBarras.firstNotNullOfOrNull { dinDeCodigoBarras(it) }
            ?: codigosBarras.firstNotNullOfOrNull { c -> formatos.firstNotNullOfOrNull { it.canonizar(c.trim()) } }
        // Prioridad: un DIN ISBT 128; si no, los números que siguen a «Unidad:», «Ext:»…;
        // así las fechas, lotes y registros sanitarios de la etiqueta no compiten.
        val estrictosTodos = candidatos.filter { it.estricto }
        val estrictos = estrictosTodos.filter { it.formato == TipoFormato.ISBT128 }
            .ifEmpty { estrictosTodos.filter { it.anclado } }
            .ifEmpty { estrictosTodos }
            .map { it.normalizado }.distinct()

        var propuesto: String? = when {
            estrictos.size == 1 -> estrictos.single()
            estrictos.size > 1 -> {
                avisos += AvisoEtiqueta.VARIOS_IDENTIFICADORES
                // Si el código de barras coincide con uno de ellos, se propone ese
                estrictos.firstOrNull { it == barras }
            }
            candidatos.isNotEmpty() -> {
                avisos += AvisoEtiqueta.CARACTERES_DUDOSOS
                null
            }
            else -> null
        }
        val elegido = candidatos.firstOrNull { it.normalizado == propuesto }
        if (elegido != null) {
            if (elegido.controlValido == false) avisos += AvisoEtiqueta.CONTROL_INVALIDO
            if ((elegido.confianza ?: 1f) < umbralConfianza) avisos += AvisoEtiqueta.BAJA_CONFIANZA
        }
        if (barras != null) {
            when {
                propuesto == null -> {
                    avisos += AvisoEtiqueta.SOLO_BARRAS
                    propuesto = barras
                }
                propuesto == barras -> avisos += AvisoEtiqueta.BARRAS_CONCORDANTE
                else -> avisos += AvisoEtiqueta.BARRAS_DISCORDANTE
            }
        }
        if (candidatos.isEmpty() && barras == null) avisos += AvisoEtiqueta.SIN_IDENTIFICADOR
        // Sin otra lectura que confirme el número: en una etiqueta de plaquetas, un número que termina
        // en 0 pudo ser «…Q». Se ofrece la alternativa sin el 0 para elegirla con un toque.
        val textoEtiqueta = pagina.lineas.joinToString(" ") { it.texto }.lowercase()
        val sospechoso = candidatos.firstOrNull { it.normalizado == propuesto && it.estricto && it.componente == null && it.normalizado.endsWith("0") }
        val candidatosFinal = if (sospechoso != null && "plaqueta" in textoEtiqueta && barras != propuesto) {
            avisos += AvisoEtiqueta.POSIBLE_Q_COMO_CERO
            val alternativa = sospechoso.normalizado.dropLast(1)
            candidatos + sospechoso.copy(normalizado = alternativa, estricto = false, sugerido = alternativa, componente = "Q")
        } else candidatos
        val componente = candidatos.firstOrNull { it.normalizado == propuesto && it.componente != null }?.componente
            ?: codigosBarras.firstNotNullOfOrNull { Regex("^\\*?\\d+([A-Z]{1,2})\\*?$").find(it.trim().uppercase())?.groupValues?.get(1) }
        return LecturaEtiqueta(candidatosFinal, propuesto, barras, avisos, componente)
    }

    /**
     * Si otra lectura de la misma etiqueta (el código de barras Code 39 «*295216Q*» u otro texto con
     * letra) da el número sin el último dígito, ese dígito era en realidad la letra del componente
     * mal leída («295216Q» → «2952160»). Solo se corrige un número que sigue a «Unidad:», «Ext:»…
     * y cuya versión corta está confirmada por otra lectura.
     */
    private fun corregirLetraLeidaComoDigito(candidatos: List<OcurrenciaId>, codigosBarras: List<String>): List<OcurrenciaId> {
        val referencias = mutableMapOf<String, String>() // número → letra del componente
        candidatos.filter { it.estricto && it.componente != null && it.normalizado.all(Char::isDigit) }
            .forEach { referencias[it.normalizado] = it.componente!! }
        val conLetra = Regex("^\\*?(\\d{6,12})([A-Z]{1,2})\\*?$")
        codigosBarras.forEach { c -> conLetra.find(c.trim().uppercase())?.let { referencias[it.groupValues[1]] = it.groupValues[2] } }
        if (referencias.isEmpty()) return candidatos
        return candidatos.map { c ->
            val corto = c.normalizado.dropLast(1)
            val letra = referencias[corto]
            if (letra != null && c.estricto && c.anclado && c.componente == null && c.normalizado.all(Char::isDigit)) {
                c.copy(normalizado = corto, componente = letra)
            } else c
        }
    }

    companion object {
        private val DIN_BARRAS = Regex("^=?([A-NP-Z]\\d{12})(\\d{2})?$")

        /**
         * Extrae el DIN de un código de barras ISBT 128 (estructura de datos 001:
         * «=» + DIN de 13 caracteres + 2 de indicadores). Null si no corresponde.
         */
        fun dinDeCodigoBarras(valor: String): String? {
            val v = valor.trim().uppercase().replace(" ", "")
            DIN_BARRAS.matchEntire(v)?.let { return it.groupValues[1] }
            return FormatoIsbt128.canonizar(v)?.takeIf { v.startsWith("=") }
        }
    }
}
