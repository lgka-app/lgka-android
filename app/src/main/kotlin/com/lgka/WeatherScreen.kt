package com.lgka

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.BitmapShader
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

/// WMO -> Material icon mapping (UI layer).
object WmoIcons {
    fun icon(code: Int, isDay: Boolean): ImageVector = when (code) {
        0, 1 -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
        2 -> Icons.Outlined.WbCloudy
        3 -> Icons.Outlined.Cloud
        45, 48 -> Icons.Outlined.Dehaze
        in 51..67, in 80..82 -> Icons.Outlined.WaterDrop
        in 71..77, 85, 86 -> Icons.Outlined.AcUnit
        95, 96, 99 -> Icons.Outlined.Thunderstorm
        else -> Icons.Outlined.Cloud
    }
}

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


// ── Weather screen ──────────────────────────────────────────────────────────
// Apple-Weather-style full-bleed sky with translucent cards. The cards are
// content, so they are plain scrims (DESIGN_GUIDELINES.md §1.3).

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val vm = LocalHomeViewModel.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var menu by remember { mutableStateOf(false) }
    val w = vm.weather

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (w != null) {
            SkyBox(code = preview?.first ?: w.code, isDay = preview?.second ?: w.isDay,
                   particles = true, modifier = Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().readableWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(56.dp))
                Hero(w)
                Spacer(Modifier.height(20.dp))
                if (w.hourly.isNotEmpty()) { HourlyCard(w); Spacer(Modifier.height(14.dp)) }
                if (w.daily.isNotEmpty()) { DailyCard(w); Spacer(Modifier.height(14.dp)) }
                StatsGrid(w)
                Spacer(Modifier.height(20.dp))
                val uriHandler = LocalUriHandler.current
                // Attribution comes from the payload: the school's rooftop station when it is
                // healthy (current values), Open-Meteo for the forecast / as fallback.
                Column(Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)
                           .clickable { uriHandler.openUri("https://open-meteo.com/") }
                           .padding(vertical = 14.dp),
                       horizontalAlignment = Alignment.CenterHorizontally) {
                    if (w.fromSchoolStation) {
                        Text(stringResource(R.string.weather_source_school), color = Color.White.copy(alpha = 0.9f),
                             style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    w.attribution.forEach { line ->
                        Text(line, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (vm.weatherError) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.weather_data_not_available), color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.check_internet_connection), color = Color.White.copy(alpha = 0.8f),
                             style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { haptics.light(); scope.launch { vm.refresh(setOf(lgka.api.Resource.Weather)) } }) { Text(stringResource(R.string.try_again)) }
                    }
                } else Loading()
            }
        }

        // top bar overlay
        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptics.light(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back), tint = Color.White) }
            Text(stringResource(R.string.weather_page_title), color = Color.White, fontWeight = FontWeight.SemiBold,
                 modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            if (BuildConfig.DEBUG) {
                // Debug-only sky preview: long-press the title (no visible control).
                Box {
                    Box(Modifier.size(48.dp).combinedClickable(onClick = {}, onLongClick = { menu = true },
                        onLongClickLabel = stringResource(R.string.a11y_sky_preview)))
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.live)) }, onClick = { preview = null; menu = false })
                        listOf("Klar (Tag)" to (0 to true), "Klar (Nacht)" to (0 to false),
                               "Teilweise bewölkt" to (2 to true), "Bedeckt" to (3 to true),
                               "Nebel" to (45 to true), "Regen (Tag)" to (63 to true),
                               "Regen (Nacht)" to (63 to false), "Gewitter" to (95 to true),
                               "Schnee (Tag)" to (73 to true), "Schnee (Nacht)" to (73 to false))
                            .forEach { (label, value) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { preview = value; menu = false })
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(w: WeatherUi) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.city), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Text("${w.temp.toInt()}°", color = Color.White, style = MaterialTheme.typography.displayLarge.copy(fontSize = 92.sp), fontWeight = FontWeight.Thin)
        Text(stringResource(wmoRes(w.code)), color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.Medium)
        w.daily.firstOrNull()?.let { today ->
            Text(stringResource(R.string.high_low, today.tempMax.toInt(), today.tempMin.toInt()), color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GlassCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(18.dp)).padding(14.dp), content = content)
}

