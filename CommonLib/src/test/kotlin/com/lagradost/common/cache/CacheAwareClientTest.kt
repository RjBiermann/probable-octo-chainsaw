package com.lagradost.common.cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CacheAwareClientTest {

    @Test
    fun `CacheEntry stores response data`() {
        val entry = CacheEntry(
            body = "<html>test</html>",
            url = "https://example.com",
            statusCode = 200,
            etag = "abc",
            lastModified = null,
            isOfflineFallback = false
        )
        assertEquals("<html>test</html>", entry.body)
        assertEquals("abc", entry.etag)
        assertEquals(false, entry.isOfflineFallback)
    }

    @Test
    fun `CacheEntry offline fallback flag`() {
        val entry = CacheEntry("body", "url", 200, null, null, isOfflineFallback = true)
        assertEquals(true, entry.isOfflineFallback)
    }

    @Test
    fun `FetchResult stores network data`() {
        val result = FetchResult("body", 200, "etag1", "Mon, 01 Jan 2024 00:00:00 GMT")
        assertEquals("body", result.body)
        assertEquals(200, result.statusCode)
        assertEquals("etag1", result.etag)
    }

    @Test
    fun `cachedGet returns from memory cache`() = runTest {
        val memoryCache = MemoryCache<CacheEntry>(10, 60_000)
        val client = CacheAwareClient("Test", CacheConfig.forLevel(CacheLevel.BALANCED), memoryCache, null, this)

        val cached = CacheEntry("cached body", "https://example.com", 200, null, null)
        memoryCache.put("https://example.com", cached)

        val result = client.cachedGet("https://example.com") { null }
        assertEquals("cached body", result?.body)
    }

    @Test
    fun `cachedGet fetches from network on cache miss`() = runTest {
        val memoryCache = MemoryCache<CacheEntry>(10, 60_000)
        val client = CacheAwareClient("Test", CacheConfig.forLevel(CacheLevel.BALANCED), memoryCache, null, this)

        val result = client.cachedGet("https://example.com") {
            FetchResult("<html>fresh</html>", 200, null, null)
        }
        assertEquals("<html>fresh</html>", result?.body)
    }

    @Test
    fun `cachedGet returns null when network fails and no cache`() = runTest {
        val memoryCache = MemoryCache<CacheEntry>(10, 60_000)
        val client = CacheAwareClient("Test", CacheConfig.forLevel(CacheLevel.BALANCED), memoryCache, null, this)

        val result = client.cachedGet("https://example.com") { null }
        assertNull(result)
    }

    @Test
    fun `cachedGet with forceRefresh bypasses cache`() = runTest {
        val memoryCache = MemoryCache<CacheEntry>(10, 60_000)
        val client = CacheAwareClient("Test", CacheConfig.forLevel(CacheLevel.BALANCED), memoryCache, null, this)

        memoryCache.put("https://example.com", CacheEntry("old", "https://example.com", 200, null, null))

        val result = client.cachedGet("https://example.com", forceRefresh = true) {
            FetchResult("fresh", 200, null, null)
        }
        assertEquals("fresh", result?.body)
    }

    @Test
    fun `invalidate clears both memory and disk`() = runTest {
        val memoryCache = MemoryCache<CacheEntry>(10, 60_000)
        val client = CacheAwareClient("Test", CacheConfig.forLevel(CacheLevel.BALANCED), memoryCache, null, this)

        memoryCache.put("https://example.com", CacheEntry("data", "https://example.com", 200, null, null))
        client.invalidate("https://example.com")

        assertNull(memoryCache.get("https://example.com"))
    }

    @Test
    fun `cachedGet returns null for non-2xx and does not cache`() = runTest {
        val memoryCache = MemoryCache<CacheEntry>(10, 60_000)
        val client = CacheAwareClient("Test", CacheConfig.forLevel(CacheLevel.BALANCED), memoryCache, null, this)

        val result = client.cachedGet("https://example.com") {
            FetchResult("<html>Cloudflare challenge</html>", 403, null, null)
        }
        assertNull(result)
        assertNull(memoryCache.get("https://example.com"))
    }

    @Test
    fun `cachedGet returns null for 5xx server errors`() = runTest {
        val memoryCache = MemoryCache<CacheEntry>(10, 60_000)
        val client = CacheAwareClient("Test", CacheConfig.forLevel(CacheLevel.BALANCED), memoryCache, null, this)

        val result = client.cachedGet("https://example.com") {
            FetchResult("Internal Server Error", 500, null, null)
        }
        assertNull(result)
        assertNull(memoryCache.get("https://example.com"))
    }
}
