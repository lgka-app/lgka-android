package com.lgka

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.testTag
import lgka.api.DayPlan
import lgka.api.Resource
import androidx.compose.ui.res.pluralStringResource
import lgka.api.Substitutions

// ── Substitution ────────────────────────────────────────────────────────────

@Composable
fun SubstitutionCards(onOpen: (PdfRequest) -> Unit, onUnavailable: (String) -> Unit) {
    val vm = LocalHomeViewModel.current
    val scope = rememberCoroutineScope()
    if (vm.subLoading) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { SkeletonRow(); SkeletonRow() }
    } else if (vm.subError) {
        Card(shape = CardShape) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CloudOff, null, modifier = Modifier.size(40.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.server_connection_failed), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.server_connection_hint),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { scope.launch { vm.refresh(setOf(Resource.Substitutions)) } }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.try_again))
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SubCard(vm.today, onOpen, onUnavailable, "home.plan.today")
            SubCard(vm.tomorrow, onOpen, onUnavailable, "home.plan.tomorrow")
        }
    }
}

@Composable
private fun SubCard(plan: DayPlan?, onOpen: (PdfRequest) -> Unit, onUnavailable: (String) -> Unit, tag: String) {
    val vm = LocalHomeViewModel.current
    val scope = rememberCoroutineScope()
    val connectionFailed = stringResource(R.string.server_connection_failed)
    if (plan == null) {
        // the server has no plan for this day (never published) — tap to re-check
        HomeCard(onClick = { scope.launch { vm.refresh(setOf(Resource.Substitutions)) } }) {
            IconTile(Icons.Filled.Refresh)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.error_loading), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val canOpen = plan.canDisplay
    val weekday = weekdayRes(plan.meta.weekday)?.let { stringResource(it) } ?: stringResource(R.string.no_info_yet)
    val entries = plan.plan.entries
    val subtitle = if (canOpen) {
        stringResource(R.string.plan_with_date, plan.meta.date,
                       if (entries.isEmpty()) stringResource(R.string.no_substitutions)
                       else pluralStringResource(R.plurals.substitutions_count, entries.size, entries.size))
    } else null
    HomeCard(onClick = {
        scope.launch {
            try {
                onOpen(PdfRequest(vm.pdfFile(plan.pdf.sha256, plan.pdf.url), weekday, null))
            } catch (e: Exception) {
                onUnavailable(connectionFailed)
            }
        }
    }, enabled = canOpen, modifier = Modifier.testTag(tag)) {
        IconTile(Icons.Outlined.CalendarToday, if (canOpen) 0.12f else 0.06f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (canOpen) weekday else stringResource(R.string.no_info_yet),
                 fontWeight = FontWeight.SemiBold,
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canOpen) 1f else 0.45f))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canOpen) Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(14.dp),
                          tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** German Untis weekday name -> localized resource; null for "weekend"/unknown. */
fun weekdayRes(german: String?): Int? = when (german) {
    "Montag" -> R.string.weekday_montag
    "Dienstag" -> R.string.weekday_dienstag
    "Mittwoch" -> R.string.weekday_mittwoch
    "Donnerstag" -> R.string.weekday_donnerstag
    "Freitag" -> R.string.weekday_freitag
    "Samstag" -> R.string.weekday_samstag
    "Sonntag" -> R.string.weekday_sonntag
    else -> null
}
