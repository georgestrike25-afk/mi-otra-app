package com.hemocheck.ui.comun

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.hemocheck.R
import com.hemocheck.core.analisis.AvisoPagina
import com.hemocheck.core.analisis.Problema
import com.hemocheck.core.corroboracion.AvisoCorroboracion
import com.hemocheck.core.corroboracion.EstadoCorroboracion
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.core.corroboracion.TipoAviso
import com.hemocheck.core.etiqueta.AvisoEtiqueta
import com.hemocheck.core.imagen.AvisoCalidad
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.datos.db.OrigenDato

data class Presentacion(val texto: Int, val icono: ImageVector, val gravedad: Gravedad)

fun presentacion(e: EstadoCorroboracion): Presentacion = when (e) {
    EstadoCorroboracion.COINCIDENCIA_EXACTA -> Presentacion(R.string.estado_coincidencia_exacta, Icons.Filled.CheckCircle, Gravedad.CORRECTO)
    EstadoCorroboracion.REGISTRO_NO_ENCONTRADO -> Presentacion(R.string.estado_no_encontrado, Icons.Filled.SearchOff, Gravedad.PELIGRO)
    EstadoCorroboracion.IDENTIFICADOR_ILEGIBLE -> Presentacion(R.string.estado_ilegible, Icons.Filled.TextFields, Gravedad.PELIGRO)
    EstadoCorroboracion.PENDIENTE_REVISION -> Presentacion(R.string.estado_pendiente_revision, Icons.Filled.RateReview, Gravedad.ADVERTENCIA)
    EstadoCorroboracion.REACTIVO_DOCUMENTADO -> Presentacion(R.string.estado_reactivo, Icons.Filled.ErrorOutline, Gravedad.PELIGRO)
    EstadoCorroboracion.NO_REACTIVO_DOCUMENTADO -> Presentacion(R.string.estado_no_reactivo, Icons.Filled.RemoveCircleOutline, Gravedad.INFO)
    EstadoCorroboracion.INDETERMINADO_DOCUMENTADO -> Presentacion(R.string.estado_indeterminado, Icons.AutoMirrored.Filled.HelpOutline, Gravedad.ADVERTENCIA)
    EstadoCorroboracion.PENDIENTE_DOCUMENTADO -> Presentacion(R.string.estado_pendiente_documentado, Icons.Filled.HourglassEmpty, Gravedad.ADVERTENCIA)
    EstadoCorroboracion.RESULTADOS_CONTRADICTORIOS -> Presentacion(R.string.estado_contradictorios, Icons.Filled.SyncProblem, Gravedad.PELIGRO)
    EstadoCorroboracion.DUPLICADO_AMBIGUO -> Presentacion(R.string.estado_duplicado, Icons.Filled.ContentCopy, Gravedad.PELIGRO)
    EstadoCorroboracion.ERROR_OCR -> Presentacion(R.string.estado_error_ocr, Icons.Filled.WarningAmber, Gravedad.ADVERTENCIA)
    EstadoCorroboracion.REGISTRO_INCOMPLETO -> Presentacion(R.string.estado_incompleto, Icons.Filled.PlaylistRemove, Gravedad.ADVERTENCIA)
    EstadoCorroboracion.NO_VERIFICABLE -> Presentacion(R.string.estado_no_verificable, Icons.Filled.ImageNotSupported, Gravedad.ADVERTENCIA)
}

@Composable
fun EtiquetaEstado(e: EstadoCorroboracion) {
    val p = presentacion(e)
    Etiqueta(stringResource(p.texto), p.icono, p.gravedad)
}

fun presentacion(c: CategoriaResultado): Presentacion = when (c) {
    CategoriaResultado.REACTIVO -> Presentacion(R.string.categoria_reactivo, Icons.Filled.ErrorOutline, Gravedad.PELIGRO)
    CategoriaResultado.NO_REACTIVO -> Presentacion(R.string.categoria_no_reactivo, Icons.Filled.RemoveCircleOutline, Gravedad.INFO)
    CategoriaResultado.INDETERMINADO -> Presentacion(R.string.categoria_indeterminado, Icons.AutoMirrored.Filled.HelpOutline, Gravedad.ADVERTENCIA)
    CategoriaResultado.PENDIENTE -> Presentacion(R.string.categoria_pendiente, Icons.Filled.HourglassEmpty, Gravedad.ADVERTENCIA)
    CategoriaResultado.NO_INTERPRETADO -> Presentacion(R.string.categoria_no_interpretado, Icons.Filled.Block, Gravedad.ADVERTENCIA)
}

