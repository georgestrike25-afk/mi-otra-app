package com.hemocheck.core.analisis

/**
 * Qué tan bien se leyó una fila, para elegir entre dos fotos de la misma hoja: cuenta los
 * resultados leídos, si se encontró el «Estado» (Apto / No Apto) y si el número quedó dudoso.
 */
object CalidadLectura {
    /** Problemas que hacen dudar del número leído. */
    val NUMERO_DUDOSO = setOf(
        Problema.ID_FUERA_DE_ORDEN, Problema.ID_BAJA_CONFIANZA, Problema.ID_CARACTERES_DUDOSOS, Problema.ID_REPETIDO_EN_PAGINA,
    )

    fun puntaje(resultados: Int, conEstado: Boolean, numeroDudoso: Boolean): Int =
        resultados * 2 + (if (conEstado) 5 else 0) - (if (numeroDudoso) 10 else 0)
}
