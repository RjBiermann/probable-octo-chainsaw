package com.lagradost.common.cache

import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Disk-based cache storing raw HTML responses as files.
 * Each entry stores metadata (TTL, ETag, Last-Modified) alongside the body.
 * Uses a simple header+body format to avoid org.json dependency in tests.
 */
class DiskCache(
    private val cacheDir: File,
    private val maxBytes: Long,
    private val defaultTtlMs: Long
) {
    companion object {
        private const val TAG = "DiskCache"
        private const val SEPARATOR = "\n---CACHE_BODY_START---\n"
    }

    data class Entry(
        val body: String,
        val etag: String?,
        val lastModified: String?,
        val storedAt: Long,
        val ttlMs: Long
    ) {
        val isExpired: Boolean get() = System.currentTimeMillis() > storedAt + ttlMs
    }

    init {
        require(maxBytes >= 0) { "maxBytes must be non-negative" }
        require(defaultTtlMs > 0) { "defaultTtlMs must be positive" }
        if (!cacheDir.mkdirs() && !cacheDir.isDirectory) {
            Log.w(TAG, "Failed to create cache directory: ${cacheDir.absolutePath}")
        }
    }

    @Synchronized
    fun get(key: String): Entry? {
        val entry = readEntry(key) ?: return null
        if (entry.isExpired) return null
        return entry
    }

    /** Get entry even if expired — for stale-while-revalidate. */
    @Synchronized
    fun getStale(key: String): Entry? = readEntry(key)

    @Synchronized
    fun put(key: String, body: String, etag: String? = null, lastModified: String? = null, ttlMs: Long = defaultTtlMs) {
        require(ttlMs > 0) { "ttlMs must be positive" }
        try {
            val sb = StringBuilder()
            sb.appendLine("storedAt=${System.currentTimeMillis()}")
            sb.appendLine("ttlMs=$ttlMs")
            sb.appendLine("etag=${etag ?: ""}")
            sb.appendLine("lastModified=${lastModified ?: ""}")
            sb.append("bodyLength=${body.length}")
            sb.append(SEPARATOR)
            sb.append(body)
            // Atomic write: write to temp file then rename to avoid partial reads on crash
            val target = fileForKey(key)
            val temp = File(target.parent, "${target.name}.tmp")
            temp.writeText(sb.toString())
            if (!temp.renameTo(target)) {
                // renameTo can fail on some filesystems; fall back to copy+delete
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            pruneIfNeeded()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write cache entry for key: $key", e)
        }
    }

    @Synchronized
    fun invalidate(key: String) {
        try { fileForKey(key).delete() } catch (e: Exception) {
            Log.w(TAG, "Failed to invalidate cache entry for key: $key", e)
        }
    }

    @Synchronized
    fun clear() {
        try { cacheDir.listFiles()?.forEach { it.delete() } } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cache directory", e)
        }
    }

    @Synchronized
    fun getCacheSize(): Long {
        return try {
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to calculate cache size", e)
            0L
        }
    }

    private fun readEntry(key: String): Entry? {
        return try {
            val file = fileForKey(key)
            if (!file.exists()) return null
            val text = file.readText()
            val separatorIndex = text.indexOf(SEPARATOR)
            if (separatorIndex < 0) {
                Log.w(TAG, "Corrupt cache entry (missing separator), deleting: $key")
                file.delete()
                return null
            }
            val headers = text.substring(0, separatorIndex)

            val headerMap = headers.lines().associate { line ->
                val eq = line.indexOf('=')
                if (eq < 0) "" to ""
                else line.substring(0, eq) to line.substring(eq + 1)
            }

            // Use bodyLength header for precise extraction (avoids issues if SEPARATOR appears in HTML body)
            val bodyLength = headerMap["bodyLength"]?.toIntOrNull()
            val bodyStart = separatorIndex + SEPARATOR.length
            val body = if (bodyLength != null) {
                text.substring(bodyStart, minOf(bodyStart + bodyLength, text.length))
            } else {
                // Legacy entries without bodyLength — use separator-based split (best effort).
                // If the HTML body contains the SEPARATOR string, it will be truncated.
                Log.w(TAG, "Cache entry missing bodyLength header, using fallback extraction: $key")
                text.substring(bodyStart)
            }

            Entry(
                body = body,
                etag = headerMap["etag"]?.ifBlank { null },
                lastModified = headerMap["lastModified"]?.ifBlank { null },
                storedAt = headerMap["storedAt"]?.toLongOrNull() ?: 0L,
                ttlMs = headerMap["ttlMs"]?.toLongOrNull() ?: defaultTtlMs
            )
        } catch (e: Exception) {
            Log.w(TAG, "Corrupt cache entry, deleting: $key", e)
            try { fileForKey(key).delete() } catch (cleanup: Exception) {
                Log.w(TAG, "Failed to delete corrupt cache file for key: $key", cleanup)
            }
            null
        }
    }

    private fun fileForKey(key: String): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.cache")
    }

    private fun pruneIfNeeded() {
        if (maxBytes <= 0) return
        try {
            val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
            var totalSize = files.sumOf { it.length() }
            for (file in files) {
                if (totalSize <= maxBytes) break
                totalSize -= file.length()
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prune cache directory", e)
        }
    }
}
