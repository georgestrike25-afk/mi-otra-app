package com.hemocheck.imagen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Convierte las páginas de un PDF (planilla digital) en imágenes nítidas, con el renderizador
 * propio de Android: todo en el teléfono, sin conexión. Cada página sigue después el mismo
 * camino que una foto (OCR, filas, collage), pero sin enderezar porque ya es plana.
 */
object LectorPdf {
    /** Lado mayor de la página renderizada, igual al de las fotos procesadas. */
    const val LADO_MAX = 3200
    const val PAGINAS_MAX = 30

    class PdfNoLegibleException(causa: Throwable? = null) : Exception(causa)
    class PdfProtegidoException : Exception()

    /** Cantidad de páginas del PDF. */
    suspend fun contarPaginas(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        abrir(context, uri) { it.pageCount }
    }

    /** Renderiza la página [indice] (desde 0) como JPEG de alta calidad. */
    suspend fun renderizar(context: Context, uri: Uri, indice: Int): ByteArray = withContext(Dispatchers.IO) {
        abrir(context, uri) { r ->
            r.openPage(indice).use { pagina ->
                val escala = LADO_MAX.toFloat() / maxOf(pagina.width, pagina.height).coerceAtLeast(1)
                val ancho = (pagina.width * escala).toInt().coerceAtLeast(1)
                val alto = (pagina.height * escala).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
                try {
                    // Los PDF suelen tener fondo transparente: se pinta blanco como el papel
                    bmp.eraseColor(Color.WHITE)
                    pagina.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    ByteArrayOutputStream().use { s ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, s)
                        s.toByteArray()
                    }
                } finally {
                    bmp.recycle()
                }
            }
        }
    }

    private fun <T> abrir(context: Context, uri: Uri, bloque: (PdfRenderer) -> T): T {
        val descriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            throw PdfNoLegibleException(e)
        } ?: throw PdfNoLegibleException()
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: SecurityException) {
            descriptor.close()
            throw PdfProtegidoException()
        } catch (e: Exception) {
            descriptor.close()
            throw PdfNoLegibleException(e)
        }
        return renderer.use(bloque)
    }
}
