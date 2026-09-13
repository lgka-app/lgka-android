package com.lgka

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Card
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import lgka.api.Resource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import lgka.api.Weather

// ── Weather row ─────────────────────────────────────────────────────────────

@Composable
fun WeatherRow(onOpen: () -> Unit) {
    val vm = LocalHomeViewModel.current
    val w = vm.weather
    val scope = rememberCoroutineScope()
    if (w != null) {
        val description = stringResource(wmoRes(w.code))
        val a11y = stringResource(R.string.a11y_weather_card, description, w.temp.toInt())
        // Card(onClick) makes the entire card the touch target, not just the text.
        val haptics = rememberHaptics()
        Card(
            onClick = { haptics.medium(); onOpen() },
            shape = CardShape,
            modifier = Modifier.fillMaxWidth().testTag("home.weather").semantics { contentDescription = a11y; role = Role.Button },
        ) {
            Box(Modifier.fillMaxWidth().clip(CardShape)) {
                SkyBox(code = w.code, isDay = w.isDay, particles = false, modifier = Modifier.matchParentSize())
                // The row defines the card height (never clips at large font sizes).
                Row(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.city), color = Color.White,
                             style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("${w.temp.toInt()}°", color = Color.White,
                             style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                        Text(description, color = Color.White.copy(alpha = 0.9f),
                             style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.feels_like, w.feelsLike.toInt()),
                             color = Color.White.copy(alpha = 0.85f),
                             style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(WmoIcons.icon(w.code, w.isDay), null, tint = Color.White, modifier = Modifier.size(30.dp))
                        w.daily.firstOrNull()?.let { today ->
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.high_low, today.tempMax.toInt(), today.tempMin.toInt()),
                                 color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    } else if (vm.weatherError) {
        HomeCard {
            Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.weather_data_not_available),
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            RetryButton { scope.launch { vm.refresh(setOf(Resource.Weather)) } }
        }
    } else {
        SkeletonRow()
    }
}
