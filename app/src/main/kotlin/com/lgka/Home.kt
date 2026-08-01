package com.lgka

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/// Home hub — mirrors home_screen.dart: weather card, substitution cards,
/// schedule class card, events; toolbar: news / sick note / settings.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showClassDialog by remember { mutableStateOf(false) }
    var pdf by remember { mutableStateOf<PdfRequest?>(null) }
    val pull = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.s("appTitle"), fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = { nav.navigate("news") }) {
                        Icon(Icons.Outlined.Newspaper, L.s("news"))
                    }
                    IconButton(onClick = {
                        if (prefs.krankmeldungInfoShown) nav.navigate("krankmeldungForm")
                        else nav.navigate("krankmeldungInfo")
                    }) {
                        Icon(Icons.Outlined.MedicalServices, L.s("krankmeldung"))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, L.s("settings"))
                    }
                })
        }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            state = pull,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    HomeModel.loadAll(FetchMode.Refresh)
                    refreshing = false
                }
            },
            modifier = Modifier.padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { WeatherRow { nav.navigate("weather") } }
                item { SectionHeader(L.s("substitutionPlan")) }
                item { SubstitutionCards { req -> pdf = req } }
                item { SectionHeader(L.s("schedule")) }
                item { ScheduleCard(onSetClass = { showClassDialog = true },
                                    onOpen = { req -> pdf = req }) }
                item { SectionHeader(L.s("termine")) }
                item { EventsColumn() }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showSettings) SettingsSheet(nav) { showSettings = false }
    if (showClassDialog) ClassDialog { showClassDialog = false }
    pdf?.let { request ->
        PdfViewerDialog(request) { pdf = null }
    }
}

data class PdfRequest(val file: java.io.File, val title: String, val targetPage: Int?)

