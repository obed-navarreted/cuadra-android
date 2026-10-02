package com.cuadra.caja.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cuadra.caja.CuadraApp
import com.cuadra.caja.R
import com.cuadra.caja.data.scanner.ScanHub
import com.cuadra.caja.domain.FrameCrop
import com.cuadra.caja.domain.Luma
import com.cuadra.caja.domain.LowLightMonitor
import com.cuadra.caja.domain.ScanCode
import com.cuadra.caja.domain.ScanConfirmer
import com.cuadra.caja.domain.ScanDebounce
import com.cuadra.caja.domain.ScanHint
import com.cuadra.caja.domain.ScanHints
import com.cuadra.caja.domain.ScanRoi
import com.cuadra.caja.ui.theme.CuadraColors
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/** Botón cuadrado (56 dp; 48 dp en la tarjeta del total) con el icono de escanear (descripción «Escanear» para lectores de pantalla). */
@Composable
fun ScanButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val shape = RoundedCornerShape(16.dp)
    val description = stringResource(R.string.scan_button)
    Box(
        modifier.size(size).clip(shape).background(CuadraColors.Surface, shape).border(BorderStroke(1.dp, CuadraColors.Line), shape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(R.drawable.ic_scan), contentDescription = null, modifier = Modifier.size(28.dp)) }
}

/** Icono de escanear de 48 dp para dentro de un campo de texto (mismo sitio que el micrófono). */
@Composable
fun ScanFieldIcon(onClick: () -> Unit) {
    val description = stringResource(R.string.scan_button)
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_scan), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

private val Formats = intArrayOf(
    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
    Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_QR_CODE,
)

private fun formatName(format: Int): String? = when (format) {
    Barcode.FORMAT_EAN_13 -> "EAN-13"
    Barcode.FORMAT_EAN_8 -> "EAN-8"
    Barcode.FORMAT_UPC_A -> "UPC-A"
    Barcode.FORMAT_UPC_E -> "UPC-E"
    Barcode.FORMAT_CODE_128 -> "Code 128"
    Barcode.FORMAT_CODE_39 -> "Code 39"
    Barcode.FORMAT_QR_CODE -> "QR"
    else -> null
}

private fun hasCameraPermission(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Cuánto se ve la confirmación «Escaneado» antes de cerrar el lector (un solo código). */
private const val SCANNED_FLASH_MILLIS = 450L

/** Lo que la cámara le pide a quien la maneja: enfocar al tocar y acercar. */
internal class CameraHandle {
    @Volatile var camera: Camera? = null
    @Volatile var view: PreviewView? = null

    /**
     * Enfoque y exposición en el punto tocado (x, y en píxeles de la vista). Después de 3 s la cámara vuelve sola al enfoque continuo
     * (CameraX lo trae encendido por omisión: `CONTINUOUS_PICTURE`).
     */
    fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        val v = view ?: return
        runCatching {
            val point = v.meteringPointFactory.createPoint(x, y)
            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
        }
    }

    /** Pellizco: multiplica el zoom actual. */
    fun zoomBy(factor: Float) {
        val cam = camera ?: return
        val z: ZoomState = cam.cameraInfo.zoomState.value ?: return
        runCatching { cam.cameraControl.setZoomRatio((z.zoomRatio * factor).coerceIn(z.minZoomRatio, z.maxZoomRatio)) }
    }

    /** Deslizador: 0 (sin zoom) a 1 (máximo). */
    fun setLinear(value: Float) { runCatching { camera?.cameraControl?.setLinearZoom(value.coerceIn(0f, 1f)) } }
}

/**
 * Lector de códigos: cámara (CameraX + ML Kit incluido en la app, sin conexión), linterna, zoom, enfoque al tocar y siempre un campo para teclear el código.
 * El permiso de cámara se pide aquí, al abrir el lector (no al arrancar la app). Sin permiso o sin cámara, teclear sigue funcionando.
 * También recibe lo que lea el lector físico (Zebra/teclado) mientras está abierto: cae en el mismo `onCode`.
 *
 * @param onCode recibe cada código leído o tecleado (ya limpio). Si `continuousOption` está apagado o el modo continuo está apagado,
 *  quien llama cierra el lector; si está encendido, el lector sigue abierto (pausado mientras `paused`).
 * @param onDismiss cierre por el botón o por atrás.
 */
