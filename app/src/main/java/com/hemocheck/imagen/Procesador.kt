package com.hemocheck.imagen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.hemocheck.captura.ModoCaptura
import com.hemocheck.captura.PaginaCapturada
import com.hemocheck.core.imagen.AnalizadorCalidad
import com.hemocheck.core.imagen.AvisoCalidad
import com.hemocheck.core.imagen.DetectorHoja
import com.hemocheck.core.imagen.ImagenGris
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.ocr.MotorOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Prepara una foto para el OCR sin alterar su contenido: corrige rotación y perspectiva y
 * aumenta levemente el contraste. La foto original se conserva sin cambios como evidencia.
 */
class Procesador(private val ocr: MotorOcr) {

    /**
     * [corregirBordes] = false usa la foto tal cual (sin enderezar), p. ej. si la hoja no está
     * sobre un fondo contrastado y la corrección automática la deformaría.
     */
    suspend fun procesar(
        jpeg: ByteArray, rotacion: Int, modo: ModoCaptura, corregirBordes: Boolean = true,
    ): PaginaCapturada = withContext(Dispatchers.Default) {
        val avisos = mutableSetOf<AvisoCalidad>()
        val base = decodificar(jpeg, rotacion)

        // Bordes de la hoja (en la etiqueta no se corrige la perspectiva: la bolsa no es plana)
        val gris = aGris(base, DetectorHoja.LADO_DETECCION * 2)
        val deteccion = if (modo == ModoCaptura.HOJA && corregirBordes) DetectorHoja.detectar(gris) else null
        var trabajo = base
        if (deteccion != null) {
            if (deteccion.tocaBorde) avisos += AvisoCalidad.POSIBLE_CORTE
            val esquinas = deteccion.esquinas
            if (esquinas == null) {
                avisos += AvisoCalidad.SIN_BORDES
            } else {
                val escala = base.width.toFloat() / gris.ancho
                corregirPerspectiva(base, esquinas.flatMap { listOf(it.x * escala, it.y * escala) }.toFloatArray())?.let { trabajo = it }
            }
        }
        avisos += AnalizadorCalidad.evaluar(AnalizadorCalidad.medir(aGris(trabajo, AnalizadorCalidad.LADO_ANALISIS)))

        // El OCR lee una copia en escala de grises con más contraste; la imagen que se guarda y
        // se muestra queda en color (mismas dimensiones, así las coordenadas coinciden).
        var paraOcr = realzar(trabajo)
        var pagina: PaginaOcr? = null
        var errorOcr = false
        try {
            pagina = ocr.reconocer(paraOcr)
            // Si el texto está girado 90° o 180°, se endereza y se vuelve a leer
            val giro = giroNecesario(pagina)
            if (giro != 0) {
                paraOcr = rotar(paraOcr, giro)
                trabajo = rotar(trabajo, giro, reciclar = trabajo !== base)
                pagina = ocr.reconocer(paraOcr)
            }
            // En la etiqueta se hace una segunda lectura sobre la imagen en color y se unen
            if (modo == ModoCaptura.ETIQUETA) {
                val color = ocr.reconocer(trabajo)
                pagina = pagina.copy(lineas = pagina.lineas + color.lineas)
            }
        } catch (e: Exception) {
            errorOcr = true
        }
        if (pagina != null) {
            if (pagina.vacia) avisos += AvisoCalidad.SIN_TEXTO
            else if ((pagina.confianzaMedia ?: 1f) < 0.7f) avisos += AvisoCalidad.TEXTO_ILEGIBLE
        }
        val codigos = if (modo == ModoCaptura.ETIQUETA) {
            (runCatching { ocr.codigosDeBarras(trabajo) }.getOrDefault(emptyList()) +
                runCatching { ocr.codigosDeBarras(paraOcr) }.getOrDefault(emptyList())).distinct()
        } else emptyList()

        val procesadaJpeg = comprimir(trabajo)
        PaginaCapturada(
            modo = modo,
            jpegOriginal = jpeg,
            rotacion = rotacion,
            jpegProcesada = procesadaJpeg,
            anchoProcesada = trabajo.width,
            altoProcesada = trabajo.height,
            ocr = pagina,
            errorOcr = errorOcr,
            codigosBarras = codigos,
            avisos = avisos,
            bordesDetectados = deteccion?.esquinas != null && trabajo !== base,
        ).also {
            if (paraOcr !== trabajo && paraOcr !== base) paraOcr.recycle()
            if (trabajo !== base) trabajo.recycle()
            base.recycle()
        }
    }

