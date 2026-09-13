package com.lgka

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
