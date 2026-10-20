package com.hemocheck.ui.camara

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hemocheck.Contenedor
import com.hemocheck.R
import com.hemocheck.captura.ModoCaptura
import com.hemocheck.captura.PaginaCapturada
import com.hemocheck.core.analisis.AnalizadorHoja
import com.hemocheck.core.etiqueta.AnalizadorEtiqueta
import com.hemocheck.core.imagen.AnalizadorCalidad
import com.hemocheck.core.imagen.AvisoCalidad
import com.hemocheck.core.imagen.ImagenGris
import com.hemocheck.core.ocr.PaginaOcr
import com.hemocheck.datos.ArchivosCifrados
import com.hemocheck.ui.comun.Aviso
import com.hemocheck.ui.comun.Gravedad
import com.hemocheck.ui.comun.textoCalidad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private sealed interface EstadoCaptura {
    data object Vista : EstadoCaptura
    data object Procesando : EstadoCaptura
    class Revision(val pagina: PaginaCapturada, val vistaPrevia: Bitmap) : EstadoCaptura
    data class Error(val mensaje: Int) : EstadoCaptura
}

private enum class ModoFlash { APAGADO, AUTOMATICO, ENCENDIDO, LINTERNA }

/**
 * Cámara integrada de HemoCheck (CameraX). La vista previa y la captura ocurren dentro de la
 * app: no se abre ninguna aplicación de cámara externa y las fotos no van a la galería.
 */
@Composable
fun PantallaCamara(contenedor: Contenedor, modo: ModoCaptura, onTerminar: () -> Unit, onCancelar: () -> Unit) {
    val context = LocalContext.current
    var permiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denegado by remember { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permiso = ok
        denegado = !ok
    }
    LaunchedEffect(Unit) { if (!permiso) lanzador.launch(Manifest.permission.CAMERA) }

    if (!permiso) {
        PermisoCamara(
            denegado = denegado,
            onPedir = { lanzador.launch(Manifest.permission.CAMERA) },
            onAjustes = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            },
            onCancelar = onCancelar,
        )
        return
    }
    VistaCamara(contenedor, modo, onTerminar, onCancelar)
}

@Composable
fun PermisoCamara(denegado: Boolean, onPedir: () -> Unit, onAjustes: () -> Unit, onCancelar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag("permiso_camara"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.permiso_camara_titulo), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.permiso_camara_texto))
        if (denegado) Aviso(stringResource(R.string.permiso_camara_denegado), Gravedad.ADVERTENCIA)
        Button(onClick = onPedir, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.permiso_camara_pedir)) }
        if (denegado) OutlinedButton(onClick = onAjustes, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.permiso_camara_ajustes)) }
        TextButton(onClick = onCancelar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancelar)) }
    }
}

