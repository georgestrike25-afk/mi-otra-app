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

/**
 * Imagen ampliada de una fila de la planilla con su encabezado encima, recortados con el mismo
 * ancho para que las columnas queden alineadas (como en la hoja). Si el encabezado está cerca de
 * la fila se recorta un solo bloque; si está lejos, se apilan las dos franjas con una línea
 * separadora. Todo en memoria. Null si no se conoce la ubicación de la fila.
 */
fun componerFila(bmp: Bitmap, encabezado: Caja?, fila: Caja?, anchoReferencia: Int, altoReferencia: Int): Bitmap? {
    val f = fila ?: return null
    val escala = bmp.width.toFloat() / anchoReferencia.coerceAtLeast(1)
    val margen = maxOf(f.alto, 12)
    val izq = minOf(f.izq, encabezado?.izq ?: f.izq) - margen
    val der = maxOf(f.der, encabezado?.der ?: f.der) + margen
    fun franja(c: Caja): Caja? = Caja(
        (izq * escala).toInt(), ((c.arriba - margen) * escala).toInt(),
        (der * escala).toInt(), ((c.abajo + margen) * escala).toInt(),
    ).limitarA(bmp.width, bmp.height)

    val e = encabezado
    if (e == null || f.arriba - e.abajo <= f.alto * 4) {
        val c = franja(if (e == null) f else Caja(f.izq, minOf(e.arriba, f.arriba), f.der, maxOf(e.abajo, f.abajo))) ?: return null
        return Bitmap.createBitmap(bmp, c.izq, c.arriba, c.ancho, c.alto)
    }
    val ce = franja(e) ?: return null
    val cf = franja(f) ?: return null
    val separacion = maxOf(4, (f.alto * escala / 4).toInt())
    val salida = Bitmap.createBitmap(maxOf(ce.ancho, cf.ancho), ce.alto + separacion + cf.alto, Bitmap.Config.ARGB_8888)
    val lienzo = android.graphics.Canvas(salida)
    lienzo.drawColor(android.graphics.Color.GRAY)
    lienzo.drawBitmap(bmp, android.graphics.Rect(ce.izq, ce.arriba, ce.der, ce.abajo), android.graphics.Rect(0, 0, ce.ancho, ce.alto), null)
    lienzo.drawBitmap(bmp, android.graphics.Rect(cf.izq, cf.arriba, cf.der, cf.abajo), android.graphics.Rect(0, ce.alto + separacion, cf.ancho, ce.alto + separacion + cf.alto), null)
    return salida
}

/**
 * Recorte de [zona] con un recuadro alrededor de [marca] (la fila de la unidad): rojo si hay
 * una alerta documentada (reactivo o «No Apto»), azul si no. En memoria, nunca en disco.
 */
fun recortarConMarca(bmp: Bitmap, zona: Caja, marca: Caja?, anchoReferencia: Int, alerta: Boolean): Bitmap? {
    val escala = bmp.width.toFloat() / anchoReferencia.coerceAtLeast(1)
    fun esc(c: Caja) = Caja((c.izq * escala).toInt(), (c.arriba * escala).toInt(), (c.der * escala).toInt(), (c.abajo * escala).toInt())
    val z = esc(zona).limitarA(bmp.width, bmp.height) ?: return null
    val salida = Bitmap.createBitmap(bmp, z.izq, z.arriba, z.ancho, z.alto).copy(Bitmap.Config.ARGB_8888, true)
    if (marca != null) {
        val m = esc(marca)
        val pincel = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = maxOf(4f, m.alto / 10f)
            color = if (alerta) 0xFFD32F2F.toInt() else 0xFF1565C0.toInt()
            isAntiAlias = true
        }
        android.graphics.Canvas(salida).drawRect(
            (m.izq - z.izq).toFloat(), (m.arriba - z.arriba).toFloat(), (m.der - z.izq).toFloat(), (m.abajo - z.arriba).toFloat(), pincel,
        )
    }
    return salida
}
