package com.hemocheck.core.resultado

import com.hemocheck.core.ocr.TextoLinea
import kotlinx.serialization.Serializable

/**
 * Categoría del resultado tal como está ESCRITO en el documento. No es una interpretación
 * clínica: solo clasifica el texto. Lo que no se reconoce queda como [NO_INTERPRETADO].
 */
@Serializable
enum class CategoriaResultado { REACTIVO, NO_REACTIVO, INDETERMINADO, PENDIENTE, NO_INTERPRETADO }

/** Tipo de registro de serología (lo declara el usuario al capturar las hojas). */
@Serializable
enum class TipoRegistro { CONVENCIONAL, MOLECULAR }

object ClasificadorResultado {
    // Sobre texto en minúsculas y sin tildes. Las negaciones van antes que las afirmaciones
    // para que «no reactivo» no se lea como «reactivo».
    private val noReactivo = listOf(
        "no\\s*-?\\s*reactiv[oa]s?", "n\\s*/\\s*r", "n\\.\\s*r\\.?", "nr",
        "no\\s+detectad[oa]s?", "no\\s+detectable", "no\\s+se\\s+detecta", "no\\s+detectado",
        "negativ[oa]s?", "neg\\.?", "\\(\\s*-\\s*\\)",
    )
    private val indeterminado = listOf(
        "indeterminad[oa]s?", "indet\\.?", "dudos[oa]s?", "zona\\s+gris",
    )
    private val pendiente = listOf(
        "pendientes?", "pend\\.", "en\\s+proceso", "en\\s+curso", "en\\s+estudio",
        "a\\s+repetir", "repetir", "sin\\s+resultado", "s\\s*/\\s*r",
    )
    private val reactivo = listOf(
        "reactiv[oa]s?(\\s+debil)?", "debil(mente)?\\s+reactiv[oa]", "positiv[oa]s?", "pos\\.?",
        "detectad[oa]s?", "detectable", "\\(\\s*\\+\\s*\\)", "r",
    )

    private val frases: List<Pair<CategoriaResultado, String>> =
        noReactivo.map { CategoriaResultado.NO_REACTIVO to it } +
            indeterminado.map { CategoriaResultado.INDETERMINADO to it } +
            pendiente.map { CategoriaResultado.PENDIENTE to it } +
            reactivo.map { CategoriaResultado.REACTIVO to it }

    // Una expresión por frase, para saber qué categoría coincidió
    private val regexes: List<Pair<CategoriaResultado, Regex>> = frases.map { (cat, p) ->
        cat to Regex("(?<![\\p{L}\\p{N}])(?:$p)(?![\\p{L}\\p{N}])")
    }

    data class Frase(val categoria: CategoriaResultado, val fragmento: TextoLinea.Fragmento)

    /** Busca frases de resultado en una línea, sin solapamientos (gana la más larga). */
    fun buscar(linea: TextoLinea): List<Frase> {
        val candidatas = regexes.flatMap { (cat, r) -> linea.buscarNormalizado(r).map { Frase(cat, it) } }
            .sortedByDescending { it.fragmento.fin - it.fragmento.inicio }
        val elegidas = mutableListOf<Frase>()
        for (c in candidatas) {
            if (elegidas.none { it.fragmento.inicio < c.fragmento.fin && c.fragmento.inicio < it.fragmento.fin }) {
                elegidas += c
            }
        }
        return elegidas.sortedBy { it.fragmento.inicio }
    }

    /** Clasifica un texto completo (p. ej. corregido a mano). Null si no es una frase de resultado. */
    fun clasificar(texto: String): CategoriaResultado? {
        val t = TextoLinea.normalizarConservandoLongitud(texto.trim())
        if (t.isEmpty()) return null
        return regexes.firstOrNull { (_, r) -> r.matchEntire(t) != null }?.first
    }
}

/** Determinación serológica que puede aparecer como encabezado de columna o etiqueta. */
data class Determinacion(
    val clave: String,
    val nombre: String,
    val tipo: TipoRegistro,
    val patron: Regex,
    /** Determinaciones que este resultado documenta a la vez (p. ej. NAT multiplex). */
    val cubre: Set<String> = setOf(clave),
)

object CatalogoDeterminaciones {
    private fun r(p: String) = Regex("(?<![\\p{L}\\p{N}])(?:$p)(?![\\p{L}\\p{N}])")

