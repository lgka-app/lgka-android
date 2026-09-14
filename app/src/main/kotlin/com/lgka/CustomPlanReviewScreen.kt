package com.lgka

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import lgka.plan.CourseCode
import lgka.plan.CustomPlan
import lgka.plan.CustomPlanBuilder
import lgka.plan.SchoolReference

private val Ok = Color(0xFF34C759)
private val Warn = Color(0xFFFF9500)
private val Remove = Color(0xFFFF3B30)
private val Estimated = Color(0xFFFFCC00)
/** Dark yellow, readable on the yellow row. */
private val EstimatedText = Color(0xFF9E7A00)

/**
 * Pushed from Home: scanning a Kurswahlprotokoll (setup, then review) or correcting the saved courses.
 * Saving presents the finished plan, which opens into its PDF in place; closing that returns to Home.
 */
@Composable
fun CustomPlanHost(edit: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val vm = LocalHomeViewModel.current
    val store = LocalContainer.current.customPlans
    var reviewing by remember { mutableStateOf(DebugCustomPlan.takeDraft()) }
    var loadFailed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<SavedCustomPlan?>(null) }
    val save: (SavedCustomPlan) -> Unit = { store.save(it); ready = it }

    ready?.let { saved ->
        CustomPlanReadyScreen(saved, onClose = onDone)
        return
    }

    if (edit) {
        LaunchedEffect(Unit) {
            if (reviewing != null) return@LaunchedEffect
            val saved = store.saved
            reviewing = try {
                saved?.let { s -> CustomPlanSource.pick(CustomPlanSource.plans(vm), s.plan.stufe, s.kurswahl)?.let { CustomPlanDraft.fromSaved(s, it) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            loadFailed = reviewing == null
        }
        val draft = reviewing
        if (draft != null) CustomPlanReviewScreen(draft, onBack = onBack, onSave = save) else LoadingScreen(loadFailed, onBack)
    } else {
        val draft = reviewing
        if (draft != null) {
            BackHandler { reviewing = null }
            CustomPlanReviewScreen(draft, onBack = { reviewing = null }, onSave = save)
        } else {
            CustomPlanSetupScreen(onBack = onBack, onDraft = { reviewing = it })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadingScreen(failed: Boolean, onBack: () -> Unit) {
    val haptics = rememberHaptics()
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.custom_review_title)) }, navigationIcon = {
            IconButton(onClick = { haptics.light(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (failed) Text(stringResource(R.string.custom_error_generic), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else CircularProgressIndicator()
        }
    }
}

/** The courses found, checked against the Stufenplan; every course can be corrected by hand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPlanReviewScreen(initial: CustomPlanDraft, onBack: () -> Unit, onSave: (SavedCustomPlan) -> Unit) {
    val resources = LocalResources.current
    val haptics = rememberHaptics()
    var draft by remember(initial) { mutableStateOf(initial) }
    val plan = remember(draft) { draft.plan }
    var addSubject by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.custom_review_title)) },
            navigationIcon = {
                IconButton(onClick = { haptics.light(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
            },
            actions = {
                // saves and presents the finished plan
                TextButton(onClick = { haptics.success(); onSave(draft.saved) }, modifier = Modifier.testTag("customPlan.save")) {
                    Text(stringResource(R.string.custom_review_next), fontWeight = FontWeight.SemiBold)
                }
            })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).readableWidth().padding(horizontal = 20.dp),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Greeting(draft.name) }
            item {
                Card(shape = CardShape) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = draft.name, onValueChange = { draft = draft.copy(name = it) }, singleLine = true,
                            placeholder = { Text(stringResource(R.string.custom_review_name_placeholder)) }, modifier = Modifier.fillMaxWidth())
                        Row {
                            Text(stringResource(R.string.custom_review_plan), Modifier.weight(1f))
                            Text("${plan.stufe} · ${CustomPlanLabels.halbjahr(resources, plan.halbjahr)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HoursRow(plan)
                    }
                }
            }
            if (plan.checks.issues.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.custom_review_issues)) }
                item {
                    Card(shape = CardShape) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            plan.checks.issues.forEach { issue ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.Warning, null, Modifier.size(20.dp), tint = Warn)
                                    Spacer(Modifier.width(10.dp))
                                    Text(CustomPlanLabels.issue(resources, issue, plan), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.custom_review_courses)) }
            items(draft.choices, key = { it.subject }) { choice ->
                CourseRow(draft, plan, choice,
                    onChange = { updated -> draft = draft.copy(choices = draft.choices.map { if (it.subject == choice.subject) updated else it }) },
                    onRemove = { haptics.medium(); draft = draft.copy(choices = draft.choices - choice) })
            }
            item {
                TextButton(onClick = { haptics.light(); addSubject = true }) {
                    Icon(Icons.Filled.AddCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.custom_review_add_subject), fontWeight = FontWeight.Medium)
                }
            }
            item {
                Text(stringResource(R.string.custom_review_footer), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }

    if (addSubject) {
        AddSubjectDialog(draft, onDismiss = { addSubject = false }, onAdd = {
            haptics.medium()
            draft = draft.copy(choices = draft.choices + it)
            addSubject = false
        })
    }
}

/** "Nice, Luka! …" with the first word of the name field, what the colours mean, and that AI read the sheet. */
@Composable
private fun Greeting(name: String) {
    val first = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    Card(shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (first.isEmpty()) stringResource(R.string.custom_review_greeting_no_name) else stringResource(R.string.custom_review_greeting, first),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.custom_review_greeting_body), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.custom_review_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HoursRow(plan: CustomPlan) {
    val total = plan.checks.totalHours
    val expected = plan.checks.expectedTotal
    val matches = expected == null || expected == total
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (matches) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (matches) Ok else Warn)
        Spacer(Modifier.width(8.dp))
        // everyone's total differs: compare with the sheet, never show it as a goal
        Text(when {
            expected != null && expected != total -> stringResource(R.string.custom_review_hours_mismatch, total, expected)
            expected != null -> stringResource(R.string.custom_review_hours_match, total)
            else -> stringResource(R.string.custom_review_hours, total)
        }, fontWeight = FontWeight.Medium)
    }
}

private enum class RowStatus { FINE, ESTIMATED, PROBLEM }

private val ProblemKinds = setOf(CustomPlan.Issue.Kind.NOT_IN_PLAN, CustomPlan.Issue.Kind.AMBIGUOUS,
    CustomPlan.Issue.Kind.UNREADABLE, CustomPlan.Issue.Kind.HOURS_MISMATCH)

/**
 * Red: the course could not be matched, is missing or clashes; yellow: its hours were not readable on the
 * sheet and were filled in from the rest of it (iOS rowStatus).
 */
private fun rowStatus(subject: String, course: CustomPlan.Course?, plan: CustomPlan): RowStatus {
    if (course == null) return RowStatus.PROBLEM
    val issues = plan.checks.issues
    if (issues.any { it.subject == subject && it.kind in ProblemKinds }) return RowStatus.PROBLEM
    val own = course.codes + course.id
    if (issues.any { it.kind == CustomPlan.Issue.Kind.CONFLICT && it.codes.any { code -> code in own } }) return RowStatus.PROBLEM
    if (issues.any { it.kind == CustomPlan.Issue.Kind.INFERRED && it.subject == subject }) return RowStatus.ESTIMATED
    return RowStatus.FINE
}

/** A course; tap to pick another parallel course, swipe left to remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseRow(draft: CustomPlanDraft, plan: CustomPlan, choice: CustomPlan.Choice,
                      onChange: (CustomPlan.Choice) -> Unit, onRemove: () -> Unit) {
    val resources = LocalResources.current
    val haptics = rememberHaptics()
    val course = plan.courses.firstOrNull { it.subjectKey == choice.subject }
    val status = rowStatus(choice.subject, course, plan)
    val cardColor = CardDefaults.cardColors().containerColor
    val tint = when (status) {
        RowStatus.PROBLEM -> Remove.copy(alpha = 0.16f).compositeOver(cardColor)
        RowStatus.ESTIMATED -> Estimated.copy(alpha = 0.22f).compositeOver(cardColor)
        RowStatus.FINE -> Color.Unspecified
    }
    val name = CustomPlanLabels.subject(resources, choice.subject, choice.code)
    val stufenplan = draft.loaded.stufenplan
    val konfession = draft.kurswahl?.konfession
    val lf = CustomPlanBuilder.candidates(choice.subject, CustomPlan.Level.LEISTUNGSFACH, konfession, stufenplan)
    val basis = CustomPlanBuilder.candidates(choice.subject, CustomPlan.Level.BASISFACH, konfession, stufenplan)
    var open by remember { mutableStateOf(false) }
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove()
    }

    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Remove).padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Outlined.Delete, null, tint = Color.White)
            }
        }) {
        Box {
            HomeCard(onClick = { open = true }, containerColor = tint) {
                Column(Modifier.weight(1f)) {
                    Text(course?.let { CustomPlanLabels.title(resources, it) } ?: name, fontWeight = FontWeight.SemiBold)
                    if (course != null) {
                        Text("${course.teacherLabel} · ${stringResource(R.string.custom_review_hours, course.hours)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (course.expectedHours == course.hours) MaterialTheme.colorScheme.onSurfaceVariant else Remove)
                    } else {
                        Text(stringResource(R.string.custom_review_pick_course), style = MaterialTheme.typography.bodySmall,
                            color = Remove, fontWeight = FontWeight.Medium)
                    }
                    if (status == RowStatus.ESTIMATED) {
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, null, Modifier.size(14.dp), tint = EstimatedText)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.custom_review_estimated), style = MaterialTheme.typography.labelSmall,
                                color = EstimatedText, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Icon(Icons.Outlined.UnfoldMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                val pick: (String, CustomPlan.Level) -> Unit = { code, level ->
                    haptics.medium()
                    open = false
                    // a hand-picked course sets its own hours, so only the total is compared with the sheet
                    onChange(choice.copy(code = code, level = level, hours = stufenplan.slotsFor(code).sumOf { it.hours }))
                }
                if (lf != basis) {
                    MenuSection(stringResource(R.string.custom_review_leistungsfach))
                    CodeItems(lf, CustomPlan.Level.LEISTUNGSFACH, draft, choice, course, pick)
                    HorizontalDivider()
                    MenuSection(stringResource(R.string.custom_review_basisfach))
                    CodeItems(basis, CustomPlan.Level.BASISFACH, draft, choice, course, pick)
                } else {
                    CodeItems(lf, choice.level, draft, choice, course, pick)
                }
            }
        }
    }
}

@Composable
private fun MenuSection(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun CodeItems(codes: List<String>, level: CustomPlan.Level, draft: CustomPlanDraft, choice: CustomPlan.Choice,
                      course: CustomPlan.Course?, pick: (String, CustomPlan.Level) -> Unit) {
    for (code in codes) {
        val teacher = draft.loaded.stufenplan.slotsFor(code).firstNotNullOfOrNull { it.teacher }
        val selected = choice.code == code || (choice.code == null && course?.codes == listOf(code))
        DropdownMenuItem(
            text = { Text(listOfNotNull(code, teacher?.let { SchoolReference.lastName(it) }).joinToString(" · ")) },
            leadingIcon = { if (selected) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
            onClick = { pick(code, level) })
    }
}

@Composable
private fun AddSubjectDialog(draft: CustomPlanDraft, onDismiss: () -> Unit, onAdd: (CustomPlan.Choice) -> Unit) {
    val resources = LocalResources.current
    var subject by remember { mutableStateOf<SchoolReference.Subject?>(null) }
    val stufenplan = draft.loaded.stufenplan
    val konfession = draft.kurswahl?.konfession
    val taken = draft.choices.map { it.subject }.toSet()
    val available = SchoolReference.subjects.filter { it.key !in taken && CustomPlanBuilder.candidates(it.key, null, konfession, stufenplan).isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(subject?.let { CustomPlanLabels.subject(resources, it.key) } ?: stringResource(R.string.custom_review_add_subject)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                val chosen = subject
                if (chosen == null) {
                    items(available, key = { it.key }) { s ->
                        DialogRow(CustomPlanLabels.subject(resources, s.key)) { subject = s }
                    }
                } else {
                    val codes = CustomPlanBuilder.candidates(chosen.key, null, konfession, stufenplan)
                    val both = codes.mapNotNull { CourseCode.of(it)?.capitalised }.toSet().size == 2
                    items(codes, key = { it }) { code ->
                        val teacher = stufenplan.slotsFor(code).firstNotNullOfOrNull { it.teacher }
                        DialogRow(listOfNotNull(code, teacher?.let { SchoolReference.lastName(it) }).joinToString(" · ")) {
                            val parsed = CourseCode.of(code)
                            val level = if (both && parsed?.capitalised == true) CustomPlan.Level.LEISTUNGSFACH else CustomPlan.Level.BASISFACH
                            onAdd(CustomPlan.Choice(chosen.key, level, stufenplan.slotsFor(code).sumOf { it.hours }, parsed?.number, code))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun DialogRow(text: String, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp))
}
