package com.lgka

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.filled.Error
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
/** Title of an estimated course: a deeper yellow on light backgrounds, the system yellow on dark ones. */
private val EstimatedLight = Color(0.8f, 0.6f, 0f)
private val EstimatedDark = Color(0xFFFFD60A)
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

/**
 * The courses found, checked against the Stufenplan; every course can be corrected by hand. The plan is built
 * once on opening; corrections only change the list of choices, and "Weiter" builds the real plan from them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPlanReviewScreen(initial: CustomPlanDraft, onBack: () -> Unit, onSave: (SavedCustomPlan) -> Unit) {
    val resources = LocalResources.current
    val haptics = rememberHaptics()
    var draft by remember(initial) { mutableStateOf(initial) }
    val checked = remember(initial) { initial.plan }
    val review = remember(initial, draft.choices) { ReviewState.of(initial, checked, draft.choices) }
    var addSubject by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.custom_review_title)) },
            navigationIcon = {
                IconButton(onClick = { haptics.light(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
            },
            actions = {
                // builds the plan from the choices, saves it and presents it
                TextButton(onClick = { haptics.success(); onSave(draft.saved) }, modifier = Modifier.testTag("customPlan.save")) {
                    Text(stringResource(R.string.custom_review_next), fontWeight = FontWeight.SemiBold)
                }
            })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).readableWidth().padding(horizontal = 20.dp),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Greeting(draft.name, review.marked) }
            item { SectionHeader(stringResource(R.string.custom_review_overview)) }
            item {
                Card(shape = CardShape) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = draft.name, onValueChange = { draft = draft.copy(name = it) }, singleLine = true,
                            placeholder = { Text(stringResource(R.string.custom_review_name_placeholder)) }, modifier = Modifier.fillMaxWidth())
                        Row {
                            Text(stringResource(R.string.custom_review_plan), Modifier.weight(1f))
                            Text("${checked.stufe} · ${CustomPlanLabels.halbjahr(resources, checked.halbjahr)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HoursRow(review.totalHours, checked.checks.expectedTotal)
                    }
                }
            }
            if (review.issues.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.custom_review_issues)) }
                item {
                    Card(shape = CardShape) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            review.issues.forEach { issue ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.Warning, null, Modifier.size(20.dp), tint = Warn)
                                    Spacer(Modifier.width(10.dp))
                                    Text(CustomPlanLabels.issue(resources, issue, checked), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.custom_review_courses)) }
            items(draft.choices, key = { it.subject }) { choice ->
                val row = review.rows.getValue(choice.subject)
                CourseRow(draft, row, choice,
                    onChange = { updated -> draft = draft.copy(choices = draft.choices.map { if (it.subject == choice.subject) updated else it }) },
                    onRemove = { haptics.medium(); draft = draft.copy(choices = draft.choices.filter { it.subject != choice.subject }) })
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

private enum class RowStatus { FINE, ESTIMATED, PROBLEM }

/** A course row as shown: its course (null when none matched) and the colour of its title. */
private data class ReviewRow(val course: CustomPlan.Course?, val status: RowStatus)

/**
 * What the review shows for the current choices, without building the plan: a choice still as on opening
 * keeps its course and status from that check; a changed or added one is read from the Stufenplan slots
 * of its code and counts as fine.
 */
private data class ReviewState(val rows: Map<String, ReviewRow>, val issues: List<CustomPlan.Issue>, val totalHours: Int) {
    /** Some course is yellow or red: only then is the colour explained. */
    val marked: Boolean get() = rows.values.any { it.status != RowStatus.FINE }

    companion object {
        fun of(initial: CustomPlanDraft, checked: CustomPlan, choices: List<CustomPlan.Choice>): ReviewState {
            val before = initial.choices.associateBy { it.subject }
            val now = choices.associateBy { it.subject }
            // subjects changed, added or removed since opening: their findings no longer apply
            val edited = (before.keys + now.keys).filter { before[it] != now[it] }.toSet()
            val editedCodes = checked.courses.filter { it.subjectKey in edited }.flatMap { it.codes + it.id }.toSet()
            val issues = checked.checks.issues.filter { issue ->
                when {
                    issue.kind == CustomPlan.Issue.Kind.TOTAL_MISMATCH -> edited.isEmpty()
                    issue.kind == CustomPlan.Issue.Kind.UNKNOWN_ROW -> choices.size <= initial.initialChoiceCount
                    issue.kind == CustomPlan.Issue.Kind.CONFLICT -> issue.codes.none { it in editedCodes }
                    else -> issue.subject == null || issue.subject !in edited
                }
            }
            val stufenplan = initial.loaded.stufenplan
            val rows = choices.associate { choice ->
                val code = choice.code
                choice.subject to if (choice.subject !in edited || code == null) {
                    val course = checked.courses.firstOrNull { it.subjectKey == choice.subject }
                    ReviewRow(course, if (choice.subject in edited) RowStatus.FINE else rowStatus(choice.subject, course, issues))
                } else {
                    val subject = SchoolReference.subjectName(choice.subject, code)
                    val teachers = stufenplan.slotsFor(code).mapNotNull { it.teacher }.distinct()
                        .map { CustomPlan.Teacher(it, SchoolReference.teacherName(it)) }
                    ReviewRow(CustomPlan.Course(code, choice.subject, subject, choice.level, listOf(code),
                        CustomPlanBuilder.title(subject, choice.level, listOf(code)), teachers, choice.hours, choice.hours), RowStatus.FINE)
                }
            }
            return ReviewState(rows, issues, rows.values.sumOf { it.course?.hours ?: 0 })
        }
    }
}

