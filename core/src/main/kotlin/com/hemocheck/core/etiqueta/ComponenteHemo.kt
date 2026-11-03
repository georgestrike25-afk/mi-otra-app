package com.hemocheck.core.etiqueta

import com.hemocheck.core.ocr.PaginaOcr

/**
 * Componente sanguíneo que indica la letra al final del número de la etiqueta
 * (p. ej. 295216G). El número es de la donación y es común a todos sus componentes: el mismo
 * número con distinta letra son hemocomponentes distintos de la misma donación y es válido.
 */
enum class ComponenteHemo(val letra: String) {
    GLOBULOS_ROJOS("G"),
    PLASMA("P"),
    PLAQUETAS("Q"),
    PLAQUETAS_AFERESIS("U");

    companion object {
        fun desdeLetra(letra: String?): ComponenteHemo? {
            val l = letra?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
            return when {
                l == "G" -> GLOBULOS_ROJOS
                l == "Q" -> PLAQUETAS
                l == "U" -> PLAQUETAS_AFERESIS
                l.startsWith("P") -> PLASMA // «P», «PF» (plasma fresco)…
                else -> null
            }
        }

        /** Componente de la etiqueta: por la letra que sigue al número [id] o, si no, por el texto («Plaquetas»…). */
        fun enPagina(pagina: PaginaOcr, id: String): ComponenteHemo? {
            val letra = Regex("(?<![0-9A-Z])${Regex.escape(id)}([A-Z]{1,2})(?![0-9A-Z])")
            for (l in pagina.lineas) {
                letra.find(l.texto.uppercase().replace(" ", ""))?.let { m -> desdeLetra(m.groupValues[1])?.let { return it } }
            }
            val texto = pagina.lineas.joinToString(" ") { it.texto }.lowercase()
                .replace('á', 'a').replace('í', 'i').replace('ó', 'o')
            return when {
                "plaqueta" in texto -> PLAQUETAS
                "globulos" in texto -> GLOBULOS_ROJOS
                "plasma" in texto -> PLASMA
                else -> null
            }
        }
    }
}
