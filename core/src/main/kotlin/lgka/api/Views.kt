package lgka.api

import lgka.ScheduleGrades
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The app's hourly window: from the start of [nowLocal]'s hour for 24 hours.
 * Timestamps are Europe/Berlin wall-clock strings ("YYYY-MM-DDTHH:MM").
 */
fun Weather.hourlyWindow(nowLocal: LocalDateTime, hours: Long = 24): List<HourlyForecast> {
    val start = nowLocal.truncatedTo(ChronoUnit.HOURS)
    val end = start.plusHours(hours)
    return hourly.filter { h ->
        val dt = runCatching { LocalDateTime.parse(h.dt) }.getOrNull() ?: return@filter false
        !dt.isBefore(start) && dt.isBefore(end)
    }
}

/** Grades a timetable PDF covers, from its title ("… - 5-10", "… - J11"), falling back to `gradeLevel`. */
val ScheduleItem.grades: List<Int>
    get() = ScheduleGrades.fromTitle(title).ifEmpty {
        when (gradeLevel) {
            "Klassen 5-10" -> (5..10).toList()
            "J11" -> listOf(11)
            "J12" -> listOf(12)
            "J11/J12" -> listOf(11, 12)
            else -> emptyList()
        }
    }

/** Does this PDF contain [cls] ("10b" → grade 10, "j11" → grade 11)? Falls back to the class index. */
fun ScheduleItem.covers(cls: String): Boolean {
    val key = cls.lowercase()
    if (classIndex.containsKey(key)) return true
    return ScheduleGrades.gradeOf(key)?.let { it in grades } ?: false
}

/**
 * The timetable for a class within a semester group: the PDF whose class
 * index or grades contain it, else a sensible fallback by level.
 */
fun scheduleFor(cls: String, group: List<ScheduleItem>): ScheduleItem? {
    group.firstOrNull { it.covers(cls) }?.let { return it }
    val jahrgang = (ScheduleGrades.gradeOf(cls) ?: 0) >= 11
    return group.firstOrNull { s -> if (jahrgang) s.grades.any { it >= 11 } else s.grades.any { it <= 10 } }
        ?: group.firstOrNull()
}

/** Prefer the 2. Halbjahr once it is published, else the 1. Halbjahr. */
fun Schedules.preferredGroup(): List<ScheduleItem> {
    val second = items.filter { it.halbjahr == "2. Halbjahr" && it.available }
    if (second.isNotEmpty()) return second
    return items.filter { it.halbjahr == "1. Halbjahr" && it.available }.ifEmpty { items.filter { it.available } }
}

/** API pages are real 1-based PDF pages; pagers are 0-based. */
fun pagerIndex(page: Int, pageCount: Int): Int = (page - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
