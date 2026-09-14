package com.lgka

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import lgka.plan.Kurswahl
import lgka.plan.KurswahlParser
import lgka.plan.SchoolReference
import lgka.plan.Stufenplan
import lgka.plan.StufenplanParser
import lgka.plan.TextBox
import lgka.plan.fillGaps
import lgka.plan.recheckRegions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reads a photo of a Kurswahlprotokoll on the device (ML Kit with the bundled Latin model: nothing
 * leaves the phone). Two passes per photo: the whole sheet, which finds the subject column and the
 * "Summen" row; then the table in two overlapping halves, each enlarged, which reads the small
 * bracketed course numbers ("5(3)") far more reliably. A burst of photos is merged cell by cell.
 */
object KurswahlScanner {
    @Serializable
    data class Shot(val boxes: List<TextBox>, val aspect: Double)

    @Serializable
    data class Result(
        val kurswahl: Kurswahl,
        /** Every recognised box of the first photo, 0…1 of that image. */
        val boxes: List<TextBox>,
        /** Image height ÷ width of the first photo. */
        val aspect: Double,
        val shots: List<Shot>? = null,
    )

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Decodes a picked photo upright (EXIF applied), at most [maxPixels] on the long side, as a software bitmap. */
    fun decode(resolver: ContentResolver, uri: Uri, maxPixels: Int = 4096): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longSide = max(info.size.width, info.size.height)
            if (longSide > maxPixels) {
                val scale = maxPixels.toFloat() / longSide
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Several photos of one sheet: each read on its own, then merged cell by cell. */
    suspend fun read(images: List<Bitmap>): Result = withContext(Dispatchers.Default) {
        val shots = mutableListOf<Shot>()
        val sheets = mutableListOf<Kurswahl>()
        var firstError: Exception? = null
        for (image in images) {
            val boxes = recognize(image, RectF(0f, 0f, 1f, 1f)).toMutableList()
            tableRegion(boxes)?.let { table ->
                // the table in two overlapping halves, each enlarged more than the whole table could be
                boxes += recognize(image, RectF(table.left, table.top, table.right, table.top + table.height() * 0.56f))
                boxes += recognize(image, RectF(table.left, table.top + table.height() * 0.44f, table.right, table.bottom))
            }
            val aspect = image.height.toDouble() / max(1, image.width)
            shots += Shot(boxes, aspect)
            try {
                val detail = KurswahlParser.parseDetailed(boxes, aspect)
                var sheet = detail.kurswahl
                // what the first reading missed (required subjects, the plan's Halbjahr column, the sums) read again
                // in enlarged bands, plain and with raised contrast; those readings only fill cells still unread
                val regions = KurswahlParser.recheckRegions(detail)
                if (regions.isNotEmpty()) {
                    val extra = mutableListOf<TextBox>()
                    for (region in regions) {
                        val rect = RectF(region.left.toFloat(), region.top.toFloat(), region.right.toFloat(), region.bottom.toFloat())
                        extra += recognize(image, rect)
                        extra += recognize(image, rect, enhance = true)
                    }
                    try {
                        sheet = KurswahlParser.fillGaps(sheet, KurswahlParser.parse(boxes + extra, aspect))
                    } catch (_: KurswahlParser.Failure) {
                    }
                }
                sheets += sheet
            } catch (e: KurswahlParser.Failure) {
                if (firstError == null) firstError = e
            }
        }
        if (sheets.isEmpty()) throw firstError ?: KurswahlParser.Failure(KurswahlParser.Failure.Reason.NO_SUBJECTS)
        Result(KurswahlParser.merge(sheets), shots.first().boxes, shots.first().aspect, shots)
    }

    /**
     * Recognised text of a region (0…1, origin top-left), mapped back to whole-image coordinates.
     * [enhance]: grey with raised contrast, for faint print and glare.
     */
    private suspend fun recognize(image: Bitmap, region: RectF, enhance: Boolean = false): List<TextBox> {
        val left = (region.left * image.width).roundToInt().coerceIn(0, image.width - 1)
        val top = (region.top * image.height).roundToInt().coerceIn(0, image.height - 1)
        val right = (region.right * image.width).roundToInt().coerceIn(left + 1, image.width)
        val bottom = (region.bottom * image.height).roundToInt().coerceIn(top + 1, image.height)
        val cropped = Bitmap.createBitmap(image, left, top, right - left, bottom - top)
        // enlarge small crops so a 2 mm digit is ~40 px tall; cap the size for memory
        val scale = min(3.0, 3600.0 / max(cropped.width, cropped.height))
        val scaled = if (scale > 1.2) cropped.scale((cropped.width * scale).roundToInt(), (cropped.height * scale).roundToInt()) else cropped
        val input = if (enhance) contrasted(scaled) else scaled
        val text = recognizer.process(InputImage.fromBitmap(input, 0)).await()
        val regionWidth = (right - left).toDouble() / image.width
        val regionHeight = (bottom - top).toDouble() / image.height
        val x0 = left.toDouble() / image.width
        val y0 = top.toDouble() / image.height
        return text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            TextBox(line.text,
                x = x0 + box.left.toDouble() / input.width * regionWidth,
                y = y0 + box.top.toDouble() / input.height * regionHeight,
                width = box.width().toDouble() / input.width * regionWidth,
                height = box.height().toDouble() / input.height * regionHeight,
                confidence = line.confidence.toDouble())
        }
    }

