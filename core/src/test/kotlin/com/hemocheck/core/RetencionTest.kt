package com.hemocheck.core

import com.hemocheck.core.retencion.AlmacenRetencion
import com.hemocheck.core.retencion.ArchivoAlmacen
import com.hemocheck.core.retencion.CoordinadorLimpieza
import com.hemocheck.core.retencion.PoliticaRetencion
import com.hemocheck.core.retencion.RelojProtegido
import com.hemocheck.core.retencion.SistemaArchivos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RetencionTest {
    private val zona = ZoneId.of("America/Argentina/Buenos_Aires")
    private fun ms(a: Int, m: Int, d: Int, h: Int = 10) = LocalDateTime.of(a, m, d, h, 0).atZone(zona).toInstant().toEpochMilli()

    @Test
    fun `seis meses calendario`() {
        assertEquals(ms(2027, 4, 15), PoliticaRetencion.vencimientoMillis(ms(2026, 10, 15), zona))
        // 31 de agosto + 6 meses = último día de febrero
        assertEquals(ms(2027, 2, 28), PoliticaRetencion.vencimientoMillis(ms(2026, 8, 31), zona))
        assertEquals(ms(2028, 2, 29), PoliticaRetencion.vencimientoMillis(ms(2027, 8, 31), zona))
    }

    @Test
    fun `dentro del plazo y vencido`() {
        val vence = PoliticaRetencion.vencimientoMillis(ms(2026, 10, 15), zona)
        assertFalse(PoliticaRetencion.estaVencido(vence, ms(2027, 4, 15, 9)))
        assertTrue(PoliticaRetencion.estaVencido(vence, ms(2027, 4, 15, 10)))
        assertTrue(PoliticaRetencion.estaVencido(vence, ms(2027, 5, 1)))
    }

    @Test
    fun `atrasar el reloj no revive registros`() {
        var maximo = 0L
        var real = ms(2027, 5, 1)
        val reloj = RelojProtegido({ maximo }, { maximo = it }, { real })
        assertEquals(ms(2027, 5, 1), reloj.ahoraMillis())
        real = ms(2026, 1, 1)
        assertEquals(ms(2027, 5, 1), reloj.ahoraMillis())
    }

    /** Almacén en memoria que imita la transacción de Room. */
    private class AlmacenFalso(var vencidos: MutableMap<String, Boolean>, val vigentes: MutableSet<String>) : AlmacenRetencion {
        val pendientes = mutableSetOf<String>()
        var filas = 0
        override fun archivosDeVencidos(ahoraMillis: Long) = vencidos.keys.toList()
        override fun eliminarVencidos(ahoraMillis: Long, archivos: List<String>): Int {
            pendientes += archivos
            val n = vencidos.size
            vencidos.clear()
            filas += n
            return n
        }
        override fun archivosPendientes() = pendientes.toList()
        override fun quitarPendiente(nombre: String) { pendientes -= nombre }
        override fun archivosReferenciados() = vigentes.toSet()
    }

    private class ArchivosFalsos(val archivos: MutableMap<String, Long>, val fallan: MutableSet<String> = mutableSetOf()) : SistemaArchivos {
        override fun listar() = archivos.map { ArchivoAlmacen(it.key, it.value) }
        override fun existe(nombre: String) = nombre in archivos
        override fun eliminar(nombre: String): Boolean {
            if (nombre in fallan) return false
            archivos.remove(nombre)
            return true
        }
    }

    @Test
    fun `limpieza de originales y derivados, conserva vigentes`() {
        val ahora = ms(2027, 5, 1)
        val almacen = AlmacenFalso(mutableMapOf("orig1" to true, "proc1" to true, "etq1" to true), mutableSetOf("vigente"))
        val fs = ArchivosFalsos(mutableMapOf("orig1" to 0L, "proc1" to 0L, "etq1" to 0L, "vigente" to 0L))
        val r = CoordinadorLimpieza(almacen, fs).ejecutar(ahora)
        assertEquals(3, r.archivosEliminados)
        assertTrue(r.completa)
        assertEquals(setOf("vigente"), fs.archivos.keys)
        assertTrue(almacen.pendientes.isEmpty())
    }

    @Test
    fun `fallo de borrado se reintenta en la siguiente ejecucion`() {
        val ahora = ms(2027, 5, 1)
        val almacen = AlmacenFalso(mutableMapOf("orig1" to true), mutableSetOf())
        val fs = ArchivosFalsos(mutableMapOf("orig1" to 0L), fallan = mutableSetOf("orig1"))
        val primera = CoordinadorLimpieza(almacen, fs).ejecutar(ahora)
        assertFalse(primera.completa)
        // La fila ya no existe (la clave se perdió) pero el archivo queda anotado
        assertEquals(listOf("orig1"), almacen.archivosPendientes())
        fs.fallan.clear()
        val segunda = CoordinadorLimpieza(almacen, fs).ejecutar(ahora)
        assertTrue(segunda.completa)
        assertEquals(1, segunda.archivosEliminados)
        assertTrue(fs.archivos.isEmpty())
    }

    @Test
    fun `huerfanos recientes se respetan y los viejos se borran`() {
        val ahora = ms(2027, 5, 1)
        val almacen = AlmacenFalso(mutableMapOf(), mutableSetOf())
        val fs = ArchivosFalsos(mutableMapOf("viejo" to ahora - 2 * 3_600_000L, "reciente" to ahora - 60_000L))
        val r = CoordinadorLimpieza(almacen, fs).ejecutar(ahora)
        assertEquals(1, r.huerfanosEliminados)
        assertEquals(setOf("reciente"), fs.archivos.keys)
    }
}
