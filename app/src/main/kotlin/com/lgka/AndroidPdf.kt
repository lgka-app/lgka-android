package com.lgka

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import lgka.Glyph
import lgka.GlyphClustering
import lgka.Line
import java.io.File

/**
 * PdfBox-Android side of the PDF-library boundary: glyph positions in, the
 * shared [GlyphClustering] from :core does the rest — no duplicated geometry.
 */
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
        return GlyphClustering.lines(collector.positions.map {
            Glyph(it.unicode, it.xDirAdj.toDouble(), it.yDirAdj.toDouble(), it.widthDirAdj.toDouble())
        })
    }
}

/** Per-page lowercased text (1-based PDFBox pages). */
private fun pageTexts(doc: PDDocument): List<String> {
    val stripper = PDFTextStripper()
    return (1..doc.numberOfPages).map { page ->
        stripper.startPage = page
        stripper.endPage = page
        try { stripper.getText(doc).lowercase() } catch (e: Exception) { "" }
    }
}

/** Class-to-page index — identical contract to the verified JVM ClassIndex. */
fun buildClassIndexAndroid(file: File): Map<String, Int> {
    val classes = (5..10).flatMap { grade -> "abcde".map { "$grade$it" } }
    val index = sortedMapOf<String, Int>()
    PDDocument.load(file).use { doc ->
        pageTexts(doc).forEachIndexed { pageIndex, text ->
            for (c in classes) {
                if (!index.containsKey(c) && text.contains(c)) index[c] = pageIndex + 2
            }
        }
    }
    return index
}

/**
 * Jahrgang-to-page index: every page whose text carries a "J11"/"J12"/"J13"… header
 * (Untis prints the Jahrgang where class pages print "5b"). Same page contract as
 * [buildClassIndexAndroid]; the first page wins. Discovered, never hardcoded.
 */
fun buildJahrgangIndexAndroid(file: File): Map<String, Int> {
    val marker = Regex("""\bj(1\d)\b""")
    val index = linkedMapOf<String, Int>()
    PDDocument.load(file).use { doc ->
        pageTexts(doc).forEachIndexed { pageIndex, text ->
            for (m in marker.findAll(text)) index.putIfAbsent("j${m.groupValues[1]}", pageIndex + 2)
        }
    }
    return index
}

/** Per-page lowercased text — used by the PDF viewer's search. */
fun pageTextsAndroid(file: File): List<String> = PDDocument.load(file).use { pageTexts(it) }
