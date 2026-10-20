package com.hemocheck.core.geometria

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/** Rectángulo alineado a los ejes, en píxeles de la imagen (derecha y abajo exclusivos). */
@Serializable
data class Caja(val izq: Int, val arriba: Int, val der: Int, val abajo: Int) {
    val ancho: Int get() = der - izq
    val alto: Int get() = abajo - arriba
    val centroX: Float get() = (izq + der) / 2f
    val centroY: Float get() = (arriba + abajo) / 2f
    val area: Long get() = ancho.toLong().coerceAtLeast(0) * alto.toLong().coerceAtLeast(0)
    val esValida: Boolean get() = ancho > 0 && alto > 0

    fun union(otra: Caja): Caja =
        Caja(min(izq, otra.izq), min(arriba, otra.arriba), max(der, otra.der), max(abajo, otra.abajo))

    fun expandir(dx: Int, dy: Int): Caja = Caja(izq - dx, arriba - dy, der + dx, abajo + dy)

    /** Limita la caja a la imagen; devuelve null si queda vacía. */
    fun limitarA(anchoImagen: Int, altoImagen: Int): Caja? {
        val c = Caja(
            izq.coerceIn(0, anchoImagen), arriba.coerceIn(0, altoImagen),
            der.coerceIn(0, anchoImagen), abajo.coerceIn(0, altoImagen)
        )
        return if (c.esValida) c else null
    }

    /** Píxeles de superposición horizontal con otra caja (0 si no se tocan). */
    fun superposicionHorizontal(otra: Caja): Int = max(0, min(der, otra.der) - max(izq, otra.izq))

    fun interseca(otra: Caja): Boolean =
        izq < otra.der && otra.izq < der && arriba < otra.abajo && otra.arriba < abajo

    companion object {
        fun unionDe(cajas: Iterable<Caja?>): Caja? =
            cajas.filterNotNull().reduceOrNull { a, b -> a.union(b) }
    }
}

@Serializable
data class Punto(val x: Float, val y: Float)
