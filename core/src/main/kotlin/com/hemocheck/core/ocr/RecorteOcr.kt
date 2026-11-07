package com.hemocheck.core.ocr

import com.hemocheck.core.geometria.Caja

/**
 * El texto leído que cae dentro de [zona], con las cajas trasladadas a las coordenadas de la imagen
 * recortada a esa zona (que pasa a medir lo mismo que la zona). Una línea cuenta si su centro está dentro.
 */
fun PaginaOcr.recortada(zona: Caja): PaginaOcr {
    fun mover(c: Caja?) = c?.let { Caja(it.izq - zona.izq, it.arriba - zona.arriba, it.der - zona.izq, it.abajo - zona.arriba) }
    val dentro = lineas.filter { l ->
        l.caja?.let { it.centroX >= zona.izq && it.centroX <= zona.der && it.centroY >= zona.arriba && it.centroY <= zona.abajo } == true
    }
    return PaginaOcr(
        zona.ancho, zona.alto,
        dentro.map { l -> l.copy(caja = mover(l.caja), palabras = l.palabras.map { it.copy(caja = mover(it.caja)) }) },
    )
}