/** Referencia mutable a la cámara enlazada (para el enfoque táctil y el flash). */
private class RefCamara {
    var camara: Camera? = null
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun VistaCamara(contenedor: Contenedor, modo: ModoCaptura, onTerminar: () -> Unit, onCancelar: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alcance = rememberCoroutineScope()
    val borrador by contenedor.borradores.serologia.collectAsState()

    var estado by remember { mutableStateOf<EstadoCaptura>(EstadoCaptura.Vista) }
    var avisosVivo by remember { mutableStateOf(emptySet<AvisoCalidad>()) }
    var transmitiendo by remember { mutableStateOf(false) }
    var errorCamara by remember { mutableStateOf<Int?>(null) }
    var flash by remember { mutableStateOf(ModoFlash.APAGADO) }
    var tieneFlash by remember { mutableStateOf(false) }
    val ref = remember { RefCamara() }
    val ejecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    val selector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
    }
    val captura = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(selector)
            .build()
    }
    val analisis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .build()
    }
    val vistaPrevia = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Enlace de la cámara al ciclo de vida de la pantalla
    DisposableEffect(lifecycleOwner) {
        var proveedor: ProcessCameraProvider? = null
        val futuro = ProcessCameraProvider.getInstance(context)
        futuro.addListener({
            try {
                val p = futuro.get()
                proveedor = p
                val preview = Preview.Builder().setResolutionSelector(selector).build()
                preview.setSurfaceProvider(vistaPrevia.surfaceProvider)
                analisis.setAnalyzer(ejecutor, AnalizadorVivo { avisosVivo = it })
                p.unbindAll()
                val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, captura, analisis)
                ref.camara = cam
                tieneFlash = cam.cameraInfo.hasFlashUnit()
                errorCamara = null
            } catch (e: Exception) {
                errorCamara = R.string.camara_error_iniciar
            }
        }, ContextCompat.getMainExecutor(context))
        val observador = Observer<PreviewView.StreamState> { transmitiendo = it == PreviewView.StreamState.STREAMING }
        vistaPrevia.previewStreamState.observe(lifecycleOwner, observador)
        onDispose {
            vistaPrevia.previewStreamState.removeObserver(observador)
            analisis.clearAnalyzer()
            proveedor?.unbindAll()
            ref.camara = null
        }
    }
    DisposableEffect(Unit) { onDispose { ejecutor.shutdown() } }

    // La rotación de la foto sigue la orientación física del teléfono
    DisposableEffect(Unit) {
        val oyente = object : OrientationEventListener(context) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                val r = when (o) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                captura.targetRotation = r
                analisis.targetRotation = r
            }
        }
        oyente.enable()
        onDispose { oyente.disable() }
    }

    fun aplicarFlash(m: ModoFlash) {
        flash = m
        captura.flashMode = when (m) {
            ModoFlash.AUTOMATICO -> ImageCapture.FLASH_MODE_AUTO
            ModoFlash.ENCENDIDO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        ref.camara?.cameraControl?.enableTorch(m == ModoFlash.LINTERNA)
    }

    fun tomarFoto() {
        if (contenedor.archivos.espacioLibre() < ArchivosCifrados.ESPACIO_RESERVADO * 2) {
            estado = EstadoCaptura.Error(R.string.camara_sin_espacio)
            return
        }
        estado = EstadoCaptura.Procesando
        captura.takePicture(ejecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotacion = image.imageInfo.rotationDegrees
                val bytes = try {
                    val buffer = image.planes[0].buffer
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                } finally {
                    image.close()
                }
                alcance.launch(Dispatchers.Main) {
                    estado = try {
                        val pagina = contenedor.procesador.procesar(bytes, rotacion, modo)
                        EstadoCaptura.Revision(pagina, withContext(Dispatchers.Default) { decodificarVista(pagina.jpegProcesada) })
                    } catch (e: OutOfMemoryError) {
                        EstadoCaptura.Error(R.string.camara_error_procesar)
                    } catch (e: Exception) {
                        EstadoCaptura.Error(R.string.camara_error_procesar)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                alcance.launch(Dispatchers.Main) { estado = EstadoCaptura.Error(R.string.camara_error_captura) }
            }
        })
    }

    fun confirmar(p: PaginaCapturada, terminar: Boolean) {
        val formatos = contenedor.ajustes.identificadores.formatos()
        alcance.launch(Dispatchers.Main) {
            withContext(Dispatchers.Default) {
                if (modo == ModoCaptura.HOJA) {
                    val hoja = p.ocr?.let { AnalizadorHoja(formatos).analizar(it, borrador.tipo) }
                    contenedor.borradores.agregarPagina(p, hoja)
                } else {
                    val lectura = AnalizadorEtiqueta(formatos).analizar(p.ocr ?: PaginaOcr(p.anchoProcesada, p.altoProcesada, emptyList()), p.codigosBarras)
                    contenedor.borradores.fijarEtiqueta(p, lectura)
                }
            }
            if (terminar || modo == ModoCaptura.ETIQUETA) onTerminar() else estado = EstadoCaptura.Vista
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                vistaPrevia.apply {
                    // Enfoque al tocar
                    setOnTouchListener { v, ev ->
                        if (ev.action == MotionEvent.ACTION_UP) {
                            val punto = meteringPointFactory.createPoint(ev.x, ev.y)
                            ref.camara?.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(punto, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                    .setAutoCancelDuration(4, TimeUnit.SECONDS).build()
                            )
                            v.performClick()
                        }
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize().testTag("vista_previa_camara"),
        )
        GuiaEncuadre(modo)

        // Barra superior
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancelar) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancelar), tint = Color.White) }
            Text(
                text = stringResource(if (modo == ModoCaptura.HOJA) R.string.camara_titulo_hoja else R.string.camara_titulo_etiqueta),
                color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (tieneFlash) {
                IconButton(onClick = { aplicarFlash(ModoFlash.entries[(flash.ordinal + 1) % ModoFlash.entries.size]) }) {
                    Icon(
                        when (flash) {
                            ModoFlash.APAGADO -> Icons.Filled.FlashOff
                            ModoFlash.AUTOMATICO -> Icons.Filled.FlashAuto
                            ModoFlash.ENCENDIDO -> Icons.Filled.FlashOn
                            ModoFlash.LINTERNA -> Icons.Filled.FlashlightOn
                        },
                        contentDescription = stringResource(
                            when (flash) {
                                ModoFlash.APAGADO -> R.string.flash_apagado
                                ModoFlash.AUTOMATICO -> R.string.flash_automatico
                                ModoFlash.ENCENDIDO -> R.string.flash_encendido
                                ModoFlash.LINTERNA -> R.string.flash_linterna
                            }
                        ),
                        tint = Color.White,
                    )
                }
            }
        }

        // Indicaciones y controles inferiores
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(if (transmitiendo) R.string.camara_lista else R.string.camara_iniciando),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag("estado_camara").semantics { contentDescription = if (transmitiendo) "transmitiendo" else "iniciando" },
            )
            errorCamara?.let { Aviso(stringResource(it), Gravedad.PELIGRO) }
            val indicacion = if (modo == ModoCaptura.HOJA) R.string.camara_indicacion_hoja else R.string.camara_indicacion_etiqueta
            Text(stringResource(indicacion), color = Color.White, style = MaterialTheme.typography.bodySmall)
            avisosVivo.forEach { Aviso(stringResource(textoCalidad(it)), Gravedad.ADVERTENCIA) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (modo == ModoCaptura.HOJA) {
                    Text(stringResource(R.string.camara_paginas, borrador.paginas.size), color = Color.White)
                }
                FilledIconButton(
                    onClick = { tomarFoto() },
                    enabled = estado == EstadoCaptura.Vista && ref.camara != null && transmitiendo,
                    modifier = Modifier.size(76.dp).testTag("boton_tomar_foto"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Text(stringResource(R.string.camara_tomar), style = MaterialTheme.typography.labelSmall)
                }
                if (modo == ModoCaptura.HOJA && borrador.paginas.isNotEmpty()) {
                    Button(onClick = onTerminar) { Text(stringResource(R.string.camara_terminar)) }
                }
            }
        }

        when (val e = estado) {
            EstadoCaptura.Procesando -> Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.camara_procesando), color = Color.White)
                }
            }
            is EstadoCaptura.Revision -> RevisionFoto(
                pagina = e.pagina,
                vistaPrevia = e.vistaPrevia,
                modo = modo,
                onRepetir = { estado = EstadoCaptura.Vista },
                onConfirmar = { terminar -> confirmar(e.pagina, terminar) },
            )
            is EstadoCaptura.Error -> Box(Modifier.fillMaxSize().background(Color(0xDD000000)).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Aviso(stringResource(e.mensaje), Gravedad.PELIGRO)
                    Button(onClick = { estado = EstadoCaptura.Vista }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.camara_reintentar)) }
                }
            }
            EstadoCaptura.Vista -> Unit
        }
    }
}

