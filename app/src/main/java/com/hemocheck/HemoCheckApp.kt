package com.hemocheck

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.hemocheck.datos.db.BaseDatos
import com.hemocheck.retencion.Limpieza
import com.hemocheck.seguridad.AlmacenClavesAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class HemoCheckApp : Application() {
    /** Null si no se pudo abrir el almacenamiento protegido (ver [errorInicio]). */
    @Volatile var contenedor: Contenedor? = null
        private set

    /** Tipo de error al iniciar (sin detalles que puedan contener datos). */
    @Volatile var errorInicio: String? = null
        private set

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        iniciar()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            // Al pasar a segundo plano (otra app, pantalla apagada) se bloquea
            // (salvo una salida breve al selector de archivos para elegir un PDF)
            override fun onStop(owner: LifecycleOwner) {
                contenedor?.sesion?.alSalir()
            }

            // Al volver, se vuelve a comprobar el vencimiento y se limpia lo vencido
            override fun onStart(owner: LifecycleOwner) {
                contenedor?.sesion?.alVolver()
                limpiarEnSegundoPlano()
            }
        })
        Limpieza.programar(this)
    }

    private fun iniciar() {
        try {
            contenedor = Contenedor(this, AlmacenClavesAndroid(this), { clave -> BaseDatos.abrirCifrada(this, clave) })
            errorInicio = null
        } catch (e: Throwable) {
            contenedor = null
            errorInicio = e.javaClass.simpleName
        }
    }

    private fun limpiarEnSegundoPlano() {
        val c = contenedor ?: return
        alcance.launch { runCatching { c.limpieza.ejecutar() } }
    }

    /**
     * Elimina todos los datos y claves y vuelve a empezar. Solo se ofrece cuando las claves de
     * cifrado ya no están disponibles (los datos ya no serían recuperables).
     */
    fun restablecer() {
        runCatching { contenedor?.bd?.close() }
        contenedor = null
        AlmacenClavesAndroid(this).destruir()
        deleteDatabase(BaseDatos.NOMBRE)
        File(noBackupFilesDir, "boveda").deleteRecursively()
        for (nombre in listOf("hemocheck_ajustes", "hemocheck_seguridad", "hemocheck_reloj")) {
            getSharedPreferences(nombre, Context.MODE_PRIVATE).edit().clear().commit()
        }
        iniciar()
    }
}
