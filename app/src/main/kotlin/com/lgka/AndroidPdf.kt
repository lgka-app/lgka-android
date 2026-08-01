package com.lgka

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import lgka.Line
import lgka.Word
import java.io.File

/// PdfBox-Android implementations of the PDF-library boundary — the designed
/// swap for extractor/PdfWords.kt + ClassIndex.kt (same API, com.tom_roush
/// package). Geometry/word-clustering logic is IDENTICAL to the verified JVM
/// implementation.

private const val LINE_TOLERANCE = 2.5
private const val WORD_GAP = 3.0

private class PositionCollector : PDFTextStripper() {
    val positions = mutableListOf<TextPosition>()
    override fun processTextPosition(text: TextPosition) {
        positions.add(text)
        super.processTextPosition(text)
    }
}

fun extractLinesAndroid(file: File): List<Line> {
    PDDocument.load(file).use { doc ->
        val collector = PositionCollector()
        collector.startPage = 1
        collector.endPage = 1
        collector.getText(doc)

        val byLine = mutableListOf<Pair<Double, MutableList<TextPosition>>>()
        for (p in collector.positions.sortedBy { it.yDirAdj }) {
            val line = byLine.lastOrNull()
            if (line != null && Math.abs(p.yDirAdj - line.first) <= LINE_TOLERANCE) {
                line.second.add(p)
            } else {
                byLine.add(p.yDirAdj.toDouble() to mutableListOf(p))
            }
        }

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
                if (t.isBlank()) { flush(); continue }
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

/// Class-to-page index — identical contract to the verified JVM ClassIndex.
fun buildClassIndexAndroid(file: File): Map<String, Int> {
    val classes = (5..10).flatMap { grade -> "abcde".map { "$grade$it" } }
    val index = sortedMapOf<String, Int>()
    PDDocument.load(file).use { doc ->
        val stripper = PDFTextStripper()
        for (page in 1..doc.numberOfPages) {
            stripper.startPage = page
            stripper.endPage = page
            val text = try {
                stripper.getText(doc).lowercase()
            } catch (e: Exception) {
                continue
            }
            for (c in classes) {
                if (!index.containsKey(c) && text.contains(c)) {
                    index[c] = page + 1 // (page-1) zero-based + 2
                }
            }
        }
    }
    return index
}

/// Per-page lowercased text — used by the PDF viewer's search.
fun pageTextsAndroid(file: File): List<String> {
    PDDocument.load(file).use { doc ->
        val stripper = PDFTextStripper()
        return (1..doc.numberOfPages).map { page ->
            stripper.startPage = page
            stripper.endPage = page
            try { stripper.getText(doc).lowercase() } catch (e: Exception) { "" }
        }
    }
}
