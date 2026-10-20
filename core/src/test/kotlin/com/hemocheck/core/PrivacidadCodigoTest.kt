package com.hemocheck.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Revisión estática del código de la app (sin ejecutarlo): ausencia de registros de depuración
 * con datos, de permisos de red, de cámaras externas y de copias de seguridad.
 */
class PrivacidadCodigoTest {
    private val app: File? = System.getProperty("hemocheck.raiz")?.let { raiz ->
        listOf(File(raiz, "app"), File(raiz, "../app")).firstOrNull { File(it, "src/main").isDirectory }
    }

    private fun fuentes(): List<File> {
        assumeTrue("No se encontró el código de la app", app != null)
        return File(app, "src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private val manifiesto: String
        get() {
            assumeTrue(app != null)
            return File(app, "src/main/AndroidManifest.xml").readText()
        }

    @Test
    fun `no hay llamadas a Log ni println`() {
        val malas = fuentes().filter { f ->
            val t = f.readText()
            Regex("\\bLog\\.[vdiwe]\\(").containsMatchIn(t) || "import android.util.Log" in t || Regex("\\bprintln\\(").containsMatchIn(t) ||
                Regex("printStackTrace\\(").containsMatchIn(t)
        }
        assertTrue("Archivos con registros: ${malas.map { it.name }}", malas.isEmpty())
    }

    @Test
    fun `no se usa una camara externa ni la galeria`() {
        val malas = fuentes().filter { f ->
            val t = f.readText()
            "ACTION_IMAGE_CAPTURE" in t || "MediaStore" in t || "TakePicture()" in t || "ACTION_PICK" in t || "GetContent" in t
        }
        assertTrue("Archivos con cámara externa o galería: ${malas.map { it.name }}", malas.isEmpty())
    }

    @Test
    fun `sin permiso de Internet ni copias de seguridad`() {
        val m = manifiesto
        assertTrue(Regex("android.permission.INTERNET\"\\s+tools:node=\"remove\"").containsMatchIn(m))
        assertFalse(Regex("<uses-permission android:name=\"android.permission.INTERNET\"\\s*/>").containsMatchIn(m))
        assertTrue("android:allowBackup=\"false\"" in m)
        assertTrue("android:dataExtractionRules" in m)
    }

    @Test
    fun `pantallas protegidas contra capturas`() {
        val main = fuentes().first { it.name == "MainActivity.kt" }.readText()
        assertTrue("FLAG_SECURE" in main)
    }

    @Test
    fun `sin bibliotecas de red, analiticas ni publicidad`() {
        assumeTrue(app != null)
        val build = File(app, "build.gradle.kts").readText() + File(app, "../gradle/libs.versions.toml").readText()
        for (prohibida in listOf("okhttp", "retrofit", "firebase", "analytics", "crashlytics", "play-services-ads", "ktor")) {
            assertFalse("Dependencia no permitida: $prohibida", build.contains(prohibida, ignoreCase = true))
        }
    }
}
