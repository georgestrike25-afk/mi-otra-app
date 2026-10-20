package com.hemocheck.core.corroboracion

import com.hemocheck.core.resultado.CatalogoDeterminaciones
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import kotlinx.serialization.Serializable

/**
 * Estados de la corroboración documental. Son independientes entre sí y se muestran por
 * separado; ninguno equivale a «apto», «liberado» ni «seguro».
 */
@Serializable
enum class EstadoCorroboracion {
    COINCIDENCIA_EXACTA,
    REGISTRO_NO_ENCONTRADO,
    IDENTIFICADOR_ILEGIBLE,
    PENDIENTE_REVISION,
    REACTIVO_DOCUMENTADO,
    NO_REACTIVO_DOCUMENTADO,
    INDETERMINADO_DOCUMENTADO,
    PENDIENTE_DOCUMENTADO,
    RESULTADOS_CONTRADICTORIOS,
    DUPLICADO_AMBIGUO,
    ERROR_OCR,
    REGISTRO_INCOMPLETO,
    NO_VERIFICABLE,
}

@Serializable
enum class EstadoEntrada { CONFIRMADA, PENDIENTE_REVISION }

/** Registro de serología guardado, con lo necesario para corroborar. */
data class RegistroAlmacenado(
    val entradaId: Long,
    val tipo: TipoRegistro,
    val idNormalizado: String,
    val estado: EstadoEntrada,
    val resultados: List<ResultadoAlmacenado>,
    /** Se conoce la ubicación del identificador en la foto (permite el recorte). */
    val tieneUbicacionId: Boolean,
    /** La foto de origen existe y está disponible. */
    val imagenDisponible: Boolean,
)

data class ResultadoAlmacenado(
    val clave: String,
    val textoCrudo: String,
    val categoria: CategoriaResultado,
)

enum class ConcordanciaBarras { CONCORDANTE, DISCORDANTE, SIN_CODIGO }

data class SolicitudCorroboracion(
    /** Identificador confirmado por el usuario, ya canonizado. Null si no pudo leerse. */
    val idEtiqueta: String?,
    val errorOcrEtiqueta: Boolean = false,
    /** El usuario modificó el identificador propuesto por el OCR. */
    val idCorregidoManualmente: Boolean = false,
    val concordanciaBarras: ConcordanciaBarras = ConcordanciaBarras.SIN_CODIGO,
    val ubicacionIdEtiqueta: Boolean = true,
)

@Serializable
enum class TipoAviso {
    SIN_REGISTRO_TIPO,
    SOLO_PENDIENTES,
    PENDIENTES_ADEMAS,
    DUPLICADOS,
    CONTRADICCION,
    FALTA_DETERMINACION,
    NO_INTERPRETADO,
    SIN_UBICACION,
    SIN_IMAGEN,
    ID_SIMILAR,
    BARRAS_DISCORDANTE,
    ID_CORREGIDO,
    ETIQUETA_ILEGIBLE,
    ETIQUETA_ERROR_OCR,
    ETIQUETA_SIN_UBICACION,
}

/** Aviso concreto con sus datos (el texto lo arma la interfaz). */
@Serializable
data class AvisoCorroboracion(
    val estado: EstadoCorroboracion,
    val tipo: TipoAviso,
    val tipoRegistro: TipoRegistro? = null,
    val detalle: String? = null,
)

@Serializable
data class FilaDeterminacion(
    val tipoRegistro: TipoRegistro,
    val clave: String,
    val nombre: String,
    /** Textos documentados (uno por registro confirmado que la informa). */
    val textos: List<String>,
    val categorias: List<CategoriaResultado>,
    val contradictoria: Boolean,
    val ausente: Boolean,
)

@Serializable
data class SeccionInforme(
    val tipo: TipoRegistro,
    val confirmadas: List<Long>,
    val pendientes: List<Long>,
    val estados: List<EstadoCorroboracion>,
)

