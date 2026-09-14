package lgka.plan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * A personal J11 / J12 timetable: everything needed to draw it (as a PDF), self-contained, plus the
 * course choices it was built from so a newer Stufenplan can rebuild it without scanning the
 * Kurswahlprotokoll again.
 */
@Serializable
data class CustomPlan(
    val version: Int = CURRENT_VERSION,
    val name: String,
    /** "J11". */
    val stufe: String,
    /** "1. Halbjahr". */
    val halbjahr: String,
    val schuljahr: String? = null,
    /** Untis export time of the Stufenplan the lessons come from. */
    val stand: String? = null,
    val periods: List<SchoolReference.Period>,
    val breaks: List<SchoolReference.Break>,
    val courses: List<Course>,
    val lessons: List<Lesson>,
    val choices: List<Choice>,
    val checks: Checks,
    /** Short remarks for the plan's footer ("Sport: s1 / s2 / s3 …"). */
    val notes: List<String>,
    /** ISO 8601. */
    val generatedAt: String,
    /** sha256 of the Stufenplan PDF, to notice a newer plan. */
    val planSha256: String? = null,
) {
    @Serializable
    enum class Level { @SerialName("LF") LEISTUNGSFACH, @SerialName("Basis") BASISFACH }

    @Serializable
    data class Course(
        /** The course code, or the codes joined with "/" when several parallel courses share every slot. */
        val id: String,
        val subjectKey: String,
        /** "Mathematik", "kath. Religion". */
        val subject: String,
        val level: Level,
        val codes: List<String>,
        /** "Mathematik (LF, M3)", "Gemeinschaftskunde (LF)", "Sport (s1 / s2 / s3)". */
        val title: String,
        val teachers: List<Teacher>,
        /** Weekly hours in the Stufenplan. */
        val hours: Int,
        /** Weekly hours according to the Kurswahlprotokoll. */
        val expectedHours: Int? = null,
    ) {
        /** "Johannes Burger"; last names joined for several teachers ("Burger / Domin / Bachmor"). */
        val teacherLabel: String
            get() = if (teachers.size == 1) teachers[0].name ?: teachers[0].code
            else teachers.joinToString(" / ") { t -> t.name?.split(" ")?.last() ?: t.code }
    }

    @Serializable
    data class Teacher(val code: String, val name: String? = null)

    @Serializable
    data class Lesson(
        /** 0 = Montag … 4 = Freitag. */
        val day: Int,
        val start: Int,
        val end: Int,
        val course: String,
        val rooms: List<String>,
        val teachers: List<String>,
    ) {
        val roomLabel: String get() = rooms.joinToString(" / ")
        val hours: Int get() = end - start + 1
    }

    /** One subject as chosen in the Kurswahlprotokoll (or corrected by the user). */
    @Serializable
    data class Choice(
        val subject: String,
        val level: Level,
        val hours: Int,
        val parallel: Int? = null,
        /** A course code picked by hand; wins over the automatic match. */
        val code: String? = null,
    )

    @Serializable
    data class Checks(val totalHours: Int, val expectedTotal: Int? = null, val issues: List<Issue>) {
        val ok: Boolean get() = issues.isEmpty()
    }

    @Serializable
    data class Issue(
        val kind: Kind,
        val subject: String? = null,
        val codes: List<String> = emptyList(),
        /** German, for the JSON; the app words it itself. */
        val message: String,
    ) {
        @Serializable
        enum class Kind {
            @SerialName("unreadable") UNREADABLE,
            @SerialName("notInPlan") NOT_IN_PLAN,
            @SerialName("ambiguous") AMBIGUOUS,
            @SerialName("hoursMismatch") HOURS_MISMATCH,
            @SerialName("totalMismatch") TOTAL_MISMATCH,
            @SerialName("conflict") CONFLICT,
            @SerialName("gradeMismatch") GRADE_MISMATCH,
            /** A row with hours whose subject was not recognised; `codes` holds the hours. */
            @SerialName("unknownRow") UNKNOWN_ROW,
            /** The sheet's "Summen" row was not readable, so a missing subject would go unnoticed. */
            @SerialName("sumUnreadable") SUM_UNREADABLE,
            /** A Halbjahr value was not read and taken over from the rest of the sheet. */
            @SerialName("inferred") INFERRED,
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        val dayNames = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag")
    }
}

object CustomPlanBuilder {
    /** A subject matched to course codes of the plan, or why it could not be. */
    private sealed interface Resolution {
        data class Courses(val codes: List<String>) : Resolution
        data class Problem(val issue: CustomPlan.Issue) : Resolution
    }

    /** The subjects taken in one Halbjahr, with the sheet's unreadable cells as issues. */
    fun choices(kurswahl: Kurswahl, half: Int): Pair<List<CustomPlan.Choice>, List<CustomPlan.Issue>> {
        val choices = mutableListOf<CustomPlan.Choice>()
        val issues = mutableListOf<CustomPlan.Issue>()
        for (row in kurswahl.rows) {
            val cell = row.halves.getOrNull(half) ?: continue
            if (cell.unreadable) {
                // an empty cell is common for subjects never taken; only a cell with some text is worth asking about
                if (cell.raw != null) {
                    issues += CustomPlan.Issue(CustomPlan.Issue.Kind.UNREADABLE, row.subject,
                        message = "${subjectName(row.subject)}: Eintrag „${cell.raw}“ im Kurswahlprotokoll nicht lesbar")
                }
                continue
            }
            val hours = cell.hours ?: continue
            if (SchoolReference.subject(row.subject) == null) {
                issues += CustomPlan.Issue(CustomPlan.Issue.Kind.UNKNOWN_ROW, codes = listOf("$hours"),
                    message = "Ein Fach mit $hours Wochenstunden wurde im Kurswahlprotokoll nicht erkannt")
                continue
            }
            if (cell.inferred == true) {
                issues += CustomPlan.Issue(CustomPlan.Issue.Kind.INFERRED, row.subject,
                    message = "${subjectName(row.subject)}: Stunden nicht lesbar, aus dem Kurswahlprotokoll ergänzt")
            }
            // winprosa prints the parallel course only in the first Halbjahr column ("5(3)", then "5"):
            // a later Halbjahr with the same hours continues that course
            val parallel = cell.parallel ?: row.halves.take(half).reversed().firstOrNull { it.parallel != null && it.hours == hours }?.parallel
            choices += CustomPlan.Choice(row.subject, level(row.fachart, hours), hours, parallel)
        }
        return choices to issues
    }

    /** Kurswahlprotokoll + Stufenplan → plan. */
    fun build(kurswahl: Kurswahl, plan: Stufenplan, halbjahr: String, planSha256: String? = null, now: Instant = Instant.now()): CustomPlan {
        val grade = plan.grade ?: 11
        val half = Kurswahl.halfIndex(grade, halbjahr)
        val (found, scanIssues) = choices(kurswahl, half)
        val choices = found.toMutableList()
        val issues = scanIssues.toMutableList()
        val sheetGrade = plan.schuljahr?.let { kurswahl.grade(it) }
        if (sheetGrade != null && sheetGrade != grade) {
            issues += CustomPlan.Issue(CustomPlan.Issue.Kind.GRADE_MISMATCH,
                message = "Das Kurswahlprotokoll gehört zu J$sheetGrade, der Stundenplan ist ${plan.stufe}")
        }
        resolveUnknownRows(kurswahl, half, plan, choices, issues)
        if (kurswahl.sums.getOrNull(half) == null) {
            issues += CustomPlan.Issue(CustomPlan.Issue.Kind.SUM_UNREADABLE,
                message = "Die Summe im Kurswahlprotokoll war nicht lesbar, bitte prüfen, ob alle Fächer da sind")
        }
        choices.sortBy { order(it.subject) }
        return build(kurswahl.name ?: "", choices, kurswahl.konfession, plan, halbjahr, kurswahl.sums.getOrNull(half), issues, planSha256, now)
    }

    /** Choices → plan. Used after scanning and again whenever the user corrects a course. */
    fun build(
        name: String,
        choices: List<CustomPlan.Choice>,
        konfession: Kurswahl.Konfession?,
        plan: Stufenplan,
        halbjahr: String,
        expectedTotal: Int?,
        extraIssues: List<CustomPlan.Issue> = emptyList(),
        planSha256: String? = null,
        now: Instant = Instant.now(),
    ): CustomPlan {
        val issues = extraIssues.toMutableList()
        val courses = mutableListOf<CustomPlan.Course>()
        val lessons = mutableListOf<CustomPlan.Lesson>()
        val notes = mutableListOf<String>()

        for (choice in choices) {
            val subjectName = subjectName(choice.subject)
            when (val resolution = resolve(choice, konfession, plan)) {
                is Resolution.Problem -> issues += resolution.issue
                is Resolution.Courses -> {
                    val codes = resolution.codes
                    val slots = codes.flatMap { plan.slotsFor(it) }
                    val subject = SchoolReference.subjectName(choice.subject, codes[0])
                    val id = codes.joinToString("/")
                    val courseLessons = mutableListOf<CustomPlan.Lesson>()
                    for (slot in slots) {
                        val i = courseLessons.indexOfFirst { it.day == slot.day && it.start == slot.start && it.end == slot.end }
                        if (i >= 0) {
                            val existing = courseLessons[i]
                            courseLessons[i] = existing.copy(
                                rooms = if (slot.room != null && slot.room !in existing.rooms) existing.rooms + slot.room else existing.rooms,
                                teachers = if (slot.teacher != null && slot.teacher !in existing.teachers) existing.teachers + slot.teacher else existing.teachers)
                        } else {
                            courseLessons += CustomPlan.Lesson(slot.day, slot.start, slot.end, id, listOfNotNull(slot.room), listOfNotNull(slot.teacher))
                        }
                    }
                    val teacherCodes = slots.mapNotNull { it.teacher }.distinct()
                    val hours = courseLessons.sumOf { it.hours }
                    val course = CustomPlan.Course(
                        id, choice.subject, subject, choice.level, codes, title(subject, choice.level, codes),
                        teacherCodes.map { CustomPlan.Teacher(it, SchoolReference.teacherName(it)) }, hours, choice.hours)
                    if (hours != choice.hours) {
                        issues += CustomPlan.Issue(CustomPlan.Issue.Kind.HOURS_MISMATCH, choice.subject, codes,
                            "${course.title}: $hours Std. im Stundenplan, laut Kurswahl ${choice.hours} Std.")
                    }
                    if (codes.size > 1) {
                        notes += "$subjectName: ${codes.joinToString(" / ")} im Kurswahlprotokoll nicht vermerkt, alle zur selben Zeit"
                    }
                    courses += course
                    lessons += courseLessons
                }
            }
        }

        // two courses in the same period means one of them was matched wrongly
        val byPeriod = HashMap<String, MutableList<String>>()
        for (lesson in lessons) for (p in lesson.start..lesson.end) byPeriod.getOrPut("${lesson.day}-$p") { mutableListOf() } += lesson.course
        val reported = HashSet<List<String>>()
        for (day in 0 until 5) {
            for (p in 1..15) {
                val ids = byPeriod["$day-$p"] ?: continue
                if (ids.toSet().size <= 1) continue
                val pair = ids.toSet().sorted()
                if (!reported.add(pair)) continue
                val titles = pair.map { id -> courses.firstOrNull { it.id == id }?.title ?: id }
                issues += CustomPlan.Issue(CustomPlan.Issue.Kind.CONFLICT, codes = pair,
                    message = "${CustomPlan.dayNames[day]} $p. Std.: ${titles.joinToString(" und ")} gleichzeitig")
            }
        }

        val total = lessons.sumOf { it.hours }
        if (expectedTotal != null && expectedTotal != total) {
            issues += CustomPlan.Issue(CustomPlan.Issue.Kind.TOTAL_MISMATCH,
                message = "Summe $total Std., laut Kurswahlprotokoll $expectedTotal Std.")
        }
        return CustomPlan(
            name = name, stufe = plan.stufe, halbjahr = halbjahr, schuljahr = plan.schuljahr, stand = plan.stand,
            periods = SchoolReference.periods, breaks = SchoolReference.breaks, courses = courses,
            lessons = lessons.sortedWith(compareBy({ it.day }, { it.start })), choices = choices,
            checks = CustomPlan.Checks(total, expectedTotal, issues), notes = notes,
            generatedAt = DateTimeFormatter.ISO_INSTANT.format(now.truncatedTo(ChronoUnit.SECONDS)), planSha256 = planSha256)
    }

    /**
     * Rows whose subject was not recognised, identified by what the Stufenplan allows: a subject not
     * chosen yet (a language above the first other subject, as the sheet orders them) with a course of
     * exactly that parallel number and hours that clashes with nothing chosen. Taken only when a single
     * subject fits; otherwise the row stays an issue for the user.
     */
    private fun resolveUnknownRows(kurswahl: Kurswahl, half: Int, plan: Stufenplan,
                                   choices: MutableList<CustomPlan.Choice>, issues: MutableList<CustomPlan.Issue>) {
        issues.removeAll { it.kind == CustomPlan.Issue.Kind.UNKNOWN_ROW }
        val languages = setOf("D", "E", "F", "L", "I", "Sp")
        val firstOther = kurswahl.rows.indexOfFirst { SchoolReference.subject(it.subject) != null && it.subject !in languages }
            .takeIf { it >= 0 } ?: kurswahl.rows.size
        val named = kurswahl.rows.map { row -> SchoolReference.subject(row.subject)?.let { sheetGroup(it.key) } }
        for ((index, row) in kurswahl.rows.withIndex()) {
            if (SchoolReference.subject(row.subject) != null) continue
            val cell = row.halves.getOrNull(half) ?: continue
            val hours = cell.hours ?: continue
            // winprosa prints the course number in the first Halbjahr column only: a later one continues it
            val parallel = cell.parallel ?: row.halves.take(half).reversed().firstOrNull { it.parallel != null && it.hours == hours }?.parallel
            val occupied = cells(choices, kurswahl.konfession, plan)
            val taken = choices.map { it.subject }.toSet()
            // the sheet lists subjects in groups: a row can only be a subject of a group between its named neighbours'
            val above = named.subList(0, index).lastOrNull { it != null }
            val below = named.subList(index + 1, named.size).firstOrNull { it != null }
            val matches = SchoolReference.subjects.map { it.key }
                .filter { key -> key !in taken && (index < firstOther) == (key in languages) }
                .filter { key -> sheetGroup(key).let { g -> g == null || ((above == null || g >= above) && (below == null || g <= below)) } }
                .mapNotNull { key ->
                    val choice = CustomPlan.Choice(key, level(row.fachart, hours), hours, parallel)
                    val resolution = resolve(choice, kurswahl.konfession, plan) as? Resolution.Courses ?: return@mapNotNull null
                    if (parallel != null && !resolution.codes.all { CourseCode.of(it)?.number == parallel }) return@mapNotNull null
                    val courseCells = cells(listOf(choice), kurswahl.konfession, plan)
                    choice.takeIf { courseCells.size == hours && courseCells.none { it in occupied } }
                }
            // several unrecognised rows with the very same values (two Leistungsfach languages "5(1)"): as many
            // subjects as rows fit, so they are those subjects in the sheet's order
            val alike = kurswahl.rows.withIndex().count { (j, other) ->
                j >= index && SchoolReference.subject(other.subject) == null && other.halves.getOrNull(half)?.let { c ->
                    c.hours == hours && (c.parallel ?: other.halves.take(half).reversed().firstOrNull { it.parallel != null && it.hours == hours }?.parallel) == parallel
                } == true
            }
            if (matches.size == 1) {
                choices += matches[0]
            } else if (matches.size > 1 && matches.size == alike) {
                choices += matches.minBy { order(it.subject) }
            } else {
                issues += CustomPlan.Issue(CustomPlan.Issue.Kind.UNKNOWN_ROW, codes = listOf("$hours"),
                    message = "Ein Fach mit $hours Wochenstunden wurde im Kurswahlprotokoll nicht erkannt")
            }
        }
    }

    /** "day-period" of every lesson the choices resolve to. */
    private fun cells(choices: List<CustomPlan.Choice>, konfession: Kurswahl.Konfession?, plan: Stufenplan): Set<String> {
        val result = HashSet<String>()
        for (choice in choices) {
            val resolution = resolve(choice, konfession, plan) as? Resolution.Courses ?: continue
            for (slot in resolution.codes.flatMap { plan.slotsFor(it) }) for (p in slot.start..slot.end) result += "${slot.day}-$p"
        }
        return result
    }

    fun level(fachart: String?, hours: Int): CustomPlan.Level = when (fachart) {
        "L" -> CustomPlan.Level.LEISTUNGSFACH
        "B", "m" -> CustomPlan.Level.BASISFACH
        else -> if (hours >= 5) CustomPlan.Level.LEISTUNGSFACH else CustomPlan.Level.BASISFACH
    }

    /** Every course of the plan a subject could be at a level ("M", LF → M1, M2, M3). */
    fun candidates(subject: String, level: CustomPlan.Level?, konfession: Kurswahl.Konfession?, plan: Stufenplan): List<String> {
        var stems = SchoolReference.subject(subject)?.stems ?: listOf(subject.lowercase())
        if (subject == "Rel") {
            when (konfession) {
                Kurswahl.Konfession.KATHOLISCH -> stems = listOf("kr")
                Kurswahl.Konfession.EVANGELISCH -> stems = listOf("er")
                null -> Unit
            }
        }
        val all = plan.codes.mapNotNull { CourseCode.of(it) }.filter { it.stem in stems }
        if (level == null) return all.map { it.code }.sorted()
        // Untis capitalises Leistungsfach codes; a stem written only one way (LTh, kR) is not a hint
        val hasBoth = all.map { it.capitalised }.toSet().size == 2
        val atLevel = if (hasBoth) all.filter { it.capitalised == (level == CustomPlan.Level.LEISTUNGSFACH) } else all
        return atLevel.map { it.code }.sorted()
    }

    private fun resolve(choice: CustomPlan.Choice, konfession: Kurswahl.Konfession?, plan: Stufenplan): Resolution {
        val name = subjectName(choice.subject)
        choice.code?.let { code ->
            return if (code in plan.codes) Resolution.Courses(listOf(code))
            else Resolution.Problem(CustomPlan.Issue(CustomPlan.Issue.Kind.NOT_IN_PLAN, choice.subject, listOf(code),
                "$name: Kurs $code steht nicht im Stundenplan"))
        }
        val candidates = candidates(choice.subject, choice.level, konfession, plan).mapNotNull { CourseCode.of(it) }
        choice.parallel?.let { parallel ->
            candidates.firstOrNull { it.number == parallel }?.let { return Resolution.Courses(listOf(it.code)) }
            // a subject with a single course has no number in Untis ("esp", "GK")
            if (candidates.size == 1) return Resolution.Courses(listOf(candidates[0].code))
            val stem = SchoolReference.subject(choice.subject)?.stems?.firstOrNull()
            val wanted = if (stem != null) {
                (if (choice.level == CustomPlan.Level.LEISTUNGSFACH) stem.replaceFirstChar { it.uppercase() } else stem) + parallel
            } else "${choice.subject}$parallel"
            return Resolution.Problem(CustomPlan.Issue(CustomPlan.Issue.Kind.NOT_IN_PLAN, choice.subject, candidates.map { it.code },
                "$name: Kurs $wanted steht nicht im Stundenplan"))
        }
        if (candidates.size == 1) return Resolution.Courses(listOf(candidates[0].code))
        val unnumbered = candidates.filter { it.number == null }
        if (unnumbered.size == 1) return Resolution.Courses(listOf(unnumbered[0].code))
        if (candidates.size > 1) {
            // parallel courses in the very same slots (Basis-Sport s1/s2/s3): the number does not matter here
            val slotSets = candidates.map { c -> plan.slotsFor(c.code).map { "${it.day}-${it.start}-${it.end}" }.toSet() }
            if (slotSets.toSet().size == 1) return Resolution.Courses(candidates.map { it.code })
            return Resolution.Problem(CustomPlan.Issue(CustomPlan.Issue.Kind.AMBIGUOUS, choice.subject, candidates.map { it.code },
                "$name: mehrere Kurse möglich (${candidates.joinToString(", ") { it.code }})"))
        }
        return Resolution.Problem(CustomPlan.Issue(CustomPlan.Issue.Kind.NOT_IN_PLAN, choice.subject,
            message = "$name: kein passender Kurs im Stundenplan"))
    }

    fun title(subject: String, level: CustomPlan.Level, codes: List<String>): String {
        if (codes.size > 1) return "$subject (${codes.joinToString(" / ")})"
        val code = codes[0]
        val numbered = CourseCode.of(code)?.number != null
        return when (level) {
            CustomPlan.Level.LEISTUNGSFACH -> if (numbered) "$subject (LF, $code)" else "$subject (LF)"
            CustomPlan.Level.BASISFACH -> "$subject ($code)"
        }
    }

    private fun subjectName(key: String): String = SchoolReference.subject(key)?.name ?: key

    /**
     * The blocks of the Kurswahlprotokoll, top to bottom. Within a block the order varies between sheets
     * (Sp before or after F, Inf before or after Sport), the blocks themselves do not.
     */
    private val sheetGroups = listOf(
        setOf("D", "E", "F", "Sp", "L", "I"), setOf("BK", "Mu"), setOf("G", "Gk", "Geo", "WBS"), setOf("Rel", "Eth", "Phil"),
        setOf("M"), setOf("Bio", "Ph", "Ch", "NwT"), setOf("Inf", "Sport", "Psy", "Ast", "LTh"),
    )

    private fun sheetGroup(key: String): Int? = sheetGroups.indexOfFirst { key in it }.takeIf { it >= 0 }

    /** Row order of the Kurswahlprotokoll. */
    private fun order(key: String): Int = SchoolReference.subjects.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: Int.MAX_VALUE
}
