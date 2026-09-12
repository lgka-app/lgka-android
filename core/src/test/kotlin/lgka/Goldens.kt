package lgka

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlin.test.fail

/**
 * Locates the lgka-app/verification checkout (CI clones it next to the repo
 * as `verification`; `lgka-verification` also works; LGKA_VERIFICATION_DIR
 * overrides) and compares parser output with the goldens the way the Rust
 * comparator does: numbers as doubles, null == null, key order irrelevant.
 */
object Goldens {
    val root: File? by lazy {
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

    fun file(relative: String): File = File(root ?: fail("verification checkout not found"), relative)

    fun json(relative: String): JsonObject = JsonParser.parseString(file(relative).readText()).asJsonObject

    fun list(dir: String, prefix: String = "", suffix: String = ".json"): List<String> =
        (file(dir).listFiles() ?: emptyArray())
            .map { it.name }.filter { it.startsWith(prefix) && it.endsWith(suffix) }.sorted()

    private val gson = com.google.gson.GsonBuilder().serializeNulls().create()

    fun diff(golden: JsonElement, actual: JsonElement, path: String, out: MutableList<String>) {
        when {
            golden.isJsonObject && actual.isJsonObject -> {
                val g = golden.asJsonObject; val a = actual.asJsonObject
                for (k in (g.keySet() + a.keySet()).sorted()) {
                    val p = if (path.isEmpty()) k else "$path.$k"
                    when {
                        g.has(k) && a.has(k) -> diff(g[k], a[k], p, out)
                        g.has(k) -> out.add("$p: missing in actual")
                        else -> out.add("$p: unexpected in actual")
                    }
                }
            }
            golden.isJsonArray && actual.isJsonArray -> {
                val g = golden.asJsonArray; val a = actual.asJsonArray
                if (g.size() != a.size()) out.add("$path.length: ${g.size()} vs ${a.size()}")
                g.zip(a).forEachIndexed { i, (gv, av) -> diff(gv, av, "$path[$i]", out) }
            }
            golden.isJsonNull && actual.isJsonNull -> {}
            golden.isJsonPrimitive && actual.isJsonPrimitive -> {
                val g = golden.asJsonPrimitive; val a = actual.asJsonPrimitive
                if (g.isNumber && a.isNumber) {
                    if (g.asDouble != a.asDouble) out.add("$path: $g vs $a")
                } else if (g != a) out.add("$path: ${g.toString().take(80)} vs ${a.toString().take(80)}")
            }
            else -> out.add("$path: type mismatch ${golden.javaClass.simpleName} vs ${actual.javaClass.simpleName}")
        }
    }

    fun assertMatches(golden: JsonElement, actual: Any?, name: String) {
        val actualJson = gson.toJsonTree(actual)
        val diffs = mutableListOf<String>()
        diff(golden, actualJson, "", diffs)
        if (diffs.isNotEmpty()) fail("$name:\n" + diffs.take(10).joinToString("\n"))
    }

    fun JsonArray.zip(other: JsonArray): List<Pair<JsonElement, JsonElement>> =
        (0 until minOf(size(), other.size())).map { get(it) to other.get(it) }
}
