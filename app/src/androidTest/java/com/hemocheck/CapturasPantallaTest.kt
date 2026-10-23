package com.hemocheck

import android.app.UiModeManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hemocheck.captura.EstadoBorradorSerologia
import com.hemocheck.captura.FilaEditable
import com.hemocheck.captura.ModoCaptura
import com.hemocheck.captura.PaginaCapturada
import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.etiqueta.AnalizadorEtiqueta
import com.hemocheck.core.resultado.TipoRegistro
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Capturas de pantalla de la app con documentos SINTÉTICOS (números ficticios 9001xx), en modo
 * claro y oscuro, para revisar el diseño. Se guardan en /sdcard/capturas y la CI las publica.
 */
@RunWith(AndroidJUnit4::class)
class CapturasPantallaTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val app = instr.targetContext.applicationContext as HemoCheckApp
    private val c get() = app.contenedor!!

    private fun shell(comando: String) {
        instr.uiAutomation.executeShellCommand(comando).use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }
    }

    private fun captura(nombre: String) {
        compose.waitForIdle()
        Thread.sleep(2500)
        shell("screencap -p /sdcard/capturas/$nombre.png")
    }

    private fun existe(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun jpeg(bmp: Bitmap): ByteArray =
        ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it); it.toByteArray() }

    /** Planilla con el formato del servicio (Referencia, Estado y columnas de resultados). */
    private fun planilla(columnas: List<String>, noApto: Int?, reactivo: Pair<Int, Int>?): ByteArray {
        val ancho = 520 + columnas.size * 230
        val bmp = Bitmap.createBitmap(ancho, 1300, Bitmap.Config.ARGB_8888)
        val l = Canvas(bmp)
        l.drawColor(Color.rgb(246, 244, 238))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 28f; typeface = Typeface.SANS_SERIF }
        val n = Paint(p).apply { typeface = Typeface.DEFAULT_BOLD }
        l.drawText("SEROLOGIA - DOCUMENTO FICTICIO DE PRUEBA", 40f, 70f, n)
        l.drawText("Referencia", 40f, 170f, n)
        l.drawText("Estado", 240f, 170f, n)
        columnas.forEachIndexed { i, t -> l.drawText(t, 420f + i * 230f, 170f, n) }
        l.drawLine(30f, 195f, ancho - 30f, 195f, p)
        for (f in 0 until 14) {
            val y = 250f + f * 62f
            l.drawText("9001%02d".format(f + 1), 40f, y, p)
            l.drawText(if (f == noApto) "No Apto" else "Apto", 240f, y, p)
            columnas.indices.forEach { i -> l.drawText(if (reactivo == f to i) "REACTIVO" else "NO REACTIVO", 420f + i * 230f, y, p) }
        }
        return jpeg(bmp)
    }

    /** Bolsa con etiqueta adhesiva del formato local (ficticia) y texto de la bolsa al costado. */
    private fun etiqueta(): ByteArray {
        val bmp = Bitmap.createBitmap(1200, 1500, Bitmap.Config.ARGB_8888)
        val l = Canvas(bmp)
        l.drawColor(Color.rgb(70, 12, 18))
        val blanco = Paint().apply { color = Color.WHITE }
        l.drawRect(120f, 420f, 680f, 820f, blanco)
        l.drawRect(700f, 420f, 1120f, 900f, blanco)
        l.drawRect(120f, 860f, 680f, 1180f, blanco)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 30f }
        l.drawText("Hospital Ficticio - F.Ext:1/1/2026", 150f, 480f, p)
        l.drawText("Globulos Rojos Sedimentados", 150f, 540f, p.apply { textSize = 34f })
        l.drawText("Unidad:900104G", 150f, 610f, Paint(p).apply { textSize = 52f; typeface = Typeface.DEFAULT_BOLD })
        var x = 160f
        var i = 0
        while (x < 620f) {
            val w = if (i % 3 == 0) 7f else 3f
            l.drawRect(x, 640f, x + w, 730f, Paint().apply { color = Color.BLACK })
            x += w + if (i % 2 == 0) 5f else 3f
            i++
        }
        l.drawText("*900104G*", 300f, 780f, p.apply { textSize = 28f })
        l.drawText("CPD-OPTISOL 450 mL", 720f, 480f, p)
        l.drawText("Grupo ABO y Rh:", 720f, 560f, p)
        l.drawText("SEROLOGIA NO REACTIVA", 150f, 950f, p)
        return jpeg(bmp)
    }

    private suspend fun guardar(tipo: TipoRegistro, pagina: PaginaCapturada) {
        val hoja = AnalizadorHoja(c.ajustes.identificadores.formatos()).analizar(pagina.ocr!!, tipo)
        val filas = hoja.entradas.map { FilaEditable.desde(pagina.uid, it, hoja).copy(confirmada = true) }
        c.repositorio.guardarSerologia(EstadoBorradorSerologia(tipo, "Prueba", listOf(pagina), mapOf(pagina.uid to hoja), filas))
    }

    private fun nocturno(activo: Boolean) {
        val modo = instr.targetContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        instr.runOnMainSync {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                modo.setApplicationNightMode(if (activo) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
            }
        }
        if (android.os.Build.VERSION.SDK_INT < 31) shell("cmd uimode night ${if (activo) "yes" else "no"}")
        Thread.sleep(3000)
        compose.waitForIdle()
    }

    private fun abrirCollage(vid: Long) {
        compose.waitUntil(15_000) { existe("opcion_historial") }
        compose.onNodeWithTag("opcion_historial").performClick()
        compose.waitUntil(15_000) { existe("verificacion_$vid") }
        compose.onNodeWithTag("verificacion_$vid").performClick()
        compose.waitUntil(15_000) { existe("pantalla_collage") }
        Thread.sleep(4000)
    }

    @Test
    fun capturasDelDisenio() = runBlocking {
        shell("mkdir -p /sdcard/capturas")
        c.ajustes.permitirCapturas = true
        if (!c.pin.configurado) c.pin.establecer("482916".toCharArray())
        c.ajustes.avisoInicialAceptado = true
        val formatos = c.ajustes.identificadores.formatos()
        try {
            guardar(TipoRegistro.CONVENCIONAL, c.procesador.procesar(
                planilla(listOf("HIV Ag/Ac", "Anti-HTLV", "Anti-HVC", "AgHBs", "Anti-HBc", "Chagas", "SIFILIS"), noApto = 3, reactivo = 3 to 6),
                0, ModoCaptura.HOJA, corregirBordes = false,
            ))
            guardar(TipoRegistro.MOLECULAR, c.procesador.procesar(
                planilla(listOf("HIV NAT", "HBV NAT", "HCV NAT"), noApto = null, reactivo = null), 0, ModoCaptura.HOJA, corregirBordes = false,
            ))
            val pe = c.procesador.procesar(etiqueta(), 0, ModoCaptura.ETIQUETA)
            val lectura = AnalizadorEtiqueta(formatos).analizar(pe.ocr!!, pe.codigosBarras)
            val vid = c.repositorio.crearVerificacion(pe, lectura, "900104")

            compose.runOnUiThread { c.sesion.desbloquear() }
            nocturno(false)
            compose.waitUntil(15_000) { existe("opcion_historial") }
            captura("1-inicio-claro")
            abrirCollage(vid)
            captura("2-collage-claro")
            compose.onNodeWithTag("pantalla_collage").performTouchInput { swipeUp() }
            captura("3-collage-claro-abajo")

            nocturno(true)
            compose.runOnUiThread { c.sesion.desbloquear() }
            compose.waitUntil(15_000) { existe("pantalla_collage") }
            Thread.sleep(3000)
            captura("4-collage-oscuro")
            compose.onNodeWithTag("pantalla_collage").performTouchInput { swipeUp() }
            captura("5-collage-oscuro-abajo")
            compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(15_000) { existe("opcion_historial") }
            captura("6-inicio-oscuro")
        } finally {
            nocturno(false)
            c.repositorio.borrarTodo()
        }
    }
}
