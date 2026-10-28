package com.hemocheck.datos.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hemocheck.core.corroboracion.EstadoEntrada
import kotlinx.coroutines.flow.Flow

@Dao
interface SerologiaDao {
    @Insert
    fun insertarLote(l: LoteEntidad): Long

    @Insert
    fun insertarPagina(p: PaginaEntidad): Long

    @Insert
    fun insertarEntrada(e: EntradaEntidad): Long

    @Update
    fun actualizarEntrada(e: EntradaEntidad)

    @Insert
    fun insertarResultados(r: List<ResultadoEntidad>)

    @Query("DELETE FROM resultados WHERE entradaId = :entradaId")
    fun borrarResultados(entradaId: Long)

    @Query("SELECT * FROM entradas WHERE idNormalizado = :id AND vence > :ahora ORDER BY creado")
    fun entradasPorId(id: String, ahora: Long): List<EntradaEntidad>

    /** Identificadores distintos pero visualmente similares (posible error de OCR). */
    @Query("SELECT * FROM entradas WHERE claveConfusion = :clave AND idNormalizado != :id AND vence > :ahora")
    fun entradasSimilares(clave: String, id: String, ahora: Long): List<EntradaEntidad>

    /** Números que coinciden con un patrón LIKE (p. ej. «2956_2»: difieren en un solo dígito). */
    @Query("SELECT * FROM entradas WHERE idNormalizado LIKE :patron AND idNormalizado != :id AND vence > :ahora")
    fun entradasPorPatron(patron: String, id: String, ahora: Long): List<EntradaEntidad>

    @Query("SELECT * FROM entradas WHERE id = :id AND vence > :ahora")
    fun entrada(id: Long, ahora: Long): EntradaEntidad?

    @Query("SELECT * FROM entradas WHERE id IN (:ids) AND vence > :ahora")
    fun entradas(ids: List<Long>, ahora: Long): List<EntradaEntidad>

    @Query("SELECT * FROM resultados WHERE entradaId = :entradaId AND vence > :ahora ORDER BY id")
    fun resultados(entradaId: Long, ahora: Long): List<ResultadoEntidad>

    @Query("SELECT * FROM paginas WHERE id = :id AND vence > :ahora")
    fun pagina(id: Long, ahora: Long): PaginaEntidad?

    @Query("SELECT * FROM paginas WHERE verificacionId = :verificacionId AND vence > :ahora LIMIT 1")
    fun paginaDeVerificacion(verificacionId: Long, ahora: Long): PaginaEntidad?

    @Query("SELECT * FROM lotes WHERE id = :id AND vence > :ahora")
    fun lote(id: Long, ahora: Long): LoteEntidad?

    @Query(
        "SELECT * FROM entradas WHERE vence > :ahora AND (:soloPendientes = 0 OR estado = 'PENDIENTE_REVISION') " +
            "ORDER BY creado DESC LIMIT :limite"
    )
    fun observarEntradas(ahora: Long, soloPendientes: Boolean, limite: Int = 500): Flow<List<EntradaEntidad>>

    @Query("SELECT COUNT(*) FROM entradas WHERE vence > :ahora AND estado = :estado")
    fun contar(ahora: Long, estado: EstadoEntrada): Flow<Int>
}

@Dao
interface VerificacionDao {
    @Insert
    fun insertar(v: VerificacionEntidad): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun vincular(v: List<VerificacionEntradaEntidad>)

    @Query("SELECT * FROM verificaciones WHERE id = :id AND vence > :ahora")
    fun verificacion(id: Long, ahora: Long): VerificacionEntidad?

    @Query("SELECT entradaId FROM verificacion_entradas WHERE verificacionId = :id")
    fun entradasVinculadas(id: Long): List<Long>

    @Query("UPDATE verificaciones SET revisadaEn = :momento WHERE id = :id")
    fun marcarRevisada(id: Long, momento: Long)

    @Query("SELECT * FROM verificaciones WHERE vence > :ahora ORDER BY creado DESC LIMIT :limite")
    fun observar(ahora: Long, limite: Int = 500): Flow<List<VerificacionEntidad>>

    @Query("SELECT * FROM verificaciones WHERE idEtiqueta = :id AND vence > :ahora ORDER BY creado DESC")
    fun porIdEtiqueta(id: String, ahora: Long): List<VerificacionEntidad>
}

@Dao
interface CorreccionDao {
    @Insert
    fun insertar(c: List<CorreccionEntidad>)

    @Query("SELECT * FROM correcciones WHERE entradaId = :entradaId AND vence > :ahora ORDER BY fecha")
    fun deEntrada(entradaId: Long, ahora: Long): List<CorreccionEntidad>

    @Query("SELECT * FROM correcciones WHERE verificacionId = :id AND vence > :ahora ORDER BY fecha")
    fun deVerificacion(id: Long, ahora: Long): List<CorreccionEntidad>
}

@Dao
interface RetencionDao {
    @Query("SELECT archivoOriginal FROM paginas WHERE vence <= :ahora UNION SELECT archivoProcesado FROM paginas WHERE vence <= :ahora")
    fun archivosVencidos(ahora: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun anotarBorrados(p: List<BorradoPendienteEntidad>)

    @Query("SELECT nombre FROM borrados_pendientes")
    fun pendientes(): List<String>

    @Query("DELETE FROM borrados_pendientes WHERE nombre = :nombre")
    fun quitarPendiente(nombre: String)

    @Query("SELECT archivoOriginal FROM paginas UNION SELECT archivoProcesado FROM paginas")
    fun referenciados(): List<String>

    // El orden importa poco gracias a las claves foráneas en cascada, pero se borran
    // explícitamente todas las tablas para que ningún dato vencido quede huérfano.
    @Query("DELETE FROM correcciones WHERE vence <= :ahora")
    fun borrarCorrecciones(ahora: Long): Int

    @Query("DELETE FROM resultados WHERE vence <= :ahora")
    fun borrarResultados(ahora: Long): Int

    @Query("DELETE FROM entradas WHERE vence <= :ahora")
    fun borrarEntradas(ahora: Long): Int

    @Query("DELETE FROM paginas WHERE vence <= :ahora")
    fun borrarPaginas(ahora: Long): Int

    @Query("DELETE FROM verificaciones WHERE vence <= :ahora")
    fun borrarVerificaciones(ahora: Long): Int

    @Query("DELETE FROM lotes WHERE vence <= :ahora")
    fun borrarLotes(ahora: Long): Int
}
