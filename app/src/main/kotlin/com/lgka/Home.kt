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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import lgka.ScheduleGrades
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
                        onNavigate(if (prefs.krankmeldungInfoShown) KrankmeldungFormRoute else KrankmeldungInfoRoute)
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
                    vm.loadAll(FetchMode.Refresh)
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
                item { SubstitutionCards { req -> pdf = req } }
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

    if (showSettings) SettingsSheet(onBugReport = { showSettings = false; onNavigate(BugReportRoute) }) { showSettings = false }
    if (showClassDialog) ClassDialog { showClassDialog = false }
    pdf?.let { request -> PdfViewerDialog(request) { pdf = null } }
}

data class PdfRequest(val file: File, val title: String, val targetPage: Int?, val schedule: SchoolApi.Schedule? = null,
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
            RetryButton { scope.launch { vm.loadWeather(FetchMode.Refresh) } }
        }
    } else {
        SkeletonRow()
    }
}

// ── Substitution ────────────────────────────────────────────────────────────

@Composable
fun SubstitutionCards(onOpen: (PdfRequest) -> Unit) {
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
                OutlinedButton(onClick = { scope.launch { vm.loadSubstitution(FetchMode.Refresh) } }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.try_again))
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SubCard(vm.today, onOpen, "home.plan.today")
            SubCard(vm.tomorrow, onOpen, "home.plan.tomorrow")
        }
    }
}

@Composable
private fun SubCard(plan: SchoolApi.SubPlan?, onOpen: (PdfRequest) -> Unit, tag: String) {
    val vm = LocalHomeViewModel.current
    val scope = rememberCoroutineScope()
    if (plan == null) {
        // per-card failure (home_screen per-day retry parity)
        HomeCard(onClick = { scope.launch { vm.loadSubstitution(FetchMode.Refresh) } }) {
            IconTile(Icons.Filled.Refresh)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.error_loading), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val canOpen = plan.canDisplay
    val weekday = weekdayRes(plan.weekday)?.let { stringResource(it) } ?: stringResource(R.string.no_info_yet)
    val subtitle = if (canOpen && plan.planDate != null) {
        stringResource(R.string.plan_with_date, plan.planDate,
                       if (plan.entries.isEmpty()) stringResource(R.string.no_substitutions)
                       else pluralStringResource(R.plurals.substitutions_count, plan.entries.size, plan.entries.size))
    } else null
    HomeCard(onClick = { plan.file?.let { onOpen(PdfRequest(it, weekday, null)) } }, enabled = canOpen,
             modifier = Modifier.testTag(tag)) {
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
    val api = LocalContainer.current.api
    val prefs = LocalContainer.current.prefs
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }

    if (vm.scheduleLoading) {
        SkeletonRow()
    } else if (vm.schedules.isEmpty()) {
        HomeCard {
            Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(if (vm.scheduleError) R.string.server_connection_failed else R.string.no_schedules_available),
                 color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (vm.scheduleError) RetryButton { scope.launch { vm.loadSchedules(FetchMode.Refresh) } }
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
                enabled = !loading,
                onLongClick = onSetClass, // the iOS context menu equivalent
                onClick = {
                    // the PDF whose discovered grades contain the class (5-10, J11, J12, a future J13, …)
                    val target = scheduleFor(cls, group) ?: return@HomeCard
                    loading = true
                    scope.launch {
                        try {
                            val (file, index) = api.schedulePdf(target)
                            onOpen(PdfRequest(file, className, index[cls], target, index)) // pdf_viewer header: class only
                        } catch (e: Exception) {
                            onUnavailable(unavailable) // home_screen SnackBar parity
                        }
                        loading = false
                    }
                },
                modifier = Modifier.testTag("home.schedule")) {
                IconTile(Icons.Outlined.TableChart)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(className, fontWeight = FontWeight.SemiBold)
                    Text(half, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (loading) {
                    val label = stringResource(R.string.loading_schedule)
                    CircularProgressIndicator(Modifier.size(18.dp).semantics { contentDescription = label }, strokeWidth = 2.dp)
                }
                else Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(14.dp),
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

/**
 * The schedule PDF for a class: by discovered grades first, then by the legacy
 * gradeLevel label, then the first available PDF.
 */
fun scheduleFor(cls: String, group: List<SchoolApi.Schedule>): SchoolApi.Schedule? {
    group.firstOrNull { it.covers(cls) }?.let { return it }
    val jahrgang = (ScheduleGrades.gradeOf(cls) ?: 0) >= 11
    return group.firstOrNull { s -> if (jahrgang) s.grades.any { it >= 11 } else s.grades.any { it <= 10 } }
        ?: group.firstOrNull { if (jahrgang) it.gradeLevel == "J11/J12" else it.gradeLevel == "Klassen 5-10" }
        ?: group.firstOrNull()
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
    } else if (vm.events.isEmpty()) {
        HomeCard {
            Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(if (vm.eventsError) R.string.server_connection_failed else R.string.no_events_available),
                 color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (vm.eventsError) RetryButton { scope.launch { vm.loadEvents(FetchMode.Refresh) } }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vm.events.take(4).forEach { event ->
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
private fun eventSubtitle(event: SchoolApi.Event): String {
    val locale = ComposeLocale.current.platformLocale
    val date = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: return event.time ?: ""
    val base = date.format(DateTimeFormatter.ofPattern("EEE, d. MMMM", locale))
    return if (event.time != null) "$base · ${event.time}" else base
}
