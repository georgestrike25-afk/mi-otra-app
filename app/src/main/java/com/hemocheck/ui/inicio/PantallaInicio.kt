package com.hemocheck.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.core.corroboracion.EstadoEntrada
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.Gravedad

@Composable
fun PantallaInicio(
    contenedor: Contenedor,
    onRegistrar: () -> Unit,
    onVerificar: () -> Unit,
    onHistorial: () -> Unit,
    onAjustes: () -> Unit,
    onAyuda: () -> Unit,
    onBloquear: () -> Unit,
) {
    Scaffold(topBar = {
        Barra(stringResource(R.string.app_name), onAtras = null) {
            IconButton(onClick = onAyuda) { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.ayuda)) }
            IconButton(onClick = onBloquear) { Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.bloquear_ahora)) }
        }
    }) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Opcion(Icons.Filled.DocumentScanner, R.string.inicio_registrar, R.string.inicio_registrar_desc, "opcion_registrar", onRegistrar)
            Opcion(Icons.Filled.QrCodeScanner, R.string.inicio_verificar, R.string.inicio_verificar_desc, "opcion_verificar", onVerificar)
            Opcion(Icons.Filled.History, R.string.inicio_historial, R.string.inicio_historial_desc, "opcion_historial", onHistorial)
            Opcion(Icons.Filled.Settings, R.string.inicio_configuracion, R.string.inicio_configuracion_desc, "opcion_configuracion", onAjustes)
            Aviso(stringResource(R.string.limite_clinico_corto), Gravedad.INFO)
        }
    }
}

@Composable
private fun Opcion(icono: ImageVector, titulo: Int, descripcion: Int, tag: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).testTag(tag)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icono, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(titulo), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(descripcion), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
