package lgka

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities

/**
 * News scraper — Kotlin port of NewsService (news_service.dart), verified
 * against the news goldens.
 *
 * Parity notes vs the Dart `html` package:
 *  - Dart `.text` returns RAW concatenated text nodes -> Jsoup `wholeText()`
 *    (Jsoup `.text()` would collapse whitespace and break the goldens).
 *  - Dart `attributes['x']` is null when absent, "" when present-empty ->
 *    Jsoup needs hasAttr() to distinguish.
 *  - Dart innerHtml escapes only &<> in text and keeps parsed entities like
 *    nbsp as raw unicode -> Jsoup EscapeMode.xhtml + prettyPrint(false).
 */
object News {
    private const val BASE = "https://lessing-gymnasium-karlsruhe.de"

    private fun absolutize(href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("/") -> "$BASE$href"
        else -> "$BASE/cm3/$href"
    }

    private fun configure(doc: Document) {
        // base mode + UTF-8: escapes &<>" and nbsp (as &nbsp;), leaves
        // umlauts raw — matching the Dart html package's serializer.
        doc.outputSettings().prettyPrint(false)
            .escapeMode(Entities.EscapeMode.base)
            .charset("UTF-8")
    }

    /**
     * Dart `.text`: concatenation of descendant TEXT NODES only. Jsoup's
     * wholeText() synthesizes "\n" for <br>, which Dart does not — so this
     * walks text nodes directly.
     */
    private fun rawText(e: Element): String {
        val sb = StringBuilder()
        fun walk(node: org.jsoup.nodes.Node) {
            if (node is org.jsoup.nodes.TextNode) sb.append(node.wholeText)
            for (child in node.childNodes()) walk(child)
        }
        walk(e)
        return sb.toString()
    }

    private fun attrOrNull(e: Element, name: String): String? =
        if (e.hasAttr(name)) e.attr(name) else null

    // ---- list page metadata ------------------------------------------------

    data class Metadata(
        val title: String, val author: String, val description: String,
        val createdDate: String, val parsedDateIso: String?, val views: Int,
        val url: String, val tags: List<String>,
    )

