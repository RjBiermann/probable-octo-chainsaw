package com.lagradost.common.intelligence

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowsingModelsTest {

    @Test
    fun `ViewHistoryEntry has required fields`() {
        val entry = ViewHistoryEntry(
            url = "https://neporn.com/video/1",
            title = "Test Video",
            thumbnail = "https://img.com/1.jpg",
            duration = 300,
            tags = listOf("amateur", "hd"),
            sourcePlugin = "Neporn",
            timestamp = System.currentTimeMillis()
        )
        assertEquals("Neporn", entry.sourcePlugin)
        assertEquals(2, entry.tags.size)
    }

    @Test
    fun `TagAffinity stores score as provided`() {
        val affinity = TagAffinity("amateur", score = 1.5f, viewCount = 10, lastSeen = 0L)
        assertEquals(1.5f, affinity.score)
    }

    @Test
    fun `TagSource stores plugin-tag-url mapping`() {
        val source = TagSource("amateur", "Neporn", "https://neporn.com/categories/amateur/", 0L)
        assertEquals("amateur", source.tag)
        assertEquals("https://neporn.com/categories/amateur/", source.categoryUrl)
    }

    // --- Engagement affinity tests ---

    @Test
    fun `calculateEngagementAffinity scores higher for more engaged items`() {
        val highEngagement = calculateEngagementAffinity(
            favoriteCount = 3, completedCount = 2, watchingCount = 1, daysSinceLastEngagement = 0
        )
        val lowEngagement = calculateEngagementAffinity(
            favoriteCount = 0, completedCount = 0, watchingCount = 1, daysSinceLastEngagement = 0
        )
        assertTrue(highEngagement > lowEngagement)
    }

    @Test
    fun `calculateEngagementAffinity decays with time`() {
        val recent = calculateEngagementAffinity(
            favoriteCount = 1, completedCount = 1, watchingCount = 0, daysSinceLastEngagement = 0
        )
        val old = calculateEngagementAffinity(
            favoriteCount = 1, completedCount = 1, watchingCount = 0, daysSinceLastEngagement = 30
        )
        assertTrue(recent > old)
    }

    @Test
    fun `calculateEngagementAffinity returns zero for no engagement`() {
        val score = calculateEngagementAffinity(
            favoriteCount = 0, completedCount = 0, watchingCount = 0, daysSinceLastEngagement = 0
        )
        assertEquals(0f, score)
    }

    @Test
    fun `calculateEngagementAffinity is clamped to 0-1 range`() {
        val score = calculateEngagementAffinity(
            favoriteCount = 100, completedCount = 100, watchingCount = 100, daysSinceLastEngagement = 0
        )
        assertTrue(score <= 1.0f)
        assertTrue(score >= 0f)
    }
}
