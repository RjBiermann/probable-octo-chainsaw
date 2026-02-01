package com.lagradost.common

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchHistoryConfigTest {

    private val storage = mutableMapOf<String, String?>()
    private val config = WatchHistoryConfig(
        getKey = { storage[it] },
        setKey = { k, v -> storage[k] = v }
    )

    @Test
    fun `disabled by default`() {
        assertFalse(config.isEnabled("Neporn"))
    }

    @Test
    fun `global toggle enables all plugins`() {
        config.setGlobalEnabled(true)
        assertTrue(config.isEnabled("Neporn"))
        assertTrue(config.isEnabled("HQPorner"))
    }

    @Test
    fun `per-plugin override can disable when global is on`() {
        config.setGlobalEnabled(true)
        config.setPluginOverride("Neporn", false)
        assertFalse(config.isEnabled("Neporn"))
        assertTrue(config.isEnabled("HQPorner"))
    }

    @Test
    fun `per-plugin override can enable when global is off`() {
        config.setGlobalEnabled(false)
        config.setPluginOverride("Neporn", true)
        assertTrue(config.isEnabled("Neporn"))
        assertFalse(config.isEnabled("HQPorner"))
    }

    @Test
    fun `isEnabled returns false when disabled`() {
        assertEquals(false, config.isEnabled("Neporn"))
    }

    @Test
    fun `isEnabled returns true when enabled`() {
        config.setGlobalEnabled(true)
        assertEquals(true, config.isEnabled("Neporn"))
    }
}
