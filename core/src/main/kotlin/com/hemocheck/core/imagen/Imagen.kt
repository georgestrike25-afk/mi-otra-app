package com.hemocheck.core.imagen

import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.geometria.Punto
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Imagen en escala de grises (luminancia 0–255), fila por fila. */
class ImagenGris(val ancho: Int, val alto: Int, val pixeles: ByteArray) {
    init {
        require(pixeles.size >= ancho * alto) { "Tamaño de imagen inconsistente" }
    }

    fun valor(x: Int, y: Int): Int = pixeles[y * ancho + x].toInt() and 0xFF

    /** Reduce la imagen por promedio de bloques hasta que el lado mayor no supere [ladoMax]. */
    fun reducir(ladoMax: Int): ImagenGris {
        val factor = max(1, (max(ancho, alto) + ladoMax - 1) / ladoMax)
        if (factor == 1) return this
        val w = ancho / factor
        val h = alto / factor
        val salida = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var suma = 0
            for (dy in 0 until factor) for (dx in 0 until factor) suma += valor(x * factor + dx, y * factor + dy)
            salida[y * w + x] = (suma / (factor * factor)).toByte()
        }
        return ImagenGris(w, h, salida)
    }

    companion object {
        /** A partir de un plano Y (luminancia) con paso de fila [pasoFila]. */
        fun desdePlanoY(ancho: Int, alto: Int, datos: ByteArray, pasoFila: Int): ImagenGris {
            if (pasoFila == ancho) return ImagenGris(ancho, alto, datos)
            val salida = ByteArray(ancho * alto)
            for (y in 0 until alto) System.arraycopy(datos, y * pasoFila, salida, y * ancho, ancho)
            return ImagenGris(ancho, alto, salida)
        }

        /** A partir de píxeles ARGB empaquetados (como devuelve Bitmap.getPixels). */
        fun desdeArgb(ancho: Int, alto: Int, argb: IntArray): ImagenGris {
            val salida = ByteArray(ancho * alto)
            for (i in 0 until ancho * alto) {
                val p = argb[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                salida[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
            }
            return ImagenGris(ancho, alto, salida)
        }
    }
}

data class MetricasCalidad(
    val brilloMedio: Double,
    /** Varianza del laplaciano: valores bajos indican imagen borrosa o movida. */
    val nitidez: Double,
    /** Fracción de píxeles casi blancos saturados (posibles reflejos). */
    val fraccionSaturada: Double,
    val fraccionOscura: Double,
)

enum class AvisoCalidad { POCA_LUZ, SOBREEXPUESTA, DESENFOQUE, REFLEJOS, POSIBLE_CORTE, SIN_BORDES, TEXTO_ILEGIBLE, SIN_TEXTO }

/**
 * Indicadores de calidad de la captura. Son orientativos: generan advertencias para que el
 * usuario repita la foto, nunca bloquean ni modifican el contenido.
 * Los umbrales son conservadores y deben ajustarse con documentos reales del servicio.
 */
object AnalizadorCalidad {
    const val LADO_ANALISIS = 640
    const val UMBRAL_POCA_LUZ = 60.0
    const val UMBRAL_SOBREEXPUESTA = 235.0
    const val UMBRAL_NITIDEZ = 80.0
    const val UMBRAL_REFLEJOS = 0.02

    fun medir(original: ImagenGris, region: Caja? = null): MetricasCalidad {
        val img = original.reducir(LADO_ANALISIS)
        val escala = img.ancho.toDouble() / original.ancho
        val r = region?.let {
            Caja((it.izq * escala).toInt(), (it.arriba * escala).toInt(), (it.der * escala).toInt(), (it.abajo * escala).toInt())
                .limitarA(img.ancho, img.alto)
        } ?: Caja(0, 0, img.ancho, img.alto)
        var suma = 0L
        var saturados = 0L
        var oscuros = 0L
        var n = 0L
        var sumaLap = 0.0
        var sumaLap2 = 0.0
        var nLap = 0L
        for (y in r.arriba until r.abajo) for (x in r.izq until r.der) {
            val v = img.valor(x, y)
            suma += v
            if (v >= 250) saturados++
            if (v <= 30) oscuros++
            n++
            if (x > r.izq && x < r.der - 1 && y > r.arriba && y < r.abajo - 1) {
                val lap = (img.valor(x - 1, y) + img.valor(x + 1, y) + img.valor(x, y - 1) + img.valor(x, y + 1) - 4 * v).toDouble()
                sumaLap += lap
                sumaLap2 += lap * lap
                nLap++
            }
        }
        if (n == 0L) return MetricasCalidad(0.0, 0.0, 0.0, 0.0)
        val mediaLap = if (nLap > 0) sumaLap / nLap else 0.0
        val varLap = if (nLap > 0) sumaLap2 / nLap - mediaLap * mediaLap else 0.0
        return MetricasCalidad(suma.toDouble() / n, varLap, saturados.toDouble() / n, oscuros.toDouble() / n)
    }

    fun evaluar(m: MetricasCalidad): Set<AvisoCalidad> = buildSet {
        if (m.brilloMedio < UMBRAL_POCA_LUZ) add(AvisoCalidad.POCA_LUZ)
        if (m.brilloMedio > UMBRAL_SOBREEXPUESTA) add(AvisoCalidad.SOBREEXPUESTA)
        if (m.nitidez < UMBRAL_NITIDEZ) add(AvisoCalidad.DESENFOQUE)
        if (m.fraccionSaturada > UMBRAL_REFLEJOS && m.brilloMedio <= UMBRAL_SOBREEXPUESTA) add(AvisoCalidad.REFLEJOS)
    }
}

/** Resultado de buscar los bordes de una hoja en la imagen. */
data class DeteccionHoja(
    /** Esquinas en orden: superior izquierda, superior derecha, inferior derecha, inferior izquierda. */
    val esquinas: List<Punto>?,
    /** La hoja toca el borde de la foto: puede haber partes cortadas. */
    val tocaBorde: Boolean,
) {
    val detectada: Boolean get() = esquinas != null

    /** Tamaño de salida para la corrección de perspectiva (en las coordenadas de las esquinas). */
    fun tamanoCorregido(): Pair<Int, Int>? {
        val e = esquinas ?: return null
        fun d(a: Punto, b: Punto) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
        val ancho = max(d(e[0], e[1]), d(e[3], e[2]))
        val alto = max(d(e[0], e[3]), d(e[1], e[2]))
        return ancho.roundToInt() to alto.roundToInt()
    }
}

/**
 * Detección simple de los bordes de una hoja clara sobre un fondo más oscuro:
 * umbral de Otsu, mayor región clara conectada y sus cuatro esquinas extremas.
 * Si la forma no se parece a un cuadrilátero, no se corrige la perspectiva y se usa la foto completa.
 */
object DetectorHoja {
    const val LADO_DETECCION = 400

    fun detectar(original: ImagenGris): DeteccionHoja {
        val img = original.reducir(LADO_DETECCION)
        val w = img.ancho
        val h = img.alto
        val umbral = otsu(img)
        val visitado = BooleanArray(w * h)
        var mejor: IntArray? = null
        var mejorTam = 0
        val cola = IntArray(w * h)
        for (inicio in 0 until w * h) {
            if (visitado[inicio] || (img.pixeles[inicio].toInt() and 0xFF) <= umbral) continue
            var cabeza = 0
            var fin = 0
            cola[fin++] = inicio
            visitado[inicio] = true
            while (cabeza < fin) {
                val p = cola[cabeza++]
                val x = p % w
                val y = p / w
                for ((nx, ny) in arrayOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (!visitado[q] && (img.pixeles[q].toInt() and 0xFF) > umbral) {
                        visitado[q] = true
                        cola[fin++] = q
                    }
                }
            }
            if (fin > mejorTam) {
                mejorTam = fin
                mejor = cola.copyOf(fin)
            }
        }
        val region = mejor ?: return DeteccionHoja(null, false)
        // Región demasiado chica, o que ocupa toda la foto (no hay fondo visible)
        if (mejorTam < w * h * 0.15 || mejorTam > w * h * 0.97) {
            return DeteccionHoja(null, tocaBorde = mejorTam > w * h * 0.97)
        }
        var tl = 0; var tr = 0; var br = 0; var bl = 0
        var minS = Int.MAX_VALUE; var maxS = Int.MIN_VALUE; var minD = Int.MAX_VALUE; var maxD = Int.MIN_VALUE
        var tocaBorde = 0
        for (p in region) {
            val x = p % w
            val y = p / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) tocaBorde++
            val s = x + y
            val d = x - y
            if (s < minS) { minS = s; tl = p }
            if (s > maxS) { maxS = s; br = p }
            if (d > maxD) { maxD = d; tr = p }
            if (d < minD) { minD = d; bl = p }
        }
        val esquinas = listOf(tl, tr, br, bl).map { Punto((it % w).toFloat(), (it / w).toFloat()) }
        val areaQuad = areaPoligono(esquinas)
        // La región debe llenar el cuadrilátero (si no, la forma no es una hoja)
        if (areaQuad <= 0 || mejorTam / areaQuad < 0.85 || mejorTam / areaQuad > 1.15) {
            return DeteccionHoja(null, tocaBorde > (w + h) * 0.1)
        }
        val escalaX = original.ancho.toFloat() / w
        val escalaY = original.alto.toFloat() / h
        return DeteccionHoja(
            esquinas.map { Punto(it.x * escalaX, it.y * escalaY) },
            tocaBorde = tocaBorde > (w + h) * 0.1,
        )
    }

    fun otsu(img: ImagenGris): Int {
        val hist = IntArray(256)
        val n = img.ancho * img.alto
        for (i in 0 until n) hist[img.pixeles[i].toInt() and 0xFF]++
        var sumaTotal = 0.0
        for (i in 0..255) sumaTotal += i * hist[i].toDouble()
        var sumaB = 0.0
        var pesoB = 0
        var mejorVar = -1.0
        var umbral = 127
        for (t in 0..255) {
            pesoB += hist[t]
            if (pesoB == 0) continue
            val pesoF = n - pesoB
            if (pesoF == 0) break
            sumaB += t * hist[t].toDouble()
            val mB = sumaB / pesoB
            val mF = (sumaTotal - sumaB) / pesoF
            val v = pesoB.toDouble() * pesoF * (mB - mF) * (mB - mF)
            if (v > mejorVar) {
                mejorVar = v
                umbral = t
            }
        }
        return umbral
    }

    private fun areaPoligono(p: List<Punto>): Double {
        var a = 0.0
        for (i in p.indices) {
            val j = (i + 1) % p.size
            a += p[i].x.toDouble() * p[j].y - p[j].x.toDouble() * p[i].y
        }
        return kotlin.math.abs(a) / 2
    }
}

