package com.lagradost.common

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzyDeduplicatorTest {

    data class TestItem(val title: String, val duration: Int?, val source: String)

    @Test
    fun `deduplicates items with similar titles`() {
        val items = listOf(
            TestItem("Hot Amateur Teen Video", 300, "Neporn"),
            TestItem("Hot Amateur Teen Video HD", 302, "HQPorner"),
            TestItem("Completely Different Video", 120, "MissAV")
        )
        val deduped = FuzzyDeduplicator.deduplicate(
            items,
            titleSelector = { it.title },
            durationSelector = { it.duration },
            threshold = 85
        )
        assertEquals(2, deduped.size)
    }

    @Test
    fun `keeps items with different durations even if titles match`() {
        val items = listOf(
            TestItem("Same Title Video", 300, "Neporn"),
            TestItem("Same Title Video", 900, "HQPorner") // very different duration
        )
        val deduped = FuzzyDeduplicator.deduplicate(
            items,
            titleSelector = { it.title },
            durationSelector = { it.duration },
            threshold = 85,
            durationToleranceSec = 5
        )
        assertEquals(2, deduped.size)
    }

    @Test
    fun `findMatch returns best matching item`() {
        val candidates = listOf(
            TestItem("Blonde Girl Outdoor Scene", 300, "HQPorner"),
            TestItem("Unrelated Video", 120, "MissAV")
        )
        val match = FuzzyDeduplicator.findMatch(
            candidates,
            title = "Blonde Girl Outdoor Scene HD",
            duration = 302,
            titleSelector = { it.title },
            durationSelector = { it.duration },
            threshold = 85
        )
        assertNotNull(match)
        assertEquals("HQPorner", match.source)
    }

    @Test
    fun `findMatch returns null when no good match`() {
        val candidates = listOf(
            TestItem("Completely Different", 300, "HQPorner")
        )
        val match = FuzzyDeduplicator.findMatch(
            candidates,
            title = "Blonde Girl Outdoor",
            duration = 300,
            titleSelector = { it.title },
            durationSelector = { it.duration },
            threshold = 85
        )
        assertNull(match)
    }

    @Test
    fun `isTitleMatch returns true for similar titles`() {
        assertTrue(FuzzyDeduplicator.isTitleMatch("Hot Amateur Teen Video", "Hot Amateur Teen Video HD"))
    }

    @Test
    fun `isTitleMatch returns false for different titles`() {
        assertFalse(FuzzyDeduplicator.isTitleMatch("Hot Amateur Teen Video", "Completely Different Video"))
    }

    @Test
    fun `isTitleMatch is case insensitive`() {
        assertTrue(FuzzyDeduplicator.isTitleMatch("Some Video Title", "some video title"))
    }

    @Test
    fun `isTitleMatch respects custom threshold`() {
        // Exact match should pass any threshold
        assertTrue(FuzzyDeduplicator.isTitleMatch("Same Title", "Same Title", threshold = 100))
        // Somewhat similar titles should fail a strict threshold
        assertFalse(FuzzyDeduplicator.isTitleMatch("Video One Extra", "Video One", threshold = 99))
    }
}
