package com.hemocheck.core.identificador

import com.hemocheck.core.geometria.Caja
import com.hemocheck.core.ocr.TextoLinea
import kotlinx.serialization.Serializable

/** Un identificador de unidad o donación encontrado en una línea de OCR. */
data class OcurrenciaId(
    /** Texto tal como lo leyó el OCR. */
    val textoCrudo: String,
    /** Forma canónica (mayúsculas, sin separadores). Es la que se usa para la búsqueda exacta. */
    val normalizado: String,
    /** true si el texto cumple el formato sin necesidad de reemplazar caracteres. */
    val estricto: Boolean,
    /**
     * Corrección propuesta reemplazando caracteres que el OCR suele confundir (O/0, I/1, S/5...).
     * Nunca se aplica automáticamente: el usuario debe aceptarla.
     */
    val sugerido: String?,
    /** Resultado de verificar el carácter de control (null si no hay carácter de control). */
    val controlValido: Boolean?,
    val caja: Caja?,
    val confianza: Float?,
    val indiceLinea: Int,
    val rango: IntRange,
    val formato: TipoFormato,
    /** Aparece a continuación de una palabra clave («Unidad:», «Ext:», «Donación:»…). */
    val anclado: Boolean = false,
    /**
     * Letra de componente que sigue al número local (p. ej. «G» en 295216G). No forma parte
     * del identificador de búsqueda: la serología es de la donación, común a todos sus componentes.
     */
    val componente: String? = null,
)

enum class TipoFormato { ISBT128, NUMERICO, PERSONALIZADO }

/** Formatos de identificador habilitados en Configuración. */
@Serializable
data class ConfiguracionIdentificadores(
    val isbt128: Boolean = true,
    val numerico: Boolean = true,
    val numericoMin: Int = 6,
    val numericoMax: Int = 12,
    /** Expresión regular propia del servicio (opcional), aplicada sobre el texto en mayúsculas. */
    val patronPersonalizado: String? = null,
) {
    fun formatos(): List<FormatoIdentificador> = buildList {
        if (isbt128) add(FormatoIsbt128)
        if (numerico) add(FormatoNumerico(numericoMin.coerceAtLeast(3), numericoMax.coerceAtLeast(numericoMin)))
        patronPersonalizado?.takeIf { it.isNotBlank() }?.let { p ->
            FormatoPersonalizado.crear(p)?.let { add(it) }
        }
    }
}

sealed interface FormatoIdentificador {
    val tipo: TipoFormato
    fun buscar(linea: TextoLinea): List<OcurrenciaId>

    /** Si [texto] completo cumple el formato, devuelve su forma canónica. */
    fun canonizar(texto: String): String?
}

/** Utilidades comunes a todos los formatos. */
object IdUnidad {
    /** Mayúsculas y solo letras, dígitos y '*'. */
    fun normalizar(s: String): String =
        TextoLinea.normalizarConservandoLongitud(s).uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '*' }

    /**
     * Forma canónica de un identificador escrito o confirmado por el usuario. Si cumple algún
     * formato habilitado se usa su canonización (p. ej. los 13 caracteres del DIN ISBT 128);
     * si no, la normalización simple.
     */
    fun canonizar(texto: String, formatos: List<FormatoIdentificador>): String {
        for (f in formatos) f.canonizar(texto)?.let { return it }
        return normalizar(texto)
    }

    /**
     * Busca identificadores en una línea con todos los formatos. Si dos ocurrencias se solapan
     * (p. ej. la secuencia numérica de un DIN ISBT también cumple el formato numérico),
     * se conserva la más larga.
     */
    fun buscarEnLinea(linea: TextoLinea, formatos: List<FormatoIdentificador>): List<OcurrenciaId> {
        val todas = formatos.flatMap { it.buscar(linea) }
            .sortedWith(compareByDescending<OcurrenciaId> { it.rango.last - it.rango.first }.thenBy { it.formato.ordinal })
        val elegidas = mutableListOf<OcurrenciaId>()
        for (o in todas) {
            if (elegidas.none { it.rango.first <= o.rango.last && o.rango.first <= it.rango.last }) elegidas += o
        }
        val mayus = mayusculas(linea)
        return elegidas.sortedBy { it.rango.first }.map { o ->
            val antes = mayus.substring(maxOf(0, o.rango.first - 24), o.rango.first)
            if (PALABRA_CLAVE.containsMatchIn(antes)) o.copy(anclado = true) else o
        }
    }

    /** Palabra clave inmediatamente antes del número («Unidad:», «Ext:», «N° de donación»…). */
    private val PALABRA_CLAVE = Regex("(?<![A-Z])(UNIDAD|EXT|EXTRACCION|DONACION|DIN|N[°º.]|NRO\\.?|NUMERO)\\s*(DE\\s+(UNIDAD|DONACION|EXTRACCION))?\\s*[:.#]?\\s*$")

    internal fun mayusculas(linea: TextoLinea): String =
        String(CharArray(linea.textoNormalizado.length) { linea.textoNormalizado[it].uppercaseChar() })
}

