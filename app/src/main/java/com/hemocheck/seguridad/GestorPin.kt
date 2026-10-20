package com.hemocheck.seguridad

import android.content.SharedPreferences
import com.hemocheck.core.seguridad.CredencialPin
import com.hemocheck.core.seguridad.DerivadorPin
import com.hemocheck.core.seguridad.PoliticaIntentosPin
import com.hemocheck.core.seguridad.ValidadorPin
import com.hemocheck.core.seguridad.limpiar

/**
 * PIN de acceso. Solo se guarda el verificador (PBKDF2 + HMAC de Keystore) y el registro de
 * intentos fallidos; el PIN nunca se escribe en el almacenamiento.
 */
class GestorPin(
    private val prefs: SharedPreferences,
    claves: ProveedorClaves,
    private val ahora: () -> Long,
    iteraciones: Int = DerivadorPin.ITERACIONES_POR_OMISION,
) {
    private val derivador = DerivadorPin(claves::hmacPin, iteraciones)

    sealed interface Verificacion {
        data object Correcto : Verificacion
        data class Incorrecto(val intentosAntesDeEspera: Int) : Verificacion
        data class EnEspera(val milisegundos: Long) : Verificacion
    }

    val configurado: Boolean get() = prefs.getString(CLAVE_CREDENCIAL, null) != null

    /** Devuelve el error de validación o null si se guardó. Limpia [pin] en ambos casos. */
    fun establecer(pin: CharArray): ValidadorPin.Error? {
        try {
            ValidadorPin.validar(pin)?.let { return it }
            val cred = derivador.crear(pin)
            prefs.edit().putString(CLAVE_CREDENCIAL, cred.serializar()).putInt(CLAVE_FALLOS, 0).putLong(CLAVE_ESPERA, 0).commit()
            return null
        } finally {
            pin.limpiar()
        }
    }

    fun esperaRestante(): Long = (prefs.getLong(CLAVE_ESPERA, 0) - ahora()).coerceAtLeast(0)

    fun verificar(pin: CharArray): Verificacion {
        try {
            val espera = esperaRestante()
            if (espera > 0) return Verificacion.EnEspera(espera)
            val cred = prefs.getString(CLAVE_CREDENCIAL, null)?.let { CredencialPin.deserializar(it) }
                ?: return Verificacion.Incorrecto(0)
            if (derivador.verificar(pin, cred)) {
                prefs.edit().putInt(CLAVE_FALLOS, 0).putLong(CLAVE_ESPERA, 0).commit()
                return Verificacion.Correcto
            }
            val fallos = prefs.getInt(CLAVE_FALLOS, 0) + 1
            val nuevaEspera = PoliticaIntentosPin.espera(fallos)
            prefs.edit().putInt(CLAVE_FALLOS, fallos)
                .putLong(CLAVE_ESPERA, if (nuevaEspera > 0) ahora() + nuevaEspera else 0).commit()
            return if (nuevaEspera > 0) Verificacion.EnEspera(nuevaEspera)
            else Verificacion.Incorrecto(PoliticaIntentosPin.INTENTOS_LIBRES - fallos)
        } finally {
            pin.limpiar()
        }
    }

    fun borrar() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val CLAVE_CREDENCIAL = "credencial_pin"
        private const val CLAVE_FALLOS = "fallos"
        private const val CLAVE_ESPERA = "espera_hasta"
    }
}
