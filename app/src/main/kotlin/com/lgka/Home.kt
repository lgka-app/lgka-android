package com.lgka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import lgka.ScheduleGrades
import lgka.api.DayPlan
import lgka.api.Resource
import lgka.api.ScheduleItem
import lgka.api.SchoolEvent
import lgka.api.scheduleFor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/// Home hub — mirrors home_screen.dart: weather card, substitution cards,
/// schedule class card, events; toolbar: news / sick note / settings.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (Route) -> Unit) {
    val vm = LocalHomeViewModel.current
    val prefs = LocalContainer.current.prefs
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showClassDialog by remember { mutableStateOf(false) }
    var pdf by remember { mutableStateOf<PdfRequest?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val toast = rememberToastState()
    val haptics = rememberHaptics()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.bootstrap() }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title), fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = {
                        haptics.light()
                        onNavigate(NewsRoute)
                    }, modifier = Modifier.testTag("home.news")) { Icon(Icons.Outlined.Newspaper, stringResource(R.string.news)) }
                    IconButton(onClick = {
                        haptics.light()
                        if (prefs.krankmeldungInfoShown) openKrankmeldungForm(context) else onNavigate(KrankmeldungInfoRoute)
                    }, modifier = Modifier.testTag("home.sick")) { Icon(Icons.Outlined.MedicalServices, stringResource(R.string.krankmeldung)) }
                    IconButton(onClick = {
                        haptics.light()
                        showSettings = true
                    }, modifier = Modifier.testTag("home.settings")) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                })
        }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                haptics.medium()
                scope.launch {
                    refreshing = true
                    vm.refresh()
                    refreshing = false
                }
            },
            modifier = Modifier.padding(top = padding.calculateTopPadding())) {
            LazyColumn(
                Modifier.fillMaxSize().readableWidth().padding(horizontal = 20.dp),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { WeatherRow { onNavigate(WeatherRoute) } }
                item { SectionHeader(stringResource(R.string.substitution_plan)) }
                item { SubstitutionCards(onOpen = { req -> pdf = req }, onUnavailable = { msg -> haptics.error(); toast.show(msg) }) }
                item { SectionHeader(stringResource(R.string.schedule)) }
                item {
                    ScheduleCard(
                        onSetClass = { showClassDialog = true },
                        onOpen = { req -> pdf = req },
                        onUnavailable = { msg -> haptics.error(); toast.show(msg) })
                }
                item { SectionHeader(stringResource(R.string.termine)) }
                item { EventsColumn() }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    ToastHost(toast)
    }

    if (showSettings) SettingsSheet(
        onBugReport = { showSettings = false; onNavigate(BugReportRoute) },
        // privacy / legal notice: the same path as the bug report (dismiss, then push)
        onOpenWeb = { url, title -> showSettings = false; onNavigate(WebRoute(url, title)) }) { showSettings = false }
    if (showClassDialog) ClassDialog { showClassDialog = false }
    pdf?.let { request -> PdfViewerDialog(request) { pdf = null } }
}

/** [targetPage] is a real 1-based PDF page (the API's class index). */
data class PdfRequest(val file: File, val title: String, val targetPage: Int?, val schedule: ScheduleItem? = null,
                      val classIndex: Map<String, Int> = emptyMap())

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

// ── Schedule ────────────────────────────────────────────────────────────────

@Composable
fun ScheduleCard(onSetClass: () -> Unit, onOpen: (PdfRequest) -> Unit, onUnavailable: (String) -> Unit) {
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
    val haptics = rememberHaptics()
    var input by remember { mutableStateOf(prefs.selectedScheduleClass) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_class_title)) },
        text = {
            OutlinedTextField(value = input, onValueChange = { input = it.take(3) },
                              placeholder = { Text(stringResource(R.string.search_hint)) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                haptics.medium()
                val cls = input.trim().lowercase(Locale.ROOT)
                if (cls.isNotEmpty()) prefs.selectedScheduleClass = cls
                onDismiss()
            }) { Text(stringResource(R.string.set_class_button)) }
        },
        dismissButton = { TextButton(onClick = { haptics.light(); onDismiss() }) { Text(stringResource(R.string.cancel)) } })
}

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