@Composable
fun EtiquetaCategoria(c: CategoriaResultado) {
    val p = presentacion(c)
    Etiqueta(stringResource(p.texto), p.icono, p.gravedad)
}

fun textoTipo(t: TipoRegistro): Int = when (t) {
    TipoRegistro.CONVENCIONAL -> R.string.tipo_convencional
    TipoRegistro.MOLECULAR -> R.string.tipo_molecular
}

fun textoOrigen(o: OrigenDato?): Int = when (o) {
    OrigenDato.OCR -> R.string.origen_ocr
    OrigenDato.CORREGIDO -> R.string.origen_corregido
    OrigenDato.MANUAL -> R.string.origen_manual
    OrigenDato.CODIGO_BARRAS -> R.string.origen_barras
    null -> R.string.origen_ninguno
}

fun textoEstadoEntrada(e: EstadoEntrada): Int = when (e) {
    EstadoEntrada.CONFIRMADA -> R.string.entrada_confirmada
    EstadoEntrada.AUTOMATICA -> R.string.entrada_automatica
    EstadoEntrada.PENDIENTE_REVISION -> R.string.entrada_pendiente
}

fun textoProblema(p: Problema): Int = when (p) {
    Problema.ID_BAJA_CONFIANZA -> R.string.problema_id_baja_confianza
    Problema.ID_CARACTERES_DUDOSOS -> R.string.problema_id_dudoso
    Problema.ID_CONTROL_INVALIDO -> R.string.problema_id_control
    Problema.ID_REPETIDO_EN_PAGINA -> R.string.problema_id_repetido
    Problema.ID_SIN_UBICACION -> R.string.problema_id_sin_ubicacion
    Problema.VARIAS_UNIDADES_MISMA_FILA -> R.string.problema_varias_unidades
    Problema.RESULTADO_SIN_COLUMNA -> R.string.problema_sin_columna
    Problema.RESULTADO_AMBIGUO_FILAS -> R.string.problema_ambiguo_filas
    Problema.RESULTADO_AMBIGUO_COLUMNAS -> R.string.problema_ambiguo_columnas
    Problema.RESULTADO_BAJA_CONFIANZA -> R.string.problema_resultado_baja_confianza
    Problema.RESULTADOS_CONTRADICTORIOS_EN_FILA -> R.string.problema_contradictorios_fila
    Problema.ASOCIACION_INCIERTA -> R.string.problema_asociacion_incierta
    Problema.SIN_RESULTADOS -> R.string.problema_sin_resultados
    Problema.VALOR_NO_INTERPRETADO -> R.string.problema_no_interpretado
    Problema.RESULTADOS_SUELTOS_EN_PAGINA -> R.string.problema_sueltos
}

fun textoAvisoPagina(a: AvisoPagina): Int = when (a) {
    AvisoPagina.SIN_TEXTO -> R.string.pagina_sin_texto
    AvisoPagina.SIN_IDENTIFICADORES -> R.string.pagina_sin_identificadores
    AvisoPagina.RESULTADOS_SIN_ASOCIAR -> R.string.pagina_resultados_sin_asociar
    AvisoPagina.CONFIANZA_BAJA_GENERAL -> R.string.pagina_confianza_baja
}

/**
 * Solo se muestran las advertencias que cambian algo: que no se encontró texto. Las de calidad
 * (luz, nitidez, reflejos, bordes) no se muestran porque la lectura igual funciona.
 */
fun AvisoCalidad.relevante(): Boolean = this == AvisoCalidad.SIN_TEXTO

/** Advertencias de la hoja que vale la pena mostrar (sin texto o sin números de unidad). */
fun AvisoPagina.relevante(): Boolean = this == AvisoPagina.SIN_TEXTO || this == AvisoPagina.SIN_IDENTIFICADORES

