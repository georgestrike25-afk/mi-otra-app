package com.hemocheck.ui.serologia

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import com.hemocheck.datos.ArchivosCifrados
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.captura.PaginaCapturada
import com.hemocheck.core.resultado.TipoRegistro
import com.hemocheck.imagen.decodificarReducido
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.textoTipo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaRegistro(contenedor: Contenedor, onFotografiar: () -> Unit, onRevisar: () -> Unit, onSalir: () -> Unit) {
    val b by contenedor.borradores.serologia.collectAsStateWithLifecycle()
    val ultimo by contenedor.borradores.ultimoLote.collectAsStateWithLifecycle()
    var confirmarSalida by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val alcance = rememberCoroutineScope()

    // Guarda las hojas sin revisión fila por fila: se verifican después, en el collage
    fun guardar() {
        guardando = true
        error = null
        alcance.launch(Dispatchers.Main) {
            try {
                val actual = contenedor.borradores.serologia.value
                val id = contenedor.repositorio.guardarSerologia(actual)
                contenedor.borradores.loteGuardado(id, actual.tipo)
            } catch (e: ArchivosCifrados.SinEspacioException) {
                error = R.string.error_sin_espacio
            } catch (e: Exception) {
                error = R.string.error_guardar
            } finally {
                guardando = false
            }
        }
    }

    val salir = {
        if (b.paginas.isNotEmpty()) confirmarSalida = true
        else {
            contenedor.borradores.olvidarUltimoLote()
            onSalir()
        }
    }

    Scaffold(topBar = { Barra(stringResource(R.string.registro_titulo), onAtras = salir) }) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ultimo?.let { (id, tipo) ->
                Aviso(stringResource(R.string.registro_guardado, id, stringResource(textoTipo(tipo))), Gravedad.CORRECTO)
                if (b.paginas.isEmpty()) {
                    val otro = if (tipo == TipoRegistro.CONVENCIONAL) TipoRegistro.MOLECULAR else TipoRegistro.CONVENCIONAL
                    OutlinedButton(onClick = {
                        contenedor.borradores.iniciarSerologia(otro, b.procedencia)
                        contenedor.borradores.olvidarUltimoLote()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.registro_continuar_otro, stringResource(textoTipo(otro))))
                    }
                }
            }

            Text(stringResource(R.string.registro_tipo), style = MaterialTheme.typography.titleMedium)
            TipoRegistro.entries.forEach { t ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = b.tipo == t, enabled = b.paginas.isEmpty(), role = Role.RadioButton) { contenedor.borradores.fijarTipo(t) }
                        .padding(vertical = 4.dp)
                        .testTag("tipo_${t.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = b.tipo == t, onClick = null, enabled = b.paginas.isEmpty())
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(textoTipo(t)), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (b.paginas.isNotEmpty()) Text(stringResource(R.string.registro_tipo_fijo), style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = b.procedencia,
                onValueChange = { contenedor.borradores.fijarProcedencia(it.take(120)) },
                label = { Text(stringResource(R.string.registro_procedencia)) },
                supportingText = { Text(stringResource(R.string.registro_procedencia_ayuda)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.registro_paginas, b.paginas.size), style = MaterialTheme.typography.titleMedium)
            b.paginas.forEachIndexed { i, p ->
                TarjetaPagina(i + 1, p, filas = b.filas.count { it.paginaUid == p.uid }, onQuitar = { contenedor.borradores.quitarPagina(p.uid) })
            }

            Button(onClick = onFotografiar, modifier = Modifier.fillMaxWidth().testTag("boton_fotografiar_hoja")) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (b.paginas.isEmpty()) R.string.registro_fotografiar_primera else R.string.registro_fotografiar_otra))
            }
            error?.let { Aviso(stringResource(it), Gravedad.PELIGRO) }
            Button(
                onClick = { guardar() },
                enabled = b.paginas.isNotEmpty() && !guardando,
                modifier = Modifier.fillMaxWidth().testTag("boton_guardar_hojas"),
            ) {
                if (guardando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.registro_guardar_hojas, b.paginas.size))
            }
            if (b.paginas.isNotEmpty()) Text(stringResource(R.string.registro_guardar_explicacion), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRevisar, enabled = b.paginas.isNotEmpty() && !guardando, modifier = Modifier.fillMaxWidth().testTag("boton_revisar")) {
                Text(stringResource(R.string.registro_revisar, b.filas.size))
            }
            Aviso(stringResource(R.string.registro_nota_cifrado), Gravedad.INFO)
        }
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text(stringResource(R.string.registro_descartar_titulo)) },
            text = { Text(stringResource(R.string.registro_descartar_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    contenedor.borradores.descartarSerologia()
                    onSalir()
                }) { Text(stringResource(R.string.descartar)) }
            },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

@Composable
private fun TarjetaPagina(numero: Int, p: PaginaCapturada, filas: Int, onQuitar: () -> Unit) {
    val miniatura by produceState<Bitmap?>(null, p.uid) {
        value = withContext(Dispatchers.Default) { decodificarReducido(p.jpegProcesada, 320) }
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            miniatura?.let {
                Image(it.asImageBitmap(), contentDescription = stringResource(R.string.pagina_n, numero), contentScale = ContentScale.Fit, modifier = Modifier.size(72.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pagina_n, numero), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.registro_filas_detectadas, filas), style = MaterialTheme.typography.bodySmall)
                if (p.avisos.isNotEmpty() || p.errorOcr) {
                    Text(stringResource(R.string.registro_pagina_con_avisos, p.avisos.size + if (p.errorOcr) 1 else 0), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onQuitar) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.registro_quitar_pagina)) }
        }
    }
}
