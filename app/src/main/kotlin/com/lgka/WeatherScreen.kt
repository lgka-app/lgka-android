package com.lgka

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import lgka.api.Resource
import lgka.api.Weather

// ── Weather screen ──────────────────────────────────────────────────────────
// Apple-Weather-style full-bleed sky with translucent cards. The cards are
// content, so they are plain scrims (DESIGN_GUIDELINES.md §1.3).

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
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
                // Attribution comes from the payload: the school's rooftop station when it is
                // healthy (current values), Open-Meteo for the forecast / as fallback.
                Column(Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)
                           .clickable { haptics.light(); openExternally(context, "https://open-meteo.com/") } // the user's own browser, like the Krankmeldung form
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
