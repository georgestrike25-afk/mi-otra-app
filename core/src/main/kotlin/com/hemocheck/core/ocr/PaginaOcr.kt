package com.hemocheck.core.ocr

import com.hemocheck.core.geometria.Caja
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Resultado del OCR de una imagen, independiente del motor utilizado.
 * Las coordenadas se refieren a la imagen procesada sobre la que se ejecutó el OCR.
 */
@Serializable
data class PaginaOcr(
    val ancho: Int,
    val alto: Int,
    val lineas: List<LineaOcr>,
) {
    val textoCompleto: String get() = lineas.joinToString("\n") { it.texto }
    val vacia: Boolean get() = lineas.none { it.texto.isNotBlank() }

    /** Confianza media de las palabras con confianza informada, o null si el motor no la informa. */
    val confianzaMedia: Float?
        get() {
            val valores = lineas.flatMap { l -> l.palabras.mapNotNull { it.confianza } }
            return if (valores.isEmpty()) null else valores.average().toFloat()
        }

    fun aJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun desdeJson(texto: String): PaginaOcr = json.decodeFromString(serializer(), texto)
    }
}

@Serializable
data class LineaOcr(
    val texto: String,
    val caja: Caja?,
    val confianza: Float? = null,
    /** Inclinación de la línea en grados, si el motor la informa. */
    val angulo: Float? = null,
    val palabras: List<PalabraOcr> = emptyList(),
)

@Serializable
data class PalabraOcr(
    val texto: String,
    val caja: Caja?,
    val confianza: Float? = null,
)
