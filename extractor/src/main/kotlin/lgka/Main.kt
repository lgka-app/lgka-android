package lgka

import com.google.gson.GsonBuilder
import java.io.File

/** Runner: `<fixturesDir> <outDir>` — extracts every *.pdf into JSON. */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: lgka-extractor <fixturesDir> <outDir>" }
    val fixtures = File(args[0])
    val out = File(args[1]).apply { mkdirs() }
    val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    fixtures.listFiles { f -> f.extension == "pdf" }!!.sortedBy { it.name }.forEach { pdf ->
        val result = try {
            Extractor.extract(extractLines(pdf))
        } catch (e: Exception) {
            linkedMapOf<String, Any?>("error" to (e.message ?: e.toString()))
        }
        val target = File(out, "${pdf.nameWithoutExtension}.json")
        target.writeText(gson.toJson(result) + "\n")
        println("wrote ${target.name}")
    }
}
