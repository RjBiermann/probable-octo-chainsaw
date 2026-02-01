package com.lagradost.common.cache

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiskCacheTest {

    private lateinit var cacheDir: File
    private lateinit var cache: DiskCache

    @Before
    fun setup() {
        cacheDir = File(System.getProperty("java.io.tmpdir"), "disk_cache_test_${System.nanoTime()}")
        cacheDir.mkdirs()
        cache = DiskCache(cacheDir, maxBytes = 1024 * 1024, defaultTtlMs = 60_000)
    }

    @After
    fun teardown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `get returns null for missing key`() {
        assertNull(cache.get("missing"))
    }

    @Test
    fun `put and get round trip`() {
        cache.put("key1", "value1", etag = "etag1", lastModified = "Mon, 01 Jan 2024 00:00:00 GMT")
        val entry = cache.get("key1")!!
        assertEquals("value1", entry.body)
        assertEquals("etag1", entry.etag)
        assertEquals("Mon, 01 Jan 2024 00:00:00 GMT", entry.lastModified)
    }

    @Test
    fun `expired entry returns null via get but available via getStale`() {
        cache = DiskCache(cacheDir, maxBytes = 1024 * 1024, defaultTtlMs = 1)
        cache.put("key1", "value1")
        Thread.sleep(10)
        assertNull(cache.get("key1"))
        val stale = cache.getStale("key1")
        assertEquals("value1", stale?.body)
    }

    @Test
    fun `invalidate removes entry`() {
        cache.put("key1", "value1")
        cache.invalidate("key1")
        assertNull(cache.get("key1"))
    }

    @Test
    fun `getCacheSize returns approximate size`() {
        cache.put("key1", "x".repeat(100))
        assertTrue(cache.getCacheSize() > 0)
    }

    @Test
    fun `clear removes all entries`() {
        cache.put("a", "1")
        cache.put("b", "2")
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
