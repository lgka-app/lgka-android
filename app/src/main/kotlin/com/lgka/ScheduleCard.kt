package com.lgka

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import lgka.ScheduleGrades
import lgka.api.Resource
import lgka.api.knownClass
import lgka.api.normalizeClass
import lgka.api.scheduleFor
import lgka.api.Schedules
import lgka.api.preferredGroup

// ── Schedule ────────────────────────────────────────────────────────────────

@Composable
fun ScheduleCard(onSetClass: () -> Unit, onOpen: (PdfRequest) -> Unit, onUnavailable: (String) -> Unit,
                 onCustomPlan: (edit: Boolean) -> Unit) {
    val vm = LocalHomeViewModel.current
    val prefs = LocalContainer.current.prefs
    val scope = rememberCoroutineScope()

    if (vm.scheduleLoading) {
        SkeletonRow()
    } else if (vm.preferredGroup.isEmpty()) {
        HomeCard {
            Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(if (vm.scheduleError) R.string.server_connection_failed else R.string.no_schedules_available),
                 color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (vm.scheduleError) RetryButton { scope.launch { vm.refresh(setOf(Resource.Schedules)) } }
        }
    } else {
        val cls = prefs.selectedScheduleClass
        if (cls.isEmpty()) {
            HomeCard(onClick = onSetClass) {
                IconTile(Icons.Outlined.School)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.schedule_no_class_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.schedule_no_class_sub), style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(14.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val group = vm.preferredGroup
            val half = stringResource(if (group.firstOrNull()?.halbjahr == "1. Halbjahr") R.string.first_semester else R.string.second_semester)
            val className = formatClass(cls)
            val unavailable = stringResource(R.string.schedule_not_available, half)
            val title = stringResource(R.string.title_with_semester, className, half)
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            HomeCard(
                onLongClick = onSetClass, // the iOS context menu equivalent
                onClick = {
                    // the PDF whose class index / grades contain the class (5-10, J11, J12, a future J13, …)
                    val target = scheduleFor(cls, group) ?: return@HomeCard
                    val pdf = target.pdf
                    if (pdf == null || !target.available) { onUnavailable(unavailable); return@HomeCard }
                    // opens straight away like the substitution cards: the PDF is already on disk
                    scope.launch {
                        try {
                            val file = vm.pdfFile(pdf.sha256, pdf.url)
                            onOpen(PdfRequest(file, className, target.classIndex[cls], target, target.classIndex)) // header: class only
                        } catch (e: Exception) {
                            onUnavailable(unavailable) // home_screen SnackBar parity
                        }
                    }
                },
                modifier = Modifier.testTag("home.schedule")) {
                IconTile(Icons.Outlined.TableChart)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(className, fontWeight = FontWeight.SemiBold)
                    Text(half, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(14.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // J11 / J12: a personal plan from the crowded Stufenplan
            if ((ScheduleGrades.gradeOf(cls) ?: 0) >= 11 || LocalContainer.current.customPlans.saved != null) {
                CustomPlanCard(onOpen = onOpen, onCustomPlan = onCustomPlan)
            }
            }
        }
    }
}

@Composable
fun formatClass(cls: String): String = classDisplayName(LocalResources.current, cls)

/** "Klasse 7b" / "Jahrgang 11" — any Jahrgang number, no per-year strings. */
fun classDisplayName(resources: android.content.res.Resources, cls: String): String {
    val grade = ScheduleGrades.gradeOf(cls)
    return if (grade != null && cls.lowercase().startsWith("j")) resources.getString(R.string.jahrgang_named, grade.toString())
    else resources.getString(R.string.class_name, cls.replaceFirstChar { it.uppercase() })
}

@Composable
fun ClassDialog(onDismiss: () -> Unit) {
    val prefs = LocalContainer.current.prefs
    val vm = LocalHomeViewModel.current
    val haptics = rememberHaptics()
    var input by remember { mutableStateOf(prefs.selectedScheduleClass) }
    var rejected by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_class_title)) },
        text = {
            // spaces are dropped while typing, so "10 b" still fits the three-character limit
            OutlinedTextField(value = input, onValueChange = { input = it.filterNot(Char::isWhitespace).take(3); rejected = null },
                              placeholder = { Text(stringResource(R.string.search_hint)) }, singleLine = true,
                              isError = rejected != null,
                              supportingText = rejected?.let { { Text(stringResource(R.string.no_results, it)) } })
        },
        confirmButton = {
            TextButton(onClick = {
                if (normalizeClass(input).isEmpty()) { haptics.medium(); onDismiss(); return@TextButton }
                // only classes with a page in the timetable: anything else would open a PDF on page 1
                val cls = knownClass(input, vm.preferredGroup)
                if (cls == null) {
                    haptics.error()
                    rejected = normalizeClass(input).uppercase(Locale.ROOT)
                    return@TextButton
                }
                haptics.medium()
                prefs.selectedScheduleClass = cls
                onDismiss()
            }) { Text(stringResource(R.string.set_class_button)) }
        },
        dismissButton = { TextButton(onClick = { haptics.light(); onDismiss() }) { Text(stringResource(R.string.cancel)) } })
}
