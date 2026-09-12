package com.lgka

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import android.view.View
import android.view.HapticFeedbackConstants
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh

/** Card corner radius shared by every list card (DESIGN_GUIDELINES.md §1.3). */
val CardShape = RoundedCornerShape(16.dp)

/** 44dp tinted icon square used across the home cards (decorative). */
@Composable
fun IconTile(icon: ImageVector, alpha: Float = 0.12f) {
    Box(
        Modifier.size(44.dp).background(
            MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/**
 * Haptics — the same grammar as the iOS app (light for navigation and secondary taps,
 * medium for primary actions, success/error for outcomes), driven through the View so
 * it works inside dialogs and sheets too.
 */
class Haptics(private val view: View) {
    private fun perform(constant: Int) { view.performHapticFeedback(constant) }
    fun light() = perform(HapticFeedbackConstants.CONTEXT_CLICK)
    fun medium() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
    fun success() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    fun error() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { view.isHapticFeedbackEnabled = true; Haptics(view) }
}

/**
 * Floating toast — the iOS glass pill / Flutter FloatingToast: a capsule at the bottom
 * that slides in, stays 2 s and fades out. Never inline text, never a Snackbar bar.
 */
class ToastState(private val scope: CoroutineScope) {
    var message by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    fun show(text: String) {
        job?.cancel()
        message = text
        job = scope.launch { delay(2_200); message = null }
    }
}

@Composable
fun rememberToastState(): ToastState {
    val scope = rememberCoroutineScope()
    return remember { ToastState(scope) }
}

@Composable
fun BoxScope.ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(state.message ?: "") }
    state.message?.let { shown = it }
    AnimatedVisibility(
        visible = state.message != null,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(220)) { it / 2 },
        modifier = modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp).padding(horizontal = 24.dp),
    ) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface, shadowElevation = 6.dp) {
            Text(shown, Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                 style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}

/**
 * Tappable list card — the whole card is one touch target with Role.Button, a medium
 * haptic on tap and an optional long press (the iOS context menu equivalent).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberHaptics()
    val row: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, content = content,
        )
    }
    if (onClick != null) {
        val shape = CardShape
        Card(shape = shape, modifier = modifier.clip(shape).combinedClickable(
            enabled = enabled, role = Role.Button,
            onClick = { haptics.medium(); onClick() },
            onLongClick = onLongClick?.let { { haptics.light(); it() } })) { row() }
    } else {
        Card(shape = CardShape, modifier = modifier) { row() }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

/** Placeholder row while data loads; announced once to TalkBack. */
@Composable
fun SkeletonRow() {
    val label = stringResource(R.string.loading)
    Card(shape = CardShape, modifier = Modifier.semantics { contentDescription = label }) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Box(Modifier.size(140.dp, 14.dp).background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(90.dp, 11.dp).background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(4.dp)))
            }
        }
    }
}

/** Retry icon button with a 48dp target and a label for TalkBack. */
@Composable
fun RetryButton(onClick: () -> Unit) {
    val haptics = rememberHaptics()
    IconButton(onClick = { haptics.light(); onClick() }) {
        Icon(Icons.Filled.Refresh, stringResource(R.string.a11y_retry),
             tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** Material 3 Expressive loading indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Loading(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    LoadingIndicator(modifier = modifier.semantics { contentDescription = label })
}

/** Centered "server connection failed" state with retry. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(onClick = { haptics.light(); onRetry() }) { Text(stringResource(R.string.try_again)) }
    }
}

@Composable
fun cardColors() = CardDefaults.cardColors()

/**
 * Layout basics: on large screens (tablets, landscape) content stays in a readable
 * centred column instead of stretching edge to edge. Phones are narrower than the
 * limit, so they are unaffected.
 */
fun Modifier.readableWidth(max: Dp = 760.dp): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()
