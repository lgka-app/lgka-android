package com.lgka

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.testTag
import lgka.api.ScheduleItem
import java.io.File

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

    // after scanning or editing the custom plan: back on Home, straight into its PDF
    val customPlans = LocalContainer.current.customPlans
    val customTitle = stringResource(R.string.custom_home_title)
    androidx.compose.runtime.LaunchedEffect(customPlans.openRequest) {
        val request = customPlans.openRequest ?: return@LaunchedEffect
        customPlans.openRequest = null
        customPlans.shownPdf = PdfRequest(CustomPlanSource.pdfFile(context, request.plan), customTitle, null, shareName = customTitle)
    }

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
                        onUnavailable = { msg -> haptics.error(); toast.show(msg) },
                        onCustomPlan = { edit -> onNavigate(CustomPlanRoute(edit)) })
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
    (pdf ?: customPlans.shownPdf)?.let { request ->
        PdfViewerDialog(request) {
            pdf = null
            customPlans.shownPdf = null
        }
    }
}

/** [targetPage] is a real 1-based PDF page (the API's class index). */
data class PdfRequest(val file: File, val title: String, val targetPage: Int?, val schedule: ScheduleItem? = null,
                      val classIndex: Map<String, Int> = emptyMap(),
                      /** Name for the shared file ("LGKA_<name>.pdf") when neither a schedule nor a substitution plan. */
                      val shareName: String? = null)