@Composable
fun BarcodeScannerDialog(
    onDismiss: () -> Unit,
    onCode: (String) -> Unit,
    paused: Boolean = false,
    continuousOption: Boolean = false,
    addedCount: Int = 0,
    /** Empieza ya en modo continuo (elegir varios productos seguidos, p. ej. los de una promoción). */
    startContinuous: Boolean = false,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hub: ScanHub? = remember { (context.applicationContext as? CuadraApp)?.container?.scanner }
    val settings by (hub?.settings ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.cuadra.caja.data.scanner.ScannerSettings()) }).collectAsState()
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val cameraOff = !settings.useCamera
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var cameraBusy by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var hasZoom by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(0f) }
    var lowLight by remember { mutableStateOf(false) }
    var continuous by remember { mutableStateOf(startContinuous && continuousOption) }
    var manual by remember { mutableStateOf("") }
    var scanned by remember { mutableStateOf(false) }
    var sinceMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val handle = remember { CameraHandle() }
    val debounce = remember { ScanDebounce() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; denied = !ok }
    LaunchedEffect(cameraOff) { if (hasCamera && !granted && !cameraOff) launcher.launch(Manifest.permission.CAMERA) }
    // Para la pista «acerca o aleja»: el reloj corre solo mientras el lector está abierto.
    LaunchedEffect(Unit) { while (true) { delay(500); nowMs = SystemClock.elapsedRealtime() } }

    /** `fromCamera`: viene del análisis (se quitan repetidos y se avisa); si no, es tecleado o del lector físico (ya filtrado por `ScanHub`). */
    val deliver = { raw: String, fromCamera: Boolean, label: String? ->
        val code = ScanCode.normalize(raw)
        if (code.isNotEmpty() && (!fromCamera || debounce.accept(code, System.currentTimeMillis()))) {
            if (fromCamera) { if (hub != null) hub.record(code, label) else haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            sinceMs = SystemClock.elapsedRealtime()
            onCode(code)
            manual = ""
            // Una sola lectura: se muestra «Escaneado» un instante y se cierra. Tecleado a mano: se cierra de una vez.
            if (!(continuousOption && continuous)) { if (fromCamera) scanned = true else onDismiss() } else scanned = true
        }
    }
    val currentDeliver by rememberUpdatedState(deliver)
    LaunchedEffect(scanned) {
        if (scanned) {
            delay(SCANNED_FLASH_MILLIS)
            scanned = false
            if (!(continuousOption && continuous)) onDismiss()
        }
    }
    // El lector físico también llega aquí mientras el lector está abierto.
    DisposableEffect(hub) {
        val claim = hub?.claim { code, _ -> currentDeliver(code, false, null) }
        onDispose { claim?.close() }
    }

    val hint = ScanHints.pick(nowMs, sinceMs, lowLight, torch)
    ScannerSheet(
        ScannerUi(hasCamera, granted, denied, cameraError, hasTorch, torch, continuousOption, continuous, addedCount, manual, hasZoom, zoom, hint, scanned, cameraBusy, cameraOff),
        onDismiss = onDismiss,
        onTorch = { torch = !torch }, onContinuous = { continuous = !continuous }, onManual = { manual = it.take(64) },
        onUseManual = { deliver(manual, false, null) },
        onAllow = { launcher.launch(Manifest.permission.CAMERA) },
        onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        onZoom = { zoom = it; handle.setLinear(it) },
        camera = {
            CameraPreview(
                handle, paused = paused || scanned, torch = torch,
                onTorchAvailable = { hasTorch = it }, onZoomState = { has, linear -> hasZoom = has; zoom = linear },
                onProblem = { fatal, busy -> if (fatal) cameraError = true; cameraBusy = busy }, onLowLight = { lowLight = it },
                onBarcode = { code, label -> deliver(code, true, label) },
            )
            CameraGestures(handle)
        },
    )
}

/** Lo que el lector muestra: con o sin cámara/permiso, linterna, zoom, pista, «Escaneado», modo continuo y el código tecleado. */
data class ScannerUi(
    val hasCamera: Boolean = true, val granted: Boolean = true, val denied: Boolean = false, val cameraError: Boolean = false,
    val hasTorch: Boolean = true, val torch: Boolean = false, val continuousOption: Boolean = true, val continuous: Boolean = true,
    val addedCount: Int = 0, val manual: String = "",
    val hasZoom: Boolean = true, val zoom: Float = 0f, val hint: ScanHint = ScanHint.NONE, val scanned: Boolean = false,
    /** Otra app tiene la cámara: la vista sigue (CameraX reintenta solo) y se avisa. */
    val cameraBusy: Boolean = false,
    /** Apagada en Más › Lector de códigos. */
    val cameraOff: Boolean = false,
)

