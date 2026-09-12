package lgka

/// Geometric words/lines — shared by the JVM (PDFBox) and Android
/// (PdfBox-Android) extractors.
data class Word(val text: String, val left: Double, val right: Double)

data class Line(val top: Double, val words: List<Word>) {
    /** Words joined with single spaces — used for anchor/regex matching. */
    val text: String = words.joinToString(" ") { it.text }
}

/** One glyph as reported by a PDF text extractor (page coordinates). */
data class Glyph(val text: String, val x: Double, val y: Double, val width: Double)

/**
 * Library-agnostic word/line clustering shared by the JVM (Apache PDFBox)
 * and Android (PdfBox-Android) extractors: glyphs are grouped into visual
 * lines by y (within [LINE_TOLERANCE]), then into words by whitespace or an
 * x-gap above [WORD_GAP]. Identical on both platforms by construction.
 */
object GlyphClustering {
    /** y-distance under which two glyphs belong to the same visual line. */
    const val LINE_TOLERANCE = 2.5

    /** x-gap above which two glyphs are separate words (spaces also split). */
    const val WORD_GAP = 3.0

    fun lines(glyphs: List<Glyph>): List<Line> {
        val byLine = mutableListOf<Pair<Double, MutableList<Glyph>>>()
        for (g in glyphs.sortedBy { it.y }) {
            val line = byLine.lastOrNull()
            if (line != null && kotlin.math.abs(g.y - line.first) <= LINE_TOLERANCE) {
                line.second.add(g)
            } else {
                byLine.add(g.y to mutableListOf(g))
            }
        }
        return byLine.map { (top, lineGlyphs) ->
            val words = mutableListOf<Word>()
            val sb = StringBuilder()
            var left = 0.0
            var right = 0.0
            fun flush() {
                if (sb.isNotEmpty()) {
                    words.add(Word(sb.toString(), left, right))
                    sb.clear()
                }
            }
            for (g in lineGlyphs.sortedBy { it.x }) {
                if (g.text.isBlank()) {
                    flush()
                    continue
                }
                if (sb.isNotEmpty() && g.x - right > WORD_GAP) flush()
                if (sb.isEmpty()) left = g.x
                sb.append(g.text)
                right = g.x + g.width
            }
            flush()
            Line(top, words)
        }.filter { it.words.isNotEmpty() }
    }
}