@Composable
fun SectionHeader(title: String) {
    Text(title, fontWeight = FontWeight.Bold,
         style = MaterialTheme.typography.titleMedium,
         modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun HomeCard(content: @Composable RowScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
fun IconTile(icon: ImageVector, alpha: Float = 0.12f) {
    Box(Modifier.size(44.dp).background(
            MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

// ── Weather row ─────────────────────────────────────────────────────────────

@Composable
fun WeatherRow(onOpen: () -> Unit) {
    val w = HomeModel.weather
    if (w != null) {
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)) {
            SkyBox(code = w.code, isDay = w.isDay, particles = false,
                   modifier = Modifier.matchParentSize())
            Row(Modifier.matchParentSize().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Karlsruhe", color = Color.White, fontSize = 13.sp,
                         fontWeight = FontWeight.SemiBold)
                    Text("${w.temp.toInt()}°", color = Color.White, fontSize = 40.sp,
                         fontWeight = FontWeight.Medium)
                    Text(L.wmoDescription(w.code), color = Color.White.copy(alpha = 0.9f),
                         fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Icon(WmoIcons.icon(w.code, w.isDay), null,
                         tint = Color.White, modifier = Modifier.size(30.dp))
                    w.daily.firstOrNull()?.let { today ->
                        Spacer(Modifier.height(4.dp))
                        Text("H: ${today.tempMax.toInt()}°  T: ${today.tempMin.toInt()}°",
                             color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                }
            }
        }
    } else if (HomeModel.weatherError) {
        HomeCard {
            Icon(Icons.Outlined.CloudOff, null,
                 tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Spacer(Modifier.width(14.dp))
            Text(L.s("weatherDataNotAvailable"),
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    } else {
        SkeletonRow()
    }
}

@Composable
fun SkeletonRow() {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Box(Modifier.size(140.dp, 14.dp).background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(90.dp, 11.dp).background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    RoundedCornerShape(4.dp)))
            }
        }
    }
}

// ── Substitution ────────────────────────────────────────────────────────────

@Composable
fun SubstitutionCards(onOpen: (PdfRequest) -> Unit) {
    if (HomeModel.subLoading) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonRow(); SkeletonRow()
        }
    } else if (HomeModel.subError) {
        val scope = rememberCoroutineScope()
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp),
                   horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CloudOff, null, modifier = Modifier.size(40.dp),
                     tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                Text(L.s("serverConnectionFailed"), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(L.s("serverConnectionHint"),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = {
                    scope.launch { HomeModel.loadSubstitution(FetchMode.Refresh) }
                }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(L.s("tryAgain"))
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SubCard(HomeModel.today, onOpen)
            SubCard(HomeModel.tomorrow, onOpen)
        }
    }
}

@Composable
private fun SubCard(plan: SchoolApi.SubPlan?, onOpen: (PdfRequest) -> Unit) {
    val canOpen = plan?.canDisplay == true
    val weekday = displayWeekday(plan?.weekday)
    Card(shape = RoundedCornerShape(16.dp),
         modifier = Modifier.clickable(enabled = canOpen) {
             plan?.file?.let { onOpen(PdfRequest(it, weekday, null)) }
         }) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.CalendarToday, if (canOpen) 0.12f else 0.06f)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (canOpen) weekday else L.s("noInfoYet"),
                     fontWeight = FontWeight.SemiBold,
                     color = MaterialTheme.colorScheme.onSurface.copy(
                         alpha = if (canOpen) 1f else 0.35f))
                if (canOpen && plan != null) {
                    val n = plan.entries.size
                    val label = if (L.isGerman) {
                        if (n == 0) "Keine Vertretungen"
                        else if (n == 1) "1 Vertretung" else "$n Vertretungen"
                    } else {
                        if (n == 0) "No substitutions"
                        else if (n == 1) "1 substitution" else "$n substitutions"
                    }
                    Text("${plan.planDate} · $label",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            if (canOpen) Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                              Modifier.size(14.dp),
                              tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

private fun displayWeekday(weekday: String?): String {
    if (weekday.isNullOrEmpty() || weekday == "weekend") return L.s("noInfoYet")
    if (!L.isGerman) {
        return mapOf("Montag" to "Monday", "Dienstag" to "Tuesday", "Mittwoch" to "Wednesday",
                     "Donnerstag" to "Thursday", "Freitag" to "Friday",
                     "Samstag" to "Saturday", "Sonntag" to "Sunday")[weekday] ?: weekday
    }
    return weekday
}

// ── Schedule ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduleCard(onSetClass: () -> Unit, onOpen: (PdfRequest) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }

    if (HomeModel.scheduleLoading) {
        SkeletonRow()
    } else if (HomeModel.schedules.isEmpty()) {
        HomeCard {
            Icon(Icons.Outlined.Schedule, null,
                 tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Spacer(Modifier.width(12.dp))
            Text(if (HomeModel.scheduleError) L.s("serverConnectionFailed")
                 else L.s("noSchedulesAvailable"),
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    } else {
        val cls = prefs.selectedScheduleClass
        if (cls.isEmpty()) {
            Card(shape = RoundedCornerShape(16.dp),
                 modifier = Modifier.clickable(onClick = onSetClass)) {
                Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.School)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L.s("scheduleNoClassTitle"), fontWeight = FontWeight.SemiBold)
                        Text(L.s("scheduleNoClassSub"),
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(14.dp),
                         tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
        } else {
            val group = HomeModel.preferredGroup
            val half = if (group.firstOrNull()?.halbjahr == "1. Halbjahr")
                L.s("firstSemester") else L.s("secondSemester")
            Card(shape = RoundedCornerShape(16.dp),
                 modifier = Modifier.combinedClickable(enabled = !loading,
                     onLongClick = onSetClass) {
                     val isJ = cls.startsWith("j")
                     val target = (if (isJ) group.firstOrNull { it.gradeLevel == "J11/J12" }
                                   else group.firstOrNull { it.gradeLevel == "Klassen 5-10" })
                         ?: group.firstOrNull() ?: return@clickable
                     loading = true
                     scope.launch {
                         try {
                             val (file, index) = SchoolApi.schedulePdf(target)
                             onOpen(PdfRequest(file, "${formatClass(cls)} – $half", index[cls]))
                         } catch (_: Exception) {}
                         loading = false
                     }
                 }) {
                Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.TableChart)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(formatClass(cls), fontWeight = FontWeight.SemiBold)
                        Text(half, style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(14.dp),
                              tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
        }
    }
}

fun formatClass(cls: String): String = when (cls) {
    "j11" -> L.s("jahrgang11")
    "j12" -> L.s("jahrgang12")
    else -> (if (L.isGerman) "Klasse " else "Class ") +
        cls.replaceFirstChar { it.uppercase() }
}

@Composable
fun ClassDialog(onDismiss: () -> Unit) {
    var input by remember { mutableStateOf(prefs.selectedScheduleClass) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L.s("setClassTitle")) },
        text = {
            OutlinedTextField(value = input, onValueChange = { input = it.take(3) },
                              placeholder = { Text(L.s("searchHint")) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                val cls = input.trim().lowercase(Locale.ROOT)
                if (cls.isNotEmpty()) prefs.selectedScheduleClass = cls
                onDismiss()
            }) { Text(L.s("setClassButton")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L.s("cancel")) } })
}

// ── Events ──────────────────────────────────────────────────────────────────

@Composable
fun EventsColumn() {
    if (HomeModel.eventsLoading) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { SkeletonRow() }
        }
    } else if (HomeModel.events.isEmpty()) {
        HomeCard {
            Icon(Icons.Outlined.Event, null,
                 tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Spacer(Modifier.width(12.dp))
            Text(if (HomeModel.eventsError) L.s("serverConnectionFailed")
                 else L.s("noEventsAvailable"),
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeModel.events.take(4).forEach { event ->
                HomeCard {
                    DateTile(event.date)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(event.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(eventSubtitle(event), style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DateTile(iso: String) {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull()
    Box(Modifier.size(44.dp).background(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${date?.dayOfMonth ?: "?"}", fontWeight = FontWeight.Bold,
                 color = MaterialTheme.colorScheme.primary)
            Text(date?.format(DateTimeFormatter.ofPattern("MMM",
                     if (L.isGerman) Locale.GERMAN else Locale.ENGLISH)) ?: "",
                 fontSize = 10.sp,
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

private fun eventSubtitle(event: SchoolApi.Event): String {
    val date = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: return event.time ?: ""
    val fmt = DateTimeFormatter.ofPattern("EEE, d. MMMM",
        if (L.isGerman) Locale.GERMAN else Locale.ENGLISH)
    val base = date.format(fmt)
    return if (event.time != null) "$base · ${event.time}" else base
}
