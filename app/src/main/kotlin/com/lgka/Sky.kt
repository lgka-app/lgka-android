package com.lgka

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.BitmapShader
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.material.icons.outlined.Settings

/// AGSL port of the iOS Sky.metal fbm cloud shader (Android 13+, with a
/// gradient fallback below).
private const val SKY_AGSL = """
uniform float2 iRes;
uniform float iTime;
uniform float uCloud;
uniform float uDay;
// 256x256 tiling random texture (red: lattice values, green: per-cell hash). Sampling it
// with hardware bilinear filtering is exact at any shader precision; the arithmetic
// fract() hashes collapsed into flat cells on mobile GPUs.
uniform shader noise;

float2 wrap(float2 p) { return p - floor(p * (1.0 / 256.0)) * 256.0; }
float hash21(float2 p) { return noise.eval(wrap(floor(p)) + 0.5).g; }
float vnoise(float2 p) {
    float2 i = floor(p); float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);           // Hermite curve, as in Sky.metal
    return noise.eval(wrap(i) + u + 0.5).r;       // bilinear lattice lookup
}
float fbm(float2 p) {
    float v = 0.0; float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        v += amp * vnoise(p);
        p = p * 2.03 + float2(17.0, 9.2);
        amp *= 0.55;
    }
    return v;
}
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iRes;
    float aspect = iRes.x / iRes.y;
    float overcast = smoothstep(0.55, 0.95, uCloud);
    float3 top = uDay > 0.5
        ? mix(float3(0.16, 0.42, 0.82), float3(0.36, 0.42, 0.50), overcast)
        : float3(0.015, 0.03, 0.10);
    float3 bottom = uDay > 0.5
        ? mix(float3(0.52, 0.72, 0.94), float3(0.55, 0.60, 0.66), overcast)
        : float3(0.08, 0.12, 0.26);
    float3 col = mix(top, bottom, uv.y);

    if (uDay > 0.5 && uCloud < 0.8) {
        float2 d2 = (uv - float2(0.78, 0.20)) * float2(aspect, 1.0);
        float d = length(d2);
        float glow = exp(-d * d * 28.0) * 0.55 * (0.95 + 0.05 * sin(iTime * 0.8));
        float core = exp(-d * d * 420.0) * 1.1;
        col += float3(1.0, 0.86, 0.45) * (glow + core) * (1.0 - uCloud * 0.85);
    }

    if (uDay < 0.5 && uCloud < 0.6) {
        float dim = (1.0 - uCloud) * (1.0 - smoothstep(0.55, 0.95, uv.y));
        float2 suv = uv * float2(aspect, 1.0);
        float2 g = suv * 150.0;
        float2 cell = floor(g);
        float h = hash21(cell);
        if (h > 0.90) {
            float2 sp = cell + 0.15 + 0.7 * float2(hash21(cell + 7.0), hash21(cell + 13.0));
            float d = length(g - sp);
            float size = 0.045 + 0.05 * hash21(cell + 3.0);
            float core = exp(-d * d / (size * size)) * 0.6;
            float tw = 0.55 + 0.45 * sin(iTime * (0.4 + h * 1.6) + h * 40.0);
            col += float3(0.85, 0.9, 1.0) * core * tw * dim;
        }
        float2 g2 = suv * 34.0;
        float2 base = floor(g2);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                float2 c2 = base + float2(float(dx), float(dy));
                float h2 = hash21(c2);
                if (h2 <= 0.965) continue;
                float2 sp = c2 + 0.2 + 0.6 * float2(hash21(c2 + 7.0), hash21(c2 + 13.0));
                float2 dv = g2 - sp;
                float d = length(dv);
                float mag = (h2 - 0.965) / 0.035;
                float size = 0.05 + 0.10 * mag;
                float tw = 0.65 + 0.35 * sin(iTime * (0.5 + h2 * 2.0) + h2 * 60.0);
                float core = exp(-d * d / (size * size));
                float glow = exp(-d * d / (size * size * 26.0)) * 0.20 * mag;
                float spikes = 0.0;
                if (mag > 0.5) {
                    float sx = exp(-abs(dv.x) * 26.0) * exp(-abs(dv.y) * 3.2);
                    float sy = exp(-abs(dv.y) * 26.0) * exp(-abs(dv.x) * 3.2);
                    spikes = (sx + sy) * 0.35 * (mag - 0.5) * 2.0;
                }
                float3 tint = mix(float3(0.80, 0.88, 1.0), float3(1.0, 0.92, 0.78),
                                  hash21(c2 + 21.0));
                col += tint * (core + glow + spikes) * tw * dim * (0.55 + 0.45 * mag);
            }
        }
    }

    float2 p1 = uv * float2(2.6 * aspect, 5.2) + float2(iTime * 0.020, 0.0);
    float2 p2 = uv * float2(4.6 * aspect, 8.8) + float2(iTime * 0.045, 3.7);
    float n = fbm(p1) * 0.72 + fbm(p2) * 0.42;
    float threshold = 0.72 - uCloud * 0.38;
    float shape = smoothstep(threshold, threshold + 0.32, n) * min(uCloud * 1.25, 1.0);
    float shade = fbm(p1 + float2(0.0, 0.35));
    float3 cd = mix(float3(0.99, 0.99, 1.0), float3(0.72, 0.74, 0.79), shade * 0.8);
    float3 cn = mix(float3(0.20, 0.22, 0.28), float3(0.10, 0.11, 0.15), shade * 0.8);
    col = mix(col, uDay > 0.5 ? cd : cn, shape * 0.92);

    col += (hash21(fragCoord + fract(iTime) * 61.7) - 0.5) / 160.0;
    return half4(half3(col), 1.0);
}
"""

