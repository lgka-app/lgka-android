package lgka

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertTrue

class GoldenParityTest {
    @BeforeEach
    fun requireGoldens() = assumeTrue(Goldens.root != null, "lgka-app/verification checkout not found")

    @Test
    fun schedulePage() {
        val names = Goldens.list("goldens/schedule", prefix = "stundenplan_page_")
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val g = Goldens.json("goldens/schedule/$name")
            val html = Goldens.file(g["input"].asJsonObject["file"].asString).readText()
            Goldens.assertMatches(g["expected"], ScheduleHtml.parse(html), name)
        }
    }

    @Test
    fun news() {
        val names = Goldens.list("goldens/news", prefix = "news_")
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val g = Goldens.json("goldens/news/$name")
            val manifestPath = g["input"].asJsonObject["manifest"].asString
            val manifest = Goldens.json(manifestPath)
            val dir = File(manifestPath).parent
            val urlToFile = manifest.getAsJsonArray("articles").associate {
                it.asJsonObject["url"].asString to it.asJsonObject["file"].asString
            }
            val listHtml = Goldens.file("$dir/${manifest["listFile"].asString}").readText()
            val result = News.run(listHtml, urlToFile) { Goldens.file("$dir/$it").readText() }
            Goldens.assertMatches(g["expected"], result, name)
        }
    }

    @Test
    fun events() {
        val names = Goldens.list("goldens/events", prefix = "events_")
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val g = Goldens.json("goldens/events/$name")
            val manifestPath = g["input"].asJsonObject["manifest"].asString
            val manifest = Goldens.json(manifestPath)
            val dir = File(manifestPath).parent
            val htmls = manifest.getAsJsonArray("weeks").map {
                Goldens.file("$dir/${it.asJsonObject["file"].asString}").readText()
            }
            val today = LocalDate.parse(g["params"].asJsonObject["today"].asString)
            Goldens.assertMatches(g["expected"], Events.aggregate(htmls, today), name)
        }
    }

    @Test
    fun weather() {
        val names = Goldens.list("goldens/weather", prefix = "weather_")
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val g = Goldens.json("goldens/weather/$name")
            val json = Goldens.file(g["input"].asJsonObject["file"].asString).readText()
            val refNow = LocalDateTime.parse(g["params"].asJsonObject["referenceNow"].asString)
            Goldens.assertMatches(g["expected"], Weather.parse(json, refNow), name)
        }
    }
}
