package com.lagradost.common.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Cached response data. Plugins receive this instead of raw NiceResponse.
 */
data class CacheEntry(
    val body: String,
    val url: String,
    val statusCode: Int,
    val etag: String?,
    val lastModified: String?,
    val isOfflineFallback: Boolean = false
) {
    /** Append " (cached)" to a section name if this is an offline fallback. */
    fun sectionName(name: String): String =
        if (isOfflineFallback) "$name (cached)" else name
}

/**
 * Result from a network fetch. Plugins convert NiceResponse to this.
 */
data class FetchResult(
    val body: String,
    val statusCode: Int,
    val etag: String? = null,
    val lastModified: String? = null
)

/**
 * 3-tier cache wrapping NiceHttp.
 * Memory -> Disk -> Network, with stale-while-revalidate and conditional requests.
 *
 * Usage:
 * ```kotlin
 * val client = CacheAwareClient.create(context, "Neporn", CacheConfig.forLevel(CacheLevel.BALANCED))
 * val entry = client.cachedGet("https://neporn.com/latest/") { headers ->
 *     val resp = app.get("https://neporn.com/latest/", headers = headers)
 *     FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
 * }
 * val doc = entry?.let { Jsoup.parse(it.body, url) }
 * ```
 */
class CacheAwareClient(
    private val pluginName: String,
    private val config: CacheConfig,
    private val memoryCache: MemoryCache<CacheEntry>,
    private val diskCache: DiskCache?,
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "CacheAwareClient"
        private const val OFFLINE_FALLBACK_TTL_MS = 30_000L // Retry network after 30s

        /**
         * Create a CacheAwareClient for a plugin.
         * @param context Android context for disk cache directory
         * @param pluginName Plugin name (used for cache directory isolation)
         * @param config Cache configuration
         */
        fun create(context: Context, pluginName: String, config: CacheConfig): CacheAwareClient {
            val memoryCache = MemoryCache<CacheEntry>(config.memoryMaxEntries, config.pageTtlMs)
            val diskCache = if (config.diskEnabled) {
                val dir = File(context.cacheDir, "plugin_cache/$pluginName")
                DiskCache(dir, config.diskMaxBytes, config.pageTtlMs)
            } else null
            return CacheAwareClient(pluginName, config, memoryCache, diskCache)
        }
    }

    /**
     * Get a URL with caching. Returns cached content when available.
     *
     * @param url URL to fetch
     * @param forceRefresh Bypass cache (e.g., pull-to-refresh)
     * @param ttlMs Override default TTL for this request
     * @param fetcher The actual network fetch function. Receives conditional request headers.
     *               Returns FetchResult or null on error.
     */
    suspend fun cachedGet(
        url: String,
        forceRefresh: Boolean = false,
        ttlMs: Long = config.pageTtlMs,
        fetcher: suspend (conditionalHeaders: Map<String, String>) -> FetchResult?
    ): CacheEntry? {
        if (forceRefresh) {
            return fetchAndCache(url, ttlMs, fetcher)
        }

        // Tier 1: Memory
        memoryCache.get(url)?.let { return it }

        // Tier 2: Disk
        diskCache?.get(url)?.let { diskEntry ->
            val entry = CacheEntry(diskEntry.body, url, 200, diskEntry.etag, diskEntry.lastModified)
            memoryCache.put(url, entry, ttlMs)
            return entry
        }

        // Tier 3: Network
        val entry = fetchAndCache(url, ttlMs, fetcher)

        // Offline fallback: if network failed, try stale disk
        if (entry == null) {
            diskCache?.getStale(url)?.let { stale ->
                val fallback = CacheEntry(stale.body, url, 200, stale.etag, stale.lastModified, isOfflineFallback = true)
                // Use short TTL for offline fallback so network is retried quickly
                memoryCache.put(url, fallback, OFFLINE_FALLBACK_TTL_MS)
                return fallback
            }
        }

        return entry
    }

    /**
     * Perform stale-while-revalidate: return stale cache immediately, refresh in background.
     * Use this for homepage rows where instant display matters.
     */
    suspend fun staleWhileRevalidate(
        url: String,
        ttlMs: Long = config.pageTtlMs,
        fetcher: suspend (conditionalHeaders: Map<String, String>) -> FetchResult?
    ): CacheEntry? {
        // Try memory first (if within TTL)
        memoryCache.get(url)?.let { return it }

        // Try disk (even stale)
        val stale = diskCache?.getStale(url)
        if (stale != null) {
            val entry = CacheEntry(stale.body, url, 200, stale.etag, stale.lastModified)
            memoryCache.put(url, entry, ttlMs)

            // Refresh in background for next visit
            if (stale.isExpired) {
                backgroundScope.launch {
                    try {
                        fetchAndCache(url, ttlMs, fetcher)
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Background revalidation failed for $url: ${e.message}")
                    }
                }
            }
            return entry
        }

        // Nothing cached — must fetch
        return fetchAndCache(url, ttlMs, fetcher)
    }

    /** Search TTL for plugins to use with cachedGet(ttlMs=...) */
    val searchTtlMs: Long get() = config.searchTtlMs

    fun invalidate(url: String) {
        memoryCache.invalidate(url)
        diskCache?.invalidate(url)
    }

    fun clearAll() {
        memoryCache.clear()
        diskCache?.clear()
    }

    /** Cancel background coroutines and release resources. Call on plugin unload. */
    fun close() {
        backgroundScope.cancel()
        clearAll()
    }

    fun getCacheSize(): Long = diskCache?.getCacheSize() ?: 0L

    private suspend fun fetchAndCache(
        url: String,
        ttlMs: Long,
        fetcher: suspend (conditionalHeaders: Map<String, String>) -> FetchResult?
    ): CacheEntry? {
        // Build conditional headers from disk cache
        val conditionalHeaders = mutableMapOf<String, String>()
        diskCache?.getStale(url)?.let { stale ->
            stale.etag?.let { conditionalHeaders["If-None-Match"] = it }
            stale.lastModified?.let { conditionalHeaders["If-Modified-Since"] = it }
        }

        val result = try {
            fetcher(conditionalHeaders)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch failed for: $url", e)
            null
        } ?: return null

        // 304 Not Modified — use cached body with updated timestamp
        if (result.statusCode == 304) {
            diskCache?.getStale(url)?.let { stale ->
                val entry = CacheEntry(stale.body, url, 200, stale.etag, stale.lastModified)
                memoryCache.put(url, entry, ttlMs)
                diskCache.put(url, stale.body, stale.etag, stale.lastModified, ttlMs)
                return entry
            }
            // No stale entry available — 304 body is empty, skip caching
            Log.w(TAG, "Server returned 304 but no stale cache entry for: $url")
            return null
        }

        // Don't cache error responses (e.g., Cloudflare 403 challenge pages)
        if (result.statusCode !in 200..299) {
            Log.w(TAG, "Non-2xx response (${result.statusCode}) for: $url — skipping cache")
            return null
        }

        val entry = CacheEntry(result.body, url, result.statusCode, result.etag, result.lastModified)
        memoryCache.put(url, entry, ttlMs)
        diskCache?.put(url, result.body, result.etag, result.lastModified, ttlMs)
        return entry
    }
}

/**
 * Fetch a URL as a parsed Document, with cache support.
 * Handles the null-client fallback (direct fetch) and Jsoup parsing.
 *
 * @param fetch Single fetch lambda that receives conditional headers (empty when client is null).
 *              Return a FetchResult from NiceHttp response.
 */
suspend fun CacheAwareClient?.fetchDocument(
    url: String,
    ttlMs: Long? = null,
    fetch: suspend (headers: Map<String, String>) -> FetchResult?
): org.jsoup.nodes.Document? {
    if (this == null) {
        // No try-catch here: let exceptions propagate to the caller's existing error handling
        // (each plugin wraps getMainPage/search in try-catch with proper user feedback)
        val result = fetch(emptyMap()) ?: return null
        return org.jsoup.Jsoup.parse(result.body, url)
    }
    val entry = if (ttlMs != null) cachedGet(url, ttlMs = ttlMs, fetcher = fetch)
                else cachedGet(url, fetcher = fetch)
    return entry?.let { org.jsoup.Jsoup.parse(it.body, url) }
}
