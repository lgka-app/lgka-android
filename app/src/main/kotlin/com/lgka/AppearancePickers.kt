package com.lgka

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Accent picker — the iOS palette: plain colour circles, the selected one carries a white check
 *  and a soft ring in its own colour. Every swatch is a 48dp+ radio-button target with a spoken name. */
@Composable
fun AccentRow(swatchSize: Int, modifier: Modifier = Modifier) {
    val prefs = LocalContainer.current.prefs
    val haptics = rememberHaptics()
    val selectedLabel = stringResource(R.string.a11y_selected)
    val target = maxOf(swatchSize + 14, 48)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (swatchSize >= 44) 12.dp else 4.dp)) {
        Accent.entries.forEach { accent ->
            val selected = prefs.accentColor == accent.key
            val name = stringResource(accent.labelRes)
            val ring by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "accentRing")
            val check by animateFloatAsState(if (selected) 1f else 0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "accentCheck")
            Box(
                Modifier.size(target.dp)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = {
                        if (!selected) { haptics.light(); prefs.accentColor = accent.key }
                    })
                    .semantics { contentDescription = if (selected) "$name, $selectedLabel" else name },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size((swatchSize + 10).dp).border(2.dp, accent.color.copy(alpha = 0.45f * ring), CircleShape))
                Box(Modifier.size(swatchSize.dp).background(accent.color, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, Modifier.size((swatchSize * 0.5f).dp).scale(check), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ThemeModeRow() {
    val prefs = LocalContainer.current.prefs
    val haptics = rememberHaptics()
    val options = listOf(
        Triple("dark", Icons.Filled.DarkMode, R.string.theme_dark),
        Triple("system", Icons.Filled.BrightnessAuto, R.string.theme_auto),
        Triple("light", Icons.Filled.LightMode, R.string.theme_light))
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, (mode, icon, label) ->
            SegmentedButton(
                selected = prefs.themeMode == mode,
                onClick = { if (prefs.themeMode != mode) { haptics.light(); prefs.themeMode = mode } },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                icon = { Icon(icon, null, Modifier.size(16.dp)) }) {
                Text(stringResource(label))
            }
        }
    }
}
