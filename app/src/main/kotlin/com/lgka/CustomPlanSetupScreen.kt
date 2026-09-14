package com.lgka

import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.onClick
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import lgka.plan.KurswahlParser

private val Good = Color(0xFF34C759)
private val Bad = Color(0xFFFF3B30)

/** Animations off when the system's animator duration scale is 0 (Reduce Motion equivalent). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * Explains the custom timetable in three steps (what you need, how to take the photo, what you get),
 * then scans the sheet with the automatic camera. The tutorial images follow the app language
 * (drawable-nodpi German, drawable-en-nodpi English).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPlanSetupScreen(onBack: () -> Unit, onDraft: (CustomPlanDraft) -> Unit) {
    val vm = LocalHomeViewModel.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var showCamera by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    var failure by remember { mutableStateOf<String?>(null) }
    var resultExpanded by remember { mutableStateOf(false) }

    fun read(images: List<Bitmap>) {
        if (images.isEmpty()) return
        reading = true
        scope.launch {
            progress.snapTo(0f)
            // rises steadily towards 90 % while the work runs, never further before it is done
            val rising = launch { progress.animateTo(0.9f, tween(6000, easing = LinearEasing)) }
            try {
                // the Stufenplan PDFs load while the photos are read
                val next = coroutineScope {
                    val plans = async { CustomPlanSource.plans(vm) }
                    val scan = KurswahlScanner.read(images)
                    val published = plans.await()
                    val loaded = CustomPlanSource.pick(published, null, scan.kurswahl)
                        ?: CustomPlanSource.pick(published, null, null) ?: throw CustomPlanSource.NoPlanPublished()
                    DebugCustomPlan.keep(context, scan)
                    CustomPlanDraft.fromScan(scan.kurswahl, loaded)
                }
                rising.cancel()
                progress.animateTo(1f, tween(350))
                delay(250)
                haptics.success()
                onDraft(next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: KurswahlParser.Failure) {
                haptics.error()
                failure = resources.getString(R.string.custom_error_not_a_sheet)
            } catch (e: CustomPlanSource.NoPlanPublished) {
                haptics.error()
                failure = resources.getString(R.string.custom_error_no_plan)
            } catch (e: Exception) {
                haptics.error()
                failure = resources.getString(R.string.custom_error_generic)
            } finally {
                rising.cancel()
                reading = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.custom_title)) },
                    navigationIcon = {
                        IconButton(onClick = { haptics.light(); onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back))
                        }
                    })
            },
            bottomBar = {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Button(onClick = { haptics.medium(); showCamera = true }, enabled = !reading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("customPlan.scan")) {
                        Icon(Icons.Filled.PhotoCamera, null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.custom_setup_scan), fontWeight = FontWeight.SemiBold)
                    }
                }
            }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).readableWidth()
                    .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                Spacer(Modifier.height(0.dp))
                NeedSection()
                PhotoSection()
                ResultSection(expanded = resultExpanded, onExpand = { resultExpanded = true })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.custom_setup_privacy), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        ResultOverlay(resultExpanded, onClose = { haptics.light(); resultExpanded = false })
        if (reading) ReadingScreen { progress.value }
        if (showCamera) {
            KurswahlCameraScreen(onCapture = { images -> showCamera = false; read(images) }, onCancel = { showCamera = false })
        }
    }

    failure?.let { message ->
        AlertDialog(onDismissRequest = { failure = null }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { haptics.light(); failure = null }) { Text(stringResource(android.R.string.ok)) } })
    }
}

@Composable
private fun TutorialHeader(title: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── 1 · What you need ───────────────────────────────────────────────────────────

@Composable
private fun NeedSection() {
    val accent = MaterialTheme.colorScheme.primary
    val reduceMotion = rememberReduceMotion()
    val label = stringResource(R.string.custom_setup_a11y_sample)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TutorialHeader(stringResource(R.string.custom_setup_need_title), stringResource(R.string.custom_setup_need_body))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(accent.copy(alpha = 0.08f))
                .clearAndSetSemantics { contentDescription = label },
            contentAlignment = Alignment.Center) {
            Box(Modifier.padding(vertical = 22.dp).widthIn(max = 200.dp).fillMaxWidth()
                    .aspectRatio(210f / 297f)
                    .rotate(if (reduceMotion) 0f else -2.5f)
                    .shadow(16.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))) {
                Image(painterResource(R.drawable.kurswahl_sample), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                // where the scan reads on the sample sheet (fractions of the image)
                Callout(stringResource(R.string.custom_setup_callout_subjects), 0.33f, 0.48f)
                Callout(stringResource(R.string.custom_setup_callout_hours), 0.64f, 0.33f)
                Callout(stringResource(R.string.custom_setup_callout_sums), 0.64f, 0.915f)
            }
        }
    }
}

/**
 * A label centred on a spot of the sample sheet ([x], [y] fractions of the image), moved inwards where it
 * would cross the edge, so it stays whole at every width and in every language.
 */
