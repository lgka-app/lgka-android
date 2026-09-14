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
import android.os.SystemClock
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import lgka.plan.needsRereads
import lgka.plan.recheckRegions
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reads a photo of a Kurswahlprotokoll on the device (ML Kit with the bundled Latin model: nothing
 * leaves the phone). Two passes per photo: the whole sheet, which finds the subject column and the
 * "Summen" row; then the table enlarged on its own, which reads the small bracketed course numbers
 * ("5(3)") far more reliably. The photos of a burst are read at once and merged cell by cell.
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
        /** Milliseconds per phase of the reading: firstReads, parse, rereads (only when read again), total. */
        val timings: Map<String, Long>? = null,
    )

    /** No reread starts later than this after the reading began (the whole burst should take about 6 s). */
    private const val REREAD_DEADLINE_MS = 5_000L

    /** Long side of every image ML Kit gets, in pixels: a whole sheet is shrunk to it, a part enlarged up to it. */
    private const val INPUT_SIDE = 2400.0

    /** Recognitions of the current reading and their summed duration (to tell parallel from queued work). */
    private val recognitions = AtomicInteger()
    private val recognitionMillis = AtomicLong()

    /** One recognizer for every photo and part, on a thread per shot of a burst (more only slows each recognition). */
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.Builder().setExecutor(Executors.newFixedThreadPool(3)).build())
    }

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

    /**
     * Several photos of one sheet: every photo's first reading at once, each parsed, then merged cell by cell.
     * Only when the merged reading is incomplete are the parts each first reading missed read again, and no
     * reread starts after [REREAD_DEADLINE_MS] from the start.
     */
    suspend fun read(images: List<Bitmap>): Result = withContext(Dispatchers.Default) {
        val started = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - started
        val timings = LinkedHashMap<String, Long>()
        recognitions.set(0)
        recognitionMillis.set(0)
        val shots = images.map { image ->
            async {
                val boxes = recognize(image, RectF(0f, 0f, 1f, 1f)).toMutableList()
                // the table enlarged on its own (as far as INPUT_SIDE allows): its width limits the enlargement, so two
                // halves would be enlarged no further and cost another recognition (on a Pixel 7 about a second each)
                tableRegion(boxes)?.let { table -> boxes += recognize(image, table) }
                Shot(boxes, image.height.toDouble() / max(1, image.width))
            }
        }.awaitAll()
        timings["firstReads"] = elapsed()
        var firstError: Exception? = null
        val details = shots.map { shot ->
            try {
                KurswahlParser.parseDetailed(shot.boxes, shot.aspect)
            } catch (e: KurswahlParser.Failure) {
                if (firstError == null) firstError = e
                null
            }
        }
        if (details.all { it == null }) throw firstError ?: KurswahlParser.Failure(KurswahlParser.Failure.Reason.NO_SUBJECTS)
        var merged = KurswahlParser.merge(details.mapNotNull { it?.kurswahl })
        timings["parse"] = elapsed() - timings.getValue("firstReads")
        if (KurswahlParser.needsRereads(merged)) {
            val rereadsStarted = elapsed()
            // what each first reading missed (required subjects, weak Halbjahr columns, the sums) read again in enlarged
            // bands, plain and with raised contrast; those readings only fill cells still unread
            val sheets = images.indices.map { i ->
                async {
                    val detail = details[i] ?: return@async null
                    val extra = mutableListOf<TextBox>()
                    for (region in KurswahlParser.recheckRegions(detail)) {
                        val rect = RectF(region.left.toFloat(), region.top.toFloat(), region.right.toFloat(), region.bottom.toFloat())
                        for (enhance in listOf(false, true)) {
                            if (elapsed() >= REREAD_DEADLINE_MS) break
                            extra += recognize(images[i], rect, enhance)
                        }
                    }
                    if (extra.isEmpty()) {
                        detail.kurswahl
                    } else {
                        try {
                            KurswahlParser.fillGaps(detail.kurswahl, KurswahlParser.parse(shots[i].boxes + extra, shots[i].aspect))
                        } catch (_: KurswahlParser.Failure) {
                            detail.kurswahl
                        }
                    }
                }
            }.awaitAll()
            merged = KurswahlParser.merge(sheets.filterNotNull())
            timings["rereads"] = elapsed() - rereadsStarted
        }
        timings["recognitions"] = recognitions.get().toLong()
        timings["recognitionMs"] = recognitionMillis.get()
        timings["total"] = elapsed()
        Result(merged, shots.first().boxes, shots.first().aspect, shots, timings)
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
        // enlarge small crops so a 2 mm digit is ~40 px tall, shrink the whole sheet: ML Kit gets at most INPUT_SIDE
        val scale = min(3.0, INPUT_SIDE / max(cropped.width, cropped.height))
        val scaled = if (scale > 1.2 || scale < 1.0) cropped.scale((cropped.width * scale).roundToInt(), (cropped.height * scale).roundToInt()) else cropped
        val input = if (enhance) contrasted(scaled) else scaled
        val recognitionStarted = SystemClock.elapsedRealtime()
        val text = recognizer.process(InputImage.fromBitmap(input, 0)).await()
        recognitions.incrementAndGet()
        recognitionMillis.addAndGet(SystemClock.elapsedRealtime() - recognitionStarted)
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