/** Caracteres que el OCR suele confundir entre sí. */
object Confusiones {
    private val aDigito = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0',
        'I' to '1', 'L' to '1', '|' to '1',
        'Z' to '2', 'S' to '5', 'G' to '6', 'B' to '8',
    )
    private val aLetra = mapOf('1' to 'I', '5' to 'S', '8' to 'B', '2' to 'Z', '6' to 'G')

    /** Clase de caracteres admitida en posiciones numéricas para la búsqueda tolerante. */
    const val CLASE_DIGITO = "[0-9OQDILZSGB|]"

    fun comoDigito(c: Char): Char? = if (c.isDigit()) c else aDigito[c]
    fun comoLetra(c: Char): Char? = if (c in 'A'..'Z') c else aLetra[c]

    /**
     * Clave para detectar identificadores visualmente similares: dos identificadores distintos
     * con la misma clave podrían ser el mismo leído con un error de OCR.
     */
    fun clave(id: String): String = buildString {
        for (c in IdUnidad.normalizar(id)) {
            append(
                when (c) {
                    'O', 'Q', 'D' -> '0'
                    'I', 'L' -> '1'
                    'Z' -> '2'; 'S' -> '5'; 'G' -> '6'; 'B' -> '8'
                    else -> c
                }
            )
        }
    }
}

/**
 * Número de identificación de donación ISBT 128 (DIN): 13 caracteres, «A9999 YY NNNNNN»
 * (código del centro, año y número de secuencia), seguidos opcionalmente de dos dígitos de
 * indicadores (flags) y del carácter de control ISO 7064 Mod 37-2.
 * La forma canónica son los 13 caracteres del DIN.
 */
object FormatoIsbt128 : FormatoIdentificador {
    override val tipo = TipoFormato.ISBT128

    private val estricto = Regex(
        "(?<![A-Z0-9])([A-NP-Z]) ?(\\d{4}) ?(\\d{2}) ?(\\d{6})(?: ?(\\d{2}))?(?: ?([0-9A-Z*]))?(?![A-Z0-9])"
    )
    private val D = Confusiones.CLASE_DIGITO
    private val tolerante = Regex("(?<![A-Z0-9])([A-Z0-9]) ?($D{4}) ?($D{2}) ?($D{6})(?![A-Z0-9])")
    private val completo = Regex("^=?([A-NP-Z])(\\d{4})(\\d{2})(\\d{6})(\\d{2})?([0-9A-Z*])?$")

    override fun canonizar(texto: String): String? {
        val m = completo.find(IdUnidad.normalizar(texto)) ?: return null
        return m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + m.groupValues[4]
    }

    /** Verifica el carácter de control de un texto escrito (DIN + flags opcionales + control). */
    fun controlDeTexto(texto: String): Boolean? {
        val m = completo.find(IdUnidad.normalizar(texto)) ?: return null
        val control = m.groups[6]?.value?.single() ?: return null
        val din = m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + m.groupValues[4]
        return Mod37_2.calcular(din) == control
    }

    override fun buscar(linea: TextoLinea): List<OcurrenciaId> {
        val texto = IdUnidad.mayusculas(linea)
        val resultado = mutableListOf<OcurrenciaId>()
        for (m in estricto.findAll(texto)) {
            val din = m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + m.groupValues[4]
            val control = m.groups[6]?.value?.single()
            val frag = linea.fragmento(m.range.first, m.range.last + 1)
            resultado += OcurrenciaId(
                textoCrudo = frag.texto, normalizado = din, estricto = true, sugerido = null,
                controlValido = control?.let { Mod37_2.calcular(din) == it },
                caja = frag.caja, confianza = frag.confianza, indiceLinea = linea.indiceLinea,
                rango = m.range, formato = tipo,
            )
        }
        for (m in tolerante.findAll(texto)) {
            if (resultado.any { it.rango.first <= m.range.last && m.range.first <= it.rango.last }) continue
            val cuerpo = m.groupValues[2] + m.groupValues[3] + m.groupValues[4]
            // Exige mayoría de dígitos reales para no tomar palabras como identificadores
            if (cuerpo.count { it.isDigit() } < 9) continue
            val letra = Confusiones.comoLetra(m.groupValues[1].single())?.takeIf { it != 'O' } ?: continue
            val corregido = letra + cuerpo.map { Confusiones.comoDigito(it) ?: '?' }.joinToString("")
            if ('?' in corregido) continue
            val frag = linea.fragmento(m.range.first, m.range.last + 1)
            resultado += OcurrenciaId(
                textoCrudo = frag.texto, normalizado = IdUnidad.normalizar(m.value), estricto = false,
                sugerido = corregido, controlValido = null, caja = frag.caja, confianza = frag.confianza,
                indiceLinea = linea.indiceLinea, rango = m.range, formato = tipo,
            )
        }
        return resultado
    }
}

