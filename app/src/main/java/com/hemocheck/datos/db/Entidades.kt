package com.hemocheck.datos.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import kotlinx.serialization.json.Json

/** Cómo se obtuvo un dato: leído por OCR, OCR corregido por el usuario, o escrito a mano. */
enum class OrigenDato { OCR, CORREGIDO, MANUAL, CODIGO_BARRAS }

enum class ClasePagina { SEROLOGIA, ETIQUETA }

/**
 * Todas las tablas tienen `vence`: el instante (fijado al crear) a partir del cual el dato
 * deja de mostrarse y se elimina. Las consultas filtran siempre por `vence > ahora`.
 */
@Entity(tableName = "lotes", indices = [Index("vence")])
data class LoteEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: TipoRegistro,
    /** Procedencia indicada por el usuario (carpeta, servicio, etc.). */
    val procedencia: String,
    val creado: Long,
    val vence: Long,
)

@Entity(
    tableName = "paginas",
    foreignKeys = [
        ForeignKey(LoteEntidad::class, ["id"], ["loteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(VerificacionEntidad::class, ["id"], ["verificacionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("loteId"), Index("verificacionId"), Index("vence")],
)
data class PaginaEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loteId: Long?,
    val verificacionId: Long?,
    val clase: ClasePagina,
    val numero: Int,
    /** Foto original tal como salió de la cámara (cifrada). */
    val archivoOriginal: String,
    /** Imagen corregida para OCR (cifrada); las coordenadas del OCR se refieren a ella. */
    val archivoProcesado: String,
    /** Clave AES-256 propia de esta página. Al borrarse la fila, los archivos quedan ilegibles. */
    val claveArchivo: ByteArray,
    /** Grados que hay que rotar la foto original para verla derecha. */
    val rotacionOriginal: Int,
    val anchoProcesada: Int,
    val altoProcesada: Int,
    val ocrJson: String?,
    val errorOcr: Boolean,
    /** Avisos de calidad separados por comas. */
    val avisosCalidad: String,
    val creado: Long,
    val vence: Long,
) {
    override fun equals(other: Any?) = other is PaginaEntidad && other.id == id && other.archivoOriginal == archivoOriginal
    override fun hashCode() = archivoOriginal.hashCode()
}

@Entity(
    tableName = "entradas",
    foreignKeys = [
        ForeignKey(LoteEntidad::class, ["id"], ["loteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PaginaEntidad::class, ["id"], ["paginaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("loteId"), Index("paginaId"), Index("idNormalizado"), Index("claveConfusion"), Index("vence")],
)
data class EntradaEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loteId: Long,
    val paginaId: Long,
    val tipo: TipoRegistro,
    /** Lo que leyó el OCR (null si la fila se agregó a mano). */
    val idOcr: String?,
    /** Identificador canonizado: es el único que se usa para la búsqueda exacta. */
    val idNormalizado: String,
    val claveConfusion: String,
    val origenId: OrigenDato,
    val cajaId: Caja?,
    val cajaFila: Caja?,
    val cajaEncabezados: Caja?,
    val fecha: String?,
    val validacion: String?,
    val estado: EstadoEntrada,
    /** Problemas detectados en el análisis (separados por comas), para mostrar en el historial. */
    val problemas: String,
    val creado: Long,
    val vence: Long,
    val revisadoEn: Long?,
)

@Entity(
    tableName = "resultados",
    foreignKeys = [ForeignKey(EntradaEntidad::class, ["id"], ["entradaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entradaId"), Index("vence")],
)
data class ResultadoEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entradaId: Long,
    val clave: String,
    val nombre: String,
    /** Texto documentado (confirmado o corregido por el usuario). */
    val texto: String,
    /** Texto leído por OCR, para comparar con la corrección. */
    val textoOcr: String?,
    val categoria: CategoriaResultado,
    val origen: OrigenDato,
    val caja: Caja?,
    val cajaEncabezado: Caja?,
    val confianza: Float?,
    val vence: Long,
)

@Entity(
    tableName = "verificaciones",
    indices = [Index("idEtiqueta"), Index("vence")],
)
data class VerificacionEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identificador confirmado por el usuario (null si la etiqueta fue ilegible). */
    val idEtiqueta: String?,
    val idOcr: String?,
    val origenId: OrigenDato?,
    val idCodigoBarras: String?,
    val cajaIdEtiqueta: Caja?,
    /** Informe de corroboración (JSON) tal como se generó en ese momento. */
    val informeJson: String,
    /** Estados separados por comas (para listar sin leer el informe). */
    val estados: String,
    val creado: Long,
    val vence: Long,
    /** Momento en que el usuario registró haber revisado visualmente la comparación. */
    val revisadaEn: Long?,
)

/** Registros de serología mostrados en una corroboración. */
@Entity(
    tableName = "verificacion_entradas",
    primaryKeys = ["verificacionId", "entradaId"],
    foreignKeys = [
        ForeignKey(VerificacionEntidad::class, ["id"], ["verificacionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(EntradaEntidad::class, ["id"], ["entradaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("entradaId")],
)
data class VerificacionEntradaEntidad(val verificacionId: Long, val entradaId: Long)

@Entity(
    tableName = "correcciones",
    foreignKeys = [
        ForeignKey(EntradaEntidad::class, ["id"], ["entradaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(VerificacionEntidad::class, ["id"], ["verificacionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("entradaId"), Index("verificacionId"), Index("vence")],
)
data class CorreccionEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entradaId: Long?,
    val verificacionId: Long?,
    val campo: String,
    val valorAnterior: String?,
    val valorNuevo: String?,
    val fecha: Long,
    val vence: Long,
)

/** Archivos cuyo registro ya se eliminó y que falta borrar del almacenamiento. */
@Entity(tableName = "borrados_pendientes")
data class BorradoPendienteEntidad(@PrimaryKey val nombre: String)

class Convertidores {
    private val json = Json

    @TypeConverter
    fun cajaATexto(c: Caja?): String? = c?.let { json.encodeToString(Caja.serializer(), it) }

    @TypeConverter
    fun textoACaja(s: String?): Caja? = s?.let { json.decodeFromString(Caja.serializer(), it) }
}
