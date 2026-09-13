package com.lgka

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.layout.width
import androidx.core.graphics.createBitmap
import java.io.File

/**
 * Page renderer with lazy, width-fitted bitmaps: only visible pages are
 * rasterized, at most [cacheSize] bitmaps stay in memory, and the underlying
 * PdfRenderer (not thread-safe) is serialized behind the instance lock. [close]
 * takes the same lock: closing the renderer under a page that is still rendering crashes.
 */
class PdfPages(file: File, cacheSize: Int = 6) : AutoCloseable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(fd)
    } catch (e: Exception) {
        fd.close()
        throw e
    }
    private val cache = object : LruCache<Int, Bitmap>(cacheSize) {}
    private var closed = false
    val pageCount: Int = renderer.pageCount
    /** width/height ratios so placeholders reserve the right space. */
    val aspectRatios: List<Float> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { it.width.toFloat() / it.height.toFloat() }
    }

    /** Blocking: call it off the main thread. */
    @Synchronized
    fun render(index: Int, widthPx: Int): Bitmap {
        check(!closed) { "PdfPages closed" }
        cache.get(index)?.takeIf { it.width == widthPx }?.let { return it }
        val bmp = renderer.openPage(index).use { page ->
            val scale = widthPx.toFloat() / page.width
            val target = createBitmap(widthPx, (page.height * scale).toInt().coerceAtLeast(1))
            target.eraseColor(android.graphics.Color.WHITE)
            page.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            target
        }
        cache.put(index, bmp)
        return bmp
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        cache.evictAll()
        renderer.close()
        fd.close()
    }
}
