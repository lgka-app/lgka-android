package lgka

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Schedule (Stundenplan) class-to-page index — Kotlin port of the app's
 * `_buildClassIndexInIsolate` (schedule_provider.dart), verified against
 * the class_index goldens in lgka-app/verification.
 *
 * Contract: scan each page's lowercased text for classes 5a-10e; the first
 * page containing the class string wins; the stored page number is
 * zero-based pageIndex + 2 (1-based + cover offset, matching the app's PDF
 * viewer navigation). j11/j12 are NOT parsed — the app hardcodes
 * {j11: 2, j12: 3} for the J11/J12 PDF.
 */
fun buildClassIndex(file: File): Map<String, Int> {
    val classes = (5..10).flatMap { grade -> "abcde".map { "$grade$it" } }
    val index = sortedMapOf<String, Int>()
    Loader.loadPDF(file).use { doc ->
        val stripper = PDFTextStripper()
        for (page in 1..doc.numberOfPages) { // PDFBox pages are 1-based
            stripper.startPage = page
            stripper.endPage = page
            val text = try {
                stripper.getText(doc).lowercase()
            } catch (e: Exception) {
                continue // skip pages with extraction errors, like the app
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
