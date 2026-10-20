package com.hemocheck.core

import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.imagen.AnalizadorCalidad
import com.hemocheck.core.imagen.AvisoCalidad
import com.hemocheck.core.imagen.CalculadorRecorte
import com.hemocheck.core.imagen.DetectorHoja
import com.hemocheck.core.imagen.ImagenGris
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ImagenTest {
    private fun imagen(w: Int, h: Int, f: (Int, Int) -> Int) =
        ImagenGris(w, h, ByteArray(w * h) { i -> f(i % w, i / w).coerceIn(0, 255).toByte() })

    /** Hoja blanca con "texto" (rayas negras) sobre fondo oscuro. */
    private fun hojaConTexto(w: Int, h: Int, margen: Int) = imagen(w, h) { x, y ->
        val dentro = x in margen until w - margen && y in margen until h - margen
        when {
            !dentro -> 40
            (y / 6) % 3 == 0 && (x / 4) % 2 == 0 -> 20
            else -> 230
        }
    }

    @Test
    fun `imagen nitida con buena luz no genera avisos`() {
        val m = AnalizadorCalidad.medir(hojaConTexto(800, 1000, 0))
        assertTrue(AnalizadorCalidad.evaluar(m).toString(), AnalizadorCalidad.evaluar(m).isEmpty())
    }

    @Test
    fun `imagen uniforme se detecta como borrosa`() {
        val m = AnalizadorCalidad.medir(imagen(800, 600) { _, _ -> 180 })
        assertTrue(AvisoCalidad.DESENFOQUE in AnalizadorCalidad.evaluar(m))
    }

    @Test
    fun `poca luz y reflejos`() {
        val r = Random(1)
        val oscura = AnalizadorCalidad.medir(imagen(400, 400) { _, _ -> 20 + r.nextInt(30) })
        assertTrue(AvisoCalidad.POCA_LUZ in AnalizadorCalidad.evaluar(oscura))
        val conReflejo = AnalizadorCalidad.medir(imagen(400, 400) { x, y ->
            if (x in 100..180 && y in 100..180) 255 else if ((x + y) % 7 == 0) 30 else 150
        })
        assertTrue(AvisoCalidad.REFLEJOS in AnalizadorCalidad.evaluar(conReflejo))
    }

    @Test
    fun `detecta las esquinas de una hoja sobre fondo oscuro`() {
        val img = imagen(800, 1000) { x, y -> if (x in 100 until 700 && y in 150 until 900) 220 else 40 }
        val d = DetectorHoja.detectar(img)
        val e = d.esquinas
        assertNotNull(e)
        assertTrue(abs(e!![0].x - 100) < 6 && abs(e[0].y - 150) < 6)
        assertTrue(abs(e[2].x - 700) < 6 && abs(e[2].y - 900) < 6)
        assertFalse(d.tocaBorde)
        val (w, h) = d.tamanoCorregido()!!
        assertTrue(abs(w - 600) < 12 && abs(h - 750) < 12)
    }

    @Test
    fun `hoja que llena la foto no se recorta y avisa posible corte`() {
        val d = DetectorHoja.detectar(imagen(800, 1000) { _, _ -> 220 })
        assertNull(d.esquinas)
        assertTrue(d.tocaBorde)
    }

    @Test
    fun `recorte con margen y limitado a la imagen`() {
        val c = CalculadorRecorte.alrededor(Caja(10, 10, 210, 40), 1000, 1000)!!
        assertEquals(0, c.izq)
        assertEquals(0, c.arriba)
        assertTrue(c.der > 210 && c.abajo > 40)
        assertNull(CalculadorRecorte.alrededor(null, 100, 100))
        assertNull(CalculadorRecorte.alrededor(Caja(5, 5, 5, 5), 100, 100))
    }
}
