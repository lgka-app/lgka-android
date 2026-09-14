package com.lgka

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import lgka.plan.CourseCode
import lgka.plan.CustomPlan
import lgka.plan.SchoolReference
import java.io.OutputStream

/**
 * Draws a [CustomPlan] as an A4 landscape PDF in the Untis look: header with school, year and name,
 * Mo–Fr × 11 periods with times, heavy lines between the double-period blocks, and per lesson the
 * course in bold, the teacher and the room in italics. Same layout as the iOS app (points, 842 × 595).
 */
object CustomPlanPdf {
    /** Every word the page prints that is not the school's own data, in the app language. */
    class Labels(
        val title: String,
        val schoolYear: (String) -> String,
        val stand: (String) -> String,
        val room: (String) -> String,
        /** Montag … Freitag. */
        val days: List<String>,
        val halbjahr: (String) -> String,
        /** The footer with the breaks ("9:20–9:35 · …"). */
        val footer: (String) -> String,
        val courseTitle: (CustomPlan.Course) -> String,
        /** Remark for a course that stands for several parallel courses in the same slots. */
        val sharedSlotsNote: (CustomPlan.Course) -> String,
    )

    fun render(plan: CustomPlan, labels: Labels, out: OutputStream) {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
        Page(page.canvas, 842f, 595f, labels).draw(plan)
        document.finishPage(page)
        document.writeTo(out)
        document.close()
    }

    /** Drawn in PDF coordinates (origin bottom-left, like the iOS renderer), flipped onto the canvas. */
    private class Page(val canvas: Canvas, val width: Float, val height: Float, val labels: Labels) {
        enum class Align { LEFT, CENTRE, RIGHT }

        private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        private val italic = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK }
        private val fill = Paint().apply { style = Paint.Style.FILL; color = Color.WHITE }

        fun draw(plan: CustomPlan) {
            val w = width
            val h = height
            canvas.drawRect(0f, 0f, w, h, fill)

            // header
            text("Lessing-Gymnasium Karlsruhe", bold, 11f, 30f, h - 32)
            plan.schuljahr?.let { text(labels.schoolYear(it), regular, 10f, 240f, h - 32) }
            text("D-76135, Sophienstr. 147", regular, 10f, 30f, h - 45)
            text(plan.name.ifEmpty { labels.title }, bold, 11f, w - 30, h - 32, Align.RIGHT)
            val meta = listOfNotNull(plan.stufe, labels.halbjahr(plan.halbjahr), plan.stand?.let { labels.stand(it) })
            text(meta.joinToString(" · "), regular, 9f, w - 30, h - 45, Align.RIGHT)
            text(plan.stufe, bold, 18f, 30f, h - 72)
            text(labels.title, regular, 14f, 70f, h - 72)

            // grid
            val periods = plan.periods.size
            val x0 = 30f
            val y0 = h - 88
            val labelWidth = 62f
            val columnWidth = (w - 60 - labelWidth) / 5
            val headHeight = 26f
            val rowHeight = (y0 - headHeight - 30) / periods
            val gx = x0 + labelWidth
            val gy = y0 - headHeight
            val bottom = gy - rowHeight * periods

            stroke.strokeWidth = 1.4f
            rect(x0, bottom, w - 60, headHeight + rowHeight * periods)
            line(gx, y0, gx, bottom)
            line(x0, gy, x0 + w - 60, gy)
            labels.days.take(5).forEachIndexed { i, day ->
                val xx = gx + i * columnWidth
                if (i > 0) line(xx, y0, xx, bottom)
                text(day, bold, 13f, xx + columnWidth / 2, gy + 8, Align.CENTRE)
            }

            plan.periods.forEachIndexed { index, period ->
                val p = index + 1
                val yy = gy - index * rowHeight
                text("${period.number}", bold, 11f, x0 + labelWidth / 2, yy - rowHeight / 2 + 1, Align.CENTRE)
                text("${period.start}–${period.end}", regular, 6.8f, x0 + labelWidth / 2, yy - rowHeight / 2 - 9, Align.CENTRE)
                if (p > 1) {
                    val heavy = p in SchoolReference.blockStarts
                    stroke.strokeWidth = if (heavy) 1.0f else 0.4f
                    line(x0 + if (heavy) 0f else labelWidth, yy, x0 + w - 60, yy)
                }
            }

            // lessons: white-out the cell (hides the light line inside a double period), then the text
            for (lesson in plan.lessons) {
                val course = plan.courses.firstOrNull { it.id == lesson.course } ?: continue
                val xx = gx + lesson.day * columnWidth
                val top = gy - (lesson.start - 1) * rowHeight
                val bot = gy - lesson.end * rowHeight
                canvas.drawRect(xx + 1, height - (top - 1), xx + columnWidth - 1, height - (bot + 1), fill)
                val cy = (top + bot) / 2
                val fit = columnWidth - 8
                text(labels.courseTitle(course), bold, 9.2f, xx + columnWidth / 2, cy + 6, Align.CENTRE, fit)
                text(course.teacherLabel, regular, 8.5f, xx + columnWidth / 2, cy - 4, Align.CENTRE, fit)
                if (lesson.rooms.isNotEmpty()) text(labels.room(lesson.roomLabel), italic, 8.5f, xx + columnWidth / 2, cy - 14, Align.CENTRE, fit)
            }

            val pauses = plan.breaks.joinToString(" · ") { "${it.start}–${it.end}" }
            var foot = labels.footer(pauses)
            val notes = plan.courses.filter { it.codes.size > 1 }.map(labels.sharedSlotsNote)
            if (notes.isNotEmpty()) foot += " · " + notes.joinToString(" · ")
            text(foot, regular, 7f, 30f, 18f, maxWidth = w - 60)
        }