/** Calcula los recortes ampliados que se muestran en la comparación. */
object CalculadorRecorte {
    /**
     * Recorte alrededor de una zona de texto, con margen proporcional a la altura del texto.
     * Devuelve null si no hay zona (la interfaz debe mostrar la página completa).
     */
    fun alrededor(zona: Caja?, anchoImagen: Int, altoImagen: Int, margenRelativo: Float = 1.2f, minimo: Int = 48): Caja? {
        val z = zona ?: return null
        if (!z.esValida) return null
        val alto = max(z.alto, minimo / 2)
        val margenY = (alto * margenRelativo).roundToInt()
        val margenX = max(margenY, (z.ancho * 0.05f).roundToInt())
        val c = z.expandir(margenX, margenY)
        // Tamaño mínimo para que el recorte sea legible
        val faltaX = max(0, minimo - c.ancho) / 2
        val faltaY = max(0, minimo - c.alto) / 2
        return c.expandir(faltaX, faltaY).limitarA(anchoImagen, altoImagen)
    }

    /** Une dos zonas solo si están cerca; si no, devuelve la primera (se muestran por separado). */
    fun unirSiCercanas(a: Caja?, b: Caja?, distanciaMax: Int): Caja? {
        if (a == null || b == null) return a ?: b
        val separacion = max(0, max(a.arriba, b.arriba) - min(a.abajo, b.abajo))
        return if (separacion <= distanciaMax) a.union(b) else a
    }
}
