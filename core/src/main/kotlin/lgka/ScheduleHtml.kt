package lgka

import org.jsoup.Jsoup

/**
 * Schedule page scraper — Kotlin port of the app's `_parseScheduleHtml`
 * (schedule_service.dart), verified against the stundenplan_page goldens.
 */
object ScheduleHtml {
    private const val BASE = "https://lessing-gymnasium-karlsruhe.de"

    fun parse(html: String): List<LinkedHashMap<String, Any?>> {
        val doc = Jsoup.parse(html)
        val module = doc.selectFirst("#mod-custom213")
            ?: throw LgkaParseException("schedule module #mod-custom213 not found")
        val schedules = mutableListOf<LinkedHashMap<String, Any?>>()
        val seenUrls = mutableSetOf<String>()

        for (link in module.select("a[href*=stundenplan]")) {
            val href = link.attr("href").ifEmpty { null } ?: continue
            // Dart uses raw .text (no Jsoup-style normalization) then trims.
            val linkText = link.wholeText().trim()
            val title = linkText.ifEmpty { link.attr("title") }
            if (title.isEmpty()) continue

            var fullUrl = href
            if (href.startsWith("/cm3/../")) {
                fullUrl = href.replaceFirst("/cm3/../", "$BASE/")
            } else if (href.startsWith("/")) {
                fullUrl = "$BASE$href"
            }
            if (!seenUrls.add(fullUrl)) continue

            val halbjahr = when {
                href.contains("hj2") -> "2. Halbjahr"
                href.contains("hj1") -> "1. Halbjahr"
                title.contains("1.HJ") -> "1. Halbjahr"
                title.contains("2.HJ") -> "2. Halbjahr"
                else -> "Unbekannt"
            }
            val gradeLevel = when {
                title.contains("5-10") -> "Klassen 5-10"
                title.contains("J11/12") -> "J11/J12"
                title.contains("11-12") -> "J11/J12"
                else -> "Unbekannt"
            }

            schedules.add(linkedMapOf(
                "title" to title,
                "url" to href,
                "halbjahr" to halbjahr,
                "gradeLevel" to gradeLevel,
                "fullUrl" to fullUrl,
            ))
        }

        if (schedules.isEmpty()) throw LgkaParseException("no schedule links found")
        return schedules
    }
}

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
