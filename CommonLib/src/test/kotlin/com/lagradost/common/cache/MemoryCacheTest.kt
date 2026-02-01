package com.lagradost.common.cache

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryCacheTest {

    @Test
    fun `get returns null for missing key`() {
        val cache = MemoryCache<String>(maxEntries = 5, defaultTtlMs = 60_000)
        assertNull(cache.get("missing"))
    }

    @Test
    fun `put and get round trip`() {
        val cache = MemoryCache<String>(maxEntries = 5, defaultTtlMs = 60_000)
        cache.put("key1", "value1")
        assertEquals("value1", cache.get("key1"))
    }

    @Test
    fun `evicts oldest when max entries exceeded`() {
        val cache = MemoryCache<String>(maxEntries = 2, defaultTtlMs = 60_000)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3") // should evict "a"
        assertNull(cache.get("a"))
        assertEquals("2", cache.get("b"))
        assertEquals("3", cache.get("c"))
    }

    @Test
    fun `expired entries return null`() {
        val cache = MemoryCache<String>(maxEntries = 5, defaultTtlMs = 1) // 1ms TTL
        cache.put("key1", "value1")
        Thread.sleep(10)
        assertNull(cache.get("key1"))
    }

    @Test
    fun `invalidate removes specific key`() {
        val cache = MemoryCache<String>(maxEntries = 5, defaultTtlMs = 60_000)
        cache.put("key1", "value1")
        cache.invalidate("key1")
        assertNull(cache.get("key1"))
    }

    @Test
    fun `clear removes all entries`() {
        val cache = MemoryCache<String>(maxEntries = 5, defaultTtlMs = 60_000)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals(0, cache.size)
    }
}
