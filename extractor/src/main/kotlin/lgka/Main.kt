package lgka

import com.google.gson.GsonBuilder
import java.io.File

/**
 * Runner: `<substitution|classindex> <fixturesDir> <outDir>`
 *  - substitution: every *.pdf -> <name>.json (full plan extraction)
 *  - classindex:   every *.pdf -> class_index_<name>.json (schedule index)
 */
fun main(args: Array<String>) {
    require(args.size == 3) {
        "usage: lgka-extractor <substitution|classindex> <fixturesDir> <outDir>"
    }
    val mode = args[0]
    val fixtures = File(args[1])
    val out = File(args[2]).apply { mkdirs() }
    val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    fixtures.listFiles { f -> f.extension == "pdf" }!!.sortedBy { it.name }.forEach { pdf ->
        val (name, result) = try {
            when (mode) {
                "substitution" -> pdf.nameWithoutExtension to
                    Extractor.extract(extractLines(pdf))
                "classindex" -> "class_index_${pdf.nameWithoutExtension}" to
                    linkedMapOf<String, Any?>("classIndex5to10" to buildClassIndex(pdf))
                else -> error("unknown mode: $mode")
            }
        } catch (e: Exception) {
            pdf.nameWithoutExtension to
                linkedMapOf<String, Any?>("error" to (e.message ?: e.toString()))
        }
        val target = File(out, "$name.json")
        target.writeText(gson.toJson(result) + "\n")
        println("wrote ${target.name}")
    }
}
