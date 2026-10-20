package com.hemocheck.imagen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.imagen.CalculadorRecorte

/**
 * Recorte ampliado en memoria (nunca se escribe en disco). [caja] está en coordenadas de la
 * imagen de referencia de ancho [anchoReferencia] (la imagen procesada original); [bmp]
 * puede estar reducida. Devuelve null si no hay zona: la interfaz muestra la página completa.
 */
fun recortarZona(bmp: Bitmap, caja: Caja?, anchoReferencia: Int, altoReferencia: Int, margenRelativo: Float = 1.2f): Bitmap? {
    val zona = CalculadorRecorte.alrededor(caja, anchoReferencia, altoReferencia, margenRelativo) ?: return null
    val escala = bmp.width.toFloat() / anchoReferencia.coerceAtLeast(1)
    val c = Caja(
        (zona.izq * escala).toInt(), (zona.arriba * escala).toInt(),
        (zona.der * escala).toInt(), (zona.abajo * escala).toInt(),
    ).limitarA(bmp.width, bmp.height) ?: return null
    return Bitmap.createBitmap(bmp, c.izq, c.arriba, c.ancho, c.alto)
}

/** Decodifica un JPEG en memoria reduciéndolo hasta [ladoMax]. */
fun decodificarReducido(jpeg: ByteArray, ladoMax: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
    var muestra = 1
    while (maxOf(o.outWidth, o.outHeight) / muestra > ladoMax) muestra *= 2
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = muestra })
}
