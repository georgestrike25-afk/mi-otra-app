package com.hemocheck.retencion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hemocheck.HemoCheckApp
import com.hemocheck.core.retencion.AlmacenRetencion
import com.hemocheck.core.retencion.CoordinadorLimpieza
import com.hemocheck.core.retencion.RelojProtegido
import com.hemocheck.core.retencion.ResultadoLimpieza
import com.hemocheck.datos.Ajustes
import com.hemocheck.datos.ArchivosCifrados
import com.hemocheck.datos.db.BaseDatos
import com.hemocheck.datos.db.BorradoPendienteEntidad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Implementación con Room de las operaciones que necesita la limpieza. */
class AlmacenRetencionRoom(private val bd: BaseDatos) : AlmacenRetencion {
    private val dao = bd.retencion()

    override fun archivosDeVencidos(ahoraMillis: Long): List<String> = dao.archivosVencidos(ahoraMillis)

    override fun eliminarVencidos(ahoraMillis: Long, archivos: List<String>): Int =
        bd.runInTransaction<Int> {
            dao.anotarBorrados(archivos.map { BorradoPendienteEntidad(it) })
            dao.borrarCorrecciones(ahoraMillis) + dao.borrarResultados(ahoraMillis) + dao.borrarEntradas(ahoraMillis) +
                dao.borrarPaginas(ahoraMillis) + dao.borrarVerificaciones(ahoraMillis) + dao.borrarLotes(ahoraMillis)
        }

    override fun archivosPendientes(): List<String> = dao.pendientes()
    override fun quitarPendiente(nombre: String) = dao.quitarPendiente(nombre)
    override fun archivosReferenciados(): Set<String> = dao.referenciados().toSet()
}

/**
 * Ejecuta la limpieza de datos vencidos: al abrir la app y una vez por día con WorkManager.
 * Android puede demorar las tareas en segundo plano (ahorro de batería), por eso además las
 * consultas nunca devuelven datos vencidos aunque todavía no se hayan borrado.
 */
class Limpieza(
    private val bd: BaseDatos,
    private val archivos: ArchivosCifrados,
    private val reloj: RelojProtegido,
    private val ajustes: Ajustes,
) {
    private val mutex = Mutex()

    suspend fun ejecutar(): ResultadoLimpieza = mutex.withLock {
        withContext(Dispatchers.IO) {
            val r = CoordinadorLimpieza(AlmacenRetencionRoom(bd), archivos).ejecutar(reloj.ahoraMillis())
            if (r.filasEliminadas > 0) bd.consolidar()
            ajustes.ultimaLimpieza = reloj.ahoraMillis()
            ajustes.ultimaLimpiezaCompleta = r.completa
            r
        }
    }

    companion object {
        const val NOMBRE_TRABAJO = "limpieza_retencion"

        fun programar(context: Context) {
            val pedido = PeriodicWorkRequestBuilder<TrabajoLimpieza>(1, TimeUnit.DAYS)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NOMBRE_TRABAJO, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}

class TrabajoLimpieza(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val contenedor = (applicationContext as HemoCheckApp).contenedor ?: return Result.retry()
        return runCatching { contenedor.limpieza.ejecutar() }.fold(
            onSuccess = { if (it.completa) Result.success() else Result.retry() },
            onFailure = { Result.retry() },
        )
    }
}
