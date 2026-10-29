package com.hemocheck

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hemocheck.captura.EstadoBorradorSerologia
import com.hemocheck.core.corroboracion.EstadoCorroboracion
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.core.corroboracion.TipoAviso
import com.hemocheck.core.resultado.CategoriaResultado as C
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.datos.Repositorio
import com.hemocheck.datos.db.OrigenDato
import com.hemocheck.retencion.Limpieza
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class RepositorioTest {
    private val e = Entorno()
    private val id = "X999926000123" // ficticio
    private val conv = listOf("HIV", "HBSAG", "ANTI_HBC", "HCV", "CHAGAS", "SIFILIS", "HTLV", "BRUCELOSIS").map { resultado(it, "NR", C.NO_REACTIVO) }
    private val nat = listOf("HIV", "HCV", "HBV").map { resultado(it, "No detectado", C.NO_REACTIVO) }

    @After
    fun cerrar() {
        e.bd.close()
        e.directorio.deleteRecursively()
    }

    @Test
    fun `guardar cifra los archivos y la busqueda exacta los encuentra`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        val encontrados = e.repositorio.buscarExacto(id)
        assertEquals(1, encontrados.size)
        assertEquals(EstadoEntrada.CONFIRMADA, encontrados[0].entrada.estado)
        val pagina = encontrados[0].pagina!!
        // El archivo en disco no contiene el contenido en claro
        val enDisco = java.io.File(e.directorio, pagina.archivoOriginal).readBytes()
        assertFalse(String(enDisco, Charsets.ISO_8859_1).contains("FICTICIA"))
        // Se descifra con la clave de la página
        assertEquals("FOTO-ORIGINAL-FICTICIA", String(e.archivos.leer(pagina.archivoOriginal, pagina.claveArchivo)))
        assertTrue(e.repositorio.buscarExacto("X999926000124").isEmpty())
    }

    @Test
    fun `fila sin revisar y sin problemas es lectura automatica y cuenta como coincidencia`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, confirmada = false, resultados = conv) }))
        assertEquals(EstadoEntrada.AUTOMATICA, e.repositorio.buscarExacto(id).single().entrada.estado)
        val d = e.repositorio.comparacion(e.repositorio.crearVerificacion(paginaFicticia(), null, id))!!
        val s = d.informe.secciones.single { it.tipo == TipoRegistro.CONVENCIONAL }
        assertTrue(EstadoCorroboracion.COINCIDENCIA_EXACTA in s.estados)
        assertFalse(EstadoCorroboracion.PENDIENTE_REVISION in d.informe.estados)
        assertEquals(1, d.registros.size)
    }

    @Test
    fun `fila con dudas de lectura igual se guarda y se muestra para verificarla en el collage`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, {
            fila(it, id, confirmada = false, resultados = conv, problemas = setOf(com.hemocheck.core.analisis.Problema.RESULTADO_AMBIGUO_COLUMNAS))
        }))
        assertEquals(EstadoEntrada.AUTOMATICA, e.repositorio.buscarExacto(id).single().entrada.estado)
        val d = e.repositorio.comparacion(e.repositorio.crearVerificacion(paginaFicticia(), null, id))!!
        assertEquals(1, d.registros.size)
        assertTrue(EstadoCorroboracion.COINCIDENCIA_EXACTA in d.informe.secciones.single { it.tipo == TipoRegistro.CONVENCIONAL }.estados)
    }

    @Test
    fun `corroboracion con ambos registros y foto de etiqueta`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        e.repositorio.guardarSerologia(borrador(TipoRegistro.MOLECULAR, { fila(it, id, resultados = nat) }))
        val vid = e.repositorio.crearVerificacion(paginaFicticia(com.hemocheck.captura.ModoCaptura.ETIQUETA), null, "x9999 26 000123")
        val d = e.repositorio.comparacion(vid)!!
        assertEquals(id, d.informe.idEtiqueta)
        assertTrue(d.informe.secciones.all { EstadoCorroboracion.COINCIDENCIA_EXACTA in it.estados })
        assertEquals(2, d.registros.size)
        assertNotNull(d.paginaEtiqueta)
        // Imágenes originales accesibles desde la comparación
        assertEquals("FOTO-ORIGINAL-FICTICIA", String(e.archivos.leer(d.paginaEtiqueta!!.archivoOriginal, d.paginaEtiqueta!!.claveArchivo)))
        assertTrue(d.registros.all { it.pagina != null })
    }

    @Test
    fun `la misma hoja guardada dos veces no se duplica`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        val repetida = runCatching { e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) })) }
        assertTrue(repetida.exceptionOrNull() is Repositorio.SinNovedadesException)
        // Dos fotos de la misma hoja en el mismo borrador: una sola fila
        val p1 = paginaFicticia()
        val p2 = paginaFicticia()
        e.repositorio.guardarSerologia(
            EstadoBorradorSerologia(TipoRegistro.MOLECULAR, "Prueba", listOf(p1, p2), filas = listOf(fila(p1, id, resultados = nat), fila(p2, id, resultados = nat))),
        )
        val d = e.repositorio.comparacion(e.repositorio.crearVerificacion(null, null, id))!!
        assertEquals(2, d.registros.size)
        assertEquals(1, d.registros.count { it.entrada.tipo == TipoRegistro.CONVENCIONAL })
        assertFalse(EstadoCorroboracion.DUPLICADO_AMBIGUO in d.informe.estados)
    }

    @Test
    fun `duplicados contradictorios y similares se informan`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        val distinta = conv.map { if (it.clave == "HCV") resultado("HCV", "R", C.REACTIVO) else it }
        e.ahora += 24 * 3600 * 1000L // otro día
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = distinta) }))
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, "X99992600O123", resultados = conv) }))
        val d = e.repositorio.comparacion(e.repositorio.crearVerificacion(null, null, id))!!
        assertTrue(EstadoCorroboracion.DUPLICADO_AMBIGUO in d.informe.estados)
        assertTrue(d.informe.avisos.any { it.tipo == TipoAviso.ID_SIMILAR })
        assertEquals(2, d.registros.size)
    }

    @Test
    fun `identificador corregido queda registrado como correccion`() = runBlocking {
        val loteId = e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, idOcr = "X9999 26 0OO123", resultados = conv) }))
        assertTrue(loteId > 0)
        val entrada = e.repositorio.buscarExacto(id).single()
        assertEquals(OrigenDato.CORREGIDO, entrada.entrada.origenId)
        val det = e.repositorio.detalleEntrada(entrada.entrada.id)!!
        assertTrue(det.correcciones.any { it.campo == Repositorio.CAMPO_ID && it.valorNuevo == id })
    }

    @Test
    fun `revision posterior confirma una fila pendiente sin cambiar su vencimiento`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, confirmada = false, resultados = conv) }))
        val antes = e.repositorio.buscarExacto(id).single().entrada
        e.ahora += 60_000
        assertTrue(e.repositorio.actualizarEntrada(antes.id, id, conv, emptyList(), confirmar = true))
        val despues = e.repositorio.buscarExacto(id).single().entrada
        assertEquals(EstadoEntrada.CONFIRMADA, despues.estado)
        assertEquals(antes.vence, despues.vence)
    }

    @Test
    fun `registro vencido se bloquea aunque el borrado este pendiente`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        e.avanzarMeses(5)
        assertEquals(1, e.repositorio.buscarExacto(id).size)
        e.avanzarMeses(1)
        // Sin ejecutar la limpieza: ya no se muestra
        assertTrue(e.repositorio.buscarExacto(id).isEmpty())
    }

    @Test
    fun `limpieza elimina originales, derivados y filas vencidas`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        val vid = e.repositorio.crearVerificacion(paginaFicticia(), null, id)
        assertEquals(4, e.directorio.listFiles()!!.size) // original y procesada de hoja y etiqueta
        e.avanzarMeses(3)
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, "X999926000999", resultados = conv) }))
        e.avanzarMeses(3)
        val r = Limpieza(e.bd, e.archivos, e.reloj, e.ajustes).ejecutar()
        assertTrue(r.completa)
        assertEquals(4, r.archivosEliminados)
        assertEquals(2, e.directorio.listFiles()!!.size) // queda solo el lote más nuevo
        assertNull(e.repositorio.comparacion(vid))
        assertTrue(e.bd.retencion().pendientes().isEmpty())
        assertEquals(1, e.repositorio.buscarExacto("X999926000999").size)
    }

    @Test
    fun `atrasar el reloj no recupera datos vencidos`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        e.avanzarMeses(7)
        assertTrue(e.repositorio.buscarExacto(id).isEmpty())
        e.avanzarMeses(-7)
        assertTrue(e.repositorio.buscarExacto(id).isEmpty())
    }

    @Test
    fun `etiqueta ilegible no busca registros`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        val d = e.repositorio.comparacion(e.repositorio.crearVerificacion(paginaFicticia(), null, null))!!
        assertTrue(EstadoCorroboracion.IDENTIFICADOR_ILEGIBLE in d.informe.estados)
        assertTrue(d.registros.isEmpty())
    }

    @Test
    fun `dos filas de la misma foto con el mismo numero no se juntan`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }, { fila(it, id, resultados = conv) }))
        val d = e.repositorio.comparacion(e.repositorio.crearVerificacion(null, null, id))!!
        assertEquals(2, d.registros.size)
    }

    @Test
    fun `si falta la hoja NAT se muestran numeros parecidos`() = runBlocking {
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        e.repositorio.guardarSerologia(borrador(TipoRegistro.MOLECULAR, { fila(it, "X999926000183", resultados = nat) }))
        val d = e.repositorio.comparacion(e.repositorio.crearVerificacion(null, null, id))!!
        assertTrue(d.registros.none { it.entrada.tipo == TipoRegistro.MOLECULAR })
        assertEquals(listOf("X999926000183"), d.parecidos.map { it.entrada.idNormalizado })
    }

    @Test
    fun `el mismo dia solo se guarda otra foto si se ve mejor`() = runBlocking {
        val incompleta = conv.take(4)
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = incompleta) }))
        // Igual de buena: no se guarda
        val igual = runCatching { e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = incompleta) })) }
        assertTrue(igual.exceptionOrNull() is Repositorio.SinNovedadesException)
        // Mejor (más resultados leídos): se guarda y el collage muestra esa
        e.repositorio.guardarSerologia(borrador(TipoRegistro.CONVENCIONAL, { fila(it, id, resultados = conv) }))
        assertEquals(2, e.repositorio.buscarExacto(id).size)
        val d = e.repositorio.comparacion(e.repositorio.crearVerificacion(null, null, id))!!
        assertTrue(d.registros.any { it.resultados.size == conv.size })
    }
}
