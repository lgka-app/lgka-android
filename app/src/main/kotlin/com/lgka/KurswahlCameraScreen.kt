package com.lgka

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lgka.plan.ScanHint
import lgka.plan.ScanQuad
import kotlin.math.hypot
import kotlin.math.min

private const val BURST = 3

/**
 * Guided camera for the Kurswahlprotokoll, fully automatic: outlines the sheet live, paints a faint
 * table grid on it, tells the user what to change (closer, parallel, still), keeps the torch on (brighter
 * when too dark, dimmer on glare) and takes a burst of photos by itself once everything holds. No shutter button.
 */
@Composable
fun KurswahlCameraScreen(onCapture: (List<Bitmap>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    BackHandler(onBack = onCancel)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            granted -> CameraContent(onCapture, onCancel)
            asked -> PermissionView(onCancel)
        }
    }
}

@Composable
private fun CameraContent(onCapture: (List<Bitmap>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val camera = remember { KurswahlCamera(context.applicationContext) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var flash by remember { mutableStateOf(false) }
    var photosTaken by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        camera.onAutoCapture = {
            if (!camera.isCapturing) scope.launch {
                photosTaken = 0
                val images = camera.capture(BURST) { index ->
                    // the shutter effect for every single photo of the burst
                    photosTaken = index + 1
                    haptics.success()
                    flash = true
                    scope.launch { delay(110); flash = false }
                }
                if (images.isNotEmpty()) {
                    camera.stop()
                    onCapture(images)
                } else {
                    photosTaken = 0
                    haptics.error()
                }
            }
        }
        camera.start(owner, previewView)
        onDispose { camera.shutdown() }
    }

    val hint = camera.state.hint
    LaunchedEffect(hint) { if (hint == ScanHint.READY && !camera.isCapturing) haptics.light() }
    val flashAlpha by animateFloatAsState(
        targetValue = if (flash) (if (reduceMotion) 0.35f else 0.9f) else 0f,
        animationSpec = tween(if (flash) 60 else 300), label = "flash")

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        SheetOverlay(camera, accent, reduceMotion)
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(top = 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // one row: close on the left (the only control; the photos are taken by themselves), the
            // instruction centred on the screen at the same height, kept clear of the X on both sides
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp)) {
                IconButton(onClick = { haptics.light(); onCancel() },
                    modifier = Modifier.align(Alignment.CenterStart).size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
                    Icon(Icons.Filled.Close, stringResource(R.string.scan_close), tint = Color.White)
                }
                Box(Modifier.align(Alignment.Center).padding(horizontal = 56.dp)) {
                    InstructionPill(camera, photosTaken, accent)
                }
            }
            if (hint == ScanHint.HOLD_PARALLEL && !camera.isCapturing) SpiritLevel(camera.level, accent)
        }
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
    }
}

/** Frame coordinates (upright, 0…1) → view pixels, as the preview fills the view. */
private fun mapped(quad: ScanQuad, width: Float, height: Float, aspect: Float): List<Offset> {
    val contentW: Float
    val contentH: Float
    if (width / height > aspect) { contentW = width; contentH = width / aspect } else { contentW = height * aspect; contentH = height }
    val dx = (width - contentW) / 2
    val dy = (height - contentH) / 2
    return quad.corners.map { Offset(dx + it.x.toFloat() * contentW, dy + it.y.toFloat() * contentH) }
}

/** A centred A4-portrait frame showing where the sheet should go. */
private fun guideCorners(width: Float, height: Float): List<Offset> {
    val w = min(width * 0.8f, height * 0.6f / 1.414f)
    val h = w * 1.414f
    val left = (width - w) / 2
    val top = (height - h) / 2 - 10
    return listOf(Offset(left, top), Offset(left + w, top), Offset(left + w, top + h), Offset(left, top + h))
}

/** Bilinear point inside the quad: u across, v down. */
private fun List<Offset>.point(u: Float, v: Float): Offset {
    val top = this[0] + (this[1] - this[0]) * u
    val bottom = this[3] + (this[2] - this[3]) * u
    return top + (bottom - top) * v
}

private val gridRows = generateSequence(0.30f) { it + 0.0322f }.takeWhile { it <= 0.8801f }.toList()
private val gridColumns = listOf(0.07f, 0.25f, 0.33f, 0.40f, 0.46f, 0.52f, 0.58f, 0.64f, 0.70f, 0.78f, 0.93f)

