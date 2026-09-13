package com.lgka

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin

/// New Year's Day fireworks — mirrors fireworks_overlay.dart (Jan 1, Berlin).
/// Decorative only; skipped when the system asks to remove animations.
@Composable
fun FireworksOverlay() {
    fun check(): Boolean {
        val berlin = ZonedDateTime.now(ZoneId.of("Europe/Berlin"))
        return berlin.monthValue == 1 && berlin.dayOfMonth == 1
    }
    var isNewYear by remember { mutableStateOf(check()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            isNewYear = check()
        }
    }
    val context = LocalContext.current
    val animationsOff = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (!isNewYear || animationsOff) return
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) { withFrameNanos { now -> t = (now - start) / 1e9f } }
    }
    val colors = listOf(Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFFF06292), Color(0xFF4DD0E1), Color(0xFFBA68C8))
    Canvas(Modifier.fillMaxSize()) {
        val burst = (t / 2.2f).toInt()
        val phase = (t % 2.2f) / 2.2f
        val rnd = java.util.Random(burst.toLong())
        val cx = size.width * (0.2f + rnd.nextFloat() * 0.6f)
        val cy = size.height * (0.15f + rnd.nextFloat() * 0.3f)
        val color = colors[burst % colors.size]
        for (i in 0 until 42) {
            val angle = i / 42f * (Math.PI * 2).toFloat()
            val dist = phase * (140f + rnd.nextFloat() * 120f)
            val alpha = (1f - phase).coerceIn(0f, 1f)
            drawCircle(color.copy(alpha = alpha * 0.9f), radius = 5f * (1f - phase * 0.5f),
                       center = Offset(cx + cos(angle) * dist, cy + sin(angle) * dist + phase * phase * 90f))
        }
    }
}
