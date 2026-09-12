package com.lgka

import android.content.Context
import android.util.Log
import java.io.File

/// Disk cache for raw payloads — mirrors the iOS Cache (FNV-1a keys, TTLs).
class DiskCache(context: Context) {
    val dir: File = File(context.cacheDir, "lgka-cache").apply { mkdirs() }

    companion object {
        private const val TAG = "DiskCache"
        /** Entries untouched for longer than this are removed at startup. */
        const val MAX_AGE_MS = 14L * 24 * 3600 * 1000

        fun key(url: String): String {
            var hash = -0x340d631b7bdddcdbL // FNV-1a offset basis
            for (b in url.toByteArray()) {
                hash = hash xor (b.toLong() and 0xff)
                hash *= 0x100000001b3L
            }
            return java.lang.Long.toHexString(hash)
        }
    }

    /** Cached bytes plus their age in seconds, or null. */
    fun load(url: String): Pair<ByteArray, Long>? {
        val f = File(dir, key(url))
        if (!f.exists()) return null
        return f.readBytes() to (System.currentTimeMillis() - f.lastModified()) / 1000
    }

    fun store(data: ByteArray, url: String) {
        val target = File(dir, key(url))
        val tmp = File(dir, key(url) + ".tmp")
        try {
            tmp.writeBytes(data)
            if (!tmp.renameTo(target)) target.writeBytes(data)
        } catch (e: java.io.IOException) {
            Log.w(TAG, "store failed for $url", e)
        } finally {
            tmp.delete()
        }
    }

    fun remove(url: String) {
        File(dir, key(url)).delete()
    }

    /** Deletes entries older than [MAX_AGE_MS]. */
    fun evictStale(now: Long = System.currentTimeMillis()) {
        val removed = (dir.listFiles() ?: emptyArray())
            .filter { now - it.lastModified() > MAX_AGE_MS }
            .count { it.delete() }
        if (removed > 0) Log.i(TAG, "evicted $removed stale cache entries")
    }
}
