package com.hemocheck.datos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.hemocheck.captura.EstadoBorradorSerologia
import com.hemocheck.captura.FilaEditable
import com.hemocheck.captura.PaginaCapturada
import com.hemocheck.captura.ResultadoEditable
import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.corroboracion.ConcordanciaBarras
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.core.corroboracion.InformeCorroboracion
import com.hemocheck.core.corroboracion.MotorCorroboracion
import com.hemocheck.core.corroboracion.RegistroAlmacenado
import com.hemocheck.core.corroboracion.ResultadoAlmacenado
import com.hemocheck.core.corroboracion.SolicitudCorroboracion
import com.hemocheck.core.etiqueta.LecturaEtiqueta
import com.hemocheck.core.identificador.Confusiones
import com.hemocheck.core.identificador.IdUnidad
import com.hemocheck.core.retencion.PoliticaRetencion
import com.hemocheck.core.retencion.RelojProtegido
import com.hemocheck.core.seguridad.CifradorAesGcm
import com.hemocheck.datos.db.BaseDatos
import com.hemocheck.datos.db.ClasePagina
import com.hemocheck.datos.db.CorreccionEntidad
import com.hemocheck.datos.db.EntradaEntidad
import com.hemocheck.datos.db.LoteEntidad
import com.hemocheck.datos.db.OrigenDato
import com.hemocheck.datos.db.PaginaEntidad
import com.hemocheck.datos.db.ResultadoEntidad
import com.hemocheck.datos.db.VerificacionEntidad
import com.hemocheck.datos.db.VerificacionEntradaEntidad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.ZoneId

/** Registro de serología encontrado, con su página de origen. */
data class RegistroEncontrado(
    val entrada: EntradaEntidad,
    val resultados: List<ResultadoEntidad>,
    val pagina: PaginaEntidad?,
    val lote: LoteEntidad?,
)

data class DatosComparacion(
    val verificacion: VerificacionEntidad,
    val informe: InformeCorroboracion,
    val paginaEtiqueta: PaginaEntidad?,
    val registros: List<RegistroEncontrado>,
    val correcciones: List<CorreccionEntidad>,
)

data class DetalleEntrada(
    val registro: RegistroEncontrado,
    val correcciones: List<CorreccionEntidad>,
)

/**
 * Acceso a los datos de HemoCheck. Todo lo que se lee filtra los registros vencidos, aunque
 * su borrado físico todavía no se haya completado.
 */