        private fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, height - y1, x2, height - y2, stroke)

        private fun rect(x: Float, y: Float, w: Float, h: Float) = canvas.drawRect(x, height - (y + h), x + w, height - y, stroke)

        private fun text(string: String, typeface: Typeface, size: Float, x: Float, y: Float, align: Align = Align.LEFT, maxWidth: Float? = null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = size; color = Color.BLACK }
            var lineWidth = paint.measureText(string)
            if (maxWidth != null && lineWidth > maxWidth) {
                // long subject names shrink instead of running into the next day
                paint.textSize = size * maxWidth / lineWidth
                lineWidth = paint.measureText(string)
            }
            val originX = when (align) {
                Align.LEFT -> x
                Align.CENTRE -> x - lineWidth / 2
                Align.RIGHT -> x - lineWidth
            }
            canvas.drawText(string, originX, height - y, paint)
        }
    }
}

/** The custom plan's words in the app language; subject names and the school's own data stay as printed. */
object CustomPlanLabels {
    /** A subject in the app language ("Mathematik" / "Maths"); religion by the course's stem (kR / eR). */
    fun subject(resources: Resources, key: String, code: String? = null): String {
        val stem = code?.let { CourseCode.of(it)?.stem }
        val id = when {
            key == "Rel" && stem == "kr" -> R.string.subject_rel_kr
            key == "Rel" && stem == "er" -> R.string.subject_rel_er
            else -> SUBJECTS[key]
        }
        return id?.let { resources.getString(it) } ?: SchoolReference.subject(key)?.name ?: key
    }

    fun subject(resources: Resources, course: CustomPlan.Course): String = subject(resources, course.subjectKey, course.codes.firstOrNull())

    private val SUBJECTS = mapOf(
        "Ast" to R.string.subject_ast, "BK" to R.string.subject_bk, "Bio" to R.string.subject_bio, "Ch" to R.string.subject_ch,
        "D" to R.string.subject_d, "E" to R.string.subject_e, "Eth" to R.string.subject_eth, "F" to R.string.subject_f,
        "G" to R.string.subject_g, "Geo" to R.string.subject_geo, "Gk" to R.string.subject_gk, "I" to R.string.subject_i,
        "Inf" to R.string.subject_inf, "L" to R.string.subject_l, "LTh" to R.string.subject_lth, "M" to R.string.subject_m,
        "Mu" to R.string.subject_mu, "NwT" to R.string.subject_nw_t, "Ph" to R.string.subject_ph, "Phil" to R.string.subject_phil,
        "Psy" to R.string.subject_psy, "Rel" to R.string.subject_rel, "Sp" to R.string.subject_sp, "Sport" to R.string.subject_sport,
        "WBS" to R.string.subject_wbs,
    )

