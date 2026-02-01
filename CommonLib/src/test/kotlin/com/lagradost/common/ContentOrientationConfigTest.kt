package com.lagradost.common

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentOrientationConfigTest {

    private val storage = mutableMapOf<String, String?>()
    private val config = ContentOrientationConfig(
        getKey = { storage[it] },
        setKey = { k, v -> storage[k] = v }
    )

    @Test
    fun `all orientations enabled by default`() {
        assertTrue(config.straightEnabled)
        assertTrue(config.gayEnabled)
        assertTrue(config.lesbianEnabled)
        assertTrue(config.transEnabled)
    }

    @Test
    fun `can disable individual orientations`() {
        config.gayEnabled = false
        assertFalse(config.gayEnabled)
        assertTrue(config.straightEnabled)
        assertTrue(config.lesbianEnabled)
        assertTrue(config.transEnabled)
    }

    @Test
    fun `getExcludedOrientationTags returns empty when all enabled`() {
        val excluded = config.getExcludedOrientationTags()
        assertTrue(excluded.isEmpty())
    }

    @Test
    fun `getExcludedOrientationTags returns gay tag when gay disabled`() {
        config.gayEnabled = false
        val excluded = config.getExcludedOrientationTags()
        assertEquals(setOf("gay", "solo male", "bisexual"), excluded)
    }

    @Test
    fun `getExcludedOrientationTags returns multiple tags`() {
        config.gayEnabled = false
        config.transEnabled = false
        val excluded = config.getExcludedOrientationTags()
        assertEquals(setOf("gay", "solo male", "bisexual", "transgender"), excluded)
    }

    @Test
    fun `isContentAllowed returns true when all enabled`() {
        assertTrue(config.isContentAllowed(listOf("amateur", "gay", "hd")))
    }

    @Test
    fun `isContentAllowed filters gay content when gay disabled`() {
        config.gayEnabled = false
        assertFalse(config.isContentAllowed(listOf("amateur", "gay", "hd")))
    }

    @Test
    fun `isContentAllowed filters lesbian content when lesbian disabled`() {
        config.lesbianEnabled = false
        assertFalse(config.isContentAllowed(listOf("lesbian", "amateur")))
    }

    @Test
    fun `isContentAllowed filters trans content when trans disabled`() {
        config.transEnabled = false
        assertFalse(config.isContentAllowed(listOf("transgender", "amateur")))
    }

    @Test
    fun `isContentAllowed filters bisexual content when gay disabled`() {
        config.gayEnabled = false
        assertFalse(config.isContentAllowed(listOf("bisexual", "amateur")))
    }

    @Test
    fun `isContentAllowed treats untagged content as straight`() {
        config.straightEnabled = false
        assertFalse(config.isContentAllowed(listOf("amateur", "hd")))
    }

    @Test
    fun `isContentAllowed allows untagged content when straight enabled`() {
        config.gayEnabled = false
        assertTrue(config.isContentAllowed(listOf("amateur", "hd")))
    }

    @Test
    fun `isContentAllowed allows multi-orientation content if any orientation enabled`() {
        config.gayEnabled = false
        // Content tagged both lesbian and gay — lesbian is still enabled
        assertTrue(config.isContentAllowed(listOf("lesbian", "gay")))
    }

    @Test
    fun `isContentAllowed filters solo male content when gay disabled`() {
        config.gayEnabled = false
        assertFalse(config.isContentAllowed(listOf("solo male", "amateur")))
    }

    @Test
    fun `isContentAllowed allows solo male content when gay enabled`() {
        config.straightEnabled = false
        assertTrue(config.isContentAllowed(listOf("solo male", "amateur")))
    }

    @Test
    fun `getExcludedOrientationTags includes solo male when gay disabled`() {
        config.gayEnabled = false
        val excluded = config.getExcludedOrientationTags()
        assertTrue("solo male" in excluded)
        assertTrue("gay" in excluded)
    }

    @Test
    fun `isAllEnabled returns true by default`() {
        assertTrue(config.isAllEnabled())
    }

    @Test
    fun `isAllEnabled returns false when any disabled`() {
        config.gayEnabled = false
        assertFalse(config.isAllEnabled())
    }
}