/** "Hey Luka, …" with the first word of the name field, and what to check (with the colours only when some are marked). */
@Composable
private fun Greeting(name: String, marked: Boolean) {
    val first = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    // plain text on the page like the tutorial, not a card
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (first.isEmpty()) stringResource(R.string.custom_review_greeting_no_name) else stringResource(R.string.custom_review_greeting, first),
            Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(if (marked) R.string.custom_review_greeting_body_marked else R.string.custom_review_greeting_body_clean),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HoursRow(total: Int, expected: Int?) {
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

private val ProblemKinds = setOf(CustomPlan.Issue.Kind.NOT_IN_PLAN, CustomPlan.Issue.Kind.AMBIGUOUS,
    CustomPlan.Issue.Kind.UNREADABLE, CustomPlan.Issue.Kind.HOURS_MISMATCH)

/**
 * Red: the course could not be matched, is missing or clashes; yellow: its hours were not readable on the
 * sheet and were filled in from the rest of it (iOS rowStatus).
 */
private fun rowStatus(subject: String, course: CustomPlan.Course?, issues: List<CustomPlan.Issue>): RowStatus {
    if (course == null) return RowStatus.PROBLEM
    if (issues.any { it.subject == subject && it.kind in ProblemKinds }) return RowStatus.PROBLEM
    val own = course.codes + course.id
    if (issues.any { it.kind == CustomPlan.Issue.Kind.CONFLICT && it.codes.any { code -> code in own } }) return RowStatus.PROBLEM
    if (issues.any { it.kind == CustomPlan.Issue.Kind.INFERRED && it.subject == subject }) return RowStatus.ESTIMATED
    return RowStatus.FINE
}

/** A course; tap to pick another parallel course or remove it, or swipe left to remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseRow(draft: CustomPlanDraft, row: ReviewRow, choice: CustomPlan.Choice,
                      onChange: (CustomPlan.Choice) -> Unit, onRemove: () -> Unit) {
    val resources = LocalResources.current
    val haptics = rememberHaptics()
    val course = row.course
    // the course name carries the status; the row itself stays plain
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val titleColor = when (row.status) {
        RowStatus.PROBLEM -> Remove
        RowStatus.ESTIMATED -> if (dark) EstimatedDark else EstimatedLight
        RowStatus.FINE -> Color.Unspecified
    }
    val name = CustomPlanLabels.subject(resources, choice.subject, choice.code)
    val stufenplan = draft.loaded.stufenplan
    val konfession = draft.kurswahl?.konfession
    val lf = remember(choice.subject) { CustomPlanBuilder.candidates(choice.subject, CustomPlan.Level.LEISTUNGSFACH, konfession, stufenplan) }
    val basis = remember(choice.subject) { CustomPlanBuilder.candidates(choice.subject, CustomPlan.Level.BASISFACH, konfession, stufenplan) }
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
            HomeCard(onClick = { open = true }, lightTap = true) {
                Column(Modifier.weight(1f)) {
                    Text(course?.let { CustomPlanLabels.title(resources, it) } ?: name, fontWeight = FontWeight.SemiBold, color = titleColor)
                    if (course != null) {
                        Text("${course.teacherLabel} · ${stringResource(R.string.custom_review_hours, course.hours)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (course.expectedHours == course.hours) MaterialTheme.colorScheme.onSurfaceVariant else Remove)
                    } else {
                        Text(stringResource(R.string.custom_review_pick_course), style = MaterialTheme.typography.bodySmall,
                            color = Remove, fontWeight = FontWeight.Medium)
                    }
                    if (row.status == RowStatus.ESTIMATED) {
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, null, Modifier.size(14.dp), tint = EstimatedText)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.custom_review_estimated), style = MaterialTheme.typography.labelSmall,
                                color = EstimatedText, fontWeight = FontWeight.SemiBold)
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
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.custom_review_remove, name), color = Remove) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Remove) },
                    onClick = { open = false; onRemove() },
                    modifier = Modifier.testTag("customPlan.remove"))
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