class Repositorio(
    private val bd: BaseDatos,
    private val archivos: ArchivosCifrados,
    private val ajustes: Ajustes,
    private val reloj: RelojProtegido,
    private val zona: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val formatos get() = ajustes.identificadores.formatos()

    fun ahora(): Long = reloj.ahoraMillis()

    fun canonizar(texto: String): String = IdUnidad.canonizar(texto, formatos)

    // ---------------- Registro de serología ----------------

    /** Guarda el lote: imágenes cifradas, filas revisadas, resultados y correcciones. */
    suspend fun guardarSerologia(b: EstadoBorradorSerologia): Long = withContext(Dispatchers.IO) {
        val ahora = ahora()
        val vence = PoliticaRetencion.vencimientoMillis(ahora, zona())
        val escritos = mutableListOf<String>()
        try {
            val guardadas = b.paginas.map { p -> p to escribirPagina(p, escritos) }
            bd.runInTransaction<Long> {
                val dao = bd.serologia()
                val loteId = dao.insertarLote(LoteEntidad(tipo = b.tipo, procedencia = b.procedencia, creado = ahora, vence = vence))
                val paginaIdPorUid = guardadas.mapIndexed { i, (p, a) ->
                    p.uid to dao.insertarPagina(a.entidad(loteId = loteId, verificacionId = null, clase = ClasePagina.SEROLOGIA, numero = i + 1, creado = ahora, vence = vence))
                }.toMap()
                val correcciones = mutableListOf<CorreccionEntidad>()
                for (f in b.filas) {
                    if (f.descartada || f.idTexto.isBlank()) continue
                    val paginaId = paginaIdPorUid[f.paginaUid] ?: continue
                    val (entrada, corr) = construirEntrada(f, loteId, paginaId, b.tipo, ahora, vence)
                    val entradaId = dao.insertarEntrada(entrada)
                    dao.insertarResultados(f.resultados.map { it.entidad(entradaId, vence) })
                    correcciones += corr.map { it.copy(entradaId = entradaId) }
                }
                bd.correcciones().insertar(correcciones)
                loteId
            }
        } catch (e: Exception) {
            escritos.forEach { runCatching { archivos.eliminar(it) } }
            throw e
        }
    }

    private class ArchivosPagina(val original: String, val procesado: String, val clave: ByteArray, val p: PaginaCapturada) {
        fun entidad(loteId: Long?, verificacionId: Long?, clase: ClasePagina, numero: Int, creado: Long, vence: Long) = PaginaEntidad(
            loteId = loteId, verificacionId = verificacionId, clase = clase, numero = numero,
            archivoOriginal = original, archivoProcesado = procesado, claveArchivo = clave,
            rotacionOriginal = p.rotacion, anchoProcesada = p.anchoProcesada, altoProcesada = p.altoProcesada,
            ocrJson = p.ocr?.aJson(), errorOcr = p.errorOcr, avisosCalidad = p.avisos.joinToString(",") { it.name },
            creado = creado, vence = vence,
        )
    }

    private fun escribirPagina(p: PaginaCapturada, escritos: MutableList<String>): ArchivosPagina {
        val clave = CifradorAesGcm.nuevaClave()
        val original = archivos.nuevoNombre()
        archivos.guardar(original, clave, p.jpegOriginal)
        escritos += original
        val procesado = archivos.nuevoNombre()
        archivos.guardar(procesado, clave, p.jpegProcesada)
        escritos += procesado
        return ArchivosPagina(original, procesado, clave, p)
    }

    private fun construirEntrada(
        f: FilaEditable, loteId: Long, paginaId: Long, tipo: com.hemocheck.core.resultado.TipoRegistro, ahora: Long, vence: Long,
    ): Pair<EntradaEntidad, List<CorreccionEntidad>> {
        val canon = canonizar(f.idTexto)
        val canonOcr = f.idOcr?.let { canonizar(it) }
        val origen = when {
            f.origenId == OrigenDato.MANUAL -> OrigenDato.MANUAL
            canonOcr != null && canonOcr != canon -> OrigenDato.CORREGIDO
            else -> OrigenDato.OCR
        }
        val sinDeterminacion = f.resultados.any { it.clave == AnalizadorHoja.CLAVE_DESCONOCIDA }
        val estado = when {
            f.confirmada && !sinDeterminacion -> EstadoEntrada.CONFIRMADA
            // Sin revisión fila por fila: se usa la lectura y se verifica visualmente en el collage
            else -> EstadoEntrada.AUTOMATICA
        }
        val corr = mutableListOf<CorreccionEntidad>()
        fun c(campo: String, antes: String?, despues: String?) {
            corr += CorreccionEntidad(entradaId = null, verificacionId = null, campo = campo, valorAnterior = antes, valorNuevo = despues, fecha = ahora, vence = vence)
        }
        if (origen == OrigenDato.MANUAL) c(CAMPO_FILA_MANUAL, null, canon)
        if (origen == OrigenDato.CORREGIDO) c(CAMPO_ID, canonOcr, canon)
        corr += correccionesResultados(f.resultados, f.resultadosQuitados, ahora, vence)
        return EntradaEntidad(
            loteId = loteId, paginaId = paginaId, tipo = tipo, idOcr = f.idOcr, idNormalizado = canon,
            claveConfusion = Confusiones.clave(canon), origenId = origen, cajaId = f.cajaId, cajaFila = f.cajaFila,
            cajaEncabezados = f.cajaEncabezados, fecha = f.fecha, validacion = f.validacion, estado = estado,
            problemas = f.problemas.joinToString(",") { it.name }, creado = ahora, vence = vence,
            revisadoEn = if (estado == EstadoEntrada.CONFIRMADA) ahora else null,
        ) to corr
    }

    private fun correccionesResultados(
        resultados: List<ResultadoEditable>, quitados: List<ResultadoEditable>, ahora: Long, vence: Long,
    ): List<CorreccionEntidad> {
        val corr = mutableListOf<CorreccionEntidad>()
        fun c(campo: String, antes: String?, despues: String?) {
            corr += CorreccionEntidad(entradaId = null, verificacionId = null, campo = campo, valorAnterior = antes, valorNuevo = despues, fecha = ahora, vence = vence)
        }
        for (r in resultados) {
            when {
                r.origen == OrigenDato.MANUAL && r.textoOcr == null -> c("$CAMPO_RESULTADO ${r.nombre}", null, "${r.texto} (${r.categoria.name})")
                r.textoOcr != null && (r.texto != r.textoOcr || r.origen == OrigenDato.CORREGIDO) ->
                    c("$CAMPO_RESULTADO ${r.nombre}", r.textoOcr, "${r.texto} (${r.categoria.name})")
            }
        }
        for (q in quitados) c("$CAMPO_RESULTADO_QUITADO ${q.nombre}", q.textoOcr ?: q.texto, null)
        return corr
    }

    private fun ResultadoEditable.entidad(entradaId: Long, vence: Long) = ResultadoEntidad(
        entradaId = entradaId, clave = clave, nombre = nombre, texto = texto, textoOcr = textoOcr,
        categoria = categoria, origen = origen, caja = caja, cajaEncabezado = cajaEncabezado, confianza = confianza, vence = vence,
    )

    // ---------------- Búsqueda y corroboración ----------------

    suspend fun buscarExacto(idCanonico: String): List<RegistroEncontrado> = withContext(Dispatchers.IO) {
        val ahora = ahora()
        bd.serologia().entradasPorId(idCanonico, ahora).map { completar(it, ahora) }
    }

    private fun completar(e: EntradaEntidad, ahora: Long): RegistroEncontrado {
        val dao = bd.serologia()
        return RegistroEncontrado(e, dao.resultados(e.id, ahora), dao.pagina(e.paginaId, ahora), dao.lote(e.loteId, ahora))
    }

    private fun RegistroEncontrado.aAlmacenado() = RegistroAlmacenado(
        entradaId = entrada.id, tipo = entrada.tipo, idNormalizado = entrada.idNormalizado, estado = entrada.estado,
        resultados = resultados.map { ResultadoAlmacenado(it.clave, it.texto, it.categoria) },
        tieneUbicacionId = entrada.cajaId != null,
        imagenDisponible = pagina != null && archivos.existe(pagina.archivoProcesado),
    )

    /**
     * Busca el identificador confirmado, genera el informe y guarda la corroboración con la
     * foto de la etiqueta cifrada. [idConfirmado] null = etiqueta ilegible.
     */
    suspend fun crearVerificacion(
        etiqueta: PaginaCapturada?,
        lectura: LecturaEtiqueta?,
        idConfirmado: String?,
    ): Long = withContext(Dispatchers.IO) {
        val ahora = ahora()
        val vence = PoliticaRetencion.vencimientoMillis(ahora, zona())
        val canon = idConfirmado?.takeIf { it.isNotBlank() }?.let { canonizar(it) }
        val propuesto = lectura?.propuesto
        val origen = when {
            canon == null -> null
            propuesto == null -> OrigenDato.MANUAL
            propuesto == canon && lectura?.candidatos?.none { it.normalizado == canon } == true && lectura.idCodigoBarras == canon -> OrigenDato.CODIGO_BARRAS
            propuesto == canon -> OrigenDato.OCR
            else -> OrigenDato.CORREGIDO
        }
        val cajaId = lectura?.candidatos?.firstOrNull { it.normalizado == canon || it.sugerido == canon }?.caja
        val exactos = canon?.let { bd.serologia().entradasPorId(it, ahora).map { e -> completar(e, ahora) } }.orEmpty()
        val similares = canon?.let { bd.serologia().entradasSimilares(Confusiones.clave(it), it, ahora).map { e -> completar(e, ahora) } }.orEmpty()
        val barras = lectura?.idCodigoBarras
        val solicitud = SolicitudCorroboracion(
            idEtiqueta = canon,
            errorOcrEtiqueta = etiqueta?.errorOcr == true,
            idCorregidoManualmente = origen == OrigenDato.CORREGIDO || origen == OrigenDato.MANUAL,
            concordanciaBarras = when {
                barras == null || canon == null -> ConcordanciaBarras.SIN_CODIGO
                barras == canon -> ConcordanciaBarras.CONCORDANTE
                else -> ConcordanciaBarras.DISCORDANTE
            },
            ubicacionIdEtiqueta = cajaId != null,
        )
        val informe = MotorCorroboracion(ajustes.todasEsperadas())
            .corroborar(solicitud, exactos.map { it.aAlmacenado() }, similares.map { it.aAlmacenado() })

        val escritos = mutableListOf<String>()
        try {
            val archivosEtiqueta = etiqueta?.let { escribirPagina(it, escritos) }
            bd.runInTransaction<Long> {
                val vid = bd.verificaciones().insertar(
                    VerificacionEntidad(
                        idEtiqueta = canon, idOcr = propuesto ?: lectura?.candidatos?.firstOrNull()?.textoCrudo,
                        origenId = origen, idCodigoBarras = barras, cajaIdEtiqueta = cajaId,
                        informeJson = json.encodeToString(InformeCorroboracion.serializer(), informe),
                        estados = informe.estados.joinToString(",") { it.name }, creado = ahora, vence = vence, revisadaEn = null,
                    )
                )
                archivosEtiqueta?.let {
                    bd.serologia().insertarPagina(it.entidad(null, vid, ClasePagina.ETIQUETA, 1, ahora, vence))
                }
                bd.verificaciones().vincular(exactos.map { VerificacionEntradaEntidad(vid, it.entrada.id) })
                if (origen == OrigenDato.CORREGIDO) {
                    bd.correcciones().insertar(listOf(CorreccionEntidad(entradaId = null, verificacionId = vid, campo = CAMPO_ID, valorAnterior = propuesto, valorNuevo = canon, fecha = ahora, vence = vence)))
                }
                vid
            }
        } catch (e: Exception) {
            escritos.forEach { runCatching { archivos.eliminar(it) } }
            throw e
        }
    }

    suspend fun comparacion(verificacionId: Long): DatosComparacion? = withContext(Dispatchers.IO) {
        val ahora = ahora()
        val v = bd.verificaciones().verificacion(verificacionId, ahora) ?: return@withContext null
        val informe = json.decodeFromString(InformeCorroboracion.serializer(), v.informeJson)
        val ids = bd.verificaciones().entradasVinculadas(verificacionId)
        val registros = bd.serologia().entradas(ids, ahora).map { completar(it, ahora) }
        DatosComparacion(v, informe, bd.serologia().paginaDeVerificacion(verificacionId, ahora), registros, bd.correcciones().deVerificacion(verificacionId, ahora))
    }

    suspend fun marcarRevisada(verificacionId: Long) = withContext(Dispatchers.IO) {
        bd.verificaciones().marcarRevisada(verificacionId, ahora())
    }

    // ---------------- Historial ----------------

    fun observarVerificaciones(): Flow<List<VerificacionEntidad>> = bd.verificaciones().observar(ahora())

    fun observarEntradas(soloPendientes: Boolean): Flow<List<EntradaEntidad>> = bd.serologia().observarEntradas(ahora(), soloPendientes)

    suspend fun verificacionesPorId(texto: String): List<VerificacionEntidad> = withContext(Dispatchers.IO) {
        bd.verificaciones().porIdEtiqueta(canonizar(texto), ahora())
    }

    suspend fun detalleEntrada(id: Long): DetalleEntrada? = withContext(Dispatchers.IO) {
        val ahora = ahora()
        val e = bd.serologia().entrada(id, ahora) ?: return@withContext null
        DetalleEntrada(completar(e, ahora), bd.correcciones().deEntrada(id, ahora))
    }

    /** Revisión posterior de una fila guardada (p. ej. pendiente de revisión). */
    suspend fun actualizarEntrada(
        id: Long, nuevoId: String, resultados: List<ResultadoEditable>, quitados: List<ResultadoEditable>, confirmar: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val ahora = ahora()
        val dao = bd.serologia()
        val e = dao.entrada(id, ahora) ?: return@withContext false
        val canon = canonizar(nuevoId)
        val sinDeterminacion = resultados.any { it.clave == AnalizadorHoja.CLAVE_DESCONOCIDA }
        val estado = if (confirmar && !sinDeterminacion) EstadoEntrada.CONFIRMADA else EstadoEntrada.PENDIENTE_REVISION
        bd.runInTransaction(Runnable {
            val corr = mutableListOf<CorreccionEntidad>()
            if (canon != e.idNormalizado) {
                corr += CorreccionEntidad(entradaId = id, verificacionId = null, campo = CAMPO_ID, valorAnterior = e.idNormalizado, valorNuevo = canon, fecha = ahora, vence = e.vence)
            }
            if (estado != e.estado) {
                corr += CorreccionEntidad(entradaId = id, verificacionId = null, campo = CAMPO_ESTADO, valorAnterior = e.estado.name, valorNuevo = estado.name, fecha = ahora, vence = e.vence)
            }
            corr += correccionesResultados(resultados, quitados, ahora, e.vence).map { it.copy(entradaId = id) }
            dao.actualizarEntrada(
                e.copy(
                    idNormalizado = canon, claveConfusion = Confusiones.clave(canon),
                    origenId = if (canon != e.idNormalizado && e.origenId == OrigenDato.OCR) OrigenDato.CORREGIDO else e.origenId,
                    estado = estado, revisadoEn = ahora,
                )
            )
            dao.borrarResultados(id)
            // El vencimiento no cambia: se conserva el de la fila original
            dao.insertarResultados(resultados.map { it.entidad(id, e.vence) })
            bd.correcciones().insertar(corr)
        })
        true
    }

    // ---------------- Imágenes ----------------

    /**
     * Descifra una imagen en memoria. [procesada] = la imagen corregida usada por el OCR;
     * si no, la foto original, rotada para verla derecha. Null si la página venció o falta.
     */
    suspend fun imagen(pagina: PaginaEntidad, procesada: Boolean, ladoMax: Int = 4096): Bitmap? = withContext(Dispatchers.IO) {
        if (PoliticaRetencion.estaVencido(pagina.vence, ahora())) return@withContext null
        val nombre = if (procesada) pagina.archivoProcesado else pagina.archivoOriginal
        val bytes = runCatching { archivos.leer(nombre, pagina.claveArchivo) }.getOrNull() ?: return@withContext null
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
        var muestra = 1
        while (maxOf(opciones.outWidth, opciones.outHeight) / muestra > ladoMax) muestra *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = muestra })
            ?: return@withContext null
        bytes.fill(0)
        if (procesada || pagina.rotacionOriginal == 0) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(pagina.rotacionOriginal.toFloat()) }, true)
            .also { if (it !== bmp) bmp.recycle() }
    }

    // ---------------- Administración ----------------

    fun observarConteo(estado: EstadoEntrada): Flow<Int> = bd.serologia().contar(ahora(), estado)

    fun bytesImagenes(): Long = archivos.tamanoTotal()

    /** Borra todos los registros e imágenes (acción manual del usuario desde Configuración). */
    suspend fun borrarTodo() = withContext(Dispatchers.IO) {
        bd.clearAllTables()
        archivos.eliminarTodo()
        bd.consolidar()
    }

    companion object {
        const val CAMPO_ID = "Identificador"
        const val CAMPO_ESTADO = "Estado"
        const val CAMPO_RESULTADO = "Resultado"
        const val CAMPO_RESULTADO_QUITADO = "Resultado quitado"
        const val CAMPO_FILA_MANUAL = "Fila agregada manualmente"
    }
}
