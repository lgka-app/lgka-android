package com.lgka

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import lgka.plan.ScanPoint
import lgka.plan.ScanQuad
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The sheet in a camera frame, found on a small upright luminance grid (~160 px on the long side): the
 * paper is the largest bright region (Otsu threshold, 4-connected), its corners are the extreme points
 * along the two diagonals, and it only counts when that quad is filled by the region (a rectangle,
 * not a blob). Light enough to run ~9 times a second without OpenCV or a model.
 */
object SheetDetection {
    /** An upright luminance image, values 0…255, row by row. */
    class Grid(val width: Int, val height: Int, val values: IntArray) {
        operator fun get(x: Int, y: Int) = values[y * width + x]
    }

    /** The Y plane of an analysis frame, sampled and turned upright by [rotationDegrees]. */
    fun grid(image: ImageProxy, rotationDegrees: Int, longSide: Int = 160): Grid {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val srcW = image.width
        val srcH = image.height
        val step = max(1, max(srcW, srcH) / longSide)
        val turned = rotationDegrees % 180 != 0
        val outW = (if (turned) srcH else srcW) / step
        val outH = (if (turned) srcW else srcH) / step
        val values = IntArray(outW * outH)
        for (v in 0 until outH) {
            for (u in 0 until outW) {
                val ux = u * step
                val vy = v * step
                val (sx, sy) = when (rotationDegrees) {
                    90 -> vy to (srcH - 1 - ux)
                    180 -> (srcW - 1 - ux) to (srcH - 1 - vy)
                    270 -> (srcW - 1 - vy) to ux
                    else -> ux to vy
                }
                val index = sy.coerceIn(0, srcH - 1) * rowStride + sx.coerceIn(0, srcW - 1) * pixelStride
                values[v * outW + u] = buffer.get(index).toInt() and 0xFF
            }
        }
        return Grid(outW, outH, values)
    }

    fun detect(grid: Grid): ScanQuad? {
        val threshold = otsu(grid.values)
        val w = grid.width
        val h = grid.height
        val visited = BooleanArray(w * h)
        var bestPixels: IntArray? = null
        var bestSize = 0
        val queue = IntArray(w * h)
        for (start in 0 until w * h) {
            if (visited[start] || grid.values[start] < threshold) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            while (head < tail) {
                val p = queue[head++]
                val x = p % w
                val y = p / w
                fun visit(n: Int) {
                    if (!visited[n] && grid.values[n] >= threshold) { visited[n] = true; queue[tail++] = n }
                }
                if (x > 0) visit(p - 1)
                if (x < w - 1) visit(p + 1)
                if (y > 0) visit(p - w)
                if (y < h - 1) visit(p + w)
            }
            if (tail > bestSize) {
                bestSize = tail
                bestPixels = queue.copyOf(tail)
            }
        }
        val pixels = bestPixels ?: return null
        if (bestSize < w * h * 0.06) return null

        var tl = pixels[0]; var tr = pixels[0]; var br = pixels[0]; var bl = pixels[0]
        fun sum(p: Int) = p % w + p / w
        fun diff(p: Int) = p % w - p / w
        for (p in pixels) {
            if (sum(p) < sum(tl)) tl = p
            if (sum(p) > sum(br)) br = p
            if (diff(p) > diff(tr)) tr = p
            if (diff(p) < diff(bl)) bl = p
        }
        fun point(p: Int) = ScanPoint((p % w + 0.5) / w, (p / w + 0.5) / h)
        val quad = ScanQuad(point(tl), point(tr), point(br), point(bl))
        // a sheet fills its outline; a lamp, a hand or a patterned table does not
        val quadPixels = quad.area * w * h
        return quad.takeIf { quadPixels > 0 && bestSize / quadPixels > 0.8 }
    }

    /** Mean luma 0…1 and the share of clipped white, inside the sheet's bounding box when found. */
    fun brightness(grid: Grid, quad: ScanQuad?): Pair<Double, Double> {
        var x0 = 0; var x1 = grid.width; var y0 = 0; var y1 = grid.height
        if (quad != null) {
            x0 = (quad.corners.minOf { it.x } * grid.width).toInt().coerceIn(0, grid.width)
            x1 = (quad.corners.maxOf { it.x } * grid.width).toInt().coerceIn(0, grid.width)
            y0 = (quad.corners.minOf { it.y } * grid.height).toInt().coerceIn(0, grid.height)
            y1 = (quad.corners.maxOf { it.y } * grid.height).toInt().coerceIn(0, grid.height)
        }
        if (x1 <= x0 || y1 <= y0) return 0.5 to 0.0
        var sum = 0L
        var clipped = 0
        var count = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val value = grid[x, y]
            sum += value
            if (value >= 250) clipped++
            count++
        }
        return (sum.toDouble() / count / 255) to (clipped.toDouble() / count)
    }

    /** Straightens the photographed sheet to a flat rectangle (a little margin keeps the printed border). */
    fun corrected(image: Bitmap, quad: ScanQuad?): Bitmap {
        if (quad == null) return image
        val w = image.width.toFloat()
        val h = image.height.toFloat()
        val cx = quad.corners.sumOf { it.x } / 4
        val cy = quad.corners.sumOf { it.y } / 4
        val corners = quad.corners.map { p ->
            val x = (cx + (p.x - cx) * 1.02).coerceIn(0.0, 1.0) * w
            val y = (cy + (p.y - cy) * 1.02).coerceIn(0.0, 1.0) * h
            x.toFloat() to y.toFloat()
        }
        fun distance(a: Int, b: Int) = hypot(corners[a].first - corners[b].first, corners[a].second - corners[b].second)
        val outW = ((distance(0, 1) + distance(3, 2)) / 2).roundToInt()
        val outH = ((distance(0, 3) + distance(1, 2)) / 2).roundToInt()
        if (outW < 100 || outH < 100) return image
        val src = corners.flatMap { listOf(it.first, it.second) }.toFloatArray()
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return image
        val out = createBitmap(outW, outH)
        Canvas(out).drawBitmap(image, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Scaled so the long side is at most [maxPixels]. */
    fun limited(image: Bitmap, maxPixels: Int): Bitmap {
        val longSide = max(image.width, image.height)
        if (longSide <= maxPixels) return image
        val scale = maxPixels.toFloat() / longSide
        return image.scale((image.width * scale).roundToInt(), (image.height * scale).roundToInt())
    }

    private fun otsu(values: IntArray): Int {
        val histogram = IntArray(256)
        for (v in values) histogram[v]++
        val total = values.size
        var sumAll = 0.0
        for (i in 0 until 256) sumAll += i * histogram[i]
        var sumBackground = 0.0
        var weightBackground = 0
        var best = 0.0
        var threshold = 128
        for (t in 0 until 256) {
            weightBackground += histogram[t]
            if (weightBackground == 0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0) break
            sumBackground += t * histogram[t]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sumAll - sumBackground) / weightForeground
            val between = weightBackground.toDouble() * weightForeground * (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (between > best) { best = between; threshold = t + 1 }
        }
        // a sheet is clearly brighter than what is around it; a nearly uniform frame has no sheet
        return if (abs(threshold - 128) > 120) 256 else min(threshold, 255)
    }
}
