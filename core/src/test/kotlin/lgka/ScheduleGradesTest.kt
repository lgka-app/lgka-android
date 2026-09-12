package lgka

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Grade discovery must follow whatever the school uploads: a combined "J11/12" PDF,
 * split "J11" / "J12" PDFs, or a future J13 — never a constant.
 */
class ScheduleGradesTest {
    @Test
    fun gradesFromTitles() {
        assertEquals((5..10).toList(), ScheduleGrades.fromTitle("Stundenpläne - 2026/2027 - 1.HJ - 5-10"))
        assertEquals(listOf(11), ScheduleGrades.fromTitle("Stundenpläne - 2026/2027 - 1.HJ - J11"))
        assertEquals(listOf(12), ScheduleGrades.fromTitle("Stundenpläne - 2026/2027 - 1.HJ - J12"))
        assertEquals(listOf(11, 12), ScheduleGrades.fromTitle("Stundenpläne - 2025/2026 - 1.HJ - J11/12"))
        assertEquals(listOf(11, 12), ScheduleGrades.fromTitle("Stundenpläne 11-12"))
        assertEquals(listOf(13), ScheduleGrades.fromTitle("Stundenpläne - 2027/2028 - 1.HJ - J13"))
        assertEquals(emptyList(), ScheduleGrades.fromTitle("Stundenpläne"))
    }

    @Test
    fun classTokens() {
        assertEquals(10, ScheduleGrades.gradeOf("10b"))
        assertEquals(5, ScheduleGrades.gradeOf("5a"))
        assertEquals(11, ScheduleGrades.gradeOf("j11"))
        assertEquals(13, ScheduleGrades.gradeOf("J13"))
        assertNull(ScheduleGrades.gradeOf("stundenplan"))
        assertNull(ScheduleGrades.gradeOf("7f"))
    }
}