@Composable
private fun SheetOverlay(camera: KurswahlCamera, accent: Color, reduceMotion: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val detected = camera.quad?.let { mapped(it, width, height, camera.frameAspect.toFloat()) }
        val target = detected ?: guideCorners(width, height)
        // the outline glides between detections
        val corners = target.mapIndexed { i, point ->
            val animated by animateOffsetAsState(point, if (reduceMotion) tween(0) else spring(dampingRatio = 0.85f, stiffness = 170f), label = "corner$i")
            animated
        }
        val ready = camera.state.hint == ScanHint.READY
        val tint = if (ready) accent else Color.White
        Canvas(Modifier.fillMaxSize()) {
            val outline = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                corners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val dim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addPath(outline)
            }
            drawPath(dim, Color.Black.copy(alpha = if (detected == null) 0.35f else 0.5f))
            // the Kurswahlprotokoll's table as a faint grid: subject rows and the Fachart / hours columns
            val gridColor = Color.White.copy(alpha = if (detected == null) 0.1f else 0.22f)
            val top = gridRows.first()
            val bottom = gridRows.last()
            for (v in gridRows) drawLine(gridColor, corners.point(gridColumns.first(), v), corners.point(gridColumns.last(), v), 0.75.dp.toPx())
            for (u in gridColumns) drawLine(gridColor, corners.point(u, top), corners.point(u, bottom), 0.75.dp.toPx())
            drawPath(outline, tint.copy(alpha = if (detected == null) 0.5f else 0.9f),
                style = Stroke(width = (if (detected == null) 1.5 else 2.0).dp.toPx(),
                    pathEffect = if (detected == null) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())) else null))
            // corner brackets
            val bracket = Path()
            val length = 26.dp.toPx()
            for (i in 0 until 4) {
                val corner = corners[i]
                val next = corners[(i + 1) % 4]
                val previous = corners[(i + 3) % 4]
                fun toward(to: Offset): Offset {
                    val d = to - corner
                    val distance = maxOf(hypot(d.x, d.y), 1f)
                    val l = min(length, distance / 3)
                    return corner + d * (l / distance)
                }
                val a = toward(next)
                val b = toward(previous)
                bracket.moveTo(a.x, a.y)
                bracket.lineTo(corner.x, corner.y)
                bracket.lineTo(b.x, b.y)
            }
            drawPath(bracket, tint, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun hintIcon(hint: ScanHint): ImageVector = when (hint) {
    ScanHint.NO_DOCUMENT -> Icons.Outlined.DocumentScanner
    ScanHint.TOO_DARK -> Icons.Outlined.LightMode
    ScanHint.MOVE_CLOSER -> Icons.Outlined.ZoomIn
    ScanHint.MOVE_BACK -> Icons.Outlined.ZoomOut
    ScanHint.HOLD_PARALLEL -> Icons.Outlined.Straighten
    ScanHint.GLARE -> Icons.Outlined.WbSunny
    ScanHint.HOLD_STILL -> Icons.Filled.PanTool
    ScanHint.READY -> Icons.Filled.CheckCircle
}

@Composable
private fun hintText(hint: ScanHint): String = stringResource(when (hint) {
    ScanHint.NO_DOCUMENT -> R.string.scan_hint_no_document
    ScanHint.TOO_DARK -> R.string.scan_hint_too_dark
    ScanHint.MOVE_CLOSER -> R.string.scan_hint_move_closer
    ScanHint.MOVE_BACK -> R.string.scan_hint_move_back
    ScanHint.HOLD_PARALLEL -> R.string.scan_hint_hold_parallel
    ScanHint.GLARE -> R.string.scan_hint_glare
    ScanHint.HOLD_STILL -> R.string.scan_hint_hold_still
    ScanHint.READY -> R.string.scan_hint_ready
})

@Composable
private fun InstructionPill(camera: KurswahlCamera, photosTaken: Int, accent: Color) {
    val bursting = camera.isCapturing
    val hint = camera.state.hint
    val highlighted = hint == ScanHint.READY || bursting
    val text = if (bursting) stringResource(R.string.scan_burst_photo, photosTaken.coerceAtLeast(1), BURST) else hintText(hint)
    Row(Modifier.clip(CircleShape)
            .background(if (highlighted) accent.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = text },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (bursting) Icons.Filled.PanTool else hintIcon(hint), null, tint = if (highlighted) Color.White else Color.White)
        Text(if (bursting) stringResource(R.string.scan_burst_hold) else text, color = Color.White,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            maxLines = 2, autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 14.sp),
            modifier = Modifier.weight(1f, fill = false))
        if (bursting) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(BURST) { index ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (index < photosTaken) Color.White else Color.White.copy(alpha = 0.3f)))
                }
            }
        }
    }
}

@Composable
private fun SpiritLevel(gravity: Pair<Float, Float>, accent: Color) {
    Canvas(Modifier.size(84.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
        val radius = 26.dp.toPx()
        val c = center
        // gravity of ~0.34 (20°) reaches the rim; the bubble drifts to the higher side
        val dx = (-gravity.first / 0.34f).coerceIn(-1f, 1f) * radius
        val dy = (gravity.second / 0.34f).coerceIn(-1f, 1f) * radius
        val centred = hypot(dx, dy) < 6.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.5f), radius + 8.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.35f), 8.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        drawCircle(if (centred) accent else Color.White, 8.dp.toPx(), c + Offset(dx, dy))
    }
}

@Composable
private fun PermissionView(onCancel: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(44.dp), tint = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.scan_permission_title), color = Color.White, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.scan_permission_body), color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            haptics.light()
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }) { Text(stringResource(R.string.scan_permission_settings)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.scan_close), color = Color.White.copy(alpha = 0.8f)) }
    }
}