/** Identificador numérico local (p. ej. número de donación del servicio), con guiones opcionales. */
class FormatoNumerico(val minimo: Int, val maximo: Int) : FormatoIdentificador {
    override val tipo = TipoFormato.NUMERICO

    // Número con guiones opcionales y, opcionalmente, una o dos letras de componente (295216G)
    private val estricto = Regex("(?<![0-9A-Z])(\\d(?:-?\\d){${minimo - 1},${maximo - 1}})([A-Z]{1,2})?(?![0-9A-Z])")
    private val completo = Regex("^\\*?(\\d{$minimo,$maximo})([A-Z]{1,2})?\\*?$")
    private val D = Confusiones.CLASE_DIGITO
    private val tolerante = Regex("(?<![0-9A-Z])$D(?:-?$D){${minimo - 1},${maximo - 1}}(?![0-9A-Z])")

    /** Forma canónica: solo los dígitos (sin la letra de componente ni los asteriscos del código de barras). */
    override fun canonizar(texto: String): String? = completo.find(IdUnidad.normalizar(texto))?.groupValues?.get(1)

    override fun buscar(linea: TextoLinea): List<OcurrenciaId> {
        val texto = IdUnidad.mayusculas(linea)
        val resultado = mutableListOf<OcurrenciaId>()
        for (m in estricto.findAll(texto)) {
            val frag = linea.fragmento(m.range.first, m.range.last + 1)
            resultado += OcurrenciaId(
                frag.texto, IdUnidad.normalizar(m.groupValues[1]), true, null, null,
                frag.caja, frag.confianza, linea.indiceLinea, m.range, tipo,
                componente = m.groups[2]?.value,
            )
        }
        for (m in tolerante.findAll(texto)) {
            if (resultado.any { it.rango.first <= m.range.last && m.range.first <= it.rango.last }) continue
            val cuerpo = m.value.filter { it != '-' }
            val dudosos = cuerpo.count { !it.isDigit() }
            // Como máximo dos caracteres dudosos y al menos 70 % de dígitos reales
            if (dudosos == 0 || dudosos > 2 || cuerpo.count { it.isDigit() } < cuerpo.length * 0.7) continue
            val corregido = cuerpo.map { Confusiones.comoDigito(it) ?: '?' }.joinToString("")
            if ('?' in corregido) continue
            val frag = linea.fragmento(m.range.first, m.range.last + 1)
            resultado += OcurrenciaId(
                frag.texto, IdUnidad.normalizar(m.value), false, corregido, null,
                frag.caja, frag.confianza, linea.indiceLinea, m.range, tipo,
            )
        }
        return resultado
    }
}

/** Formato definido por el servicio mediante una expresión regular. */
class FormatoPersonalizado private constructor(private val regex: Regex) : FormatoIdentificador {
    override val tipo = TipoFormato.PERSONALIZADO

    override fun canonizar(texto: String): String? {
        val mayus = TextoLinea.normalizarConservandoLongitud(texto).uppercase().trim()
        return if (regex.matches(mayus)) IdUnidad.normalizar(mayus) else null
    }

    override fun buscar(linea: TextoLinea): List<OcurrenciaId> {
        val texto = IdUnidad.mayusculas(linea)
        return regex.findAll(texto).filter { it.value.isNotBlank() }.map { m ->
            val frag = linea.fragmento(m.range.first, m.range.last + 1)
            OcurrenciaId(
                frag.texto, IdUnidad.normalizar(m.value), true, null, null,
                frag.caja, frag.confianza, linea.indiceLinea, m.range, tipo,
            )
        }.toList()
    }

    companion object {
        /** Devuelve null si la expresión no es válida o coincide con texto vacío. */
        fun crear(patron: String): FormatoPersonalizado? = runCatching {
            val r = Regex(patron)
            if (r.matches("")) null else FormatoPersonalizado(r)
        }.getOrNull()
    }
}

/** Carácter de control ISO 7064 Mod 37-2, usado por ISBT 128 sobre los 13 caracteres del DIN. */
object Mod37_2 {
    private const val ALFABETO = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ*"

    fun calcular(datos: String): Char {
        var p = 0
        for (c in datos.uppercase()) {
            val v = ALFABETO.indexOf(c)
            require(v in 0..35) { "Carácter no válido" }
            p = ((p + v) * 2) % 37
        }
        return ALFABETO[(38 - p) % 37]
    }

    fun verificar(datosConControl: String): Boolean =
        datosConControl.length >= 2 &&
            runCatching { calcular(datosConControl.dropLast(1)) == datosConControl.last().uppercaseChar() }.getOrDefault(false)
}