    /** Grey, contrast raised around mid grey: faint digits get darker, paper and glare lighter. */
    private fun contrasted(bitmap: Bitmap): Bitmap {
        val contrast = 1.8f
        val offset = 128f * (1 - contrast)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        matrix.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )))
        val result = createBitmap(bitmap.width, bitmap.height)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return result
    }

    /** From the subject column to past the fourth Halbjahr column, first subject to "Summen". */
    private fun tableRegion(boxes: List<TextBox>): RectF? {
        val keys = SchoolReference.subjects.map { it.key.lowercase() }.toSet()
        val subjects = boxes.filter { it.text.lowercase() in keys }
        if (subjects.size < 5) return null
        val columnX = subjects.map { it.midX }.sorted()[subjects.size / 2]
        val column = subjects.filter { abs(it.midX - columnX) < 0.05 }
        val top = column.minOfOrNull { it.y } ?: return null
        val summen = boxes.firstOrNull { it.text.lowercase().startsWith("summen") }
        val bottom = max(summen?.maxY ?: 0.0, column.maxOf { it.maxY }) + 0.03
        return RectF(max(0.0, columnX - 0.06).toFloat(), max(0.0, top - 0.03).toFloat(),
            min(1.0, columnX + 0.56).toFloat(), min(1.0, bottom).toFloat())
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}

/**
 * Positioned words of the first page of a PDF for [StufenplanParser] (points, origin top-left, italic
 * known), read with pdfbox-android: Android has no PDF text API of its own.
 */
object PdfText {
    fun stufenplan(file: File): Stufenplan = StufenplanParser.parse(words(file))

    fun words(file: File): List<TextBox> = PDDocument.load(file).use { document ->
        val collector = WordCollector()
        collector.startPage = 1
        collector.endPage = 1
        collector.getText(document)
        collector.words
    }

    /** Groups the text positions of every drawn string into words: split at spaces and at visible gaps. */
    private class WordCollector : PDFTextStripper() {
        val words = mutableListOf<TextBox>()

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            var run = mutableListOf<TextPosition>()
            fun flush() {
                if (run.isEmpty()) return
                val first = run.first()
                val last = run.last()
                val top = run.minOf { it.yDirAdj - it.heightDir }
                val bottom = run.maxOf { it.yDirAdj }
                val fontName = first.font?.name ?: ""
                words += TextBox(run.joinToString("") { it.unicode ?: "" },
                    x = first.xDirAdj.toDouble(), y = top.toDouble(),
                    width = (last.xDirAdj + last.widthDirAdj - first.xDirAdj).toDouble(), height = (bottom - top).toDouble(),
                    italic = fontName.contains("Italic") || fontName.contains("Oblique"))
                run = mutableListOf()
            }
            for (position in textPositions ?: return) {
                if (position.unicode.isNullOrBlank()) {
                    flush()
                    continue
                }
                val previous = run.lastOrNull()
                if (previous != null && position.xDirAdj - (previous.xDirAdj + previous.widthDirAdj) > previous.fontSizeInPt * 0.3f) flush()
                run += position
            }
            flush()
        }
    }
}
