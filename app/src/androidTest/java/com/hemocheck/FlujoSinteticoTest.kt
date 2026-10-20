package com.hemocheck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hemocheck.captura.EstadoBorradorSerologia
import com.hemocheck.captura.FilaEditable
import com.hemocheck.captura.ModoCaptura
import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.corroboracion.EstadoCorroboracion
import com.hemocheck.core.etiqueta.AnalizadorEtiqueta
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Flujo completo en el dispositivo con documentos SINTÉTICOS (datos ficticios dibujados en la
 * prueba): OCR real de ML Kit, análisis, guardado cifrado (SQLCipher + Keystore), búsqueda exacta
 * y corroboración. En CI se ejecuta con el modo avión activado.
 */
@RunWith(AndroidJUnit4::class)
class FlujoSinteticoTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HemoCheckApp
    private val c get() = app.contenedor!!

    private fun jpeg(ancho: Int, alto: Int, dibujar: Canvas.(Paint) -> Unit): ByteArray {
        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f; typeface = Typeface.MONOSPACE }
        canvas.dibujar(p)
        return ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it); it.toByteArray() }
    }

    @Test
    fun ocrBusquedaYCorroboracionConDocumentosFicticios() = runBlocking {
        assertNotNull("El almacenamiento protegido debe abrirse", app.contenedor)
        // Hoja convencional ficticia: tabla con tres unidades
        val hoja = jpeg(1700, 1200) { p ->
            drawText("SEROLOGIA - DOCUMENTO FICTICIO DE PRUEBA", 60f, 100f, p)
            drawText("Unidad", 60f, 260f, p)
            listOf("HIV", "HCV", "Chagas").forEachIndexed { i, t -> drawText(t, 900f + i * 260f, 260f, p) }
            listOf("X9999 26 000101" to listOf("NR", "NR", "NR"), "X9999 26 000102" to listOf("NR", "R", "NR"), "X9999 26 000103" to listOf("NR", "NR", "NR"))
                .forEachIndexed { f, (id, res) ->
                    val y = 380f + f * 120f
                    drawText(id, 60f, y, p)
                    res.forEachIndexed { i, r -> drawText(r, 900f + i * 260f, y, p) }
                }
        }
        val pagina = c.procesador.procesar(hoja, 0, ModoCaptura.HOJA)
        assertNotNull("El OCR debe devolver un resultado", pagina.ocr)
        val ocr = pagina.ocr!!
        val analisis = AnalizadorHoja(c.ajustes.identificadores.formatos()).analizar(ocr, TipoRegistro.CONVENCIONAL)
        val ids = analisis.entradas.map { it.ocurrencia.normalizado }
        assertTrue("OCR leyó: $ids", "X999926000102" in ids)
        val segunda = analisis.entradas.first { it.ocurrencia.normalizado == "X999926000102" }
        assertEquals(CategoriaResultado.REACTIVO, segunda.resultados.firstOrNull { it.clave == "HCV" }?.categoria)

        // Guardar confirmando las filas
        val filas = analisis.entradas.map { FilaEditable.desde(pagina.uid, it, analisis).copy(confirmada = true) }
        c.repositorio.guardarSerologia(EstadoBorradorSerologia(TipoRegistro.CONVENCIONAL, "Prueba automática", listOf(pagina), mapOf(pagina.uid to analisis), filas))

        // Etiqueta ficticia
        val etiqueta = jpeg(1200, 700) { p ->
            drawText("ETIQUETA FICTICIA", 60f, 100f, p)
            p.textSize = 72f
            drawText("X9999 26 000102", 60f, 330f, p)
        }
        val pe = c.procesador.procesar(etiqueta, 0, ModoCaptura.ETIQUETA)
        val lectura = AnalizadorEtiqueta(c.ajustes.identificadores.formatos()).analizar(pe.ocr!!, pe.codigosBarras)
        assertEquals("X999926000102", lectura.propuesto)

        val vid = c.repositorio.crearVerificacion(pe, lectura, lectura.propuesto)
        val d = c.repositorio.comparacion(vid)!!
        val conv = d.informe.secciones.single { it.tipo == TipoRegistro.CONVENCIONAL }
        assertTrue(EstadoCorroboracion.COINCIDENCIA_EXACTA in conv.estados)
        assertTrue(EstadoCorroboracion.REACTIVO_DOCUMENTADO in conv.estados)
        // No hay registro molecular para esta unidad ficticia
        assertTrue(EstadoCorroboracion.REGISTRO_NO_ENCONTRADO in d.informe.secciones.single { it.tipo == TipoRegistro.MOLECULAR }.estados)
        // Las imágenes se descifran desde la comparación
        assertNotNull(c.repositorio.imagen(d.paginaEtiqueta!!, procesada = false))
        assertNotNull(c.repositorio.imagen(d.registros.first().pagina!!, procesada = true))

        c.repositorio.borrarTodo()
    }
}
