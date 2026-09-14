package lgka.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Recorded live responses of api.lgka.app (2026-09-12) must decode with the app's models. */
class ModelsTest {
    @Test
    fun substitutions() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("substitutions.json"))
        assertEquals("substitutions", env.name)
        val data = ApiJson.decodeFromJsonElement(Substitutions.serializer(), env.data)
        val today = assertNotNull(data.today)
        assertEquals("Montag", today.meta.weekday)
        assertEquals("14.09.2026", today.meta.date)
        assertEquals(21, today.plan.entries.size)
        assertEquals(2, today.pdf.pageCount)
        assertTrue(today.canDisplay)
        val first = today.plan.entries.first()
        assertEquals("Veranst.", first.type)
        assertEquals(listOf("5a", "5b", "5c"), first.classes)
        assertEquals("vor Einschulung", first.note)
        assertEquals(1, today.plan.entriesFor("6a").size)
        assertEquals(38, today.plan.footer?.calendarWeek)
        assertEquals(2, today.pages.size)
    }

    @Test
    fun schedules() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("schedules.json"))
        val data = ApiJson.decodeFromJsonElement(Schedules.serializer(), env.data)
        assertEquals(3, data.items.size)
        val lower = data.items.first { it.gradeLevel == "Klassen 5-10" }
        assertTrue(lower.available)
        assertEquals(21, lower.pdf?.pageCount)
        assertEquals(1, lower.classIndex["5a"]) // real 1-based page, not the old pageIndex + 2
        assertEquals(18, lower.classIndex["10a"])
        assertEquals(listOf("J11", "J12"), data.items.filter { it.gradeLevel != "Klassen 5-10" }.map { it.gradeLevel })
        assertEquals(1, data.items.first { it.gradeLevel == "J11" }.classIndex["j11"])
    }

    @Test
    fun news() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("news.json"))
        val data = ApiJson.decodeFromJsonElement(News.serializer(), env.data)
        assertEquals(20, data.articles.size)
        val newest = data.articles.first()
        assertEquals("Stundenpläne", newest.title)
        assertEquals(1020, newest.id)
        assertTrue(newest.publishedAt!!.startsWith("2026-09-09"))
        assertEquals("hier", newest.links.single().text)
        val withGallery = data.articles.first { it.images.isNotEmpty() }
        assertNotNull(withGallery.images.first().thumbnailUrl)
        val withDownload = data.articles.first { it.downloads.isNotEmpty() }
        assertTrue(withDownload.downloads.first().url.startsWith("https://"))
    }

    @Test
    fun events() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("events.json"))
        val data = ApiJson.decodeFromJsonElement(Events.serializer(), env.data)
        assertEquals(20, data.events.size)
        assertEquals(SchoolEvent("2026-09-14", "07:45", "J11: Oberstufeninfo"), data.events.first())
        assertTrue(data.events.any { it.time == null }) // all-day events
        assertEquals(data.events.map { it.date }, data.events.map { it.date }.sorted())
    }

    @Test
    fun weather() {
        val env = ApiJson.decodeFromString(ResourceEnvelope.serializer(), Fixtures.text("weather.json"))
        val w = ApiJson.decodeFromJsonElement(Weather.serializer(), env.data)
        assertEquals("open-meteo", w.source)
        assertFalse(w.fromSchoolStation)
        assertEquals("open-meteo", w.current.provider)
        assertEquals(72, w.hourly.size)
        assertEquals(3, w.daily.size)
        assertFalse(w.station.healthy)
        assertTrue(w.station.reason!!.contains("old"))
        assertEquals("m/s", w.station.units["windSpeed"])
        assertTrue(w.attribution.any { it.contains("Open-Meteo") })
        assertTrue(w.current.temp > -50 && w.current.temp < 60)
    }

    @Test
    fun kollegiumFeedsTheTeacherDirectory() {
        val response = ApiJson.decodeFromString(SyncResponse.serializer(), Fixtures.text("sync_embed.json"))
        val data = ApiJson.decodeFromJsonElement(Kollegium.serializer(), response.resources["kollegium"]!!.data!!)
        assertEquals("2026/2027", data.schoolYear)
        val head = data.staff.first()
        assertEquals("Dr.", head.title)
        assertEquals(listOf("M", "Ph"), head.subjects)
        assertEquals("schulleitung", head.role)
        // a role the app doesn't know stays as sent; the UI treats it like sonstige
        assertEquals("hausmeister", data.staff.last().role)

        try {
            TeacherDirectory.update(data.staff)
            assertEquals("Dr. Erika Muster", TeacherDirectory.name("Mus"))
            assertEquals("Muster", TeacherDirectory.lastName("Mus"))
            assertEquals("Neumann-Test", TeacherDirectory.lastName("Neu"))
            // a code the list doesn't know: no name, shown as the code
            assertNull(TeacherDirectory.name("Xyz"))
            assertEquals("Xyz", TeacherDirectory.lastName("Xyz"))
        } finally {
            TeacherDirectory.update(emptyList())
        }
    }

    @Test
    fun unknownKeysAndNullsAreTolerated() {
        val json = """{"source":"school","current":{"temp":12.5,"provider":"school","brandNew":true},"hourly":[],"daily":[],"station":{"healthy":true,"reason":null},"attribution":[],"future":{"x":1}}"""
        val w = ApiJson.decodeFromString(Weather.serializer(), json)
        assertTrue(w.fromSchoolStation)
        assertEquals(12.5, w.current.temp)
        assertTrue(w.station.healthy)
    }
}
