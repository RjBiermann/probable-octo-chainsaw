package com.lagradost.common.cache

import org.junit.Test
import kotlin.test.assertEquals

class CacheConfigTest {

    @Test
    fun `CacheLevel MINIMAL has correct defaults`() {
        val config = CacheConfig.forLevel(CacheLevel.MINIMAL)
        assertEquals(20, config.memoryMaxEntries)
        assertEquals(0L, config.diskMaxBytes)
        assertEquals(2 * 60 * 1000L, config.pageTtlMs)
        assertEquals(1 * 60 * 1000L, config.searchTtlMs)
    }

    @Test
    fun `CacheLevel BALANCED has correct defaults`() {
        val config = CacheConfig.forLevel(CacheLevel.BALANCED)
        assertEquals(50, config.memoryMaxEntries)
        assertEquals(25 * 1024 * 1024L, config.diskMaxBytes)
        assertEquals(15 * 60 * 1000L, config.pageTtlMs)
        assertEquals(5 * 60 * 1000L, config.searchTtlMs)
    }

    @Test
    fun `CacheLevel AGGRESSIVE has correct defaults`() {
        val config = CacheConfig.forLevel(CacheLevel.AGGRESSIVE)
        assertEquals(100, config.memoryMaxEntries)
        assertEquals(100 * 1024 * 1024L, config.diskMaxBytes)
        assertEquals(60 * 60 * 1000L, config.pageTtlMs)
        assertEquals(15 * 60 * 1000L, config.searchTtlMs)
    }

    @Test
    fun `CacheLevel serialization round trip`() {
        assertEquals(CacheLevel.BALANCED, CacheLevel.fromString("BALANCED"))
        assertEquals(CacheLevel.MINIMAL, CacheLevel.fromString("unknown"))
    }
}
