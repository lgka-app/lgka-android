package lgka

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File

/**
 * PDF-library boundary: turns page 0 of a PDF into geometric words grouped
 * into visual lines. Everything past this file is library-agnostic, so the
 * Android app only swaps this one file for the pdfbox-android imports.
 */
private class PositionCollector : PDFTextStripper() {
    val positions = mutableListOf<TextPosition>()

    override fun processTextPosition(text: TextPosition) {
        positions.add(text)
        super.processTextPosition(text)
    }
}

/** y-distance under which two glyphs belong to the same visual line. */
private const val LINE_TOLERANCE = 2.5

/** x-gap above which two glyphs are separate words (spaces also split). */
private const val WORD_GAP = 3.0

fun extractLines(file: File): List<Line> {
    Loader.loadPDF(file).use { doc ->
        val collector = PositionCollector()
        collector.startPage = 1
        collector.endPage = 1
        collector.getText(doc)

        // Cluster glyphs into visual lines by y.
        val byLine = mutableListOf<Pair<Double, MutableList<TextPosition>>>()
        for (p in collector.positions.sortedBy { it.yDirAdj }) {
            val line = byLine.lastOrNull()
            if (line != null && Math.abs(p.yDirAdj - line.first) <= LINE_TOLERANCE) {
                line.second.add(p)
            } else {
                byLine.add(p.yDirAdj.toDouble() to mutableListOf(p))
            }
        }

        // Within each line: sort by x, split into words on whitespace or gap.
        return byLine.map { (top, glyphs) ->
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
            for (g in glyphs.sortedBy { it.xDirAdj }) {
                val t = g.unicode
                if (t.isBlank()) {
                    flush()
                    continue
                }
                if (sb.isNotEmpty() && g.xDirAdj - right > WORD_GAP) flush()
                if (sb.isEmpty()) left = g.xDirAdj.toDouble()
                sb.append(t)
                right = (g.xDirAdj + g.widthDirAdj).toDouble()
            }
            flush()
            Line(top, words)
        }.filter { it.words.isNotEmpty() }
    }
}
