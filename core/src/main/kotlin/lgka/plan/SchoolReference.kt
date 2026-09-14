package lgka.plan

import kotlinx.serialization.Serializable
import lgka.api.TeacherDirectory

/**
 * Fixed facts of the Lessing-Gymnasium the custom plan needs: Läuteordnung, subjects of the
 * Kurswahlprotokoll with their Untis course stems, and teacher names from the synced Kollegium.
 */
object SchoolReference {
    // ── Läuteordnung (Schulordnung 2025, valid from SJ 2025/26) ──────────────────

    @Serializable
    data class Period(val number: Int, val start: String, val end: String)

    /** The break follows period [after]. */
    @Serializable
    data class Break(val after: Int, val start: String, val end: String)

    /** The school website still shows an older grid; this is the valid one. */
    val periods = listOf(
        Period(1, "7:45", "8:30"), Period(2, "8:35", "9:20"),
        Period(3, "9:35", "10:20"), Period(4, "10:25", "11:10"),
        Period(5, "11:30", "12:15"), Period(6, "12:20", "13:05"),
        Period(7, "13:10", "13:55"), Period(8, "14:00", "14:45"),
        Period(9, "14:50", "15:35"), Period(10, "15:50", "16:35"),
        Period(11, "16:35", "17:20"),
    )

    val breaks = listOf(Break(2, "9:20", "9:35"), Break(4, "11:10", "11:30"), Break(9, "15:35", "15:50"))

    /** Double-period blocks 1–2, 3–4, 5–6, 10–11; 7, 8, 9 are single periods. */
    val blockStarts = setOf(1, 3, 5, 7, 8, 9, 10)

    const val LAEUTEORDNUNG_SOURCE = "Läuteordnung ab SJ 2025/26 (Schulordnung, Stand 2025)"

    // ── Subjects ─────────────────────────────────────────────────────────────────

    /**
     * [key] is the abbreviation in the Kurswahlprotokoll's "Fächer" column ("D", "Gk", "Sport");
     * [stems] the lower-cased letter part of the Untis course codes ("m" matches "M3" and "m1").
     */
    data class Subject(val key: String, val name: String, val stems: List<String>)

    val subjects = listOf(
        Subject("D", "Deutsch", listOf("d")),
        Subject("E", "Englisch", listOf("e")),
        Subject("F", "Französisch", listOf("f")),
        Subject("Sp", "Spanisch", listOf("esp", "sp")),
        Subject("L", "Latein", listOf("l")),
        Subject("I", "Italienisch", listOf("i")),
        Subject("BK", "Bildende Kunst", listOf("bk")),
        Subject("Mu", "Musik", listOf("mus", "mu")),
        Subject("G", "Geschichte", listOf("g")),
        Subject("Gk", "Gemeinschaftskunde", listOf("gk")),
        Subject("Geo", "Geographie", listOf("geo")),
        Subject("WBS", "Wirtschaft", listOf("wbs", "wi")),
        Subject("Rel", "Religion", listOf("kr", "er")),
        Subject("Eth", "Ethik", listOf("eth")),
        Subject("Phil", "Philosophie", listOf("phil")),
        Subject("M", "Mathematik", listOf("m")),
        Subject("Bio", "Biologie", listOf("bio")),
        Subject("Ph", "Physik", listOf("ph")),
        Subject("Ch", "Chemie", listOf("ch")),
        Subject("NwT", "NwT", listOf("nwt")),
        Subject("Inf", "Informatik", listOf("inf")),
        Subject("Sport", "Sport", listOf("s", "spo")),
        Subject("Psy", "Psychologie", listOf("psy")),
        Subject("Ast", "Astronomie", listOf("ast")),
        Subject("LTh", "Literatur und Theater", listOf("lth")),
    )

    fun subject(key: String): Subject? = subjects.firstOrNull { it.key.equals(key, ignoreCase = true) }

    /** Religion is printed as "kath. Religion" / "ev. Religion" depending on the course stem. */
    fun subjectName(key: String, code: String): String {
        if (key == "Rel") {
            when (CourseCode.of(code)?.stem) {
                "kr" -> return "kath. Religion"
                "er" -> return "ev. Religion"
            }
        }
        return subject(key)?.name ?: key
    }

    // ── Kollegium ────────────────────────────────────────────────────────────────

    /**
     * "Dr. Daniel Roth" from the synced staff list (api.lgka.app/v1/kollegium); null for a code it
     * doesn't know yet, which is then shown as it is printed in the timetable.
     */
    fun teacherName(code: String): String? = TeacherDirectory.name(code)

    /** "Roth", for cells naming several teachers; the code for one the staff list doesn't know. */
    fun lastName(code: String): String = TeacherDirectory.lastName(code)
}

/** An Untis course code split into its parts: "M3" → stem "m", number 3, leistungsfach. */
data class CourseCode(
    val code: String,
    /** Lower-cased letters: "m", "kr", "esp". */
    val stem: String,
    val number: Int?,
    /** Untis writes Leistungsfach courses with a capital first letter ("M3", "Esp", "S1"). */
    val capitalised: Boolean,
) {
    companion object {
        private val PATTERN = Regex("([A-Za-zÄÖÜäöü]+)(\\d*)")

        fun of(code: String): CourseCode? {
            val m = PATTERN.matchEntire(code) ?: return null
            val letters = m.groupValues[1]
            return CourseCode(code, letters.lowercase(), m.groupValues[2].toIntOrNull(), letters.first().isUpperCase())
        }
    }
}
