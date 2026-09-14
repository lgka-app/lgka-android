package lgka.plan

import kotlinx.serialization.Serializable

/**
 * A piece of text with its box, origin top-left. From a PDF (points, [italic] known)
 * or from text recognition on a photo (0…1 of the image, [confidence] known).
 */
@Serializable
data class TextBox(
    val text: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    /** Recognition confidence 0…1; null for PDF text. */
    val confidence: Double? = null,
    /** Set for PDF text: the glyphs are set in an italic face (Untis prints rooms in italics). */
    val italic: Boolean? = null,
) {
    val midX: Double get() = x + width / 2
    val midY: Double get() = y + height / 2
    val maxX: Double get() = x + width
    val maxY: Double get() = y + height

    /**
     * Splits a multi-word box into one box per word, spreading the width by character count
     * (text recognition sometimes returns "3 3 3" across three table columns as one line).
     */
    fun words(): List<TextBox> {
        val parts = WORD.findAll(text).toList()
        if (parts.size <= 1) return listOf(this)
        val total = text.length.toDouble()
        return parts.map { part ->
            copy(text = part.value, x = x + width * part.range.first / total, width = width * part.value.length / total)
        }
    }
}

private val WORD = Regex("\\S+")

/** Median of the values; null when empty. */
internal fun List<Double>.median(): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    val mid = size / 2
    return if (size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
}

/** Differences between neighbours: [1, 3, 6] → [2, 3]. */
internal fun List<Double>.adjacentDifferences(): List<Double> =
    if (size < 2) emptyList() else (1 until size).map { this[it] - this[it - 1] }
