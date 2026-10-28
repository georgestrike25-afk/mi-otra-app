package com.hemocheck.core.analisis

import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.resultado.TipoRegistro
import java.text.Normalizer

/**
 * Reconoce si una planilla es de serología convencional o de biología molecular (NAT) por su
 * texto: la sección («Biología molecular» / «Hemoterapia») y los encabezados de las columnas
 * («HIV NAT», «HBV NAT»… frente a «CMIA», «EIA», «AgHBs», «Sífilis»…). Si no es claro, null.
 */
object DetectorTipoHoja {
    private val MOLECULAR_FUERTE = Regex("biologia\\s+molecular")
    private val CONVENCIONAL_FUERTE = Regex("seccion\\s*:?\\s*hemoterapia")
    private val NAT = Regex("(?<![a-z])nat(?![a-z])")
    private val CONVENCIONAL = Regex(
        "(?<![a-z])(cmia|eia|clia|aghbs|hbsag|anti\\s*-?\\s*hbc|anti\\s*-?\\s*hvc|anti\\s*-?\\s*hcv|ag\\s*/\\s*ac|chagas|sifilis|vdrl|brucelosis|bengala|htlv)(?![a-z])",
    )

    fun detectar(pagina: PaginaOcr): TipoRegistro? {
        val texto = normalizar(pagina.lineas.joinToString("\n") { it.texto })
        var molecular = NAT.findAll(texto).count()
        var convencional = CONVENCIONAL.findAll(texto).count()
        if (MOLECULAR_FUERTE.containsMatchIn(texto)) molecular += 3
        if (CONVENCIONAL_FUERTE.containsMatchIn(texto)) convencional += 3
        return when {
            molecular >= 2 && molecular >= convencional * 2 + 1 -> TipoRegistro.MOLECULAR
            convencional >= 2 && convencional >= molecular * 2 + 1 -> TipoRegistro.CONVENCIONAL
            else -> null
        }
    }

    private fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
