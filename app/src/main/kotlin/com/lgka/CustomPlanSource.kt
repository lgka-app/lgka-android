package com.lgka

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lgka.ScheduleGrades
import lgka.api.ScheduleItem
import lgka.plan.CustomPlan
import lgka.plan.CustomPlanBuilder
import lgka.plan.Kurswahl
import lgka.plan.SchoolReference
import lgka.plan.Stufenplan
import java.io.File

/** The J11 / J12 Stufenpläne of the current semester, read from their PDFs on the device. */
object CustomPlanSource {
    data class Loaded(val stufenplan: Stufenplan, val item: ScheduleItem)

    class NoPlanPublished : Exception("no J11/J12 timetable published")

    /** Grades a schedule PDF covers: from the link title, else the API's grade level. */
    private fun ScheduleItem.planGrades(): List<Int> = ScheduleGrades.fromTitle(title).ifEmpty {
        when (gradeLevel) {
            "J11" -> listOf(11)
            "J12" -> listOf(12)
            "J11/J12" -> listOf(11, 12)
            else -> emptyList()
        }
    }

    suspend fun plans(vm: HomeViewModel): List<Loaded> {
        val items = vm.preferredGroup.filter { it.available && it.pdf != null && it.planGrades().any { g -> g >= 11 } }
        val loaded = items.mapNotNull { item ->
            val pdf = item.pdf ?: return@mapNotNull null
            val file = vm.pdfFile(pdf.sha256, pdf.url)
            Loaded(withContext(Dispatchers.IO) { PdfText.stufenplan(file) }, item)
        }
        if (loaded.isEmpty()) throw NoPlanPublished()
        return loaded
    }

    /** The plan of the sheet's Jahrgang, else of the given Stufe, else the only one. */
    fun pick(plans: List<Loaded>, stufe: String?, kurswahl: Kurswahl?): Loaded? {
        if (kurswahl != null) {
            plans.firstOrNull { loaded -> loaded.stufenplan.schuljahr?.let { kurswahl.grade(it) } == loaded.stufenplan.grade }?.let { return it }
        }
        if (stufe != null) return plans.firstOrNull { it.stufenplan.stufe == stufe }
        return plans.singleOrNull()
    }

    /**
     * The saved plan against the current Stufenplan: rebuilt (and saved) when the school published a
     * newer one or the next Halbjahr started, unchanged otherwise or when offline.
     */
    suspend fun refreshed(saved: SavedCustomPlan, vm: HomeViewModel, store: CustomPlanStore): Pair<SavedCustomPlan, Boolean> {
        val plans = try {
            plans(vm)
        } catch (e: Exception) {
            return saved to false
        }
        val loaded = pick(plans, null, saved.kurswahl) ?: pick(plans, saved.plan.stufe, null) ?: return saved to false
        if (loaded.item.pdf?.sha256 == saved.plan.planSha256) return saved to false
        val updated = CustomPlanDraft.fromSaved(saved, loaded).saved
        store.save(updated)
        return updated to true
    }

    /** The plan rendered as its localized Untis-style PDF in the cache dir. */
    suspend fun pdfFile(context: Context, plan: CustomPlan): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "custom-plan").apply { mkdirs() }
        val file = File(dir, "Stundenplan.pdf")
        // a saved plan keeps the names from when it was built: take the current staff list
        val current = plan.copy(courses = plan.courses.map { course ->
            course.copy(teachers = course.teachers.map { it.copy(name = SchoolReference.teacherName(it.code) ?: it.name) })
        })
        file.outputStream().use { CustomPlanPdf.render(current, CustomPlanLabels.pdf(context.resources), it) }
        file
    }
}

/** Course choices being reviewed against one Stufenplan; the plan is rebuilt from them on every change. */
data class CustomPlanDraft(
    val kurswahl: Kurswahl?,
    val loaded: CustomPlanSource.Loaded,
    val name: String,
    val choices: List<CustomPlan.Choice>,
    /** Findings of the scan itself (unreadable cells, sheet of another Jahrgang, …). */
    val scanIssues: List<CustomPlan.Issue>,
    val expectedTotal: Int?,
    /** Courses found by the scan; an unrecognised row counts as handled once a subject was added. */
    val initialChoiceCount: Int = 0,
) {
    val plan: CustomPlan
        get() = CustomPlanBuilder.build(name.trim(), choices, kurswahl?.konfession, loaded.stufenplan, loaded.item.halbjahr,
            expectedTotal, scanIssues.filter { it.kind != CustomPlan.Issue.Kind.UNKNOWN_ROW || choices.size <= initialChoiceCount },
            loaded.item.pdf?.sha256)

    /** Hints about the photo itself do not belong to the saved plan. */
    val saved: SavedCustomPlan
        get() {
            val value = plan
            val issues = value.checks.issues.filterNot { it.kind in PHOTO_ONLY }
            return SavedCustomPlan(value.copy(checks = value.checks.copy(issues = issues)), kurswahl, loaded.item.title)
        }

    companion object {
        private val PHOTO_ONLY = setOf(CustomPlan.Issue.Kind.UNKNOWN_ROW, CustomPlan.Issue.Kind.SUM_UNREADABLE, CustomPlan.Issue.Kind.INFERRED)
        private val SCAN_KINDS = setOf(CustomPlan.Issue.Kind.UNREADABLE, CustomPlan.Issue.Kind.GRADE_MISMATCH) + PHOTO_ONLY

        fun fromScan(kurswahl: Kurswahl, loaded: CustomPlanSource.Loaded, name: String? = null): CustomPlanDraft {
            val built = CustomPlanBuilder.build(kurswahl, loaded.stufenplan, loaded.item.halbjahr)
            return CustomPlanDraft(kurswahl, loaded, name ?: built.name, built.choices,
                built.checks.issues.filter { it.kind in SCAN_KINDS }, built.checks.expectedTotal, built.choices.size)
        }

        /** A saved plan against a (possibly newer) Stufenplan: same choices, or the sheet's choices of the new Halbjahr. */
        fun fromSaved(saved: SavedCustomPlan, loaded: CustomPlanSource.Loaded): CustomPlanDraft {
            val semesterChanged = loaded.item.halbjahr != saved.plan.halbjahr || loaded.stufenplan.stufe != saved.plan.stufe
            if (semesterChanged && saved.kurswahl != null) return fromScan(saved.kurswahl, loaded, saved.plan.name)
            return CustomPlanDraft(saved.kurswahl, loaded, saved.plan.name, saved.plan.choices, emptyList(), saved.plan.checks.expectedTotal)
        }
    }
}
