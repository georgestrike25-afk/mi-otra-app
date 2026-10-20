package com.hemocheck.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hemocheck.Contenedor
import com.hemocheck.captura.ModoCaptura
import com.hemocheck.ui.ajustes.PantallaAjustes
import com.hemocheck.ui.ayuda.PantallaAyuda
import com.hemocheck.ui.bloqueo.PantallaBloqueo
import com.hemocheck.ui.camara.PantallaCamara
import com.hemocheck.ui.comparacion.PantallaCollage
import com.hemocheck.ui.comparacion.PantallaComparacion
import com.hemocheck.ui.etiqueta.PantallaRevisionEtiqueta
import com.hemocheck.ui.historial.PantallaEntrada
import com.hemocheck.ui.historial.PantallaHistorial
import com.hemocheck.ui.inicio.PantallaInicio
import com.hemocheck.ui.serologia.PantallaRegistro
import com.hemocheck.ui.serologia.PantallaRevisionSerologia

object Rutas {
    const val INICIO = "inicio"
    const val REGISTRO = "registro"
    const val CAMARA = "camara/{modo}"
    const val REVISION_SEROLOGIA = "revision_serologia"
    const val REVISION_ETIQUETA = "revision_etiqueta"
    const val COMPARACION = "comparacion/{id}"
    const val DETALLE = "detalle/{id}"
    const val HISTORIAL = "historial"
    const val ENTRADA = "entrada/{id}"
    const val AJUSTES = "ajustes"
    const val AYUDA = "ayuda"

    fun camara(m: ModoCaptura) = "camara/${m.name}"
    fun comparacion(id: Long) = "comparacion/$id"
    fun detalle(id: Long) = "detalle/$id"
    fun entrada(id: Long) = "entrada/$id"
}

@Composable
fun AppHemoCheck(contenedor: Contenedor, biometriaDisponible: () -> Boolean, onPedirBiometria: () -> Unit) {
    val desbloqueada by contenedor.sesion.desbloqueada.collectAsStateWithLifecycle()
    val versionAjustes by contenedor.ajustes.version.collectAsStateWithLifecycle()

    if (!desbloqueada) {
        PantallaBloqueo(
            pin = contenedor.pin,
            biometriaDisponible = versionAjustes >= 0 && biometriaDisponible(),
            avisoAceptado = contenedor.ajustes.avisoInicialAceptado,
            onAceptarAviso = { contenedor.ajustes.avisoInicialAceptado = true },
            onPedirBiometria = onPedirBiometria,
            onDesbloqueada = { contenedor.sesion.desbloquear() },
        )
        return
    }

    // Tras cada desbloqueo se empieza desde Inicio (los borradores se descartaron al bloquear)
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Rutas.INICIO) {
        composable(Rutas.INICIO) {
            PantallaInicio(
                contenedor = contenedor,
                onRegistrar = { nav.navigate(Rutas.REGISTRO) },
                onVerificar = {
                    contenedor.borradores.descartarEtiqueta()
                    nav.navigate(Rutas.camara(ModoCaptura.ETIQUETA))
                },
                onHistorial = { nav.navigate(Rutas.HISTORIAL) },
                onAjustes = { nav.navigate(Rutas.AJUSTES) },
                onAyuda = { nav.navigate(Rutas.AYUDA) },
                onBloquear = { contenedor.sesion.bloquear() },
            )
        }
        composable(Rutas.REGISTRO) {
            PantallaRegistro(
                contenedor = contenedor,
                onFotografiar = { nav.navigate(Rutas.camara(ModoCaptura.HOJA)) },
                onRevisar = { nav.navigate(Rutas.REVISION_SEROLOGIA) },
                onSalir = { nav.popBackStack(Rutas.INICIO, inclusive = false) },
            )
        }
        composable(Rutas.CAMARA, arguments = listOf(navArgument("modo") { type = NavType.StringType })) { entrada ->
            val modo = ModoCaptura.valueOf(entrada.arguments?.getString("modo") ?: ModoCaptura.HOJA.name)
            PantallaCamara(
                contenedor = contenedor,
                modo = modo,
                onTerminar = {
                    if (modo == ModoCaptura.ETIQUETA) {
                        nav.navigate(Rutas.REVISION_ETIQUETA) { popUpTo(Rutas.INICIO) }
                    } else {
                        nav.popBackStack()
                    }
                },
                onCancelar = { nav.popBackStack() },
            )
        }
        composable(Rutas.REVISION_SEROLOGIA) {
            PantallaRevisionSerologia(
                contenedor = contenedor,
                onGuardado = { nav.popBackStack(Rutas.REGISTRO, inclusive = false) },
                onAtras = { nav.popBackStack() },
            )
        }
        composable(Rutas.REVISION_ETIQUETA) {
            PantallaRevisionEtiqueta(
                contenedor = contenedor,
                onRepetir = { nav.navigate(Rutas.camara(ModoCaptura.ETIQUETA)) { popUpTo(Rutas.INICIO) } },
                onVerificado = { id -> nav.navigate(Rutas.comparacion(id)) { popUpTo(Rutas.INICIO) } },
                onAtras = { nav.popBackStack(Rutas.INICIO, inclusive = false) },
            )
        }
        // Collage de tres filas (pantalla principal de la corroboración)
        composable(Rutas.COMPARACION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entrada ->
            val id = entrada.arguments?.getLong("id") ?: 0L
            PantallaCollage(
                contenedor = contenedor,
                verificacionId = id,
                onDetalle = { nav.navigate(Rutas.detalle(id)) },
                onAbrirEntrada = { nav.navigate(Rutas.entrada(it)) },
                onAtras = { nav.popBackStack() },
                onNuevaVerificacion = {
                    contenedor.borradores.descartarEtiqueta()
                    nav.navigate(Rutas.camara(ModoCaptura.ETIQUETA)) { popUpTo(Rutas.INICIO) }
                },
            )
        }
        composable(Rutas.DETALLE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entrada ->
            PantallaComparacion(
                contenedor = contenedor,
                verificacionId = entrada.arguments?.getLong("id") ?: 0L,
                onAbrirEntrada = { nav.navigate(Rutas.entrada(it)) },
                onAtras = { nav.popBackStack() },
                onNuevaVerificacion = {
                    contenedor.borradores.descartarEtiqueta()
                    nav.navigate(Rutas.camara(ModoCaptura.ETIQUETA)) { popUpTo(Rutas.INICIO) }
                },
            )
        }
        composable(Rutas.HISTORIAL) {
            PantallaHistorial(
                contenedor = contenedor,
                onAbrirVerificacion = { nav.navigate(Rutas.comparacion(it)) },
                onAbrirEntrada = { nav.navigate(Rutas.entrada(it)) },
                onAtras = { nav.popBackStack() },
            )
        }
        composable(Rutas.ENTRADA, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entrada ->
            PantallaEntrada(
                contenedor = contenedor,
                entradaId = entrada.arguments?.getLong("id") ?: 0L,
                onAtras = { nav.popBackStack() },
            )
        }
        composable(Rutas.AJUSTES) {
            PantallaAjustes(contenedor = contenedor, onAyuda = { nav.navigate(Rutas.AYUDA) }, onAtras = { nav.popBackStack() })
        }
        composable(Rutas.AYUDA) {
            PantallaAyuda(onAtras = { nav.popBackStack() })
        }
    }
}