    fun parseListPage(html: String): List<Metadata> {
        val doc = Jsoup.parse(html)
        configure(doc)
        val out = mutableListOf<Metadata>()
        for (item in doc.select(".blog-item")) {
            val titleElement = item.selectFirst("h2 a") ?: continue
            val title = rawText(titleElement).trim()
            val articleUrl = titleElement.attr("href")
            val fullUrl =
                if (articleUrl.startsWith("http")) articleUrl else "$BASE$articleUrl"

            var author = "Unknown"
            item.selectFirst(".createdby")?.let { el ->
                val t = rawText(el)
                if (t.contains("Geschrieben von")) {
                    author = t.replace("Geschrieben von", "").trim()
                        .split("\n").first().trim()
                }
            }

            var createdDate = "Unknown"
            var parsedDateIso: String? = null
            item.selectFirst(".create")?.let { el ->
                val t = rawText(el)
                if (t.contains("Erstellt:")) {
                    createdDate = t.replace("Erstellt:", "").trim()
                        .split("\n").first().trim()
                    parsedDateIso = parseGermanDate(createdDate)
                }
            }

            var views = 0
            item.selectFirst(".hits")?.let { el ->
                val t = rawText(el)
                if (t.contains("Zugriffe:")) {
                    val digits = t.replace("Zugriffe:", "").trim().replace(Regex("[^0-9]"), "")
                    views = digits.toIntOrNull() ?: 0
                }
            }

            var description = ""
            item.selectFirst(".item-content")?.let { el ->
                val ps = el.select("p").map { rawText(it).trim() }.filter { it.isNotEmpty() }
                if (ps.isNotEmpty()) description = ps.take(2).joinToString(" ")
            }

            val tags = item.selectFirst("ul.tags.list-inline")
                ?.select("a")?.map { rawText(it).trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()

            out.add(Metadata(title, author, description, createdDate, parsedDateIso, views, fullUrl, tags))
        }
        return out
    }

    /** DD.MM.YYYY -> midnight Europe/Berlin, Dart TZDateTime-style ISO; null otherwise. */
    private fun parseGermanDate(s: String): String? {
        val parts = s.split(".")
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        if (month < 1 || month > 12 || day < 1 || day > 31) return null
        val zdt = java.time.ZonedDateTime.of(year, month, day, 0, 0, 0, 0,
            java.time.ZoneId.of("Europe/Berlin"))
        val off = zdt.offset.totalSeconds
        val sign = if (off < 0) "-" else "+"
        val abs = Math.abs(off)
        return "%04d-%02d-%02dT00:00:00.000%s%02d%02d".format(
            year, month, day, sign, abs / 3600, (abs % 3600) / 60)
    }

    // ---- article page ------------------------------------------------------

    data class Article(
        val content: String?, val htmlContent: String?,
        val links: List<Map<String, String>>, val standaloneLinks: List<Map<String, String>>,
        val images: List<LinkedHashMap<String, Any?>>, val downloads: List<LinkedHashMap<String, Any?>>,
    )

    private val EMPTY_ARTICLE = Article(null, null, emptyList(), emptyList(), emptyList(), emptyList())

    private fun isStandalone(link: Element, text: String, href: String, fullUrl: String): Boolean {
        val parent = link.parent() ?: return false
        if (parent.tagName() != "p" && parent.tagName() != "div") return false
        val parentText = rawText(parent).trim()
        return text == fullUrl || text == href ||
            (text.startsWith("http") && parentText == text) ||
            (text.startsWith("http") && parentText.length <= text.length + 5)
    }

    fun parseArticle(html: String): Article {
        val doc = Jsoup.parse(html)
        configure(doc)
        val body = doc.selectFirst(".com-content-article__body") ?: return EMPTY_ARTICLE

        // downloads
        val downloads = mutableListOf<LinkedHashMap<String, Any?>>()
        for (dl in body.select("a.doclink-insert")) {
            val href = dl.attr("href")
            if (href.isEmpty()) continue
            val fullUrl = absolutize(href)
            var title = attrOrNull(dl, "data-title") ?: ""
            if (title.isEmpty()) {
                title = rawText(dl).trim()
                    .replace(Regex("\\s*\\([^)]+\\)\\s*$"), "").trim()
            }
            var fileType = "document"
            dl.selectFirst("span[class*=k-icon-document]")?.let { span ->
                for (cn in span.classNames()) {
                    if (cn.startsWith("k-icon-document-")) {
                        fileType = cn.removePrefix("k-icon-document-")
                        break
                    }
                }
            }
            if (fileType == "document") {
                dl.selectFirst("span.k-visually-hidden")?.let { span ->
                    val t = rawText(span).trim().lowercase()
                    if (t.isNotEmpty()) fileType = t
                }
            }
            var size: String? = null
            Regex("\\(([^)]+)\\)").find(rawText(dl))?.let { m ->
                val candidate = m.groupValues[1].trim()
                if (Regex("\\d+\\s*(MB|KB|GB|B|bytes?)", RegexOption.IGNORE_CASE)
                        .containsMatchIn(candidate)) {
                    size = candidate
                }
            }
            val entry = linkedMapOf<String, Any?>(
                "title" to title, "url" to fullUrl, "file_type" to fileType)
            if (size != null) entry["size"] = size
            downloads.add(entry)
        }

        // links (embedded vs standalone)
        val embedded = mutableListOf<Map<String, String>>()
        val standalone = mutableListOf<Map<String, String>>()
        for (link in body.select("a")) {
            if (link.classNames().contains("doclink-insert")) continue
            val href = attrOrNull(link, "href") ?: continue
            val text = rawText(link).trim()
            if (href.isEmpty() || text.isEmpty()) continue
            val fullUrl = absolutize(href)
            val entry = mapOf("text" to text, "url" to fullUrl)
            if (isStandalone(link, text, href, fullUrl)) standalone.add(entry)
            else embedded.add(entry)
        }

        // images: galleries first, then non-gallery <img>
        val images = mutableListOf<LinkedHashMap<String, Any?>>()
        for (gallery in body.select(".sigFreeContainer")) {
            for (link in gallery.select("a.sigFreeLink")) {
                val imageUrl = attrOrNull(link, "href")
                if (imageUrl.isNullOrEmpty()) continue
                val thumbnailUrl = attrOrNull(link, "data-thumb")
                val img = link.selectFirst("img")
                val alt = img?.let { attrOrNull(it, "alt") ?: attrOrNull(it, "title") }
                val entry = linkedMapOf<String, Any?>("url" to absolutize(imageUrl))
                if (!thumbnailUrl.isNullOrEmpty()) entry["thumbnail_url"] = absolutize(thumbnailUrl)
                if (alt != null) entry["alt"] = alt
                images.add(entry)
            }
        }
        for (img in body.select("img")) {
            if (img.classNames().contains("sigFreeImg")) continue
            val src = attrOrNull(img, "src")
            if (src.isNullOrEmpty()) continue
            val fullImageUrl = absolutize(src)
            if (images.none { it["url"] == fullImageUrl }) {
                val entry = linkedMapOf<String, Any?>("url" to fullImageUrl)
                attrOrNull(img, "alt")?.let { entry["alt"] = it }
                images.add(entry)
            }
        }

        // cloned body with downloads + standalone links removed
        val clone = body.clone()
        clone.select("a.doclink-insert").forEach { it.remove() }
        for (link in clone.select("a:not(.doclink-insert)")) {
            val href = attrOrNull(link, "href") ?: continue
            val text = rawText(link).trim()
            if (href.isEmpty() || text.isEmpty()) continue
            val fullUrl = absolutize(href)
            if (isStandalone(link, text, href, fullUrl)) link.remove()
        }

        fun cleanHtml(h: String): String =
            h.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").trim()

        val paragraphs = clone.select("p")
        val htmlContent: String = if (paragraphs.isEmpty()) {
            cleanHtml(clone.html())
        } else {
            paragraphs.map { cleanHtml(it.html()) }.filter { it.isNotEmpty() }
                .joinToString("\n\n")
        }
        val content: String = if (paragraphs.isEmpty()) {
            rawText(clone).trim()
        } else {
            paragraphs.map { rawText(it).trim() }.filter { it.isNotEmpty() }
                .joinToString("\n\n")
        }

        return Article(content, htmlContent, embedded, standalone, images, downloads)
    }

    // ---- aggregation -------------------------------------------------------

    /** Full pipeline over local fixtures; urlToFile maps article URL -> html. */
    fun run(listHtml: String, urlToFile: Map<String, String>, readFile: (String) -> String):
        List<LinkedHashMap<String, Any?>> {
        val metadata = parseListPage(listHtml)
        val events = metadata.map { md ->
            val article = urlToFile[md.url]?.let { parseArticle(readFile(it)) } ?: EMPTY_ARTICLE
            val e = linkedMapOf<String, Any?>(
                "title" to md.title,
                "author" to md.author,
                "description" to md.description,
            )
            if (article.content != null) e["content"] = article.content
            if (article.htmlContent != null) e["html_content"] = article.htmlContent
            e["created_date"] = md.createdDate
            e["views"] = md.views
            e["url"] = md.url
            e["links"] = article.links
            e["standalone_links"] = article.standaloneLinks
            e["images"] = article.images
            e["downloads"] = article.downloads
            e["tags"] = md.tags
            e["parsed_date"] = md.parsedDateIso
            e
        }
        // Sort newest-first by parsed_date; entries without a date keep their
        // relative order (mirrors NewsService's comparator; Kotlin's sort is
        // stable, which matches the observed golden order).
        return events.sortedWith { a, b ->
            val pa = a["parsed_date"] as String?
            val pb = b["parsed_date"] as String?
            when {
                pa != null && pb != null -> pb.compareTo(pa)
                pa != null -> -1
                pb != null -> 1
                else -> 0
            }
        }
    }
}
