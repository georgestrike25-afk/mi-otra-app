package com.hemocheck.core.analisis

import com.hemocheck.core.geometria.Caja
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.tan

/**
 * Inclinación de las filas de una planilla fotografiada. Con la hoja torcida, las filas suben o bajan
 * hacia la derecha y un resultado de una columna lejana queda a la altura de la fila vecina; si no se
 * compensa, se asigna a la unidad equivocada. La inclinación se estima con los propios datos de la
 * tabla: es la que alinea más resultados con el número de su fila.
 */
object InclinacionFila {
    private const val MAXIMO_GRADOS = 8
    private const val PASO = 0.1f

    /**
     * Tangente de la inclinación (y crece hacia abajo; positiva si las filas bajan hacia la derecha),
     * o null si no hay datos suficientes o no mejora la [actual].
     */
    fun estimar(numeros: List<Caja>, resultados: List<Caja>, actual: Float): Float? {
        if (numeros.size < 3 || resultados.size < 6) return null
        val alto = numeros.map { it.alto }.sorted()[numeros.size / 2].coerceAtLeast(1)
        val xs = resultados.map { it.centroX }
        if (xs.max() - xs.min() < 4f * alto) return null
        val tolerancia = 0.4f * alto

        fun puntaje(t: Float): Int {
            var n = 0
            for (id in numeros) {
                val yId = id.centroY - id.centroX * t
                for (r in resultados) {
                    if (r.izq > id.der && abs((r.centroY - r.centroX * t) - yId) <= tolerancia) n++
                }
            }
            return n
        }

        val candidatas = (-MAXIMO_GRADOS * 10..MAXIMO_GRADOS * 10).map { tan(Math.toRadians((it * PASO).toDouble())).toFloat() }
        val puntajes = candidatas.map { puntaje(it) }
        val mejor = puntajes.max()
        if (mejor == 0) return null
        // Centro de la meseta de máximo: es más estable que un único valor
        val empatadas = candidatas.filterIndexed { i, _ -> puntajes[i] == mejor }.sorted()
        val elegida = empatadas[empatadas.size / 2]
        return if (mejor > puntaje(actual)) elegida else null
    }

    /**
     * Inclinación en grados de la fila de [cajaId] a partir de sus resultados (positiva si la fila baja
     * hacia la derecha). 0 si no hay un resultado suficientemente lejos del número.
     */
    fun grados(cajaId: Caja, resultados: List<Caja>): Float {
        val lejanos = resultados.filter { it.centroX - cajaId.centroX > 3f * max(cajaId.alto, 1) }
        if (lejanos.isEmpty()) return 0f
        val pendientes = lejanos.map { (it.centroY - cajaId.centroY) / (it.centroX - cajaId.centroX) }.sorted()
        val g = Math.toDegrees(atan(pendientes[pendientes.size / 2].toDouble())).toFloat()
        return if (abs(g) < 0.3f || abs(g) > MAXIMO_GRADOS) 0f else g
    }
}
