package com.lgka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.outlined.Event
import androidx.compose.ui.platform.testTag
import lgka.api.Resource
import lgka.api.SchoolEvent
import lgka.api.Events

// ── Events ──────────────────────────────────────────────────────────────────

@Composable
fun EventsColumn() {
    val vm = LocalHomeViewModel.current
    val scope = rememberCoroutineScope()
    if (vm.eventsLoading) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(4) { SkeletonRow() } }
    } else if (vm.eventList.isEmpty()) {
        HomeCard {
            Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(if (vm.eventsError) R.string.server_connection_failed else R.string.no_events_available),
                 color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (vm.eventsError) RetryButton { scope.launch { vm.refresh(setOf(Resource.Events)) } }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vm.eventList.take(4).forEach { event ->
                val subtitle = eventSubtitle(event)
                HomeCard(modifier = Modifier.testTag("home.event").semantics(mergeDescendants = true) {
                    contentDescription = "$subtitle: ${event.title}"
                }) {
                    DateTile(event.date)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(event.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateTile(iso: String) {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull()
    val locale = ComposeLocale.current.platformLocale
    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("${date?.dayOfMonth ?: "?"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                 color = MaterialTheme.colorScheme.primary, lineHeight = 18.sp)
            Text(date?.format(DateTimeFormatter.ofPattern("MMM", locale))?.trimEnd('.') ?: "",
                 style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, lineHeight = 12.sp,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun eventSubtitle(event: SchoolEvent): String {
    val locale = ComposeLocale.current.platformLocale
    val date = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: return event.time ?: ""
    val base = date.format(DateTimeFormatter.ofPattern("EEE, d. MMMM", locale))
    return if (event.time != null) "$base · ${event.time}" else base
}
