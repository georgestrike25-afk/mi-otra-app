package com.hemocheck.seguridad

import android.os.SystemClock
import com.hemocheck.core.seguridad.TemporizadorInactividad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de bloqueo de la app. Al bloquearse se descartan los borradores en memoria
 * (fotos aún no guardadas), para que no queden accesibles sin desbloquear.
 */
class Sesion(minutosBloqueo: () -> Int) {
    private val _desbloqueada = MutableStateFlow(false)
    val desbloqueada: StateFlow<Boolean> = _desbloqueada.asStateFlow()

    private val temporizador = TemporizadorInactividad { minutosBloqueo().coerceAtLeast(1) * 60_000L }
    private val alBloquear = mutableListOf<() -> Unit>()

    fun alBloquearse(accion: () -> Unit) {
        alBloquear += accion
    }

    fun desbloquear() {
        temporizador.registrarActividad(SystemClock.elapsedRealtime())
        _desbloqueada.value = true
    }

    fun bloquear() {
        _desbloqueada.value = false
        alBloquear.forEach { runCatching { it() } }
    }

    private var salidaPermitidaHasta = 0L
    private var fueraDesde: Long? = null

    /**
     * Permite salir un momento al selector de archivos del sistema (para elegir un PDF) sin
     * bloquear ni perder las hojas en memoria. Si la ausencia dura más de [SALIDA_BREVE_MS],
     * se bloquea al volver.
     */
    fun permitirSalidaBreve() {
        if (_desbloqueada.value) salidaPermitidaHasta = SystemClock.elapsedRealtime() + SALIDA_BREVE_MS
    }

    /** La app pasó a segundo plano. */
    fun alSalir() {
        val ahora = SystemClock.elapsedRealtime()
        if (ahora < salidaPermitidaHasta) fueraDesde = ahora else bloquear()
    }

    /** La app volvió al primer plano. */
    fun alVolver() {
        if (fueraDesde != null && SystemClock.elapsedRealtime() > salidaPermitidaHasta) bloquear()
        fueraDesde = null
        salidaPermitidaHasta = 0L
    }

    fun registrarActividad() {
        if (_desbloqueada.value) temporizador.registrarActividad(SystemClock.elapsedRealtime())
    }

    /** Bloquea si se superó el tiempo de inactividad configurado. */
    fun revisarInactividad() {
        if (_desbloqueada.value && temporizador.vencido(SystemClock.elapsedRealtime())) bloquear()
    }

    companion object {
        const val SALIDA_BREVE_MS = 3 * 60_000L
    }
}