@Composable
private fun Callout(text: String, x: Float, y: Float) {
    Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier
            .layout { measurable, constraints ->
                val inset = 4.dp.roundToPx()
                val pill = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = constraints.maxWidth - 2 * inset))
                val left = (x * constraints.maxWidth - pill.width / 2f).roundToInt()
                    .coerceIn(inset, (constraints.maxWidth - inset - pill.width).coerceAtLeast(inset))
                val top = (y * constraints.maxHeight - pill.height / 2f).roundToInt()
                    .coerceIn(inset, (constraints.maxHeight - inset - pill.height).coerceAtLeast(inset))
                layout(constraints.maxWidth, constraints.maxHeight) { pill.place(left, top) }
            }
            .shadow(3.dp, CircleShape).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 3.dp))
}

// ── 2 · How to take the photo ──────────────────────────────────────────────────

@Composable
private fun PhotoSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TutorialHeader(stringResource(R.string.custom_setup_photo_title))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Example(R.drawable.kurswahl_good, good = true, Modifier.weight(1f))
            Example(R.drawable.kurswahl_bad, good = false, Modifier.weight(1f))
        }
        Card(shape = CardShape) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Tip(Icons.Outlined.Layers, stringResource(R.string.custom_setup_tip_flat))
                Tip(Icons.Outlined.CropFree, stringResource(R.string.custom_setup_tip_whole))
                Tip(Icons.Outlined.AutoMode, stringResource(R.string.custom_setup_tip_auto))
            }
        }
    }
}

@Composable
private fun Example(image: Int, good: Boolean, modifier: Modifier) {
    val color = if (good) Good else Bad
    val label = stringResource(if (good) R.string.custom_setup_a11y_good else R.string.custom_setup_a11y_bad)
    Column(modifier.clearAndSetSemantics { contentDescription = label }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f).clip(RoundedCornerShape(16.dp)).border(2.dp, color, RoundedCornerShape(16.dp))) {
            Image(painterResource(image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Icon(if (good) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null,
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color.White), tint = color)
        }
        Text(stringResource(if (good) R.string.custom_setup_good else R.string.custom_setup_bad), color = color,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── 3 · What you get ───────────────────────────────────────────────────────────

@Composable
private fun ResultSection(expanded: Boolean, onExpand: () -> Unit) {
    val label = stringResource(R.string.custom_setup_a11y_result)
    val haptics = rememberHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TutorialHeader(stringResource(R.string.custom_setup_result_title), stringResource(R.string.custom_setup_result_body))
        // hidden while enlarged, so the image seems to move into the overlay
        Image(painterResource(R.drawable.plan_result), null,
            Modifier.fillMaxWidth().aspectRatio(297f / 210f).alpha(if (expanded) 0f else 1f)
                .shadow(12.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                .clickable { haptics.light(); onExpand() }
                .clearAndSetSemantics { contentDescription = label; onClick { onExpand(); true } },
            contentScale = ContentScale.Crop)
    }
}

/** The result image enlarged over a dark background; a tap anywhere (or the X) collapses it. No zoom or pan. */
@Composable
private fun ResultOverlay(visible: Boolean, onClose: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val label = stringResource(R.string.custom_setup_a11y_result)
    if (visible) BackHandler(onBack = onClose)
    AnimatedVisibility(visible, enter = fadeIn(tween(if (reduceMotion) 0 else 220)), exit = fadeOut(tween(if (reduceMotion) 0 else 200))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
            Image(painterResource(R.drawable.plan_result), label,
                Modifier.align(Alignment.Center).safeDrawingPadding().padding(16.dp).fillMaxWidth().aspectRatio(297f / 210f)
                    .animateEnterExit(
                        enter = if (reduceMotion) EnterTransition.None else scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f),
                        exit = if (reduceMotion) ExitTransition.None else scaleOut(tween(200), targetScale = 0.6f))
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit)
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.a11y_close), tint = Color.White)
            }
        }
    }
}

// ── Reading ────────────────────────────────────────────────────────────────────

/** Plain full-screen progress while the photos are read and the plan is built. */
@Composable
private fun ReadingScreen(progress: () -> Float) {
    BackHandler {} // reading cannot be interrupted halfway
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.custom_reading_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            // read in the draw phase: every animation frame, no recomposition; no stop dot, no track gap
            LinearProgressIndicator(progress = progress, modifier = Modifier.width(260.dp), gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}