/** La hoja del lector sin cámara real (recibe la vista de la cámara como ranura): es lo que dibuja la guardia de diseño. */
@Composable
fun ScannerSheet(
    ui: ScannerUi, onDismiss: () -> Unit, onTorch: () -> Unit, onContinuous: () -> Unit, onManual: (String) -> Unit, onUseManual: () -> Unit,
    onAllow: () -> Unit, onSettings: () -> Unit, onZoom: (Float) -> Unit = {}, camera: @Composable () -> Unit,
) {
    Sheet(onDismiss, actions = {
        ButtonRow {
            CuadraButton(stringResource(R.string.close), onDismiss, Modifier.share(1f))
            CuadraButton(stringResource(R.string.scan_use_code), onUseManual, Modifier.share(1f), kind = ButtonKind.DARK, enabled = ui.manual.isNotBlank())
        }
    }) {
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineMedium)
        if (ui.hasCamera && ui.granted && !ui.cameraError && !ui.cameraOff) {
            Box(Modifier.fillMaxWidth().heightIn(min = 230.dp).clip(RoundedCornerShape(16.dp)).background(CuadraColors.Ink)) {
                Box(Modifier.matchParentSize()) { camera() }
                ScanReticle(Modifier.matchParentSize(), ui.scanned)
                if (ui.scanned) ScannedBadge(Modifier.align(Alignment.Center))
            }
            HintLine(ui)
            if (ui.hasZoom) ZoomRow(ui.zoom, onZoom)
            ChipFlow {
                if (ui.hasTorch) CuadraChip(stringResource(R.string.scan_torch), ui.torch, onTorch)
                if (ui.continuousOption) CuadraChip(stringResource(R.string.scan_continuous), ui.continuous, onContinuous)
            }
            if (ui.continuousOption && ui.continuous) Text(stringResource(R.string.scan_counter, ui.addedCount), fontWeight = FontWeight.ExtraBold)
        } else {
            val message = when {
                !ui.hasCamera -> R.string.scan_no_camera
                ui.cameraOff -> R.string.scan_camera_off
                ui.cameraError -> R.string.scan_error
                ui.denied -> R.string.scan_permission_denied
                else -> R.string.scan_rationale
            }
            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
            if (ui.hasCamera && !ui.cameraError && !ui.cameraOff && !ui.granted) {
                if (ui.denied) CuadraButton(stringResource(R.string.scan_open_settings), onSettings, Modifier.fillMaxWidth())
                CuadraButton(stringResource(R.string.scan_allow), onAllow, Modifier.fillMaxWidth(), kind = ButtonKind.DARK)
            }
        }
        // sin voz: código de barras (se escanea o se teclea)
        OutlinedTextField(
            ui.manual, onManual, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.scan_manual_label), maxLines = 1) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onUseManual() }),
        )
    }
}

/** La línea bajo la cámara: la instrucción, o (tras 4 s sin leer, o con poca luz) la pista que toca. */
@Composable
private fun HintLine(ui: ScannerUi) {
    val (text, color, bold) = when {
        ui.cameraBusy -> Triple(R.string.scan_busy, CuadraColors.Orange, true)
        ui.hint == ScanHint.LOW_LIGHT -> Triple(if (ui.hasTorch) R.string.scan_hint_light else R.string.scan_hint_light_no_torch, CuadraColors.Orange, true)
        ui.hint == ScanHint.FOCUS -> Triple(R.string.scan_hint_focus, CuadraColors.Orange, true)
        else -> Triple(R.string.scan_point, MaterialTheme.colorScheme.onSurfaceVariant, false)
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (bold) FontWeight.Bold else null)
}

@Composable
private fun ZoomRow(zoom: Float, onZoom: (Float) -> Unit) {
    val description = stringResource(R.string.scan_zoom)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(zoom, onZoom, Modifier.weight(1f).semantics { contentDescription = description }, valueRange = 0f..1f)
    }
}

