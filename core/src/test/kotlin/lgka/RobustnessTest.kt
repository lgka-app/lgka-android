package lgka

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The data layer must never trap on server data: malformed input throws a typed exception. */
class RobustnessTest {
    private fun line(y: Double, texts: List<String>): Line {
        var x = 0.0
        return Line(y, texts.map { t -> Word(t, x, x + 20).also { x += 40 } })
    }

    @Test
    fun extractorMarksShortPagesEmpty() {
        val plan = Extractor.extract(listOf(Line(0.0, listOf(Word("Hi", 0.0, 5.0)))))
        assertEquals(true, plan["isEmpty"])
    }

    @Test
    fun extractorThrowsTypedOnUnexpectedHeader() {
        val lines = listOf(
            line(10.0, listOf("Lessing-Gymnasium", "Karlsruhe")),
            line(20.0, listOf("Klassen", "12.9.", "/", "Freitag", "Vertretungen")),
            line(30.0, listOf("Art", "Stunde", "Klasse")),
            line(40.0, listOf("Entfall", "1", "5a", "---", "M", "R101", "M", "XY", "R101", "x")),
            line(50.0, listOf("12.9.2026", "(37)", "SJ", "2026-27", "padding", "padding", "padding")),
        )
        assertThrows<LgkaParseException> { Extractor.extract(lines) }
    }

    @Test
    fun scheduleParserThrowsTyped() {
        assertThrows<LgkaParseException> { ScheduleHtml.parse("<html><body>nothing</body></html>") }
        assertThrows<LgkaParseException> {
            ScheduleHtml.parse("<div id=\"mod-custom213\"><a href=\"/x.pdf\">x</a></div>")
        }
    }

    @Test
    fun eventsFilterPastAndDedupe() {
        val html = """
            <li class="ev_td_li"><a href="/cm3/index.php/termine/icalrepeat.detail/2026/09/10/1/-/x" title="Alt">09:00 Uhr</a></li>
            <li class="ev_td_li"><a href="/cm3/index.php/termine/icalrepeat.detail/2026/09/14/2/-/y" title="Kurzkonferenz &amp; Info">07:30 Uhr</a></li>
        """.trimIndent()
        val events = Events.aggregate(listOf(html, html), LocalDate.of(2026, 9, 12))
        assertEquals(1, events.size)
        assertEquals("Kurzkonferenz & Info", events[0]["title"])
        assertEquals("07:30", events[0]["time"])
    }

    @Test
    fun weatherRejectsGarbage() {
        assertThrows<Exception> { Weather.parse("not json", java.time.LocalDateTime.of(2026, 9, 12, 12, 0)) }
    }

    @Test
    fun newsHandlesEmptyDocuments() {
        assertTrue(News.parseListPage("<html></html>").isEmpty())
        assertEquals(null, News.parseArticle("<html></html>").content)
    }

    @Test
    fun glyphClusteringSplitsWordsOnGapAndWhitespace() {
        val glyphs = listOf(
            Glyph("A", 0.0, 10.0, 5.0), Glyph("b", 5.0, 10.0, 5.0),
            Glyph(" ", 10.0, 10.0, 3.0), Glyph("c", 13.0, 10.0, 5.0),
            Glyph("d", 40.0, 10.0, 5.0), Glyph("e", 0.0, 30.0, 5.0),
        )
        val lines = GlyphClustering.lines(glyphs)
        assertEquals(2, lines.size)
        assertEquals(listOf("Ab", "c", "d"), lines[0].words.map { it.text })
        assertEquals("e", lines[1].text)
    }
}