/** Deterministic 256x256 random texture for the sky shader (red: noise lattice, green: hash). */
private fun noiseBitmap(): Bitmap {
    val size = 256
    val rnd = java.util.Random(20260912L)
    val pixels = IntArray(size * size) { (0xFF shl 24) or (rnd.nextInt(256) shl 16) or (rnd.nextInt(256) shl 8) or rnd.nextInt(256) }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

fun cloudiness(code: Int): Float = when (code) {
    0, 1 -> 0.12f
    2 -> 0.5f
    45, 48 -> 0.95f
    else -> 0.85f
}

/// Animated sky: AGSL RuntimeShader (33+) or gradient fallback; optional
/// Canvas rain/snow particles.
@Composable
fun SkyBox(code: Int, isDay: Boolean, particles: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) {
            withFrameNanos { now -> t = (now - start) / 1e9f }
        }
    }

    Box(modifier) {
        if (Build.VERSION.SDK_INT >= 33) {
            val shader = remember {
                RuntimeShader(SKY_AGSL).apply {
                    setInputShader("noise", BitmapShader(noiseBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                        .apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
                }
            }
            Canvas(Modifier.matchParentSize()) {
                shader.setFloatUniform("iRes", size.width, size.height)
                shader.setFloatUniform("iTime", t)
                shader.setFloatUniform("uCloud", cloudiness(code))
                shader.setFloatUniform("uDay", if (isDay) 1f else 0f)
                drawRect(brush = ShaderBrush(shader))
            }
        } else {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                if (isDay) listOf(Color(0xFF2A6BD1), Color(0xFF85B8F0))
                else listOf(Color(0xFF04081A), Color(0xFF141F42)))))
        }
        if (particles && animate) {
            when (code) {
                in 51..67, in 80..82, 95, 96, 99 -> Precip(rain = true)
                in 71..77, 85, 86 -> Precip(rain = false)
            }
        }
    }
}

@Composable
private fun BoxScope.Precip(rain: Boolean) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) { withFrameNanos { now -> t = (now - start) / 1e9f } }
    }
    val seeds = remember { List(if (rain) 90 else 60) { Random(it).nextFloat() to Random(it + 999).nextFloat() } }
    Canvas(Modifier.matchParentSize()) {
        seeds.forEachIndexed { i, (a, b) ->
            if (rain) {
                val speed = 1500f + a * 700f
                val x = b * size.width + sin(t * 0.7f) * 24f
                val y = (a * size.height + t * speed) % (size.height + 60f) - 40f
                drawLine(Color.White.copy(alpha = 0.35f),
                         start = androidx.compose.ui.geometry.Offset(x, y),
                         end = androidx.compose.ui.geometry.Offset(x - 6f, y + 36f),
                         strokeWidth = 3.5f)
            } else {
                val speed = 140f + a * 140f
                val x = b * size.width + sin(t * (0.6f + a) + i) * 50f
                val y = (a * size.height + t * speed) % (size.height + 30f) - 20f
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 5f + a * 6f,
                           center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
    }
}
