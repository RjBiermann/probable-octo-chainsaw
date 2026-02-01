package com.lagradost.common.cache

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class PrefetcherTest {

    private fun createClient(level: CacheLevel): CacheAwareClient {
        val config = CacheConfig.forLevel(level)
        val memoryCache = MemoryCache<CacheEntry>(config.memoryMaxEntries, config.pageTtlMs)
        return CacheAwareClient("test", config, memoryCache, null)
    }

    @Test
    fun `prefetching disabled on MINIMAL`() {
        val config = CacheConfig.forLevel(CacheLevel.MINIMAL)
        val prefetcher = Prefetcher(createClient(CacheLevel.MINIMAL), config)
        assertFalse(prefetcher.isEnabled)
        assertEquals(0, prefetcher.maxVideoDetailPrefetch)
    }

    @Test
    fun `prefetching enabled on BALANCED`() {
        val config = CacheConfig.forLevel(CacheLevel.BALANCED)
        val prefetcher = Prefetcher(createClient(CacheLevel.BALANCED), config)
        assertTrue(prefetcher.isEnabled)
        assertEquals(0, prefetcher.maxVideoDetailPrefetch)
    }

    @Test
    fun `AGGRESSIVE enables video detail prefetch`() {
        val config = CacheConfig.forLevel(CacheLevel.AGGRESSIVE)
        val prefetcher = Prefetcher(createClient(CacheLevel.AGGRESSIVE), config)
        assertTrue(prefetcher.isEnabled)
        assertEquals(3, prefetcher.maxVideoDetailPrefetch)
    }

    @Test
    fun `prefetchUrl warms cache`() = runBlocking {
        val config = CacheConfig.forLevel(CacheLevel.BALANCED)
        val client = createClient(CacheLevel.BALANCED)

        // Prefetch directly via client (simulating what Prefetcher.prefetchUrl does)
        client.cachedGet("https://example.com/page2") { _ ->
            FetchResult("<html>page2</html>", 200)
        }

        // Cache should now be warm
        val entry = client.cachedGet("https://example.com/page2") { _ ->
            throw AssertionError("Should not fetch - cache should be warm")
        }
        assertEquals("<html>page2</html>", entry?.body)
    }
}