@Composable
private fun ScannedBadge(modifier: Modifier) {
    Box(modifier.background(CuadraColors.Green, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text("✓ " + stringResource(R.string.scan_done), color = Color.White, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Recuadro de lectura: velo oscuro fuera de la franja central (la única zona que se analiza), esquinas y una línea de láser en el centro.
 * La franja es ancha y baja: la forma de un código de barras.
 */
@Composable
private fun ScanReticle(modifier: Modifier, scanned: Boolean) {
    Canvas(modifier) {
        val scrim = Color.Black.copy(alpha = 0.45f)
        val x0 = size.width * ScanRoi.X0; val x1 = size.width * ScanRoi.X1
        val y0 = size.height * ScanRoi.Y0; val y1 = size.height * ScanRoi.Y1
        drawRect(scrim, Offset.Zero, GeoSize(size.width, y0))
        drawRect(scrim, Offset(0f, y1), GeoSize(size.width, size.height - y1))
        drawRect(scrim, Offset(0f, y0), GeoSize(x0, y1 - y0))
        drawRect(scrim, Offset(x1, y0), GeoSize(size.width - x1, y1 - y0))
        val stroke = 3.dp.toPx()
        val len = 22.dp.toPx().coerceAtMost((y1 - y0) / 2)
        val c = if (scanned) Color(0xFF7BE0A8) else Color.White
        fun corner(px: Float, py: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(px, py), Offset(px + dx * len, py), stroke)
            drawLine(c, Offset(px, py), Offset(px, py + dy * len), stroke)
        }
        corner(x0, y0, 1f, 1f); corner(x1, y0, -1f, 1f); corner(x0, y1, 1f, -1f); corner(x1, y1, -1f, -1f)
        val mid = (y0 + y1) / 2
        drawLine(if (scanned) Color(0xFF7BE0A8) else Color(0xCCE53935), Offset(x0 + 8.dp.toPx(), mid), Offset(x1 - 8.dp.toPx(), mid), 1.5.dp.toPx())
    }
}

/** Capa transparente sobre la cámara: tocar enfoca (con un anillo un instante) y pellizcar cambia el zoom. */
@Composable
private fun CameraGestures(handle: CameraHandle) {
    var ring by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(ring) { if (ring != null) { delay(800); ring = null } }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(handle) { detectTapGestures { p -> ring = p; handle.focusAt(p.x, p.y) } }
            .pointerInput(handle) { detectTransformGestures { _, _, zoomChange, _ -> if (zoomChange != 1f) handle.zoomBy(zoomChange) } },
    ) {
        Canvas(Modifier.fillMaxSize()) { ring?.let { drawCircle(Color.White, 28.dp.toPx(), it, style = Stroke(2.dp.toPx())) } }
    }
}

@Composable
private fun CameraPreview(
    handle: CameraHandle, paused: Boolean, torch: Boolean, onTorchAvailable: (Boolean) -> Unit, onZoomState: (Boolean, Float) -> Unit,
    onProblem: (fatal: Boolean, busy: Boolean) -> Unit, onLowLight: (Boolean) -> Unit, onBarcode: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val currentPaused by rememberUpdatedState(paused)
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnProblem by rememberUpdatedState(onProblem)
    val currentTorchAvailable by rememberUpdatedState(onTorchAvailable)
    val currentZoomState by rememberUpdatedState(onZoomState)
    val currentLowLight by rememberUpdatedState(onLowLight)
    handle.view = previewView

    DisposableEffect(lifecycleOwner) {
        // Análisis en un hilo aparte, solo con el cuadro más reciente: un equipo lento se salta cuadros en vez de acumularlos.
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Formats[0], *Formats.drop(1).toIntArray()).build())
        val confirmer = ScanConfirmer()
        val lowMonitor = LowLightMonitor()
        val busy = AtomicBoolean(false)
        var buffer = ByteArray(0)
        var lastLow = false
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var stateObserver: Observer<CameraState>? = null
        var zoomObserver: Observer<ZoomState>? = null
        var boundCamera: Camera? = null
        val stopped = AtomicBoolean(false)

        // Cada cuadro: se recorta la franja de lectura del plano de brillo, se mide la luz, y se lee. El cuadro de la cámara se suelta enseguida.
        fun analyze(proxy: ImageProxy) {
            var closed = false
            try {
                if (currentPaused || stopped.get() || !busy.compareAndSet(false, true)) return
                var handed = false
                try {
                    val plane = proxy.planes[0]
                    val crop = proxy.cropRect
                    val rotation = proxy.imageInfo.rotationDegrees
                    var rect = ScanRoi.toFrame(crop.left, crop.top, crop.width(), crop.height(), rotation)
                    // Franja degenerada (cuadro raro): se lee todo el cuadro visible.
                    if (rect.width < 64 || rect.height < 64) rect = ScanRoi.Rect(crop.left, crop.top, crop.width() and 1.inv(), crop.height() and 1.inv())
                    val decimation = ScanRoi.decimation(rect.width, rect.height)
                    val need = FrameCrop.nv21Size((rect.width / decimation) and 1.inv(), (rect.height / decimation) and 1.inv())
                    if (buffer.size != need) buffer = ByteArray(need)
                    val (w, h) = FrameCrop.cropToNv21(plane.buffer, plane.rowStride, plane.pixelStride, rect, decimation, buffer)
                    val frame = buffer
                    val low = lowMonitor.update(Luma.average(w, h, w, 1) { frame[it].toInt() })
                    if (low != lastLow) { lastLow = low; main.execute { if (!stopped.get()) currentLowLight(low) } }
                    val image = InputImage.fromByteArray(frame, w, h, rotation, InputImage.IMAGE_FORMAT_NV21)
                    proxy.close(); closed = true
                    scanner.process(image)
                        .addOnSuccessListener { found ->
                            // Un valor por cuadro: mejor uno con dígito de control válido.
                            val candidates = found.filter { it.rawValue != null }
                            val pick = candidates.firstOrNull { ScanCode.checksumValid(ScanCode.normalize(it.rawValue!!)) == true } ?: candidates.firstOrNull()
                            val confirmed = pick?.rawValue?.let { confirmer.offer(it, SystemClock.elapsedRealtime()) }
                            if (confirmed != null && !stopped.get()) currentOnBarcode(confirmed, pick?.let { formatName(it.format) })
                        }
                        .addOnCompleteListener { busy.set(false) }
                    handed = true
                } finally { if (!handed) busy.set(false) }
            } catch (e: Exception) {
                busy.set(false)
            } finally {
                if (!closed) runCatching { proxy.close() }
            }
        }

        future.addListener({
            if (stopped.get()) return@addListener
            try {
                val p = future.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }
                // Mismo campo de visión en la vista y en el análisis: la franja dibujada es la franja leída.
                val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis).apply { previewView.viewPort?.let { setViewPort(it) } }.build()
                val selector = when {
                    p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> throw IllegalStateException("sin cámara")
                }
                p.unbindAll()
                val cam = p.bindToLifecycle(lifecycleOwner, selector, group)
                boundCamera = cam
                camera = cam
                handle.camera = cam
                currentTorchAvailable(cam.cameraInfo.hasFlashUnit())
                zoomObserver = Observer<ZoomState> { z -> currentZoomState(z.maxZoomRatio > z.minZoomRatio * 1.05f, z.linearZoom) }.also { cam.cameraInfo.zoomState.observe(lifecycleOwner, it) }
                // Cámara en uso por otra app: CameraX reintenta solo; se avisa mientras dure. Un fallo sin remedio pasa a «escribe el código».
                stateObserver = Observer<CameraState> { s ->
                    val code = s.error?.code
                    val inUse = code == CameraState.ERROR_CAMERA_IN_USE || code == CameraState.ERROR_MAX_CAMERAS_IN_USE
                    val fatal = code == CameraState.ERROR_CAMERA_FATAL_ERROR || code == CameraState.ERROR_CAMERA_DISABLED || code == CameraState.ERROR_STREAM_CONFIG
                    currentOnProblem(fatal, inUse && s.type != CameraState.Type.OPEN)
                }.also { cam.cameraInfo.cameraState.observe(lifecycleOwner, it) }
            } catch (e: Exception) {
                currentOnProblem(true, false)
            }
        }, main)
        onDispose {
            stopped.set(true)
            handle.camera = null
            runCatching { boundCamera?.cameraInfo?.zoomState?.let { live -> zoomObserver?.let { live.removeObserver(it) } } }
            runCatching { boundCamera?.cameraInfo?.cameraState?.let { live -> stateObserver?.let { live.removeObserver(it) } } }
            runCatching { provider?.unbindAll() }
            executor.shutdown()
            runCatching { scanner.close() }
        }
    }
    LaunchedEffect(camera, torch) { runCatching { camera?.cameraControl?.enableTorch(torch) } }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}
