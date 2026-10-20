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
        val candidatos = TextoLinea.de(pagina)
            .flatMap { IdUnidad.buscarEnLinea(it, formatos) }
            .sortedWith(
                compareByDescending<OcurrenciaId> { it.controlValido == true }
                    .thenByDescending { it.estricto }
                    .thenByDescending { it.anclado }
                    .thenByDescending { it.formato == TipoFormato.ISBT128 }
                    .thenByDescending { it.caja?.alto ?: 0 }
                    .thenByDescending { it.confianza ?: 0f }
            )
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
        val componente = candidatos.firstOrNull { it.normalizado == propuesto && it.componente != null }?.componente
            ?: codigosBarras.firstNotNullOfOrNull { Regex("^\\*?\\d+([A-Z]{1,2})\\*?$").find(it.trim().uppercase())?.groupValues?.get(1) }
        return LecturaEtiqueta(candidatos, propuesto, barras, avisos, componente)
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
