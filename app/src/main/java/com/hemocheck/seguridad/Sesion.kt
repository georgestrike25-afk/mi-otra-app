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

    fun registrarActividad() {
        if (_desbloqueada.value) temporizador.registrarActividad(SystemClock.elapsedRealtime())
    }

    /** Bloquea si se superó el tiempo de inactividad configurado. */
    fun revisarInactividad() {
        if (_desbloqueada.value && temporizador.vencido(SystemClock.elapsedRealtime())) bloquear()
    }
}
