package com.lagradost.common.cache

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background prefetcher that warms the cache for anticipated requests.
 *
 * Usage:
 * ```kotlin
 * // After getMainPage returns, prefetch next page
 * prefetcher.prefetchUrl(nextPageUrl) { headers ->
 *     val resp = app.get(nextPageUrl, headers = headers)
 *     FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
 * }
 * ```
 *
 * Respects cache level:
 * - MINIMAL: prefetching disabled
 * - BALANCED: prefetch next page only
 * - AGGRESSIVE: prefetch next page + first N video URLs
 */
class Prefetcher(
    private val client: CacheAwareClient,
    private val config: CacheConfig,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "Prefetcher"
    }

    /** Cancel all in-flight prefetch coroutines. Call on plugin unload. */
    fun cancel() { scope.cancel() }

    /** Whether prefetching is enabled for the current cache level. */
    val isEnabled: Boolean get() = config.level != CacheLevel.MINIMAL

    /** Max video detail URLs to prefetch (only on AGGRESSIVE). */
    val maxVideoDetailPrefetch: Int get() = when (config.level) {
        CacheLevel.MINIMAL -> 0
        CacheLevel.BALANCED -> 0
        CacheLevel.AGGRESSIVE -> 3
    }

    /**
     * Prefetch a URL in the background, warming the cache for future use.
     * No-op if prefetching is disabled.
     */
    fun prefetchUrl(
        url: String,
        fetcher: suspend (conditionalHeaders: Map<String, String>) -> FetchResult?
    ) {
        if (!isEnabled) return
        scope.launch {
            try {
                client.cachedGet(url, fetcher = fetcher)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Prefetch failed for $url: ${e.message}")
            }
        }
    }

    /**
     * Prefetch multiple URLs in the background.
     * Limits to [maxVideoDetailPrefetch] for video detail URLs.
     */
    fun prefetchUrls(
        urls: List<String>,
        fetcher: suspend (url: String, conditionalHeaders: Map<String, String>) -> FetchResult?
    ) {
        if (!isEnabled || maxVideoDetailPrefetch <= 0) return
        urls.take(maxVideoDetailPrefetch).forEach { url ->
            scope.launch {
                try {
                    client.cachedGet(url) { headers -> fetcher(url, headers) }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Prefetch failed for $url: ${e.message}")
                }
            }
        }
    }
}
