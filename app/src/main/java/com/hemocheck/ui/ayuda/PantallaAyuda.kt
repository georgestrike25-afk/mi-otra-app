package com.hemocheck.ui.ayuda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hemocheck.BuildConfig
import com.hemocheck.R
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Barra
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.Seccion

@Composable
fun PantallaAyuda(onAtras: () -> Unit) {
    Scaffold(topBar = { Barra(stringResource(R.string.ayuda_titulo), onAtras = onAtras) }) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Aviso(stringResource(R.string.limite_clinico_largo), Gravedad.ADVERTENCIA, titulo = stringResource(R.string.limite_clinico_titulo))
            listOf(
                R.string.ayuda_uso_titulo to R.string.ayuda_uso,
                R.string.ayuda_camara_titulo to R.string.ayuda_camara,
                R.string.ayuda_ocr_titulo to R.string.ayuda_ocr,
                R.string.ayuda_estados_titulo to R.string.ayuda_estados,
                R.string.ayuda_privacidad_titulo to R.string.ayuda_privacidad,
                R.string.ayuda_retencion_titulo to R.string.ayuda_retencion,
                R.string.ayuda_limitaciones_titulo to R.string.ayuda_limitaciones,
            ).forEach { (t, c) -> Seccion(stringResource(t)) { Text(stringResource(c)) } }
            Text(stringResource(R.string.ayuda_version, BuildConfig.VERSION_NAME))
        }
    }
}