    val convencionales: List<Determinacion> = listOf(
        Determinacion("HIV", "HIV 1/2", TipoRegistro.CONVENCIONAL,
            r("(anti\\s*-?\\s*)?(hiv|vih)(\\s*-?\\s*1\\s*(/|y|-|\\+)?\\s*2)?(\\s*-?\\s*(p24|ag\\s*/\\s*ac|ag\\s*-\\s*ac|ac|ag)){0,2}")),
        Determinacion("HBSAG", "HBsAg", TipoRegistro.CONVENCIONAL,
            r("hbs\\s*-?\\s*ag|ag\\s*hbs|agshb|antigeno\\s+de\\s+superficie")),
        Determinacion("ANTI_HBC", "Anti-HBc", TipoRegistro.CONVENCIONAL,
            r("anti\\s*-?\\s*hbc(\\s+total)?|(ac\\s*)?hbc(\\s*(ac|total))?|anti\\s*-?\\s*core|anticore|core")),
        Determinacion("HCV", "HCV", TipoRegistro.CONVENCIONAL,
            r("(anti\\s*-?\\s*)?(hcv|vhc|hvc)(\\s*-?\\s*(ac|ag\\s*/\\s*ac))?|hepatitis\\s+c")),
        Determinacion("CHAGAS", "Chagas", TipoRegistro.CONVENCIONAL,
            r("chagas(\\s*(hai|elisa|iha|ifi|eia|cmia|clia|qlia))?|t\\.?\\s*cruzi|tr[iy]panosoma(\\s+cruzi)?")),
        Determinacion("SIFILIS", "Sífilis", TipoRegistro.CONVENCIONAL,
            r("sifilis|vdrl|rpr|treponema(\\s+pallidum)?|t\\.?\\s*pallidum|tpha|tppa|fta(\\s*-?\\s*abs)?")),
        Determinacion("HTLV", "HTLV I/II", TipoRegistro.CONVENCIONAL,
            r("htlv(\\s*-?\\s*(i\\s*[/+-]\\s*ii|1\\s*[/+-]\\s*2|i\\s*y\\s*ii))?")),
        Determinacion("BRUCELOSIS", "Brucelosis", TipoRegistro.CONVENCIONAL,
            r("brucel+osis|brucella|huddleson")),
    )

    val moleculares: List<Determinacion> = listOf(
        Determinacion("NAT_MULTIPLEX", "NAT multiplex (HIV/HCV/HBV)", TipoRegistro.MOLECULAR,
            r("(nat\\s*)?(multiplex|mpx|ultrio(\\s+elite|\\s+plus)?)|(hiv|vih)\\s*[/-]\\s*(hcv|vhc)\\s*[/-]\\s*(hbv|vhb)"),
            cubre = setOf("HIV", "HCV", "HBV")),
        Determinacion("HIV", "HIV (NAT)", TipoRegistro.MOLECULAR,
            r("(nat\\s*-?\\s*)?(hiv|vih)(\\s*-?\\s*1)?(\\s*-?\\s*(arn|rna|nat|pcr|carga\\s+viral))?")),
        Determinacion("HCV", "HCV (NAT)", TipoRegistro.MOLECULAR,
            r("(nat\\s*-?\\s*)?(hcv|vhc|hvc)(\\s*-?\\s*(arn|rna|nat|pcr))?")),
        Determinacion("HBV", "HBV (NAT)", TipoRegistro.MOLECULAR,
            r("(nat\\s*-?\\s*)?(hbv|vhb)(\\s*-?\\s*(adn|dna|nat|pcr))?")),
    )

    fun de(tipo: TipoRegistro): List<Determinacion> =
        if (tipo == TipoRegistro.CONVENCIONAL) convencionales else moleculares

    fun buscarPorClave(tipo: TipoRegistro, clave: String): Determinacion? = de(tipo).firstOrNull { it.clave == clave }

    /** Determinaciones esperadas por omisión (configurables por el servicio). */
    val esperadasPorOmision: Map<TipoRegistro, List<String>> = mapOf(
        TipoRegistro.CONVENCIONAL to listOf("HIV", "HBSAG", "ANTI_HBC", "HCV", "CHAGAS", "SIFILIS", "HTLV", "BRUCELOSIS"),
        TipoRegistro.MOLECULAR to listOf("HIV", "HCV", "HBV"),
    )

    data class Ocurrencia(val determinacion: Determinacion, val fragmento: TextoLinea.Fragmento)

    /** Busca nombres de determinaciones en una línea, sin solapamientos (gana la más larga). */
    fun buscar(linea: TextoLinea, tipo: TipoRegistro): List<Ocurrencia> {
        val candidatas = de(tipo).flatMap { d -> linea.buscarNormalizado(d.patron).map { Ocurrencia(d, it) } }
            .sortedByDescending { it.fragmento.fin - it.fragmento.inicio }
        val elegidas = mutableListOf<Ocurrencia>()
        for (c in candidatas) {
            if (elegidas.none { it.fragmento.inicio < c.fragmento.fin && c.fragmento.inicio < it.fragmento.fin }) {
                elegidas += c
            }
        }
        return elegidas.sortedBy { it.fragmento.inicio }
    }
}
