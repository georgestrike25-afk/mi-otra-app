package com.hemocheck.datos

import com.hemocheck.core.retencion.ArchivoAlmacen
import com.hemocheck.core.retencion.SistemaArchivos
import com.hemocheck.core.seguridad.CifradorAesGcm
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Almacén de imágenes cifradas en el directorio privado de la app sin copia de seguridad
 * (noBackupFilesDir/boveda). Los nombres son aleatorios y no revelan nada del contenido.
 * Las imágenes se descifran solo en memoria: nunca se escriben archivos temporales en claro.
 */
class ArchivosCifrados(private val directorio: File) : SistemaArchivos {
    init {
        directorio.mkdirs()
    }

    class SinEspacioException : IOException("Espacio insuficiente")

    fun nuevoNombre(): String = UUID.randomUUID().toString() + EXTENSION

    fun espacioLibre(): Long = directorio.usableSpace

    fun guardar(nombre: String, clave: ByteArray, datos: ByteArray) {
        if (espacioLibre() < datos.size + ESPACIO_RESERVADO) throw SinEspacioException()
        val cifrado = CifradorAesGcm.cifrar(clave, datos, nombre.toByteArray())
        val temporal = File(directorio, "$nombre.parcial")
        temporal.writeBytes(cifrado)
        if (!temporal.renameTo(File(directorio, nombre))) {
            temporal.delete()
            throw IOException("No se pudo guardar el archivo")
        }
    }

    /** @throws com.hemocheck.core.seguridad.DatosCorruptosException si el archivo fue alterado. */
    fun leer(nombre: String, clave: ByteArray): ByteArray =
        CifradorAesGcm.descifrar(clave, File(directorio, nombre).readBytes(), nombre.toByteArray())

    override fun listar(): List<ArchivoAlmacen> =
        directorio.listFiles()?.filter { it.isFile }?.map { ArchivoAlmacen(it.name, it.lastModified()) }.orEmpty()

    override fun existe(nombre: String): Boolean = File(directorio, nombre).exists()

    override fun eliminar(nombre: String): Boolean {
        val f = File(directorio, nombre)
        return !f.exists() || f.delete()
    }

    fun tamanoTotal(): Long = directorio.listFiles()?.sumOf { it.length() } ?: 0L

    fun eliminarTodo() {
        directorio.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val EXTENSION = ".hce"
        /** Margen mínimo de espacio libre para no dejar el teléfono sin almacenamiento. */
        const val ESPACIO_RESERVADO = 50L * 1024 * 1024
    }
}
