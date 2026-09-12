package lgka

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/** PDF-based goldens (substitution plans, class indexes) through Apache PDFBox. */
class PdfGoldenTest {
    private val root: File? by lazy {
        System.getenv("LGKA_VERIFICATION_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: sequenceOf("verification", "lgka-verification")
                .map { File(repoRoot().parentFile, it) }
                .firstOrNull { File(it, "goldens").isDirectory }
    }

    private fun repoRoot(): File {
        var dir = File("").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) dir = dir.parentFile
        return dir
    }

    private fun file(rel: String) = File(root ?: fail("verification checkout not found"), rel)

    @BeforeEach
    fun requireGoldens() = assumeTrue(root != null, "lgka-app/verification checkout not found")

    private fun compare(golden: com.google.gson.JsonElement, actual: Any?, name: String) {
        val diffs = mutableListOf<String>()
        diffJson(golden, com.google.gson.GsonBuilder().serializeNulls().create().toJsonTree(actual), "", diffs)
        if (diffs.isNotEmpty()) fail("$name:\n" + diffs.take(10).joinToString("\n"))
    }

    private fun diffJson(g: com.google.gson.JsonElement, a: com.google.gson.JsonElement, path: String, out: MutableList<String>) {
        when {
            g.isJsonObject && a.isJsonObject -> for (k in (g.asJsonObject.keySet() + a.asJsonObject.keySet()).sorted()) {
                val p = if (path.isEmpty()) k else "$path.$k"
                when {
                    g.asJsonObject.has(k) && a.asJsonObject.has(k) -> diffJson(g.asJsonObject[k], a.asJsonObject[k], p, out)
                    g.asJsonObject.has(k) -> out.add("$p: missing in actual")
                    else -> out.add("$p: unexpected in actual")
                }
            }
            g.isJsonArray && a.isJsonArray -> {
                if (g.asJsonArray.size() != a.asJsonArray.size()) out.add("$path.length")
                for (i in 0 until minOf(g.asJsonArray.size(), a.asJsonArray.size()))
                    diffJson(g.asJsonArray[i], a.asJsonArray[i], "$path[$i]", out)
            }
            g.isJsonNull && a.isJsonNull -> {}
            g.isJsonPrimitive && a.isJsonPrimitive ->
                if (g.asJsonPrimitive.isNumber && a.asJsonPrimitive.isNumber) {
                    if (g.asDouble != a.asDouble) out.add("$path: $g vs $a")
                } else if (g != a) out.add("$path: $g vs $a")
            else -> out.add("$path: type mismatch")
        }
    }

    @Test
    fun substitutionPlans() {
        val names = (file("goldens/substitution").listFiles() ?: emptyArray())
            .map { it.name }.filter { it.endsWith(".v2.json") }.sorted()
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val g = JsonParser.parseString(file("goldens/substitution/$name").readText()).asJsonObject
            val pdf = file(g["input"].asJsonObject["file"].asString)
            compare(g["expected"], Extractor.extract(extractLines(pdf)), name)
        }
    }

    @Test
    fun classIndexes() {
        val names = (file("goldens/schedule").listFiles() ?: emptyArray())
            .map { it.name }.filter { it.startsWith("class_index_") && it.endsWith(".json") }.sorted()
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val g = JsonParser.parseString(file("goldens/schedule/$name").readText()).asJsonObject
            val pdf = file(g["input"].asJsonObject["file"].asString)
            compare(g["expected"], mapOf("classIndex5to10" to buildClassIndex(pdf)), name)
        }
    }
}