@Serializable
data class InformeCorroboracion(
    val idEtiqueta: String?,
    val estados: List<EstadoCorroboracion>,
    val secciones: List<SeccionInforme>,
    val determinaciones: List<FilaDeterminacion>,
    val avisos: List<AvisoCorroboracion>,
)

/**
 * Compara el identificador confirmado de la etiqueta con los registros guardados.
 * Solo usa coincidencias EXACTAS del identificador canonizado; los identificadores
 * similares se informan como posible error de OCR, nunca como coincidencia.
 */
class MotorCorroboracion(
    private val esperadas: Map<TipoRegistro, List<String>> = CatalogoDeterminaciones.esperadasPorOmision,
) {
    fun corroborar(
        solicitud: SolicitudCorroboracion,
        exactos: List<RegistroAlmacenado>,
        similares: List<RegistroAlmacenado> = emptyList(),
    ): InformeCorroboracion {
        val avisos = mutableListOf<AvisoCorroboracion>()
        val id = solicitud.idEtiqueta
        if (id.isNullOrBlank()) {
            avisos += AvisoCorroboracion(EstadoCorroboracion.IDENTIFICADOR_ILEGIBLE, TipoAviso.ETIQUETA_ILEGIBLE)
            if (solicitud.errorOcrEtiqueta) avisos += AvisoCorroboracion(EstadoCorroboracion.ERROR_OCR, TipoAviso.ETIQUETA_ERROR_OCR)
            avisos += AvisoCorroboracion(EstadoCorroboracion.NO_VERIFICABLE, TipoAviso.ETIQUETA_ILEGIBLE)
            return InformeCorroboracion(null, ordenar(avisos.map { it.estado }), emptyList(), emptyList(), avisos)
        }
        // Defensa: solo se aceptan registros cuyo identificador es idéntico
        val coincidentes = exactos.filter { it.idNormalizado == id }

        if (solicitud.errorOcrEtiqueta) avisos += AvisoCorroboracion(EstadoCorroboracion.ERROR_OCR, TipoAviso.ETIQUETA_ERROR_OCR)
        if (solicitud.concordanciaBarras == ConcordanciaBarras.DISCORDANTE) {
            avisos += AvisoCorroboracion(EstadoCorroboracion.ERROR_OCR, TipoAviso.BARRAS_DISCORDANTE)
        }
        if (solicitud.idCorregidoManualmente) {
            avisos += AvisoCorroboracion(EstadoCorroboracion.PENDIENTE_REVISION, TipoAviso.ID_CORREGIDO)
        }
        if (!solicitud.ubicacionIdEtiqueta) {
            avisos += AvisoCorroboracion(EstadoCorroboracion.NO_VERIFICABLE, TipoAviso.ETIQUETA_SIN_UBICACION)
        }
        similares.filter { it.idNormalizado != id }.map { it.idNormalizado }.distinct().forEach {
            avisos += AvisoCorroboracion(EstadoCorroboracion.ERROR_OCR, TipoAviso.ID_SIMILAR, detalle = it)
        }

        val secciones = mutableListOf<SeccionInforme>()
        val filas = mutableListOf<FilaDeterminacion>()
        for (tipo in TipoRegistro.entries) {
            val delTipo = coincidentes.filter { it.tipo == tipo }
            val confirmadas = delTipo.filter { it.estado == EstadoEntrada.CONFIRMADA }
            val pendientes = delTipo.filter { it.estado == EstadoEntrada.PENDIENTE_REVISION }
            val estadosSeccion = mutableListOf<EstadoCorroboracion>()
            fun aviso(e: EstadoCorroboracion, t: TipoAviso, detalle: String? = null) {
                avisos += AvisoCorroboracion(e, t, tipo, detalle)
                estadosSeccion += e
            }

            when {
                confirmadas.isEmpty() && pendientes.isEmpty() ->
                    aviso(EstadoCorroboracion.REGISTRO_NO_ENCONTRADO, TipoAviso.SIN_REGISTRO_TIPO)
                confirmadas.isEmpty() -> {
                    aviso(EstadoCorroboracion.REGISTRO_NO_ENCONTRADO, TipoAviso.SIN_REGISTRO_TIPO)
                    aviso(EstadoCorroboracion.PENDIENTE_REVISION, TipoAviso.SOLO_PENDIENTES, pendientes.size.toString())
                }
                else -> {
                    estadosSeccion += EstadoCorroboracion.COINCIDENCIA_EXACTA
                    if (confirmadas.size > 1) {
                        aviso(EstadoCorroboracion.DUPLICADO_AMBIGUO, TipoAviso.DUPLICADOS, confirmadas.size.toString())
                    }
                    if (pendientes.isNotEmpty()) {
                        aviso(EstadoCorroboracion.PENDIENTE_REVISION, TipoAviso.PENDIENTES_ADEMAS, pendientes.size.toString())
                    }
                }
            }
            for (r in confirmadas) {
                if (!r.imagenDisponible) aviso(EstadoCorroboracion.NO_VERIFICABLE, TipoAviso.SIN_IMAGEN)
                else if (!r.tieneUbicacionId) aviso(EstadoCorroboracion.NO_VERIFICABLE, TipoAviso.SIN_UBICACION)
            }

            // Resultados documentados por determinación (solo de registros confirmados)
            if (confirmadas.isNotEmpty()) {
                val catalogo = CatalogoDeterminaciones.de(tipo)
                val todos = confirmadas.flatMap { it.resultados }
                val porClave = todos.groupBy { it.clave }
                val cubiertas = porClave.keys.flatMap { k -> catalogo.firstOrNull { it.clave == k }?.cubre ?: setOf(k) }.toSet()
                val claves = (porClave.keys + esperadas[tipo].orEmpty()).distinct()
                for (clave in claves) {
                    val lista = porClave[clave].orEmpty()
                    val nombre = catalogo.firstOrNull { it.clave == clave }?.nombre ?: clave
                    if (lista.isEmpty()) {
                        if (clave !in cubiertas) {
                            filas += FilaDeterminacion(tipo, clave, nombre, emptyList(), emptyList(), contradictoria = false, ausente = true)
                            aviso(EstadoCorroboracion.REGISTRO_INCOMPLETO, TipoAviso.FALTA_DETERMINACION, nombre)
                        }
                        continue
                    }
                    val categorias = lista.map { it.categoria }
                    val interpretables = categorias.filter { it != CategoriaResultado.NO_INTERPRETADO }.distinct()
                    val contradictoria = interpretables.size > 1
                    filas += FilaDeterminacion(tipo, clave, nombre, lista.map { it.textoCrudo }, categorias, contradictoria, ausente = false)
                    if (contradictoria) aviso(EstadoCorroboracion.RESULTADOS_CONTRADICTORIOS, TipoAviso.CONTRADICCION, nombre)
                    for (c in categorias.distinct()) {
                        when (c) {
                            CategoriaResultado.REACTIVO -> estadosSeccion += EstadoCorroboracion.REACTIVO_DOCUMENTADO
                            CategoriaResultado.NO_REACTIVO -> estadosSeccion += EstadoCorroboracion.NO_REACTIVO_DOCUMENTADO
                            CategoriaResultado.INDETERMINADO -> estadosSeccion += EstadoCorroboracion.INDETERMINADO_DOCUMENTADO
                            CategoriaResultado.PENDIENTE -> estadosSeccion += EstadoCorroboracion.PENDIENTE_DOCUMENTADO
                            CategoriaResultado.NO_INTERPRETADO ->
                                aviso(EstadoCorroboracion.NO_VERIFICABLE, TipoAviso.NO_INTERPRETADO, nombre)
                        }
                    }
                }
            }
            secciones += SeccionInforme(tipo, confirmadas.map { it.entradaId }, pendientes.map { it.entradaId }, ordenar(estadosSeccion))
        }

        val estados = ordenar(secciones.flatMap { it.estados } + avisos.map { it.estado })
        return InformeCorroboracion(id, estados, secciones, filas, avisos)
    }

    private fun ordenar(estados: List<EstadoCorroboracion>) = estados.distinct().sortedBy { it.ordinal }
}
