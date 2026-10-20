package com.hemocheck.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.ocr.LineaOcr
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.ocr.PalabraOcr
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR local con ML Kit Text Recognition (modelo latino incluido en el APK: funciona sin
 * conexión y sin Google Play Services para el modelo) y lectura de códigos de barras.
 */
class MotorOcr {
    private val texto by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val barras by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    suspend fun reconocer(bitmap: Bitmap): PaginaOcr {
        val resultado = texto.process(InputImage.fromBitmap(bitmap, 0)).esperar()
        val lineas = resultado.textBlocks.flatMap { bloque ->
            bloque.lines.map { l ->
                LineaOcr(
                    texto = l.text,
                    caja = l.boundingBox?.aCaja(),
                    confianza = l.confidence,
                    angulo = l.angle,
                    palabras = l.elements.map { e -> PalabraOcr(e.text, e.boundingBox?.aCaja(), e.confidence) },
                )
            }
        }
        return PaginaOcr(bitmap.width, bitmap.height, lineas)
    }

    suspend fun codigosDeBarras(bitmap: Bitmap): List<String> =
        barras.process(InputImage.fromBitmap(bitmap, 0)).esperar().mapNotNull { it.rawValue }

    private fun Rect.aCaja() = Caja(left, top, right, bottom)

    private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }
}
