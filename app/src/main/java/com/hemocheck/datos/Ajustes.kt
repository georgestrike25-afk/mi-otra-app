package com.hemocheck.datos

import android.content.SharedPreferences
import com.hemocheck.core.identificador.ConfiguracionIdentificadores
import com.hemocheck.core.resultado.CatalogoDeterminaciones
import com.hemocheck.core.resultado.TipoRegistro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Preferencias de la app. No contienen datos de donantes. */
class Ajustes(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _version = MutableStateFlow(0)
    /** Cambia cada vez que se modifica un ajuste (para refrescar la interfaz). */
    val version: StateFlow<Int> = _version.asStateFlow()

    var minutosBloqueo: Int
        get() = prefs.getInt("minutos_bloqueo", 3)
        set(v) = guardar { putInt("minutos_bloqueo", v) }

    var biometria: Boolean
        get() = prefs.getBoolean("biometria", false)
        set(v) = guardar { putBoolean("biometria", v) }

    var identificadores: ConfiguracionIdentificadores
        get() = prefs.getString("identificadores", null)
            ?.let { runCatching { json.decodeFromString(ConfiguracionIdentificadores.serializer(), it) }.getOrNull() }
            ?: ConfiguracionIdentificadores()
        set(v) = guardar { putString("identificadores", json.encodeToString(ConfiguracionIdentificadores.serializer(), v)) }

    fun esperadas(tipo: TipoRegistro): List<String> =
        prefs.getString("esperadas_${tipo.name}", null)?.split(",")?.filter { it.isNotBlank() }
            ?: CatalogoDeterminaciones.esperadasPorOmision[tipo].orEmpty()

    fun fijarEsperadas(tipo: TipoRegistro, claves: List<String>) =
        guardar { putString("esperadas_${tipo.name}", claves.joinToString(",")) }

    fun todasEsperadas(): Map<TipoRegistro, List<String>> = TipoRegistro.entries.associateWith { esperadas(it) }

    var ultimaLimpieza: Long
        get() = prefs.getLong("ultima_limpieza", 0)
        set(v) = guardar { putLong("ultima_limpieza", v) }

    var ultimaLimpiezaCompleta: Boolean
        get() = prefs.getBoolean("ultima_limpieza_completa", true)
        set(v) = guardar { putBoolean("ultima_limpieza_completa", v) }

    var avisoInicialAceptado: Boolean
        get() = prefs.getBoolean("aviso_inicial", false)
        set(v) = guardar { putBoolean("aviso_inicial", v) }

    private inline fun guardar(bloque: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(bloque).apply()
        _version.value++
    }
}
