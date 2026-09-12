package lgka

/**
 * Grade discovery shared by the schedule list and the PDF viewer, so a new Jahrgang
 * (J13, a split "J11" / "J12" upload, …) needs no app update.
 */
object ScheduleGrades {
    private val range = Regex("""(\d{1,2})\s*-\s*(\d{1,2})""")
    private val jahrgang = Regex("""[Jj]\s*(\d{1,2})(?:\s*/\s*(\d{1,2}))?""")
    private val classToken = Regex("""^(?:j(\d{1,2})|(\d{1,2})[a-e])$""")

    /** Grades named in a link title: "… - 5-10" → 5..10, "… - J11" → [11], "J11/12" or "11-12" → [11, 12]. */
    fun fromTitle(title: String): List<Int> {
        val tail = title.substringAfterLast(" - ")
        val grades = sortedSetOf<Int>()
        for (m in range.findAll(tail)) {
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            if (a <= b && b - a < 20) grades.addAll(a..b)
        }
        for (m in jahrgang.findAll(tail)) {
            grades.add(m.groupValues[1].toInt())
            m.groupValues[2].toIntOrNull()?.let { grades.add(it) }
        }
        return grades.toList()
    }

    /** "10b" → 10, "j11" → 11, "J13" → 13; null for anything else. */
    fun gradeOf(cls: String): Int? {
        val m = classToken.matchEntire(cls.lowercase()) ?: return null
        return (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
    }

    fun isClassToken(cls: String): Boolean = gradeOf(cls) != null
}
