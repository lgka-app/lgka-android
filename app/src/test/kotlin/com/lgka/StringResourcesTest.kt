package com.lgka

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every user-visible string exists in every locale (values/ = German, values-en/ = English) with the
 * same format placeholders, so no screen shows a raw key or a crash-prone format mismatch.
 */
class StringResourcesTest {
    private val res = File("src/main/res")

    /** name → placeholders in positional form ("%1$s", "%2$d"), sorted. */
    private fun load(folder: String): Map<String, List<String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
        val result = sortedMapOf<String, List<String>>()
        val strings = document.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val element = strings.item(i) as Element
            if (element.getAttribute("translatable") == "false") continue
            result[element.getAttribute("name")] = placeholders(element.textContent)
        }
        val plurals = document.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val element = plurals.item(i) as Element
            val items = element.getElementsByTagName("item")
            val other = (0 until items.length).map { items.item(it) as Element }.firstOrNull { it.getAttribute("quantity") == "other" }
            result["plurals/" + element.getAttribute("name")] = placeholders(other?.textContent ?: "")
        }
        return result
    }

    private fun placeholders(text: String): List<String> {
        var next = 1
        return Regex("%(\\d+\\$)?([sdf])").findAll(text.replace("%%", "")).map { match ->
            val index = match.groups[1]?.value?.dropLast(1)?.toInt() ?: next++
            "%$index\$${match.groups[2]!!.value}"
        }.toList().sorted()
    }

    @Test
    fun everyStringExistsInEveryLocaleWithTheSamePlaceholders() {
        val base = load("values")
        val locales = res.listFiles { f -> f.name.startsWith("values-") && File(f, "strings.xml").exists() }.orEmpty()
        assertTrue("no translated locale found", locales.isNotEmpty())
        for (locale in locales) {
            val translated = load(locale.name)
            assertEquals("strings missing or extra in ${locale.name}", base.keys, translated.keys)
            for ((name, expected) in base) {
                assertEquals("placeholders of $name in ${locale.name}", expected, translated[name])
            }
        }
    }
}
