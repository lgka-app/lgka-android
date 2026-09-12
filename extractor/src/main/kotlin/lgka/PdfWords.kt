package lgka

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File

/**
 * PDF-library boundary: collects page-0 glyph positions with Apache PDFBox
 * and hands them to the shared [GlyphClustering]. The Android app does the
 * same with PdfBox-Android (see app/.../AndroidPdf.kt); the clustering
 * itself lives in :core so both platforms run identical geometry.
 */
private class PositionCollector : PDFTextStripper() {
    val positions = mutableListOf<TextPosition>()

    override fun processTextPosition(text: TextPosition) {
        positions.add(text)
        super.processTextPosition(text)
    }
}

fun extractLines(file: File): List<Line> {
    Loader.loadPDF(file).use { doc ->
        val collector = PositionCollector()
        collector.startPage = 1
        collector.endPage = 1
        collector.getText(doc)
        return GlyphClustering.lines(collector.positions.map {
            Glyph(it.unicode, it.xDirAdj.toDouble(), it.yDirAdj.toDouble(), it.widthDirAdj.toDouble())
        })
    }
}