/** Resultado de la foto: vista previa procesada y advertencias, para confirmar o repetir. */
@Composable
private fun RevisionFoto(
    pagina: PaginaCapturada,
    vistaPrevia: Bitmap,
    modo: ModoCaptura,
    onRepetir: () -> Unit,
    onConfirmar: (terminar: Boolean) -> Unit,
) {
    val imagen = remember(vistaPrevia) { vistaPrevia.asImageBitmap() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(12.dp)
            .testTag("revision_foto"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.revision_foto_titulo), style = MaterialTheme.typography.titleMedium)
        Image(
            imagen, contentDescription = stringResource(R.string.revision_foto_titulo), contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Column(Modifier.verticalScroll(rememberScrollState()).weight(0.45f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pagina.errorOcr) Aviso(stringResource(R.string.revision_error_ocr), Gravedad.PELIGRO)
            if (modo == ModoCaptura.HOJA && pagina.bordesDetectados) Aviso(stringResource(R.string.revision_perspectiva_corregida), Gravedad.INFO)
            pagina.avisos.forEach { Aviso(stringResource(textoCalidad(it)), Gravedad.ADVERTENCIA) }
            if (pagina.avisos.isEmpty() && !pagina.errorOcr) Aviso(stringResource(R.string.revision_sin_avisos), Gravedad.CORRECTO)
            Aviso(stringResource(R.string.revision_original_conservado), Gravedad.INFO)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRepetir, modifier = Modifier.weight(1f).testTag("boton_repetir")) { Text(stringResource(R.string.repetir)) }
            if (modo == ModoCaptura.HOJA) {
                Button(onClick = { onConfirmar(false) }, modifier = Modifier.weight(1f).testTag("boton_otra_pagina")) {
                    Text(stringResource(R.string.confirmar_otra_pagina))
                }
            }
        }
        Button(onClick = { onConfirmar(true) }, modifier = Modifier.fillMaxWidth().testTag("boton_confirmar_foto")) {
            Text(stringResource(if (modo == ModoCaptura.HOJA) R.string.confirmar_terminar else R.string.confirmar_continuar))
        }
    }
}

