package com.lgka

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Height of the PDF viewer's top bar below the status bar (Material 3 center-aligned app bar). */
private val ViewerTopBarHeight = 64.dp

private enum class ReadyPhase { PREVIEW, EXPANDING, VIEWER }

/**
 * The finished plan presented after saving (iOS parity): page 1 of the new PDF as a thumbnail; a tap grows
 * it to where the page sits in the PDF viewer, which then takes over in place with a cross-fade.
 * Closing the viewer (or going back) ends on Home; the plan is already saved.
 */
@Composable
fun CustomPlanReadyScreen(saved: SavedCustomPlan, onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.custom_home_title)
    val a11y = stringResource(R.string.custom_ready_a11y)
    var file by remember { mutableStateOf<File?>(null) }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var phase by remember { mutableStateOf(ReadyPhase.PREVIEW) }
    /** Where the thumbnail rests in the preview (root coordinates). */
    var resting by remember { mutableStateOf<Rect?>(null) }
    val appear = remember { Animatable(0f) }
    val expand = remember { Animatable(0f) }
    val viewerAlpha = remember { Animatable(0f) }

    BackHandler(onBack = onClose)

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = with(density) { PdfPageMargin.roundToPx() }

        LaunchedEffect(saved, width) {
            try {
                val pdf = CustomPlanSource.pdfFile(context, saved.plan)
                // rendered at the width the page gets in the viewer, so the hand-over is pixel-identical
                thumbnail = withContext(Dispatchers.IO) { PdfPages(pdf).use { it.render(0, width - 2 * margin) } }
                file = pdf
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onClose() // the plan is saved; Home shows it
                return@LaunchedEffect
            }
            appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
        }

        val bitmap = thumbnail
        val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: (297f / 210f)

        fun open() {
            if (phase != ReadyPhase.PREVIEW || bitmap == null || file == null) return
            phase = ReadyPhase.EXPANDING
            haptics.medium()
            scope.launch {
                expand.animateTo(1f, spring(dampingRatio = 1f, stiffness = 380f)) // ~450 ms, no overshoot
                viewerAlpha.animateTo(1f, tween(180))
                phase = ReadyPhase.VIEWER
            }
        }

        if (phase != ReadyPhase.VIEWER) {
            // at most 520 wide, like the iOS thumbnail
            val restingWidth = with(density) { (width * 0.86f).toDp() }.coerceAtMost(520.dp)
            Column(
                // only the thumbnail opens the plan; the rest of the page does not react
                Modifier.fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { alpha = appear.value * (1f - expand.value * 2f).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.custom_ready_title), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                // the thumbnail's resting place; the page itself is drawn above, free to grow
                Box(Modifier.size(restingWidth, restingWidth / ratio).onGloballyPositioned { resting = it.boundsInRoot() })
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.custom_ready_tap), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }

            val start = resting
            if (bitmap != null && start != null) {
                val topBar = WindowInsets.statusBars.getTop(density) + with(density) { ViewerTopBarHeight.toPx() }
                val endWidth = (width - 2 * margin).toFloat()
                val endHeight = endWidth / ratio
                val endTop = topBar + (height - topBar - endHeight) / 2f
                val corner = with(density) { 12.dp.toPx() }
                val shadow = with(density) { 18.dp.toPx() }
                Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            // read in the layout phase: the page follows the spring every frame without recomposing
                            val t = expand.value
                            val w = lerp(start.width, endWidth, t)
                            val h = lerp(start.height, endHeight, t)
                            val x = lerp(start.left, margin.toFloat(), t)
                            val y = lerp(start.top, endTop, t)
                            val placeable = measurable.measure(Constraints.fixed(w.roundToInt(), h.roundToInt()))
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x.roundToInt(), y.roundToInt()) }
                        }
                        .graphicsLayer {
                            val t = expand.value
                            val a = appear.value
                            alpha = a
                            val grow = lerp(0.92f, 1f, a)
                            scaleX = grow
                            scaleY = grow
                            shadowElevation = lerp(shadow, shadow / 6f, t)
                            shape = RoundedCornerShape(lerp(corner, corner / 6f, t))
                            clip = true
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open() }
                        .clearAndSetSemantics {
                            contentDescription = a11y
                            role = Role.Button
                            onClick { open(); true }
                        })
            }
        }

        val pdf = file
        if (phase != ReadyPhase.PREVIEW && pdf != null) {
            // composed as the page starts to grow, so its first page is rendered when the fade begins
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = viewerAlpha.value }) {
                PdfViewer(PdfRequest(pdf, title, null, shareName = title), onClose = onClose)
            }
        }
    }
}
