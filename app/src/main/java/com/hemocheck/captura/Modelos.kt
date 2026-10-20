package com.hemocheck.captura

import com.hemocheck.core.analisis.EntradaPropuesta
import com.hemocheck.core.analisis.HojaAnalizada
import com.hemocheck.core.analisis.Problema
import com.hemocheck.core.analisis.ResultadoPropuesto
import com.hemocheck.core.etiqueta.LecturaEtiqueta
import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.imagen.AvisoCalidad
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.datos.db.OrigenDato
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class ModoCaptura { HOJA, ETIQUETA }

/** Foto tomada y procesada, todavía sin guardar (solo en memoria). */
class PaginaCapturada(
    val uid: String = UUID.randomUUID().toString(),
    val modo: ModoCaptura,
    /** JPEG tal como lo entregó la cámara (evidencia original). */
    val jpegOriginal: ByteArray,
    val rotacion: Int,
    /** JPEG corregido (perspectiva, rotación, contraste) sobre el que se hizo el OCR. */
    val jpegProcesada: ByteArray,
    val anchoProcesada: Int,
    val altoProcesada: Int,
    val ocr: PaginaOcr?,
    val errorOcr: Boolean,
    val codigosBarras: List<String>,
    val avisos: Set<AvisoCalidad>,
    val bordesDetectados: Boolean,
)

data class ResultadoEditable(
    val uid: String = UUID.randomUUID().toString(),
    val clave: String,
    val nombre: String,
    val texto: String,
    val textoOcr: String?,
    val categoria: CategoriaResultado,
    val origen: OrigenDato,
    val caja: Caja? = null,
    val cajaEncabezado: Caja? = null,
    val confianza: Float? = null,
)

/** Fila propuesta por el OCR (o agregada a mano) que el usuario revisa antes de guardar. */
data class FilaEditable(
    val uid: String = UUID.randomUUID().toString(),
    val paginaUid: String,
    val idTexto: String,
    val idOcr: String?,
    val sugerido: String?,
    val origenId: OrigenDato,
    val cajaId: Caja?,
    val cajaFila: Caja?,
    val cajaEncabezados: Caja?,
    val fecha: String?,
    val validacion: String?,
    val problemas: Set<Problema>,
    val resultados: List<ResultadoEditable>,
    /** Resultados leídos por OCR que el usuario quitó (se registran como corrección). */
    val resultadosQuitados: List<ResultadoEditable> = emptyList(),
    /** El usuario confirmó que la fila y sus resultados corresponden a ese identificador. */
    val confirmada: Boolean = false,
    val descartada: Boolean = false,
) {
    val tieneBloqueantes: Boolean get() = problemas.any { it.bloqueante }

    companion object {
        fun desde(paginaUid: String, e: EntradaPropuesta, hoja: HojaAnalizada): FilaEditable = FilaEditable(
            paginaUid = paginaUid,
            idTexto = e.ocurrencia.normalizado,
            idOcr = e.ocurrencia.textoCrudo,
            sugerido = e.ocurrencia.sugerido,
            origenId = OrigenDato.OCR,
            cajaId = e.ocurrencia.caja,
            cajaFila = e.cajaFila,
            cajaEncabezados = e.cajaEncabezados,
            fecha = e.fecha ?: hoja.fechaPagina,
            validacion = e.validacion ?: hoja.validacionPagina.firstOrNull(),
            problemas = e.problemas,
            resultados = e.resultados.map { it.aEditable() },
        )

        fun ResultadoPropuesto.aEditable() = ResultadoEditable(
            clave = clave, nombre = nombreDeterminacion, texto = textoCrudo, textoOcr = textoCrudo,
            categoria = categoria, origen = OrigenDato.OCR, caja = caja, cajaEncabezado = cajaEncabezado,
            confianza = confianza,
        )
    }
}

data class EstadoBorradorSerologia(
    val tipo: TipoRegistro = TipoRegistro.CONVENCIONAL,
    val procedencia: String = "",
    val paginas: List<PaginaCapturada> = emptyList(),
    val analisis: Map<String, HojaAnalizada> = emptyMap(),
    val filas: List<FilaEditable> = emptyList(),
)

data class EstadoBorradorEtiqueta(
    val pagina: PaginaCapturada? = null,
    val lectura: LecturaEtiqueta? = null,
)

/**
 * Capturas en curso, solo en memoria. Se descartan al bloquear la app o al terminar el flujo.
 */
class Borradores {
    private val _serologia = MutableStateFlow(EstadoBorradorSerologia())
    val serologia: StateFlow<EstadoBorradorSerologia> = _serologia.asStateFlow()

    private val _etiqueta = MutableStateFlow(EstadoBorradorEtiqueta())
    val etiqueta: StateFlow<EstadoBorradorEtiqueta> = _etiqueta.asStateFlow()

    /** Último lote guardado (para ofrecer continuar con las hojas del otro tipo). */
    private val _ultimoLote = MutableStateFlow<Pair<Long, TipoRegistro>?>(null)
    val ultimoLote: StateFlow<Pair<Long, TipoRegistro>?> = _ultimoLote.asStateFlow()

    fun iniciarSerologia(tipo: TipoRegistro, procedencia: String) {
        _serologia.value = EstadoBorradorSerologia(tipo, procedencia.trim())
    }

    fun fijarTipo(tipo: TipoRegistro) = _serologia.update { if (it.paginas.isEmpty()) it.copy(tipo = tipo) else it }

    fun fijarProcedencia(texto: String) = _serologia.update { it.copy(procedencia = texto) }

    fun loteGuardado(id: Long, tipo: TipoRegistro) {
        _serologia.value = EstadoBorradorSerologia()
        _ultimoLote.value = id to tipo
    }

    fun olvidarUltimoLote() {
        _ultimoLote.value = null
    }

    fun agregarPagina(p: PaginaCapturada, hoja: HojaAnalizada?) = _serologia.update { s ->
        s.copy(
            paginas = s.paginas + p,
            analisis = if (hoja != null) s.analisis + (p.uid to hoja) else s.analisis,
            filas = s.filas + hoja?.entradas.orEmpty().map { FilaEditable.desde(p.uid, it, hoja!!) },
        )
    }

    fun quitarPagina(uid: String) = _serologia.update { s ->
        s.copy(paginas = s.paginas.filter { it.uid != uid }, analisis = s.analisis - uid, filas = s.filas.filter { it.paginaUid != uid })
    }

    fun actualizarFila(fila: FilaEditable) = _serologia.update { s ->
        s.copy(filas = s.filas.map { if (it.uid == fila.uid) fila else it })
    }

    fun agregarFila(fila: FilaEditable) = _serologia.update { s -> s.copy(filas = s.filas + fila) }

    fun fijarEtiqueta(p: PaginaCapturada, lectura: LecturaEtiqueta) {
        _etiqueta.value = EstadoBorradorEtiqueta(p, lectura)
    }

    fun descartarSerologia() {
        _serologia.value = EstadoBorradorSerologia()
    }

    fun descartarEtiqueta() {
        _etiqueta.value = EstadoBorradorEtiqueta()
    }

    fun descartarTodo() {
        descartarSerologia()
        descartarEtiqueta()
        _ultimoLote.value = null
    }
}
