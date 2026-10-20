package com.hemocheck.core.retencion

import java.time.Instant
import java.time.ZoneId

/**
 * Política de conservación: cada registro vence a los seis meses CALENDARIO de su creación
 * (en la zona horaria del dispositivo). El vencimiento se fija al crear el registro y no se
 * modifica al consultarlo.
 */
object PoliticaRetencion {
    const val MESES = 6L

    /**
     * Fecha de vencimiento. Si el día no existe en el mes destino (p. ej. 31 de agosto → febrero),
     * se usa el último día de ese mes, como hace [java.time.ZonedDateTime.plusMonths].
     */
    fun vencimiento(creado: Instant, zona: ZoneId): Instant =
        creado.atZone(zona).plusMonths(MESES).toInstant()

    fun vencimientoMillis(creadoMillis: Long, zona: ZoneId): Long =
        vencimiento(Instant.ofEpochMilli(creadoMillis), zona).toEpochMilli()

    fun estaVencido(venceMillis: Long, ahoraMillis: Long): Boolean = ahoraMillis >= venceMillis
}

/**
 * Evita que atrasar el reloj del teléfono «reviva» registros vencidos: se usa siempre el
 * mayor instante observado. (Adelantar el reloj solo puede provocar un borrado anticipado.)
 */
class RelojProtegido(private val leerMaximo: () -> Long, private val guardarMaximo: (Long) -> Unit, private val ahora: () -> Long) {
    @Synchronized
    fun ahoraMillis(): Long {
        val real = ahora()
        val maximo = leerMaximo()
        return if (real > maximo) {
            guardarMaximo(real)
            real
        } else maximo
    }
}

/** Archivo cifrado del almacén privado. */
data class ArchivoAlmacen(val nombre: String, val modificadoMillis: Long)

/** Operaciones de base de datos que necesita la limpieza (se implementan con Room). */
interface AlmacenRetencion {
    /** Nombres de archivo de los registros vencidos (originales, procesadas, etiquetas). */
    fun archivosDeVencidos(ahoraMillis: Long): List<String>

    /**
     * En UNA transacción: registra [archivos] como pendientes de borrado y elimina de la base
     * todos los registros vencidos (lotes, páginas, filas, resultados, correcciones,
     * corroboraciones y sus claves de cifrado). Devuelve la cantidad de filas eliminadas.
     */
    fun eliminarVencidos(ahoraMillis: Long, archivos: List<String>): Int

    fun archivosPendientes(): List<String>
    fun quitarPendiente(nombre: String)

    /** Archivos que todavía referencia algún registro vigente. */
    fun archivosReferenciados(): Set<String>
}

interface SistemaArchivos {
    fun listar(): List<ArchivoAlmacen>
    fun existe(nombre: String): Boolean

    /** true si el archivo ya no existe después de la operación. */
    fun eliminar(nombre: String): Boolean
}

data class ResultadoLimpieza(
    val filasEliminadas: Int,
    val archivosEliminados: Int,
    val archivosConError: Int,
    val huerfanosEliminados: Int,
) {
    val completa: Boolean get() = archivosConError == 0
}

/**
 * Elimina los datos vencidos de forma recuperable ante fallos:
 * 1. Anota los archivos a borrar y elimina los registros de la base en una sola transacción
 *    (al borrarse la fila se pierde la clave con la que se cifró cada archivo).
 * 2. Borra los archivos anotados; los que fallan quedan anotados y se reintentan después.
 * 3. Borra archivos huérfanos (sin registro), p. ej. si la app se cerró a mitad de un guardado.
 */
class CoordinadorLimpieza(
    private val almacen: AlmacenRetencion,
    private val archivos: SistemaArchivos,
    /** Antigüedad mínima de un huérfano para borrarlo (evita competir con un guardado en curso). */
    private val margenHuerfanosMillis: Long = 60 * 60 * 1000L,
) {
    fun ejecutar(ahoraMillis: Long): ResultadoLimpieza {
        val aBorrar = almacen.archivosDeVencidos(ahoraMillis)
        val filas = almacen.eliminarVencidos(ahoraMillis, aBorrar)

        var eliminados = 0
        var errores = 0
        for (nombre in almacen.archivosPendientes()) {
            val ok = runCatching { archivos.eliminar(nombre) }.getOrDefault(false)
            if (ok) {
                almacen.quitarPendiente(nombre)
                eliminados++
            } else errores++
        }

        val referenciados = almacen.archivosReferenciados()
        val pendientes = almacen.archivosPendientes().toSet()
        var huerfanos = 0
        for (a in archivos.listar()) {
            if (a.nombre in referenciados || a.nombre in pendientes) continue
            if (ahoraMillis - a.modificadoMillis < margenHuerfanosMillis) continue
            if (runCatching { archivos.eliminar(a.nombre) }.getOrDefault(false)) huerfanos++
        }
        return ResultadoLimpieza(filas, eliminados, errores, huerfanos)
    }
}