    /** "1. Halbjahr" (school data) → "1. Halbjahr" / "1st semester". */
    fun halbjahr(resources: Resources, value: String): String = when {
        value.startsWith("1") -> resources.getString(R.string.first_semester)
        value.startsWith("2") -> resources.getString(R.string.second_semester)
        else -> value
    }

    /** "Mathematik (LF, M3)", "Gemeinschaftskunde (LF)", "Sport (s1 / s2 / s3)" with the level in the app language. */
    fun title(resources: Resources, course: CustomPlan.Course): String {
        val subject = subject(resources, course)
        if (course.codes.size > 1) return "$subject (${course.codes.joinToString(" / ")})"
        val code = course.codes.firstOrNull() ?: course.id
        return when (course.level) {
            CustomPlan.Level.LEISTUNGSFACH -> {
                val level = resources.getString(R.string.custom_level_short_lf)
                if (CourseCode.of(code)?.number != null) "$subject ($level, $code)" else "$subject ($level)"
            }
            CustomPlan.Level.BASISFACH -> "$subject ($code)"
        }
    }

    fun weekday(resources: Resources, german: String): String = resources.getString(when (german) {
        "Montag" -> R.string.weekday_montag
        "Dienstag" -> R.string.weekday_dienstag
        "Mittwoch" -> R.string.weekday_mittwoch
        "Donnerstag" -> R.string.weekday_donnerstag
        else -> R.string.weekday_freitag
    })

    fun pdf(resources: Resources) = CustomPlanPdf.Labels(
        title = resources.getString(R.string.custom_pdf_title),
        schoolYear = { resources.getString(R.string.custom_pdf_school_year, it) },
        stand = { resources.getString(R.string.custom_pdf_stand, it) },
        room = { resources.getString(R.string.custom_pdf_room, it) },
        days = CustomPlan.dayNames.map { weekday(resources, it) },
        halbjahr = { halbjahr(resources, it) },
        footer = { resources.getString(R.string.custom_pdf_footer, it) },
        courseTitle = { title(resources, it) },
        sharedSlotsNote = { resources.getString(R.string.custom_pdf_shared_slots, subject(resources, it), it.codes.joinToString(" / ")) },
    )

    /** An issue of the review in the app language (the builder's own messages are German, for the JSON). */
    fun issue(resources: Resources, issue: CustomPlan.Issue, plan: CustomPlan): String {
        val name = issue.subject?.let { subject(resources, it, issue.codes.firstOrNull()) } ?: ""
        return when (issue.kind) {
            CustomPlan.Issue.Kind.UNREADABLE -> resources.getString(R.string.custom_issue_unreadable, name)
            CustomPlan.Issue.Kind.NOT_IN_PLAN -> resources.getString(R.string.custom_issue_not_in_plan, name)
            CustomPlan.Issue.Kind.AMBIGUOUS -> resources.getString(R.string.custom_issue_ambiguous, name, issue.codes.joinToString(", "))
            CustomPlan.Issue.Kind.HOURS_MISMATCH -> resources.getString(R.string.custom_issue_hours, name)
            CustomPlan.Issue.Kind.TOTAL_MISMATCH -> resources.getString(R.string.custom_issue_total, plan.checks.totalHours, plan.checks.expectedTotal ?: 0)
            CustomPlan.Issue.Kind.CONFLICT -> resources.getString(R.string.custom_issue_conflict, issue.codes.joinToString(" & "))
            CustomPlan.Issue.Kind.GRADE_MISMATCH -> resources.getString(R.string.custom_issue_grade)
            CustomPlan.Issue.Kind.UNKNOWN_ROW -> resources.getString(R.string.custom_issue_unknown_row, issue.codes.firstOrNull() ?: "?")
            CustomPlan.Issue.Kind.SUM_UNREADABLE -> resources.getString(R.string.custom_issue_sum_unreadable)
            CustomPlan.Issue.Kind.INFERRED -> resources.getString(R.string.custom_issue_inferred, name)
        }
    }
}
