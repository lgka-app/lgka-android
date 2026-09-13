package com.lgka

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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
    FireworksCanvas()
}

/// Rockets rise from the bottom edge, burst near the top and fall apart with
/// drag, gravity and short tails. Several bursts overlap.
@Composable
private fun FireworksCanvas() {
    val sim = remember { FireworksSimulation() }
    val haptics = rememberHaptics()
    val glow = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current.density
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos = it }
            if (sim.takeBurst()) haptics.light()
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        sim.advance(frameNanos, size.width / density, size.height / density)
        sim.draw(this, density, glow)
    }
}

private class Rocket(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color)

private class Spark(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val life: Float, val radius: Float, val color: Color,
    val twinkle: Boolean, val drag: Float, val gravity: Float,
) {
    val trailX = FloatArray(TRAIL)
    val trailY = FloatArray(TRAIL)
    var trailCount = 0
    var age = 0f

    fun pushTrail() {
        if (trailCount == TRAIL) {
            System.arraycopy(trailX, 1, trailX, 0, TRAIL - 1)
            System.arraycopy(trailY, 1, trailY, 0, TRAIL - 1)
            trailCount--
        }
        trailX[trailCount] = x
        trailY[trailCount] = y
        trailCount++
    }

    companion object { const val TRAIL = 9 }
}

private class Flash(val x: Float, val y: Float, val color: Color) {
    var age = 0f
}

/// Positions in dp so the bursts look the same on every screen density.
private class FireworksSimulation {
    private val rockets = ArrayList<Rocket>()
    private val sparks = ArrayList<Spark>()
    private val flashes = ArrayList<Flash>()
    private var lastNanos = 0L
    private var nextLaunchIn = 0.2f
    private var bursts = 0

    fun takeBurst(): Boolean = if (bursts > 0) { bursts = 0; true } else false

    fun advance(nanos: Long, width: Float, height: Float) {
        if (width <= 0f || height <= 0f || nanos == 0L) return
        // clamp so a frame after the app was in the background does not jump
        val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 1f / 30f)
        lastNanos = nanos

        nextLaunchIn -= dt
        if (nextLaunchIn <= 0f) {
            launch(width, height)
            if (Random.nextFloat() < 0.2f) launch(width, height) // now and then a pair
            nextLaunchIn = Random.nextFloat() * 1.0f + 0.8f
        }

        val rocketIterator = rockets.iterator()
        while (rocketIterator.hasNext()) {
            val r = rocketIterator.next()
            r.vy += ROCKET_GRAVITY * dt
            r.x += r.vx * dt
            r.y += r.vy * dt
            repeat(2) { // the rising spark trail
                sparks += Spark(r.x, r.y, rnd(-18f, 18f), rnd(20f, 60f), rnd(0.25f, 0.5f), rnd(0.8f, 1.4f),
                    TRAIL_COLOR, twinkle = false, drag = 3f, gravity = 40f)
            }
            if (r.vy > -35f) {
                explode(r)
                rocketIterator.remove()
            }
        }

        val sparkIterator = sparks.iterator()
        while (sparkIterator.hasNext()) {
            val s = sparkIterator.next()
            s.age += dt
            if (s.age >= s.life) { sparkIterator.remove(); continue }
            val damping = exp(-s.drag * dt)
            s.vx *= damping
            s.vy = s.vy * damping + s.gravity * dt
            s.pushTrail()
            s.x += s.vx * dt
            s.y += s.vy * dt
        }

        val flashIterator = flashes.iterator()
        while (flashIterator.hasNext()) {
            val f = flashIterator.next()
            f.age += dt
            if (f.age > FLASH_SECONDS) flashIterator.remove()
        }
    }

    private fun launch(width: Float, height: Float) {
        val startY = height + 10f
        val apex = height * rnd(0.10f, 0.38f)
        val speed = sqrt(2f * ROCKET_GRAVITY * (startY - apex))
        rockets += Rocket(width * rnd(0.15f, 0.85f), startY, rnd(-30f, 30f), -speed, PALETTE.random())
    }

    private fun explode(r: Rocket) {
        val secondary = if (Random.nextBoolean()) PALETTE.random() else r.color
        val ring = Random.nextFloat() < 0.3f
        val count = Random.nextInt(160, 221)
        val maxSpeed = rnd(230f, 330f)
        for (n in 0 until count) {
            val angle = Random.nextFloat() * (2 * Math.PI).toFloat()
            // a filled sphere looks even when radii follow the square root
            val speed = if (ring) maxSpeed * rnd(0.92f, 1f) else maxSpeed * sqrt(Random.nextFloat())
            sparks += Spark(r.x, r.y, cos(angle) * speed, sin(angle) * speed, rnd(1.8f, 3.0f), rnd(1.5f, 2.6f),
                if (n % 3 == 0) secondary else r.color, twinkle = Random.nextFloat() < 0.3f, drag = 1.3f, gravity = 55f)
        }
        flashes += Flash(r.x, r.y, r.color)
        bursts++
    }

    fun draw(scope: DrawScope, density: Float, glow: Boolean) = with(scope) {
        val blend = if (glow) BlendMode.Plus else BlendMode.SrcOver

        for (f in flashes) {
            val t = f.age / FLASH_SECONDS
            val radius = (14f + 110f * t) * density
            val center = Offset(f.x * density, f.y * density)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.2f * (1 - t)), f.color.copy(alpha = 0.1f * (1 - t)), Color.Transparent),
                    center = center, radius = radius),
                radius = radius, center = center, blendMode = blend)
        }

        for (r in rockets) {
            drawCircle(Color.White, radius = 2.2f * density, center = Offset(r.x * density, r.y * density), blendMode = blend)
        }

        val tail = Path()
        for (s in sparks) {
            val progress = s.age / s.life
            var alpha = (1f - progress).pow(1.3f)
            if (s.twinkle && progress > 0.35f) alpha *= 0.45f + 0.55f * abs(sin(s.age * 28f))
            if (alpha <= 0.02f) continue

            if (s.trailCount > 1) {
                tail.reset()
                tail.moveTo(s.trailX[0] * density, s.trailY[0] * density)
                for (i in 1 until s.trailCount) tail.lineTo(s.trailX[i] * density, s.trailY[i] * density)
                tail.lineTo(s.x * density, s.y * density)
                drawPath(tail, s.color.copy(alpha = alpha * 0.5f),
                    style = Stroke(width = s.radius * 0.9f * density, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
            }
            val radius = s.radius * (1f - 0.35f * progress) * density
            val center = Offset(s.x * density, s.y * density)
            if (glow) drawCircle(s.color.copy(alpha = alpha * 0.22f), radius = radius * 3f, center = center, blendMode = blend)
            drawCircle(s.color.copy(alpha = alpha), radius = radius, center = center, blendMode = blend)
        }
    }

    private fun rnd(from: Float, to: Float) = from + Random.nextFloat() * (to - from)

    companion object {
        const val ROCKET_GRAVITY = 430f
        const val FLASH_SECONDS = 0.45f
        val TRAIL_COLOR = Color(1f, 0.85f, 0.6f)
        // the Flutter palette, a touch brighter so it glows on the dark background
        val PALETTE = listOf(
            Color(1f, 0.30f, 0.30f), // red
            Color(0.35f, 0.62f, 1f), // blue
            Color(0.35f, 0.95f, 0.50f), // green
            Color(0.78f, 0.45f, 1f), // purple
            Color(1f, 0.62f, 0.20f), // orange
            Color(1f, 0.90f, 0.30f), // yellow
            Color(1f, 0.45f, 0.75f), // pink
        )
    }
}
