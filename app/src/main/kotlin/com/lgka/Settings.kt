package com.lgka

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onBugReport: () -> Unit, onOpenWeb: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    var confirmLogout by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val vm = LocalHomeViewModel.current
    val privacyTitle = stringResource(R.string.privacy_label)
    val legalTitle = stringResource(R.string.legal_label)
    ModalBottomSheet(onDismissRequest = { haptics.light(); onDismiss() }) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall,
                 fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(20.dp))

            Text(stringResource(R.string.settings_section_appearance), style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Card(shape = CardShape) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.appearance_title))
                    Spacer(Modifier.height(8.dp))
                    ThemeModeRow()
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.accent_color))
                    Spacer(Modifier.height(8.dp))
                    AccentRow(swatchSize = 32)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.settings_section_more), style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Card(shape = CardShape) {
                Column {
                    SettingsTile(Icons.Outlined.BugReport, stringResource(R.string.bug_report)) { onDismiss(); onBugReport() }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    SettingsTile(Icons.Outlined.PrivacyTip, stringResource(R.string.privacy_label)) {
                        onDismiss(); onOpenWeb("https://lgka.app/privacy", privacyTitle)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    SettingsTile(Icons.Outlined.Info, stringResource(R.string.legal_label)) {
                        onDismiss(); onOpenWeb("https://lgka.app/impressum", legalTitle)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    SettingsTile(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.logout), external = false) { confirmLogout = true }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("© ${java.time.Year.now().value} ", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Luka Löhr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                     fontWeight = FontWeight.Medium)
                Text(" • v" + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.medium(); confirmLogout = false; onDismiss()
                    vm.clear() // explicit sign-out: snapshot, login and every preference go
                    container.prefs.reset(container.credentials) // back to the welcome screen
                }) {
                    Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { haptics.light(); confirmLogout = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun SettingsTile(icon: ImageVector, label: String, external: Boolean = true, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = { haptics.light(); onClick() })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
        if (external) Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp),
                           tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
