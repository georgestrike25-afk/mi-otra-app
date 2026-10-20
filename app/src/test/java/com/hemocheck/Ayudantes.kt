package com.hemocheck

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hemocheck.captura.EstadoBorradorSerologia
import com.hemocheck.captura.FilaEditable
import com.hemocheck.captura.ModoCaptura
import com.hemocheck.captura.PaginaCapturada
import com.hemocheck.captura.ResultadoEditable
import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.core.retencion.RelojProtegido
import com.hemocheck.datos.Ajustes
import com.hemocheck.datos.ArchivosCifrados
import com.hemocheck.datos.Repositorio
import com.hemocheck.datos.db.BaseDatos
import com.hemocheck.datos.db.OrigenDato
import com.hemocheck.seguridad.ProveedorClaves
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Claves falsas para pruebas en la JVM (Robolectric no tiene Android Keystore). */
class ClavesFalsas : ProveedorClaves {
    override fun claveBaseDatos() = ByteArray(32) { 1 }
    override fun hmacPin(datos: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 2 }, "HmacSHA256")) }.doFinal(datos)
    override fun destruir() = Unit
}

/** Entorno de datos en memoria con reloj controlable. Todos los datos son ficticios. */
class Entorno {
    val context: Context = ApplicationProvider.getApplicationContext()
    var ahora: Long = java.time.LocalDateTime.of(2026, 10, 15, 10, 0).atZone(ZONA).toInstant().toEpochMilli()
    private var maximo = 0L
    val reloj = RelojProtegido({ maximo }, { maximo = it }, { ahora })
    val bd = BaseDatos.enMemoria(context)
    val directorio: File = Files.createTempDirectory("boveda").toFile()
    val archivos = ArchivosCifrados(directorio)
    val ajustes = Ajustes(context.getSharedPreferences("prueba_ajustes_" + System.nanoTime(), Context.MODE_PRIVATE))
    val repositorio = Repositorio(bd, archivos, ajustes, reloj) { ZONA }

    /** Avanza el reloj ignorando el guardia (para simular el paso del tiempo). */
    fun avanzarMeses(meses: Long) {
        ahora = java.time.Instant.ofEpochMilli(ahora).atZone(ZONA).plusMonths(meses).toInstant().toEpochMilli()
    }

    companion object {
        val ZONA: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    }
}

fun paginaFicticia(modo: ModoCaptura = ModoCaptura.HOJA) = PaginaCapturada(
    modo = modo,
    jpegOriginal = "FOTO-ORIGINAL-FICTICIA".toByteArray(),
    rotacion = 90,
    jpegProcesada = "FOTO-PROCESADA-FICTICIA".toByteArray(),
    anchoProcesada = 2000,
    altoProcesada = 2800,
    ocr = null,
    errorOcr = false,
    codigosBarras = emptyList(),
    avisos = emptySet(),
    bordesDetectados = true,
)

fun resultado(clave: String, texto: String, categoria: CategoriaResultado) = ResultadoEditable(
    clave = clave, nombre = clave, texto = texto, textoOcr = texto, categoria = categoria, origen = OrigenDato.OCR,
)

fun fila(
    pagina: PaginaCapturada, id: String, confirmada: Boolean = true, resultados: List<ResultadoEditable>, idOcr: String? = id,
    problemas: Set<com.hemocheck.core.analisis.Problema> = emptySet(),
) = FilaEditable(
    paginaUid = pagina.uid, idTexto = id, idOcr = idOcr, sugerido = null, origenId = OrigenDato.OCR,
    cajaId = Caja(100, 400, 400, 430), cajaFila = Caja(100, 400, 1800, 430), cajaEncabezados = Caja(700, 300, 1800, 330),
    fecha = "2026-10-15", validacion = null, problemas = problemas, resultados = resultados, confirmada = confirmada,
)

fun borrador(tipo: TipoRegistro, vararg filas: (PaginaCapturada) -> FilaEditable): EstadoBorradorSerologia {
    val p = paginaFicticia()
    return EstadoBorradorSerologia(tipo = tipo, procedencia = "Carpeta de prueba", paginas = listOf(p), filas = filas.map { it(p) })
}
