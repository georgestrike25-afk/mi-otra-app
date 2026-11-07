package com.hemocheck.core.etiqueta

import com.hemocheck.core.ocr.LineaOcr
import java.text.Normalizer

/** Qué se encontró: una palabra o dato de paciente (bloquea) o un texto que parece un nombre (se puede confirmar). */
enum class TipoDatoPersonal(val duro: Boolean) {
    PALABRA_CLAVE(true),
    NUMERO_HC(true),
    FECHA_NACIMIENTO(true),
    POSIBLE_NOMBRE(false),
}

data class HallazgoPersonal(val tipo: TipoDatoPersonal, val texto: String)

/**
 * Detecta datos de un paciente en el texto de la foto de una etiqueta (nombre, historia clínica,
 * fecha de nacimiento). La foto de la etiqueta no debe guardarse si aparece alguno: HemoCheck solo
 * necesita el número de la unidad. Es una detección por texto: no reemplaza el cuidado de tapar el nombre.
 */
object DetectorDatosPersonales {
    private val PALABRA_CLAVE = Regex(
        "(?<![a-z])(paciente|apellidos?|nombres?|dni|d\\.n\\.i\\.?|documento|historia\\s+clinica|fecha\\s+de\\s+nacimiento|nacimiento)(?![a-z])",
    )
    private val HISTORIA_CLINICA = Regex("(?<![a-z])h\\.?\\s?c\\.?\\s*[:.\\-]?\\s*(\\d{3,})")
    private val NACIMIENTO = Regex("(?<![a-z])f\\.?\\s?n\\.?\\s*[:.\\-]\\s*\\d")

    /** Palabras habituales de una etiqueta de hemocomponente; el resto de las palabras de 4 letras o más se sospecha. */
    private val VOCABULARIO = setOf(
        "hospital", "privado", "universitario", "cordoba", "nunez", "clinica", "sanatorio", "central",
        "globulos", "globulo", "rojos", "rojo", "sedimentados", "sedimentado", "concentrado", "concentrados",
        "plaquetas", "plaqueta", "plasma", "fresco", "congelado", "congelados", "crioprecipitado",
        "unidad", "extraccion", "venc", "vencimiento", "volumen", "conservar", "serologia", "reactiva", "reactivo",
        "positivo", "negativo", "grupo", "sanguineo", "donante", "donador", "donacion", "unico", "aferesis",
        "leucorreducidos", "leucorreducido", "desleucocitados", "irradiados", "lavados", "fecha", "lote",
        "etiqueta", "servicio", "hemoterapia", "banco", "sangre", "label", "bolsa", "bolsas", "para", "como",
        "cmia", "cons", "ficticio", "ficticia", "chcmia", "cheia", "chagas", "sifilis", "bengala", "brucelosis", "htlv", "hbsag", "optisol", "manitol",
    )

    /** [idUnidad]: número de la unidad ya leído; un «Hc:» que repite ese número es el formato propio de la etiqueta. */
    fun detectar(lineas: List<LineaOcr>, idUnidad: String?): HallazgoPersonal? {
        val textos = lineas.map { it.texto }.filter { it.isNotBlank() }
        for (t in textos) {
            val n = normalizar(t)
            PALABRA_CLAVE.find(n)?.let { return HallazgoPersonal(TipoDatoPersonal.PALABRA_CLAVE, t) }
            if (NACIMIENTO.containsMatchIn(n)) return HallazgoPersonal(TipoDatoPersonal.FECHA_NACIMIENTO, t)
            for (m in HISTORIA_CLINICA.findAll(n)) {
                val digitos = m.groupValues[1]
                val propio = idUnidad != null && (idUnidad.startsWith(digitos) || digitos.startsWith(idUnidad))
                if (!propio) return HallazgoPersonal(TipoDatoPersonal.NUMERO_HC, t)
            }
        }
        // Palabras desconocidas (sin dígitos): dos o más en la etiqueta suelen ser un nombre y apellido
        val desconocidas = textos.flatMap { t ->
            normalizar(t).split(Regex("[^a-z0-9]+")).filter { w -> w.length >= 4 && w.all { it in 'a'..'z' } && w !in VOCABULARIO }
        }
        return if (desconocidas.size >= 2) HallazgoPersonal(TipoDatoPersonal.POSIBLE_NOMBRE, desconocidas.take(3).joinToString(" ").uppercase()) else null
    }

    private fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
