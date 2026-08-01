package lgka

import java.time.LocalDate

/**
 * School events scraper — Kotlin port of the app's `_parseWeekHtml` +
 * the aggregation in `fetchUpcomingEvents` (events_service.dart), verified
 * against the events goldens.
 */
object Events {
    private val LI = Regex(
        "<li\\s+class=[\"']ev_td_li[\"'][^>]*>(.*?)</li>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val HREF = Regex("href=\"[^\"]*?/icalrepeat\\.detail/(\\d{4})/(\\d{2})/(\\d{2})/")
    private val TITLE = Regex("title=\"([^\"]+)\"")
    private val TIME = Regex("(\\d{1,2}:\\d{2})\\s*Uhr")

    data class Event(val date: LocalDate, val time: String?, val title: String)

    fun parseWeekHtml(html: String, today: LocalDate): List<Event> {
        val events = mutableListOf<Event>()
        for (m in LI.findAll(html)) {
            val li = m.groupValues[1]
            val href = HREF.find(li) ?: continue
            val date = LocalDate.of(
                href.groupValues[1].toInt(),
                href.groupValues[2].toInt(),
                href.groupValues[3].toInt())
            if (date.isBefore(today)) continue
            val title = TITLE.find(li)?.groupValues?.get(1)?.trim()
                ?.let(::decodeEntities) ?: continue
            if (title.isEmpty()) continue
            val time = TIME.find(li)?.groupValues?.get(1)
            events.add(Event(date, time, title))
        }
        return events
    }

    /** Mirror of fetchUpcomingEvents aggregation: dedup + sort ascending. */
    fun aggregate(weekHtmls: List<String>, today: LocalDate): List<LinkedHashMap<String, Any?>> {
        val all = mutableListOf<Event>()
        val seen = mutableSetOf<String>()
        for (html in weekHtmls) {
            for (e in parseWeekHtml(html, today)) {
                val key = "${isoDateTime(e.date)}|${e.title.lowercase().trim()}"
                if (seen.add(key)) all.add(e)
            }
        }
        all.sortBy { it.date }
        return all.map {
            linkedMapOf(
                "date" to isoDateTime(it.date),
                "time" to it.time,
                "title" to it.title,
            )
        }
    }

    /** Dart DateTime(y,m,d).toIso8601String(): midnight with milliseconds. */
    private fun isoDateTime(d: LocalDate): String =
        "%04d-%02d-%02dT00:00:00.000".format(d.year, d.monthValue, d.dayOfMonth)

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace("&auml;", "ä").replace("&ouml;", "ö").replace("&uuml;", "ü")
        .replace("&Auml;", "Ä").replace("&Ouml;", "Ö").replace("&Uuml;", "Ü")
        .replace("&szlig;", "ß")
}