@Composable
private fun HourlyCard(w: WeatherUi) {
    GlassCard {
        Text(stringResource(R.string.hourly_forecast_label), color = Color.White.copy(alpha = 0.75f),
             style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            w.hourly.forEach { h ->
                val desc = stringResource(wmoRes(h.code))
                val label = stringResource(R.string.a11y_hour, h.time, h.temp.toInt(), desc)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }) {
                    Text(h.time, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Icon(WmoIcons.icon(h.code, h.isDay), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    if (h.pop >= 0.1) Text("${(h.pop * 100).toInt()}%", color = Color(0xFF9FE8FF), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("${h.temp.toInt()}°", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DailyCard(w: WeatherUi) {
    val weekMin = w.daily.minOf { it.tempMin }
    val weekMax = w.daily.maxOf { it.tempMax }
    val span = (weekMax - weekMin).coerceAtLeast(1.0)
    GlassCard {
        Text(stringResource(R.string.three_day_forecast_label), color = Color.White.copy(alpha = 0.75f),
             style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        w.daily.forEach { d ->
            val day = dayLabel(d.date)
            val desc = stringResource(wmoRes(d.code))
            val label = stringResource(R.string.a11y_day, day, desc, d.tempMax.toInt(), d.tempMin.toInt())
            Row(Modifier.padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = label },
                verticalAlignment = Alignment.CenterVertically) {
                Text(day, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.width(52.dp))
                Icon(WmoIcons.icon(d.code, true), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (d.pop >= 0.1) "${(d.pop * 100).toInt()}%" else "", color = Color(0xFF9FE8FF),
                     style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
                Text("${d.tempMin.toInt()}°", color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.width(8.dp))
                val startF = ((d.tempMin - weekMin) / span).toFloat().coerceIn(0f, 1f)
                val endF = ((d.tempMax - weekMin) / span).toFloat().coerceIn(0f, 1f)
                Row(Modifier.weight(1f).height(5.dp).background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(3.dp))) {
                    if (startF > 0f) Spacer(Modifier.weight(startF.coerceAtLeast(0.001f)))
                    Box(Modifier.weight((endF - startF).coerceAtLeast(0.05f)).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7FDBFF), Color(0xFFFFDC00))), RoundedCornerShape(3.dp)))
                    if (endF < 1f) Spacer(Modifier.weight((1f - endF).coerceAtLeast(0.001f)))
                }
                Spacer(Modifier.width(8.dp))
                Text("${d.tempMax.toInt()}°", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatsGrid(w: WeatherUi) {
    val uviLabel = stringResource(when {
        w.uvi < 3 -> R.string.uvi_low; w.uvi < 6 -> R.string.uvi_medium
        w.uvi < 8 -> R.string.uvi_high; w.uvi < 11 -> R.string.uvi_very_high
        else -> R.string.uvi_extreme
    })
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatTile(stringResource(R.string.weather_humidity_short), "${w.humidity} %", Modifier.weight(1f))
            StatTile(stringResource(R.string.weather_wind_short), "${w.windSpeed.toInt()} km/h", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatTile(stringResource(R.string.pressure), "${w.pressure} hPa", Modifier.weight(1f))
            StatTile(stringResource(R.string.uv_index), "%.1f · %s".format(Locale.ROOT, w.uvi, uviLabel), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(18.dp)).padding(14.dp).heightIn(min = 64.dp)
               .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun dayLabel(iso: String): String {
    val today = LocalDate.now(HomeViewModel.berlin)
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    if (date == today) return stringResource(R.string.today)
    val locale = ComposeLocale.current.platformLocale
    return date.format(DateTimeFormatter.ofPattern("EEE", locale))
}
