package com.hemocheck.core.analisis

import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.identificador.FormatoIdentificador
import com.hemocheck.core.identificador.IdUnidad
import com.hemocheck.core.identificador.OcurrenciaId
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.core.ocr.TextoLinea
import com.hemocheck.core.resultado.CatalogoDeterminaciones
import com.hemocheck.core.resultado.CategoriaResultado
import com.hemocheck.core.resultado.ClasificadorResultado
import com.hemocheck.core.resultado.Determinacion
import com.hemocheck.core.resultado.TipoRegistro
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.tan

/** Motivos por los que una fila no puede confirmarse sin revisión. */
enum class Problema(val bloqueante: Boolean) {
    ID_BAJA_CONFIANZA(true),
    ID_CARACTERES_DUDOSOS(true),
    ID_CONTROL_INVALIDO(true),
    ID_REPETIDO_EN_PAGINA(true),
    ID_SIN_UBICACION(true),
    VARIAS_UNIDADES_MISMA_FILA(true),
    RESULTADO_SIN_COLUMNA(true),
    RESULTADO_AMBIGUO_FILAS(true),
    RESULTADO_AMBIGUO_COLUMNAS(true),
    RESULTADO_BAJA_CONFIANZA(true),
    RESULTADOS_CONTRADICTORIOS_EN_FILA(true),
    ASOCIACION_INCIERTA(true),
    SIN_RESULTADOS(true),
    VALOR_NO_INTERPRETADO(false),
    RESULTADOS_SUELTOS_EN_PAGINA(false),
}

enum class Disposicion { TABLA, FORMULARIO, DESCONOCIDA }

enum class AvisoPagina { SIN_TEXTO, SIN_IDENTIFICADORES, RESULTADOS_SIN_ASOCIAR, CONFIANZA_BAJA_GENERAL }

data class ResultadoPropuesto(
    val clave: String,
    val nombreDeterminacion: String,
    /** Texto del encabezado o etiqueta tal como se leyó. */
    val textoEncabezado: String?,
    val textoCrudo: String,
    val categoria: CategoriaResultado,
    val caja: Caja?,
    val cajaEncabezado: Caja?,
    val confianza: Float?,
)

data class EntradaPropuesta(
    val ocurrencia: OcurrenciaId,
    /** Zona de la fila en la imagen (identificador + resultados asociados). */
    val cajaFila: Caja?,
    /** Zona de los encabezados de columna usados, si la hoja es una tabla. */
    val cajaEncabezados: Caja?,
    val resultados: List<ResultadoPropuesto>,
    val fecha: String?,
    val validacion: String?,
    val problemas: Set<Problema>,
) {
    /** Sin problemas bloqueantes. Aun así, el usuario debe confirmar la fila. */
    val listaParaConfirmar: Boolean get() = problemas.none { it.bloqueante }
}

data class HojaAnalizada(
    val tipo: TipoRegistro,
    val disposicion: Disposicion,
    val entradas: List<EntradaPropuesta>,
    val avisos: Set<AvisoPagina>,
    val fechaPagina: String?,
    val validacionPagina: List<String>,
    val resultadosSinAsociar: List<ResultadoPropuesto>,
)

/**
 * Asocia identificadores, columnas y resultados de una hoja de serología a partir de las
 * posiciones del texto reconocido. La regla es conservadora: ante cualquier duda sobre a qué
 * identificador o determinación corresponde un resultado, no lo asigna y marca la fila
 * como pendiente de revisión.
 */
