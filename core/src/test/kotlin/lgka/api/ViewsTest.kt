package lgka.api

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewsTest {
    private fun weather(): Weather {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("weather.json"))
        return ApiJson.decodeFromJsonElement(Weather.serializer(), env.data)
    }

    @Test
    fun hourlyWindowStartsAtTheCurrentHourAndSpans24Hours() {
        val w = weather()
        val window = w.hourlyWindow(LocalDateTime.parse("2026-09-12T13:37:00"))
        assertEquals(24, window.size)
        assertEquals("2026-09-12T13:00", window.first().dt)
        assertEquals("2026-09-13T12:00", window.last().dt)
        // near the end of the forecast the window simply gets shorter
        assertTrue(w.hourlyWindow(LocalDateTime.parse("2026-09-14T20:00:00")).size < 24)
        assertEquals(0, w.hourlyWindow(LocalDateTime.parse("2027-01-01T00:00:00")).size)
    }

    @Test
    fun classIndexPagesMapToPagerIndices() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("schedules.json"))
        val schedules = ApiJson.decodeFromJsonElement(Schedules.serializer(), env.data)
        val lower = schedules.items.first { it.gradeLevel == "Klassen 5-10" }
        // "5a" is on PDF page 1 → pager index 0 (the old app stored pageIndex + 2 for this)
        assertEquals(0, pagerIndex(lower.classIndex.getValue("5a"), lower.pdf!!.pageCount))
        assertEquals(17, pagerIndex(lower.classIndex.getValue("10a"), lower.pdf.pageCount))
        assertEquals(20, pagerIndex(99, 21)) // clamped
        assertEquals(0, pagerIndex(0, 21))
    }

    @Test
    fun scheduleLookupCoversSplitJahrgangUploads() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("schedules.json"))
        val schedules = ApiJson.decodeFromJsonElement(Schedules.serializer(), env.data)
        val group = schedules.preferredGroup()
        assertEquals(3, group.size)
        assertEquals("Klassen 5-10", assertNotNull(scheduleFor("7b", group)).gradeLevel)
        assertEquals("J11", assertNotNull(scheduleFor("j11", group)).gradeLevel)
        assertEquals("J12", assertNotNull(scheduleFor("J12", group)).gradeLevel)
        assertTrue(group.first { it.gradeLevel == "J11" }.covers("j11"))
        assertTrue(!group.first { it.gradeLevel == "J11" }.covers("5a"))
        assertNull(scheduleFor("7b", emptyList()))
    }

    @Test
    fun knownClassIgnoresCaseAndSpaces() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("schedules.json"))
        val group = ApiJson.decodeFromJsonElement(Schedules.serializer(), env.data).preferredGroup()
        for (input in listOf("10B", "10b", " 10b ", "10 b", "1 0B")) assertEquals("10b", knownClass(input, group))
        assertEquals("j11", knownClass("J11", group))
        assertEquals("j11", knownClass("j 11", group))
    }

    @Test
    fun knownClassSearchesEveryPdfOfTheGroup() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("schedules.json"))
        val group = ApiJson.decodeFromJsonElement(Schedules.serializer(), env.data).preferredGroup()
        // one key from each of the three uploads (5-10, J11, J12)
        assertEquals("7b", knownClass("7b", group))
        assertEquals("j11", knownClass("j11", group))
        assertEquals("j12", knownClass("J12", group))
        assertEquals("j12", knownClass("j12", group.filter { it.gradeLevel == "J12" }))
        assertNull(knownClass("j11", group.filter { it.gradeLevel == "J12" }))
    }

    @Test
    fun knownClassRejectsUnknownInput() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("schedules.json"))
        val group = ApiJson.decodeFromJsonElement(Schedules.serializer(), env.data).preferredGroup()
        // "5e" and "j13" are valid class tokens but have no page in any PDF
        for (input in listOf("5e", "j13", "11a", "abc", "", "   ", "10bb")) assertNull(knownClass(input, group), input)
        assertNull(knownClass("7b", emptyList()))
    }
}
