package com.hemocheck.core.seguridad

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado autenticado AES-256-GCM con el proveedor criptográfico de la plataforma (JCA).
 * Formato: "HC" + versión (1 byte) + IV de 12 bytes + texto cifrado con etiqueta de 16 bytes.
 * Los datos asociados (AAD) vinculan el contenido a su contexto (p. ej. el nombre del archivo),
 * de modo que un archivo cifrado no puede intercambiarse por otro sin que falle el descifrado.
 */
object CifradorAesGcm {
    private const val VERSION: Byte = 1
    private val MAGIA = byteArrayOf('H'.code.toByte(), 'C'.code.toByte())
    const val LARGO_IV = 12
    const val LARGO_ETIQUETA_BITS = 128
    const val LARGO_CLAVE = 32
    private val azar = SecureRandom()

    fun nuevaClave(): ByteArray = ByteArray(LARGO_CLAVE).also { azar.nextBytes(it) }

    fun cifrar(clave: ByteArray, datos: ByteArray, aad: ByteArray? = null): ByteArray {
        require(clave.size == LARGO_CLAVE) { "La clave debe tener 256 bits" }
        val iv = ByteArray(LARGO_IV).also { azar.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(LARGO_ETIQUETA_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        val cifrado = cipher.doFinal(datos)
        return ByteBuffer.allocate(MAGIA.size + 1 + LARGO_IV + cifrado.size)
            .put(MAGIA).put(VERSION).put(iv).put(cifrado).array()
    }

    /** @throws DatosCorruptosException si el contenido fue alterado, la clave o el AAD no coinciden. */
    fun descifrar(clave: ByteArray, paquete: ByteArray, aad: ByteArray? = null): ByteArray {
        require(clave.size == LARGO_CLAVE) { "La clave debe tener 256 bits" }
        val cabecera = MAGIA.size + 1 + LARGO_IV
        if (paquete.size < cabecera + LARGO_ETIQUETA_BITS / 8 ||
            paquete[0] != MAGIA[0] || paquete[1] != MAGIA[1] || paquete[2] != VERSION
        ) throw DatosCorruptosException()
        val iv = paquete.copyOfRange(MAGIA.size + 1, cabecera)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(LARGO_ETIQUETA_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return try {
            cipher.doFinal(paquete, cabecera, paquete.size - cabecera)
        } catch (e: AEADBadTagException) {
            throw DatosCorruptosException()
        }
    }
}

class DatosCorruptosException : Exception("Los datos cifrados no son válidos o fueron modificados")

/** Sobrescribe con ceros un arreglo con material sensible cuando deja de usarse. */
fun ByteArray.limpiar() = fill(0)
fun CharArray.limpiar() = fill('\u0000')

/** Credencial del PIN guardada: nunca contiene el PIN, solo sal y verificador. */
data class CredencialPin(val version: Int, val sal: ByteArray, val iteraciones: Int, val verificador: ByteArray) {
    fun serializar(): String = listOf(
        version.toString(), iteraciones.toString(),
        java.util.Base64.getEncoder().encodeToString(sal),
        java.util.Base64.getEncoder().encodeToString(verificador),
    ).joinToString(":")

    companion object {
        fun deserializar(s: String): CredencialPin? = runCatching {
            val p = s.split(":")
            require(p.size == 4)
            CredencialPin(
                p[0].toInt(), java.util.Base64.getDecoder().decode(p[2]), p[1].toInt(),
                java.util.Base64.getDecoder().decode(p[3]),
            )
        }.getOrNull()
    }

    override fun equals(other: Any?) = other is CredencialPin && version == other.version &&
        iteraciones == other.iteraciones && sal.contentEquals(other.sal) && verificador.contentEquals(other.verificador)

    override fun hashCode() = sal.contentHashCode()
}

/**
 * Derivación del PIN: PBKDF2-HMAC-SHA256 con sal aleatoria, y el resultado se pasa por una
 * función «pimienta» (en Android, un HMAC con una clave no exportable de Android Keystore).
 * Así, aunque alguien copiara el archivo con el verificador, no podría probar PINs fuera del
 * teléfono.
 */
class DerivadorPin(
    private val pimienta: (ByteArray) -> ByteArray,
    private val iteraciones: Int = ITERACIONES_POR_OMISION,
) {
    fun crear(pin: CharArray): CredencialPin {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return CredencialPin(VERSION, sal, iteraciones, derivar(pin, sal, iteraciones))
    }

    fun verificar(pin: CharArray, credencial: CredencialPin): Boolean {
        if (credencial.version != VERSION) return false
        val calculado = derivar(pin, credencial.sal, credencial.iteraciones)
        return MessageDigest.isEqual(calculado, credencial.verificador).also { calculado.limpiar() }
    }

    private fun derivar(pin: CharArray, sal: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(pin, sal, iter, 256)
        try {
            val base = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return pimienta(base).also { base.limpiar() }
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val VERSION = 1
        const val ITERACIONES_POR_OMISION = 200_000
    }
}

object ValidadorPin {
    const val LARGO_MIN = 6
    const val LARGO_MAX = 12

    enum class Error { LARGO, NO_NUMERICO, TRIVIAL }

    fun validar(pin: CharArray): Error? {
        if (pin.size !in LARGO_MIN..LARGO_MAX) return Error.LARGO
        if (pin.any { it !in '0'..'9' }) return Error.NO_NUMERICO
        val todosIguales = pin.all { it == pin[0] }
        val ascendente = pin.indices.drop(1).all { pin[it] - pin[it - 1] == 1 }
        val descendente = pin.indices.drop(1).all { pin[it - 1] - pin[it] == 1 }
        return if (todosIguales || ascendente || descendente) Error.TRIVIAL else null
    }
}

/** Espera creciente después de varios PIN incorrectos. */
object PoliticaIntentosPin {
    const val INTENTOS_LIBRES = 5

    /** Milisegundos de espera tras [fallos] intentos fallidos consecutivos. */
    fun espera(fallos: Int): Long = when {
        fallos < INTENTOS_LIBRES -> 0L
        fallos == 5 -> 30_000L
        fallos == 6 -> 60_000L
        fallos == 7 -> 2 * 60_000L
        fallos == 8 -> 5 * 60_000L
        else -> 15 * 60_000L
    }
}

/** Bloqueo por inactividad (lógica pura; la app la alimenta con el reloj del sistema). */
class TemporizadorInactividad(private val limiteMillis: () -> Long) {
    @Volatile private var ultimaActividad: Long = 0L

    fun registrarActividad(ahora: Long) {
        ultimaActividad = ahora
    }

    fun vencido(ahora: Long): Boolean = ultimaActividad != 0L && ahora - ultimaActividad >= limiteMillis()
}
