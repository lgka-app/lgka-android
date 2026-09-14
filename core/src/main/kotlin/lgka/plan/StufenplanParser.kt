package lgka.plan

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Every course slot of an Untis "Stufenplan" (the one-page J11 / J12 grid the school publishes). */
@Serializable
data class Stufenplan(
    /** "J11", "J12". */
    val stufe: String,
    /** "2026-2027". */
    val schuljahr: String? = null,
    /** Untis export time, "7.9.2026 14:39". */
    val stand: String? = null,
    val slots: List<Slot>,
) {
    @Serializable
    data class Slot(
        /** 0 = Montag … 4 = Freitag. */
        val day: Int,
        val start: Int,
        val end: Int,
        /** Untis course code without the leading separator dot: "M3", "d3", "kR1", "GK". */
        val code: String,
        val teacher: String? = null,
        val room: String? = null,
    ) {
        val hours: Int get() = end - start + 1
    }

    /** Every course code in the plan. */
    val codes: Set<String> get() = slots.mapTo(HashSet()) { it.code }

    fun slotsFor(code: String): List<Slot> = slots.filter { it.code == code }

    /** 11 for "J11". */
    val grade: Int? get() = stufe.dropWhile { !it.isDigit() }.toIntOrNull()
}

/**
 * Reads an Untis Stufenplan from the positioned words of its PDF page. No OCR: the PDF carries
 * real text, only its reading order is useless, so the grid is rebuilt from positions.
 *
 * Layout: day names head five columns, period numbers run down the left margin, and every cell is
 * a block of a course-code line, a teacher line and an (italic) room line, the three aligned column
 * by column. A block centred on a period label is a single period, a block centred between two
 * labels a double period.
 */
object StufenplanParser {
    class Failure(message: String) : Exception(message)

    val dayNames = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag")

    /** Rooms when the PDF does not say which words are italic. */
    private val ROOM = Regex("^(\\d{3}|NWT\\d*|BIO[A-Z]*|CHHS|PHHS|MUSIK|BK(OG|UG)|WB\\d|CBH|FrEb|Less|AULA|Aula)$")
    private val STUFE = Regex("J\\d{2}")
    private val SCHULJAHR = Regex("\\d{4}-\\d{4}")
    private val DATE = Regex("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")
    private val TIME = Regex("\\d{1,2}:\\d{2}")

    fun parse(words: List<TextBox>): Stufenplan {
        val headers = dayNames.mapNotNull { name -> words.firstOrNull { it.text == name } }
        if (headers.size != dayNames.size) throw Failure("no day header")
        val dayX = headers.map { it.midX }
        val headerBottom = headers.maxOf { it.maxY }
        val columnWidth = (dayX[4] - dayX[0]) / 4
        val gridLeft = dayX[0] - columnWidth / 2

        val labels = sortedMapOf<Int, Double>()
        for (w in words) {
            if (w.midX >= gridLeft || w.y <= headerBottom) continue
            val n = w.text.toIntOrNull() ?: continue
            if (n in 1..15 && n !in labels) labels[n] = w.midY
        }
        if (labels.size < 2) throw Failure("no period labels")
        val labelGaps = labels.keys.mapNotNull { p -> labels[p + 1]?.let { it - labels.getValue(p) } }
        val rowHeight = labelGaps.median() ?: 38.0

        val slots = mutableListOf<Stufenplan.Slot>()
        val grid = words.filter { it.y > headerBottom + 2 && it.midX > gridLeft }
        for (day in dayNames.indices) {
            val inColumn = grid.filter { w -> dayX.indices.minBy { abs(dayX[it] - w.midX) } == day }
            val lines = lines(inColumn, tolerance = rowHeight * 0.06)
            var i = 0
            while (i < lines.size) {
                val code = lines[i]
                var teacher = emptyList<TextBox>()
                var room = emptyList<TextBox>()
                var used = 1
                if (i + 1 < lines.size && lines[i + 1].y - code.y < rowHeight * 0.6 && !isRoomLine(lines[i + 1].words)) {
                    teacher = lines[i + 1].words
                    used = 2
                    if (i + 2 < lines.size && lines[i + 2].y - lines[i + 1].y < rowHeight * 0.6 && isRoomLine(lines[i + 2].words)) {
                        room = lines[i + 2].words
                        used = 3
                    }
                }
                val centre = teacher.firstOrNull()?.midY ?: code.y
                val (start, end) = span(centre, labels)
                for (c in code.words) {
                    val text = c.text.removePrefix(".")
                    if (text.isEmpty()) continue
                    slots += Stufenplan.Slot(day, start, end, text,
                        teacher = aligned(teacher, c, within = 12.0)?.text,
                        room = aligned(room, c, within = 14.0)?.text)
                }
                i += used
            }
        }

        val top = words.filter { it.y < headerBottom - 5 }
        val stufe = top.firstOrNull { STUFE.matches(it.text) }?.text ?: "J?"
        val schuljahr = top.firstOrNull { SCHULJAHR.matches(it.text) }?.text
        val stand = top.firstOrNull { DATE.matches(it.text) }?.let { date ->
            val time = top.firstOrNull { abs(it.midY - date.midY) < 3 && it.x > date.x && TIME.matches(it.text) }
            listOfNotNull(date.text, time?.text).joinToString(" ")
        }
        val ordered = slots.sortedWith(compareBy({ it.day }, { it.start }, { it.code }))
        return Stufenplan(stufe, schuljahr, stand, ordered)
    }

    private class Line(val y: Double, val words: MutableList<TextBox>)

    /** Words grouped into lines (by vertical centre), top to bottom, each line left to right. */
    private fun lines(words: List<TextBox>, tolerance: Double): List<Line> {
        val lines = mutableListOf<Line>()
        for (w in words.sortedBy { it.midY }) {
            val last = lines.lastOrNull()
            if (last != null && abs(last.y - w.midY) <= tolerance) last.words += w else lines += Line(w.midY, mutableListOf(w))
        }
        return lines.map { Line(it.y, it.words.sortedBy { w -> w.x }.toMutableList()) }
    }

    private fun isRoomLine(words: List<TextBox>): Boolean {
        if (words.all { it.italic != null }) return words.all { it.italic == true }
        return words.all { ROOM.matches(it.text) }
    }

    private fun span(y: Double, labels: Map<Int, Double>): Pair<Int, Int> {
        var best = 0 to 0
        var distance = Double.POSITIVE_INFINITY
        for ((p, ly) in labels) {
            if (abs(ly - y) < distance) { distance = abs(ly - y); best = p to p }
            val next = labels[p + 1] ?: continue
            if (abs((ly + next) / 2 - y) < distance) { distance = abs((ly + next) / 2 - y); best = p to p + 1 }
        }
        return best
    }

    private fun aligned(line: List<TextBox>, word: TextBox, within: Double): TextBox? {
        val nearest = line.minByOrNull { abs(it.midX - word.midX) } ?: return null
        return nearest.takeIf { abs(it.midX - word.midX) < within }
    }
}