fun textoCalidad(a: AvisoCalidad): Int = when (a) {
    AvisoCalidad.POCA_LUZ -> R.string.calidad_poca_luz
    AvisoCalidad.SOBREEXPUESTA -> R.string.calidad_sobreexpuesta
    AvisoCalidad.DESENFOQUE -> R.string.calidad_desenfoque
    AvisoCalidad.REFLEJOS -> R.string.calidad_reflejos
    AvisoCalidad.POSIBLE_CORTE -> R.string.calidad_corte
    AvisoCalidad.SIN_BORDES -> R.string.calidad_sin_bordes
    AvisoCalidad.TEXTO_ILEGIBLE -> R.string.calidad_texto_ilegible
    AvisoCalidad.SIN_TEXTO -> R.string.calidad_sin_texto
}

fun textoAvisoEtiqueta(a: AvisoEtiqueta): Int = when (a) {
    AvisoEtiqueta.SIN_TEXTO -> R.string.etiqueta_sin_texto
    AvisoEtiqueta.SIN_IDENTIFICADOR -> R.string.etiqueta_sin_identificador
    AvisoEtiqueta.VARIOS_IDENTIFICADORES -> R.string.etiqueta_varios
    AvisoEtiqueta.BAJA_CONFIANZA -> R.string.etiqueta_baja_confianza
    AvisoEtiqueta.CARACTERES_DUDOSOS -> R.string.etiqueta_dudosos
    AvisoEtiqueta.CONTROL_INVALIDO -> R.string.etiqueta_control
    AvisoEtiqueta.BARRAS_DISCORDANTE -> R.string.etiqueta_barras_discordante
    AvisoEtiqueta.BARRAS_CONCORDANTE -> R.string.etiqueta_barras_concordante
    AvisoEtiqueta.SOLO_BARRAS -> R.string.etiqueta_solo_barras
}

/** Texto completo de un aviso de corroboración. */
@Composable
fun textoAviso(a: AvisoCorroboracion): String {
    val tipo = a.tipoRegistro?.let { stringResource(textoTipo(it)) } ?: ""
    val d = a.detalle ?: ""
    return when (a.tipo) {
        TipoAviso.SIN_REGISTRO_TIPO -> stringResource(R.string.aviso_sin_registro, tipo)
        TipoAviso.SOLO_PENDIENTES -> stringResource(R.string.aviso_solo_pendientes, tipo, d)
        TipoAviso.PENDIENTES_ADEMAS -> stringResource(R.string.aviso_pendientes_ademas, tipo, d)
        TipoAviso.DUPLICADOS -> stringResource(R.string.aviso_duplicados, tipo, d)
        TipoAviso.CONTRADICCION -> stringResource(R.string.aviso_contradiccion, tipo, d)
        TipoAviso.FALTA_DETERMINACION -> stringResource(R.string.aviso_falta_determinacion, tipo, d)
        TipoAviso.NO_INTERPRETADO -> stringResource(R.string.aviso_no_interpretado, tipo, d)
        TipoAviso.SIN_UBICACION -> stringResource(R.string.aviso_sin_ubicacion, tipo)
        TipoAviso.SIN_IMAGEN -> stringResource(R.string.aviso_sin_imagen, tipo)
        TipoAviso.ID_SIMILAR -> stringResource(R.string.aviso_id_similar, formatearId(d))
        TipoAviso.BARRAS_DISCORDANTE -> stringResource(R.string.aviso_barras_discordante)
        TipoAviso.ID_CORREGIDO -> stringResource(R.string.aviso_id_corregido)
        TipoAviso.ETIQUETA_ILEGIBLE -> stringResource(R.string.aviso_etiqueta_ilegible)
        TipoAviso.ETIQUETA_ERROR_OCR -> stringResource(R.string.aviso_etiqueta_error_ocr)
        TipoAviso.ETIQUETA_SIN_UBICACION -> stringResource(R.string.aviso_etiqueta_sin_ubicacion)
        TipoAviso.LECTURA_AUTOMATICA -> stringResource(R.string.aviso_lectura_automatica, tipo)
    }
}

fun gravedadAviso(a: AvisoCorroboracion): Gravedad = presentacion(a.estado).gravedad
