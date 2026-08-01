package lgka

import com.google.gson.GsonBuilder
import java.io.File

/**
 * Runner: `<mode> <fixturesDir> <outDir> [extra]`
 *  - substitution: every *.pdf -> <name>.json (full plan extraction)
 *  - classindex:   every *.pdf -> class_index_<name>.json (schedule index)
 *  - schedulehtml: every stundenplan_page_*.html -> <name>.json
 *  - news:         every manifest_*.json -> news_<stamp>.json
 *  - events:       every manifest_*.json -> events_<stamp>.json
 *  - weather:      every openmeteo_*.json -> weather_<stamp>.json
 *                  (extra arg = referenceNow ISO from the golden params)
 */
fun main(args: Array<String>) {
    require(args.size >= 3) {
        "usage: lgka-extractor <mode> <fixturesDir> <outDir> [referenceNow]"
    }
    val mode = args[0]
    val fixtures = File(args[1])
    val out = File(args[2]).apply { mkdirs() }
    val gson = GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create()

    fun write(name: String, result: Any?) {
        val target = File(out, "$name.json")
        target.writeText(gson.toJson(result) + "\n")
        println("wrote ${target.name}")
    }

    fun forFiles(glob: (File) -> Boolean, run: (File) -> Pair<String, Any?>) {
        fixtures.listFiles()!!.filter(glob).sortedBy { it.name }.forEach { f ->
            val (name, result) = try {
                run(f)
            } catch (e: Exception) {
                f.nameWithoutExtension to
                    linkedMapOf<String, Any?>("error" to (e.message ?: e.toString()))
            }
            write(name, result)
        }
    }

    fun stamp(manifest: File): String =
        manifest.name.removePrefix("manifest_").removeSuffix(".json")

    when (mode) {
        "substitution" -> forFiles({ it.extension == "pdf" }) { pdf ->
            pdf.nameWithoutExtension to Extractor.extract(extractLines(pdf))
        }
        "classindex" -> forFiles({ it.extension == "pdf" }) { pdf ->
            "class_index_${pdf.nameWithoutExtension}" to
                linkedMapOf<String, Any?>("classIndex5to10" to buildClassIndex(pdf))
        }
        "schedulehtml" -> forFiles({ it.extension == "html" }) { page ->
            page.nameWithoutExtension to ScheduleHtml.parse(page.readText())
        }
        "news" -> forFiles({ it.name.startsWith("manifest_") }) { mf ->
            val m = com.google.gson.JsonParser.parseString(mf.readText()).asJsonObject
            val urlToFile = m.getAsJsonArray("articles").associate {
                it.asJsonObject["url"].asString to it.asJsonObject["file"].asString
            }
            val listHtml = File(fixtures, m["listFile"].asString).readText()
            "news_${stamp(mf)}" to News.run(listHtml, urlToFile) { name ->
                File(fixtures, name).readText()
            }
        }
        "events" -> forFiles({ it.name.startsWith("manifest_") }) { mf ->
            val m = com.google.gson.JsonParser.parseString(mf.readText()).asJsonObject
            val today = java.time.LocalDate.parse(m["today"].asString)
            val htmls = m.getAsJsonArray("weeks").map {
                File(fixtures, it.asJsonObject["file"].asString).readText()
            }
            "events_${stamp(mf)}" to Events.aggregate(htmls, today)
        }
        "weather" -> {
            require(args.size == 4) { "weather mode needs <referenceNow>" }
            val refNow = java.time.LocalDateTime.parse(args[3])
            forFiles({ it.name.startsWith("openmeteo_") }) { snap ->
                "weather_${snap.nameWithoutExtension.removePrefix("openmeteo_")}" to
                    Weather.parse(snap.readText(), refNow)
            }
        }
        else -> error("unknown mode: $mode")
    }
}
