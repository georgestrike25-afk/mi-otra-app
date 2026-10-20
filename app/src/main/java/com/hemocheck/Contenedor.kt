package com.hemocheck

import android.content.Context
import com.hemocheck.captura.Borradores
import com.hemocheck.core.retencion.RelojProtegido
import com.hemocheck.datos.Ajustes
import com.hemocheck.datos.ArchivosCifrados
import com.hemocheck.datos.Repositorio
import com.hemocheck.datos.db.BaseDatos
import com.hemocheck.imagen.Procesador
import com.hemocheck.ocr.MotorOcr
import com.hemocheck.retencion.Limpieza
import com.hemocheck.seguridad.GestorPin
import com.hemocheck.seguridad.ProveedorClaves
import com.hemocheck.seguridad.Sesion
import java.io.File

/** Dependencias de la app (inyección manual, sin bibliotecas adicionales). */
class Contenedor(
    context: Context,
    val claves: ProveedorClaves,
    abrirBaseDatos: (ByteArray) -> BaseDatos,
) {
    private val app = context.applicationContext

    val ajustes = Ajustes(app.getSharedPreferences("hemocheck_ajustes", Context.MODE_PRIVATE))

    private val prefsReloj = app.getSharedPreferences("hemocheck_reloj", Context.MODE_PRIVATE)
    val reloj = RelojProtegido(
        leerMaximo = { prefsReloj.getLong("maximo", 0L) },
        guardarMaximo = { prefsReloj.edit().putLong("maximo", it).apply() },
        ahora = System::currentTimeMillis,
    )

    val bd: BaseDatos = abrirBaseDatos(claves.claveBaseDatos())
    val archivos = ArchivosCifrados(File(app.noBackupFilesDir, "boveda"))
    val repositorio = Repositorio(bd, archivos, ajustes, reloj)
    val limpieza = Limpieza(bd, archivos, reloj, ajustes)
    val borradores = Borradores()
    val sesion = Sesion { ajustes.minutosBloqueo }
    val pin = GestorPin(app.getSharedPreferences("hemocheck_seguridad", Context.MODE_PRIVATE), claves, reloj::ahoraMillis)
    val ocr = MotorOcr()
    val procesador = Procesador(ocr)

    init {
        // Nada en memoria sobrevive a un bloqueo
        sesion.alBloquearse { borradores.descartarTodo() }
    }
}
