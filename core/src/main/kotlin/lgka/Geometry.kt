package lgka

/// Geometric words/lines — shared by the JVM (PDFBox) and Android
/// (PdfBox-Android) extractors.
data class Word(val text: String, val left: Double, val right: Double)

data class Line(val top: Double, val words: List<Word>) {
    /** Words joined with single spaces — used for anchor/regex matching. */
    val text: String = words.joinToString(" ") { it.text }
}