class AnalizadorHoja(
    private val formatos: List<FormatoIdentificador>,
    /** Por debajo de esta confianza el OCR de un dato se marca para revisión. */
    private val umbralConfianza: Float = UMBRAL_CONFIANZA_POR_OMISION,
) {
    private data class Det(val d: Determinacion, val f: TextoLinea.Fragmento)
    private data class Res(val categoria: CategoriaResultado, val f: TextoLinea.Fragmento)

    fun analizar(pagina: PaginaOcr, tipo: TipoRegistro): HojaAnalizada {
        val lineas = TextoLinea.de(pagina)
        val avisos = mutableSetOf<AvisoPagina>()
        if (pagina.vacia) avisos += AvisoPagina.SIN_TEXTO
        pagina.confianzaMedia?.let { if (it < umbralConfianza) avisos += AvisoPagina.CONFIANZA_BAJA_GENERAL }

        val tanInclinacion = tan(Math.toRadians(inclinacionMediana(pagina).toDouble())).toFloat()
        fun yc(c: Caja): Float = c.centroY - c.centroX * tanInclinacion
        fun mismaFila(a: Caja, b: Caja): Boolean = abs(yc(a) - yc(b)) <= 0.6f * max(a.alto, b.alto)

        // 1. Identificadores, determinaciones, frases de resultado y fechas
        val ids = lineas.flatMap { IdUnidad.buscarEnLinea(it, formatos) }
        val dets = mutableListOf<Det>()
        val frases = mutableListOf<Res>()
        val fechas = mutableListOf<Pair<String, TextoLinea.Fragmento>>()
        for (l in lineas) {
            val idsLinea = ids.filter { it.indiceLinea == l.indiceLinea }
            val detsLinea = CatalogoDeterminaciones.buscar(l, tipo)
                .filter { o -> idsLinea.none { solapa(it.rango, o.fragmento) } }
            dets += detsLinea.map { Det(it.determinacion, it.fragmento) }
            val fechasLinea = buscarFechas(l)
            fechas += fechasLinea
            frases += ClasificadorResultado.buscar(l)
                .filter { fr ->
                    idsLinea.none { solapa(it.rango, fr.fragmento) } &&
                        detsLinea.none { solapaF(it.fragmento, fr.fragmento) } &&
                        fechasLinea.none { solapaF(it.second, fr.fragmento) }
                }
                .map { Res(it.categoria, it.fragmento) }
        }
        val fechaPagina = fechas.firstOrNull { (_, f) -> "fecha" in lineas[f.indiceLinea].textoNormalizado }?.first
            ?: fechas.firstOrNull()?.first
        val validacionPagina = lineas.filter { REGEX_VALIDACION.containsMatchIn(it.textoNormalizado) }
            .map { it.texto }.take(5)

        if (ids.isEmpty()) {
            avisos += AvisoPagina.SIN_IDENTIFICADORES
            val sueltos = frases.map { aPropuesto(it, null) }
            if (sueltos.isNotEmpty()) avisos += AvisoPagina.RESULTADOS_SIN_ASOCIAR
            return HojaAnalizada(tipo, Disposicion.DESCONOCIDA, emptyList(), avisos, fechaPagina, validacionPagina, sueltos)
        }

        // 2. Etiquetas de formulario: determinación con un resultado a su derecha en la misma franja
        val etiquetaDe = mutableMapOf<Res, Det>()
        val detsUsadas = mutableSetOf<Det>()
        for (r in frases.sortedBy { it.f.caja?.izq ?: 0 }) {
            val rc = r.f.caja ?: continue
            val det = dets.filter { d ->
                val dc = d.f.caja ?: return@filter false
                d !in detsUsadas && mismaFila(dc, rc) && dc.der <= rc.izq + rc.alto
            }.maxByOrNull { it.f.caja!!.der } ?: continue
            // No debe haber otro resultado entre la etiqueta y este resultado
            val entre = frases.any { o ->
                o !== r && o.f.caja?.let { oc -> mismaFila(oc, rc) && oc.izq >= det.f.caja!!.der && oc.der <= rc.izq } == true
            }
            if (entre) continue
            etiquetaDe[r] = det
            detsUsadas += det
        }
        val encabezados = dets.filter { it !in detsUsadas && it.f.caja != null }

        // 3. Agrupar ocurrencias del mismo identificador
        val distintos = ids.map { it.normalizado }.distinct()
        val asignadas = mutableSetOf<Res>()

        val entradas = if (distintos.size == 1) {
            listOf(entradaUnica(ids, frases, etiquetaDe, encabezados, fechas, lineas, asignadas, ::mismaFila, ::yc))
        } else {
            entradasPorFila(ids, frases, etiquetaDe, encabezados, fechas, lineas, asignadas, ::mismaFila, ::yc)
        }

        val sueltos = frases.filter { it !in asignadas && !esFraseDeFilaAmbigua(it, entradas) }
            .map { aPropuesto(it, etiquetaDe[it]) }
        if (sueltos.isNotEmpty()) avisos += AvisoPagina.RESULTADOS_SIN_ASOCIAR

        val entradasFinales = if (sueltos.isNotEmpty()) {
            entradas.map { it.copy(problemas = it.problemas + Problema.RESULTADOS_SUELTOS_EN_PAGINA) }
        } else entradas

        val disposicion = when {
            encabezados.isNotEmpty() && entradasFinales.any { e -> e.cajaEncabezados != null } -> Disposicion.TABLA
            etiquetaDe.isNotEmpty() -> Disposicion.FORMULARIO
            else -> Disposicion.DESCONOCIDA
        }
        return HojaAnalizada(tipo, disposicion, entradasFinales, avisos, fechaPagina, validacionPagina, sueltos)
    }

    /** La página tiene un único identificador (puede repetirse, p. ej. en encabezado y pie). */
    private fun entradaUnica(
        ids: List<OcurrenciaId>,
        frases: List<Res>,
        etiquetaDe: Map<Res, Det>,
        encabezados: List<Det>,
        fechas: List<Pair<String, TextoLinea.Fragmento>>,
        lineas: List<TextoLinea>,
        asignadas: MutableSet<Res>,
        mismaFila: (Caja, Caja) -> Boolean,
        yc: (Caja) -> Float,
    ): EntradaPropuesta {
        val principal = ids.maxWithOrNull(compareBy<OcurrenciaId>({ it.estricto }, { it.confianza ?: 0f })) ?: ids.first()
        val problemas = problemasDeId(principal).toMutableSet()
        val resultados = mutableListOf<ResultadoPropuesto>()
        // Resultados con etiqueta (formulario) en cualquier lugar de la página
        for ((r, d) in etiquetaDe) {
            resultados += aPropuesto(r, d)
            asignadas += r
        }
        // Resultados en la fila del identificador, por columna
        var cajaEncabezados: Caja? = null
        val cajaId = principal.caja
        if (cajaId != null) {
            val enFila = frases.filter { it !in asignadas && it.f.caja?.let { c -> mismaFila(c, cajaId) } == true }
            val col = asignarColumnas(enFila, cajaId, encabezados, yc)
            resultados += col.resultados
            asignadas += col.asignadas
            problemas += col.problemas
            cajaEncabezados = col.cajaEncabezados
            resultados += valoresNoInterpretados(lineas, cajaId, encabezados, col, mismaFila, yc, problemas)
        } else {
            problemas += Problema.ID_SIN_UBICACION
        }
        finalizarProblemas(resultados, problemas)
        return EntradaPropuesta(
            ocurrencia = principal,
            // En un formulario la zona relevante incluye las etiquetas de cada determinación
            cajaFila = Caja.unionDe(listOf(cajaId) + resultados.flatMap { listOf(it.caja, it.cajaEncabezado) }
                .takeIf { etiquetaDe.isNotEmpty() }.orEmpty() + resultados.map { it.caja }),
            cajaEncabezados = cajaEncabezados,
            resultados = resultados,
            fecha = cajaId?.let { c -> fechas.firstOrNull { (_, f) -> f.caja?.let { mismaFila(it, c) } == true }?.first },
            validacion = cajaId?.let { c -> validacionEnFila(lineas, c, mismaFila) },
            problemas = problemas,
        )
    }

    /** Varias unidades en la página: cada resultado se asocia a la fila de su identificador. */
    private fun entradasPorFila(
        ids: List<OcurrenciaId>,
        frases: List<Res>,
        etiquetaDe: Map<Res, Det>,
        encabezados: List<Det>,
        fechas: List<Pair<String, TextoLinea.Fragmento>>,
        lineas: List<TextoLinea>,
        asignadas: MutableSet<Res>,
        mismaFila: (Caja, Caja) -> Boolean,
        yc: (Caja) -> Float,
    ): List<EntradaPropuesta> {
        val conCaja = ids.filter { it.caja != null }
        val entradas = mutableListOf<EntradaPropuesta>()
        for (o in ids) {
            val problemas = problemasDeId(o).toMutableSet()
            val cajaId = o.caja
            if (cajaId == null) {
                problemas += Problema.ID_SIN_UBICACION
                problemas += Problema.ASOCIACION_INCIERTA
                entradas += EntradaPropuesta(o, null, null, emptyList(), null, null, problemas + Problema.SIN_RESULTADOS)
                continue
            }
            val otrosEnFila = conCaja.filter { it !== o && mismaFila(it.caja!!, cajaId) }
            if (otrosEnFila.any { it.normalizado != o.normalizado }) problemas += Problema.VARIAS_UNIDADES_MISMA_FILA
            val repetidoEnOtraFila = conCaja.any { it !== o && it.normalizado == o.normalizado && !mismaFila(it.caja!!, cajaId) }
            if (repetidoEnOtraFila) problemas += Problema.ID_REPETIDO_EN_PAGINA
            // La misma unidad repetida dentro de su fila (p. ej. texto bajo un código) se une
            if (otrosEnFila.any { it.normalizado == o.normalizado && entradas.any { e -> e.ocurrencia === it } }) continue

            val resultados = mutableListOf<ResultadoPropuesto>()
            var cajaEncabezados: Caja? = null
            if (Problema.VARIAS_UNIDADES_MISMA_FILA !in problemas) {
                val enFila = frases.filter { r ->
                    val rc = r.f.caja ?: return@filter false
                    mismaFila(rc, cajaId)
                }
                val (ambiguas, propias) = enFila.partition { r ->
                    conCaja.any { otro -> otro.normalizado != o.normalizado && mismaFila(r.f.caja!!, otro.caja!!) }
                }
                if (ambiguas.isNotEmpty()) problemas += Problema.RESULTADO_AMBIGUO_FILAS
                val conEtiqueta = propias.filter { it in etiquetaDe }
                for (r in conEtiqueta) {
                    resultados += aPropuesto(r, etiquetaDe[r])
                    asignadas += r
                }
                val col = asignarColumnas(propias - conEtiqueta.toSet(), cajaId, encabezados, yc)
                resultados += col.resultados
                asignadas += col.asignadas
                problemas += col.problemas
                cajaEncabezados = col.cajaEncabezados
                resultados += valoresNoInterpretados(lineas, cajaId, encabezados, col, mismaFila, yc, problemas)
            } else {
                // No se asigna nada: el usuario debe asociar manualmente
                if (frases.any { r -> r.f.caja?.let { mismaFila(it, cajaId) } == true }) problemas += Problema.ASOCIACION_INCIERTA
            }
            finalizarProblemas(resultados, problemas)
            entradas += EntradaPropuesta(
                ocurrencia = o,
                cajaFila = filaDe(cajaId, resultados),
                cajaEncabezados = cajaEncabezados,
                resultados = resultados,
                fecha = fechas.firstOrNull { (_, f) -> f.caja?.let { mismaFila(it, cajaId) } == true }?.first,
                validacion = validacionEnFila(lineas, cajaId, mismaFila),
                problemas = problemas,
            )
        }
        // Las frases que caen en filas ambiguas no cuentan como sueltas: ya están señaladas
        return entradas
    }

    private fun esFraseDeFilaAmbigua(r: Res, entradas: List<EntradaPropuesta>): Boolean {
        val rc = r.f.caja ?: return false
        return entradas.any { e ->
            (Problema.RESULTADO_AMBIGUO_FILAS in e.problemas || Problema.VARIAS_UNIDADES_MISMA_FILA in e.problemas ||
                Problema.ASOCIACION_INCIERTA in e.problemas) &&
                e.ocurrencia.caja?.let { abs(it.centroY - rc.centroY) <= 0.6f * max(it.alto, rc.alto) } == true
        }
    }

    private class Columnas(
        val resultados: List<ResultadoPropuesto>,
        val asignadas: Set<Res>,
        val problemas: Set<Problema>,
        val cajaEncabezados: Caja?,
        val encabezadosUsados: List<Det>,
        val franja: List<Det>,
    )

    /** Asigna cada resultado de una fila a la columna cuyo encabezado está encima. */
    private fun asignarColumnas(
        enFila: List<Res>,
        cajaId: Caja,
        encabezados: List<Det>,
        yc: (Caja) -> Float,
    ): Columnas {
        val arriba = encabezados.filter { yc(it.f.caja!!) < yc(cajaId) - cajaId.alto * 0.5f }
        if (arriba.isEmpty()) {
            val problemas = if (enFila.isNotEmpty()) setOf(Problema.RESULTADO_SIN_COLUMNA) else emptySet()
            return Columnas(emptyList(), emptySet(), problemas, null, emptyList(), emptyList())
        }
        // Franja de encabezados más cercana por encima de la fila
        val masCercano = arriba.maxBy { yc(it.f.caja!!) }
        val altoRef = masCercano.f.caja!!.alto
        val franja = arriba.filter { abs(yc(it.f.caja!!) - yc(masCercano.f.caja!!)) <= altoRef * 0.8f }
            .sortedBy { it.f.caja!!.centroX }

        val resultados = mutableListOf<ResultadoPropuesto>()
        val asignadas = mutableSetOf<Res>()
        val problemas = mutableSetOf<Problema>()
        val usados = mutableListOf<Det>()
        for (r in enFila) {
            val col = columnaPara(r.f.caja!!, franja)
            when (col) {
                is Columna.Unica -> {
                    resultados += aPropuesto(r, col.det)
                    asignadas += r
                    usados += col.det
                }
                Columna.Ambigua -> problemas += Problema.RESULTADO_AMBIGUO_COLUMNAS
                Columna.Ninguna -> problemas += Problema.RESULTADO_SIN_COLUMNA
            }
        }
        val cajaEnc = Caja.unionDe(franja.map { it.f.caja })
        return Columnas(resultados, asignadas, problemas, cajaEnc, usados, franja)
    }

    private sealed interface Columna {
        data class Unica(val det: Det) : Columna
        data object Ambigua : Columna
        data object Ninguna : Columna
    }

    private fun columnaPara(c: Caja, franja: List<Det>): Columna {
        if (franja.isEmpty()) return Columna.Ninguna
        val centros = franja.map { it.f.caja!!.centroX }
        val i = centros.indices.minBy { abs(centros[it] - c.centroX) }
        val gapIzq = if (i > 0) centros[i] - centros[i - 1] else null
        val gapDer = if (i < centros.lastIndex) centros[i + 1] - centros[i] else null
        val gapRef = listOfNotNull(gapIzq, gapDer).minOrNull()
            ?: max(franja[i].f.caja!!.ancho * 2f, c.ancho.toFloat())
        val limiteIzq = centros[i] - (gapIzq ?: gapRef) / 2f
        val limiteDer = centros[i] + (gapDer ?: gapRef) / 2f
        // Fuera del alcance de las columnas extremas
        if (c.centroX < limiteIzq - gapRef * 0.5f || c.centroX > limiteDer + gapRef * 0.5f) return Columna.Ninguna
        if (gapIzq == null && c.centroX < limiteIzq) return Columna.Ninguna
        if (gapDer == null && c.centroX > limiteDer) return Columna.Ninguna
        val margen = gapRef * 0.12f
        // Centro demasiado cerca del límite entre dos columnas
        if (gapIzq != null && c.centroX - limiteIzq < margen) return Columna.Ambigua
        if (gapDer != null && limiteDer - c.centroX < margen) return Columna.Ambigua
        // Texto que invade claramente la columna vecina
        val tolerancia = gapRef * 0.15f
        if (gapIzq != null && c.izq < limiteIzq - tolerancia && c.ancho < gapRef * 1.5f) return Columna.Ambigua
        if (gapDer != null && c.der > limiteDer + tolerancia && c.ancho < gapRef * 1.5f) return Columna.Ambigua
        return Columna.Unica(franja[i])
    }

    /**
     * Para columnas sin frase de resultado reconocida, registra el texto que haya en la celda
     * como «no interpretado» (nunca se deduce un resultado).
     */
    private fun valoresNoInterpretados(
        lineas: List<TextoLinea>,
        cajaId: Caja,
        encabezados: List<Det>,
        col: Columnas,
        mismaFila: (Caja, Caja) -> Boolean,
        yc: (Caja) -> Float,
        problemas: MutableSet<Problema>,
    ): List<ResultadoPropuesto> {
        if (col.franja.isEmpty()) return emptyList()
        val sinResultado = col.franja.filter { it !in col.encabezadosUsados }
        if (sinResultado.isEmpty()) return emptyList()
        val salida = mutableListOf<ResultadoPropuesto>()
        val palabras = lineas.flatMap { l ->
            l.palabrasFuera(emptyList()).filter { f ->
                f.caja?.let { mismaFila(it, cajaId) && !it.interseca(cajaId) } == true &&
                    ClasificadorResultado.buscar(TextoLinea(com.hemocheck.core.ocr.LineaOcr(f.texto, f.caja), 0)).isEmpty()
            }
        }
        for (d in sinResultado) {
            val enCelda = palabras.filter { p -> (columnaPara(p.caja!!, col.franja) as? Columna.Unica)?.det == d }
            if (enCelda.isEmpty()) continue
            val texto = enCelda.sortedBy { it.caja!!.izq }.joinToString(" ") { it.texto }
            salida += ResultadoPropuesto(
                clave = d.d.clave, nombreDeterminacion = d.d.nombre, textoEncabezado = d.f.texto,
                textoCrudo = texto, categoria = CategoriaResultado.NO_INTERPRETADO,
                caja = Caja.unionDe(enCelda.map { it.caja }), cajaEncabezado = d.f.caja,
                confianza = enCelda.mapNotNull { it.confianza }.minOrNull(),
            )
            problemas += Problema.VALOR_NO_INTERPRETADO
        }
        return salida
    }

    private fun finalizarProblemas(resultados: List<ResultadoPropuesto>, problemas: MutableSet<Problema>) {
        if (resultados.isEmpty()) problemas += Problema.SIN_RESULTADOS
        if (resultados.any { r -> r.categoria != CategoriaResultado.NO_INTERPRETADO && (r.confianza ?: 1f) < umbralConfianza }) {
            problemas += Problema.RESULTADO_BAJA_CONFIANZA
        }
        val porClave = resultados.filter { it.categoria != CategoriaResultado.NO_INTERPRETADO }.groupBy { it.clave }
        if (porClave.values.any { lista -> lista.map { it.categoria }.distinct().size > 1 }) {
            problemas += Problema.RESULTADOS_CONTRADICTORIOS_EN_FILA
        }
    }

    private fun problemasDeId(o: OcurrenciaId): Set<Problema> = buildSet {
        if (!o.estricto) add(Problema.ID_CARACTERES_DUDOSOS)
        if (o.controlValido == false) add(Problema.ID_CONTROL_INVALIDO)
        if ((o.confianza ?: 1f) < umbralConfianza) add(Problema.ID_BAJA_CONFIANZA)
    }

    private fun aPropuesto(r: Res, d: Det?): ResultadoPropuesto = ResultadoPropuesto(
        clave = d?.d?.clave ?: CLAVE_DESCONOCIDA,
        nombreDeterminacion = d?.d?.nombre ?: "Sin determinación",
        textoEncabezado = d?.f?.texto,
        textoCrudo = r.f.texto,
        categoria = r.categoria,
        caja = r.f.caja,
        cajaEncabezado = d?.f?.caja,
        confianza = r.f.confianza,
    )

    private fun filaDe(cajaId: Caja?, resultados: List<ResultadoPropuesto>): Caja? =
        Caja.unionDe(listOf(cajaId) + resultados.map { it.caja })

    private fun validacionEnFila(lineas: List<TextoLinea>, cajaId: Caja, mismaFila: (Caja, Caja) -> Boolean): String? =
        lineas.firstOrNull { l ->
            REGEX_VALIDACION.containsMatchIn(l.textoNormalizado) && l.linea.caja?.let { mismaFila(it, cajaId) } == true
        }?.texto

    private fun solapa(r: IntRange, f: TextoLinea.Fragmento) = r.first < f.fin && f.inicio <= r.last
    private fun solapaF(a: TextoLinea.Fragmento, b: TextoLinea.Fragmento) = a.inicio < b.fin && b.inicio < a.fin

    companion object {
        const val UMBRAL_CONFIANZA_POR_OMISION = 0.80f
        const val CLAVE_DESCONOCIDA = "DESCONOCIDA"

        private val REGEX_VALIDACION = Regex("(?<![\\p{L}])(validad|confirmad|revisad|firmad|autorizad|verificad)[oa]s?")
        private val REGEX_FECHA = Regex("(?<![0-9])(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})(?![0-9])")

        /** Fechas dd/mm/aaaa (o dd-mm-aa) válidas, normalizadas a aaaa-mm-dd. */
        fun buscarFechas(l: TextoLinea): List<Pair<String, TextoLinea.Fragmento>> =
            REGEX_FECHA.findAll(l.texto).mapNotNull { m ->
                val d = m.groupValues[1].toInt()
                val mes = m.groupValues[2].toInt()
                var a = m.groupValues[3].toInt()
                if (m.groupValues[3].length == 2) a += 2000
                val fecha = runCatching { LocalDate.of(a, mes, d) }.getOrNull() ?: return@mapNotNull null
                fecha.toString() to l.fragmento(m.range.first, m.range.last + 1)
            }.toList()

        /** Mediana de la inclinación de las líneas (solo si es pequeña y consistente). */
        fun inclinacionMediana(pagina: PaginaOcr): Float {
            val angulos = pagina.lineas.mapNotNull { it.angulo }.filter { abs(it) <= 15f }.sorted()
            if (angulos.size < 3) return 0f
            val m = angulos[angulos.size / 2]
            return if (abs(m) < 0.5f) 0f else m
        }
    }
}