/** Marco de encuadre: hoja A4 vertical o etiqueta horizontal. */
@Composable
private fun GuiaEncuadre(modo: ModoCaptura) {
    Canvas(Modifier.fillMaxSize()) {
        val proporcion = if (modo == ModoCaptura.HOJA) 1.414f else 0.62f
        val ancho = size.width * 0.86f
        val alto = (ancho * proporcion).coerceAtMost(size.height * 0.72f)
        val anchoFinal = alto / proporcion
        val izq = (size.width - anchoFinal) / 2
        val arriba = (size.height - alto) / 2
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(izq, arriba),
            size = Size(anchoFinal, alto),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 4f),
        )
    }
}

private fun decodificarVista(jpeg: ByteArray): Bitmap {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
    var muestra = 1
    while (maxOf(o.outWidth, o.outHeight) / muestra > 1600) muestra *= 2
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = muestra })
}

/** Análisis en vivo de luz y nitidez sobre el plano de luminancia de la vista previa. */
private class AnalizadorVivo(private val alAvisar: (Set<AvisoCalidad>) -> Unit) : ImageAnalysis.Analyzer {
    private var ultimo = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val ahora = SystemClock.elapsedRealtime()
            if (ahora - ultimo < 500) return
            ultimo = ahora
            val plano = image.planes[0]
            val buffer = plano.buffer
            val datos = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val necesario = (image.height - 1) * plano.rowStride + image.width
            if (datos.size < necesario) return
            val img = ImagenGris.desdePlanoY(image.width, image.height, datos, plano.rowStride)
            alAvisar(AnalizadorCalidad.evaluar(AnalizadorCalidad.medir(img)))
        } catch (e: Exception) {
            // Un cuadro fallido no interrumpe la vista previa
        } finally {
            image.close()
        }
    }
}