    private fun decodificar(jpeg: ByteArray, rotacion: Int): Bitmap {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, limites)
        var muestra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / muestra > LADO_MAX_DECODIFICACION) muestra *= 2
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = muestra })
            ?: throw IllegalArgumentException("Imagen no válida")
        val escala = minOf(1f, LADO_MAX_PROCESADA.toFloat() / maxOf(bmp.width, bmp.height))
        if (rotacion == 0 && escala >= 1f) return bmp
        val m = Matrix().apply {
            postScale(escala, escala)
            postRotate(rotacion.toFloat())
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
    }

    /** Lleva el cuadrilátero de la hoja a un rectángulo (transformación proyectiva de 4 puntos). */
    private fun corregirPerspectiva(src: Bitmap, esquinas: FloatArray): Bitmap? {
        fun d(i: Int, j: Int) = Math.hypot((esquinas[2 * i] - esquinas[2 * j]).toDouble(), (esquinas[2 * i + 1] - esquinas[2 * j + 1]).toDouble())
        val ancho = maxOf(d(0, 1), d(3, 2)).roundToInt()
        val alto = maxOf(d(0, 3), d(1, 2)).roundToInt()
        if (ancho < 200 || alto < 200) return null
        val destino = floatArrayOf(0f, 0f, ancho.toFloat(), 0f, ancho.toFloat(), alto.toFloat(), 0f, alto.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(esquinas, 0, destino, 0, 4)) return null
        val salida = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        Canvas(salida).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return salida
    }

    /** Escala de grises con leve aumento de contraste (mejora la lectura, no cambia el texto). */
    private fun realzar(src: Bitmap): Bitmap {
        val salida = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val contraste = 1.25f
        val desplazamiento = (-0.5f * contraste + 0.5f) * 255f
        val cm = ColorMatrix().apply { setSaturation(0f) }
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contraste, 0f, 0f, 0f, desplazamiento,
                    0f, contraste, 0f, 0f, desplazamiento,
                    0f, 0f, contraste, 0f, desplazamiento,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        Canvas(salida).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return salida
    }

    private fun rotar(src: Bitmap, grados: Int, reciclar: Boolean = true): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(grados.toFloat()) }, true)
            .also { if (it !== src && reciclar) src.recycle() }

    /** Grados para enderezar el texto según la inclinación mediana informada por el OCR. */
    private fun giroNecesario(p: PaginaOcr): Int {
        val angulos = p.lineas.mapNotNull { it.angulo }.sorted()
        if (angulos.size < 3) return 0
        val m = angulos[angulos.size / 2]
        return when {
            abs(m) <= 45f -> 0
            m > 135f || m < -135f -> 180
            m > 0 -> -90
            else -> 90
        }
    }

    private fun comprimir(bmp: Bitmap): ByteArray =
        ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it); it.toByteArray() }

    companion object {
        const val LADO_MAX_DECODIFICACION = 4096
        const val LADO_MAX_PROCESADA = 3200

        /** Escala de grises reducida para los análisis de calidad y de bordes. */
        fun aGris(bmp: Bitmap, ladoMax: Int): ImagenGris {
            val escala = minOf(1f, ladoMax.toFloat() / maxOf(bmp.width, bmp.height))
            val chica = if (escala < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * escala).toInt().coerceAtLeast(1), (bmp.height * escala).toInt().coerceAtLeast(1), true) else bmp
            val px = IntArray(chica.width * chica.height)
            chica.getPixels(px, 0, chica.width, 0, 0, chica.width, chica.height)
            val g = ImagenGris.desdeArgb(chica.width, chica.height, px)
            if (chica !== bmp) chica.recycle()
            return g
        }
    }
}
