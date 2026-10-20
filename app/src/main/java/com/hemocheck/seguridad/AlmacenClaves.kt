package com.hemocheck.seguridad

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.hemocheck.core.seguridad.CifradorAesGcm
import com.hemocheck.core.seguridad.limpiar
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Origen de las claves de la app; se reemplaza por uno falso en las pruebas sin Keystore. */
interface ProveedorClaves {
    /** Clave aleatoria de 256 bits para abrir la base de datos SQLCipher. */
    fun claveBaseDatos(): ByteArray

    /** HMAC-SHA256 con una clave que nunca sale del dispositivo (pimienta del PIN). */
    fun hmacPin(datos: ByteArray): ByteArray

    /** Elimina las claves (la información cifrada con ellas deja de ser recuperable). */
    fun destruir()
}

/**
 * Claves protegidas por Android Keystore (en el S24+, respaldadas por hardware; se intenta
 * StrongBox y si no está disponible se usa el TEE).
 *
 * - La clave maestra AES-256-GCM nunca sale del Keystore; con ella se envuelve la clave
 *   aleatoria de la base de datos, que se guarda cifrada en el almacenamiento sin copia de
 *   seguridad (noBackupFilesDir).
 * - Cada imagen se cifra con su propia clave aleatoria guardada dentro de la base cifrada.
 * - Las claves no exigen autenticación del usuario para que la limpieza programada pueda
 *   ejecutarse en segundo plano; el acceso a la app se controla con PIN o biometría.
 */
class AlmacenClavesAndroid(private val context: Context) : ProveedorClaves {
    private val keyStore: KeyStore = KeyStore.getInstance(PROVEEDOR).apply { load(null) }
    private val archivoClaveBd: File get() = File(File(context.noBackupFilesDir, "claves").apply { mkdirs() }, "bd.bin")

    @Synchronized
    override fun claveBaseDatos(): ByteArray {
        val maestra = obtenerOCrear(ALIAS_MAESTRA) { generarMaestra(it) }
        val archivo = archivoClaveBd
        if (archivo.exists()) {
            val paquete = archivo.readBytes()
            val iv = paquete.copyOfRange(0, CifradorAesGcm.LARGO_IV)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, maestra, GCMParameterSpec(CifradorAesGcm.LARGO_ETIQUETA_BITS, iv))
            cipher.updateAAD(AAD_BD)
            return cipher.doFinal(paquete, CifradorAesGcm.LARGO_IV, paquete.size - CifradorAesGcm.LARGO_IV)
        }
        val nueva = CifradorAesGcm.nuevaClave()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, maestra)
        cipher.updateAAD(AAD_BD)
        val cifrada = cipher.doFinal(nueva)
        val temporal = File(archivo.parentFile, "bd.bin.tmp")
        temporal.writeBytes(cipher.iv + cifrada)
        check(temporal.renameTo(archivo)) { "No se pudo guardar la clave" }
        return nueva
    }

    override fun hmacPin(datos: ByteArray): ByteArray {
        val clave = obtenerOCrear(ALIAS_PIN) { alias ->
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVEEDOR)
            gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
            gen.generateKey()
        }
        return Mac.getInstance("HmacSHA256").apply { init(clave) }.doFinal(datos)
    }

    @Synchronized
    override fun destruir() {
        runCatching { keyStore.deleteEntry(ALIAS_MAESTRA) }
        runCatching { keyStore.deleteEntry(ALIAS_PIN) }
        archivoClaveBd.delete()
    }

    private fun obtenerOCrear(alias: String, crear: (String) -> SecretKey): SecretKey =
        (keyStore.getKey(alias, null) as? SecretKey) ?: crear(alias)

    private fun generarMaestra(alias: String): SecretKey {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        return try {
            gen.init(spec(strongBox = true))
            gen.generateKey()
        } catch (e: Exception) {
            // Sin StrongBox (StrongBoxUnavailableException u otro fallo): se genera en el TEE
            gen.init(spec(strongBox = false))
            gen.generateKey()
        }
    }

    companion object {
        private const val PROVEEDOR = "AndroidKeyStore"
        private const val ALIAS_MAESTRA = "hemocheck_maestra_v1"
        private const val ALIAS_PIN = "hemocheck_pin_v1"
        private val AAD_BD = "hemocheck-bd-v1".toByteArray()
    }
}

/** Utilidad para descartar copias de claves en memoria. */
inline fun <T> ByteArray.usarYLimpiar(bloque: (ByteArray) -> T): T = try {
    bloque(this)
} finally {
    limpiar()
}
