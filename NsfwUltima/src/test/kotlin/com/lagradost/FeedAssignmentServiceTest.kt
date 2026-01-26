package com.lagradost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for FeedAssignmentService.
 */
class FeedAssignmentServiceTest {

    // Test helpers
    private fun createFeed(plugin: String, section: String, data: String = "/test", homepageIds: Set<String> = emptySet()) =
        FeedItem(plugin, section, data, homepageIds)

    private fun createFeeds(vararg feeds: Pair<String, String>) =
        feeds.map { (plugin, section) -> createFeed(plugin, section) }

    // ======= addFeedsToHomepage tests =======

    @Test
    fun `addFeedsToHomepage - adds homepage to matching feeds`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1"),
            createFeed("Plugin2", "Section2"),
            createFeed("Plugin3", "Section3")
        )
        val toAssign = listOf(createFeed("Plugin1", "Section1"), createFeed("Plugin3", "Section3"))

        val result = FeedAssignmentService.addFeedsToHomepage(feeds, toAssign, "homepage1")

        assertEquals(setOf("homepage1"), result[0].homepageIds)
        assertEquals(emptySet(), result[1].homepageIds)
        assertEquals(setOf("homepage1"), result[2].homepageIds)
    }

    @Test
    fun `addFeedsToHomepage - preserves existing homepage assignments`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("existing"))
        )
        val toAssign = listOf(createFeed("Plugin1", "Section1"))

        val result = FeedAssignmentService.addFeedsToHomepage(feeds, toAssign, "new")

        assertEquals(setOf("existing", "new"), result[0].homepageIds)
    }

    @Test
    fun `addFeedsToHomepage - handles empty feeds list`() {
        val result = FeedAssignmentService.addFeedsToHomepage(emptyList(), emptyList(), "homepage1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `addFeedsToHomepage - no changes when toAssign is empty`() {
        val feeds = listOf(createFeed("Plugin1", "Section1"))
        val result = FeedAssignmentService.addFeedsToHomepage(feeds, emptyList(), "homepage1")
        assertEquals(emptySet(), result[0].homepageIds)
    }

    @Test
    fun `addFeedsToHomepage - handles duplicate assignment idempotently`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("homepage1"))
        )
        val toAssign = listOf(createFeed("Plugin1", "Section1"))

        val result = FeedAssignmentService.addFeedsToHomepage(feeds, toAssign, "homepage1")

        // Set naturally handles duplicates
        assertEquals(setOf("homepage1"), result[0].homepageIds)
    }

    // ======= setFeedHomepages tests =======

    @Test
    fun `setFeedHomepages - replaces all existing homepage assignments`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("old1", "old2"))
        )
        val feedToUpdate = createFeed("Plugin1", "Section1")

        val result = FeedAssignmentService.setFeedHomepages(feeds, feedToUpdate, setOf("new1", "new2"))

        assertEquals(setOf("new1", "new2"), result[0].homepageIds)
    }

    @Test
    fun `setFeedHomepages - clears assignments when given empty set`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("existing"))
        )
        val feedToUpdate = createFeed("Plugin1", "Section1")

        val result = FeedAssignmentService.setFeedHomepages(feeds, feedToUpdate, emptySet())

        assertEquals(emptySet(), result[0].homepageIds)
    }

    @Test
    fun `setFeedHomepages - only updates matching feed`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("original")),
            createFeed("Plugin2", "Section2", homepageIds = setOf("keep"))
        )
        val feedToUpdate = createFeed("Plugin1", "Section1")

        val result = FeedAssignmentService.setFeedHomepages(feeds, feedToUpdate, setOf("changed"))

        assertEquals(setOf("changed"), result[0].homepageIds)
        assertEquals(setOf("keep"), result[1].homepageIds)
    }

    // ======= removeFeedFromHomepage tests =======

    @Test
    fun `removeFeedFromHomepage - removes specific homepage from feed`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("home1", "home2", "home3"))
        )
        val feedToRemove = createFeed("Plugin1", "Section1")

        val result = FeedAssignmentService.removeFeedFromHomepage(feeds, feedToRemove, "home2")

        assertEquals(setOf("home1", "home3"), result[0].homepageIds)
    }

    @Test
    fun `removeFeedFromHomepage - no change when homepage not assigned`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("home1"))
        )
        val feedToRemove = createFeed("Plugin1", "Section1")

        val result = FeedAssignmentService.removeFeedFromHomepage(feeds, feedToRemove, "nonexistent")

        assertEquals(setOf("home1"), result[0].homepageIds)
    }

    @Test
    fun `removeFeedFromHomepage - results in empty set when last homepage removed`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("only"))
        )
        val feedToRemove = createFeed("Plugin1", "Section1")

        val result = FeedAssignmentService.removeFeedFromHomepage(feeds, feedToRemove, "only")

        assertEquals(emptySet(), result[0].homepageIds)
    }

    // ======= unassignFeedFromAll tests =======

    @Test
    fun `unassignFeedFromAll - clears all homepage assignments`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("home1", "home2", "home3"))
        )
        val feedToUnassign = createFeed("Plugin1", "Section1")

        val result = FeedAssignmentService.unassignFeedFromAll(feeds, feedToUnassign)

        assertEquals(emptySet(), result[0].homepageIds)
    }

    @Test
    fun `unassignFeedFromAll - does not affect other feeds`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("home1")),
            createFeed("Plugin2", "Section2", homepageIds = setOf("home2"))
        )
        val feedToUnassign = createFeed("Plugin1", "Section1")

        val result = FeedAssignmentService.unassignFeedFromAll(feeds, feedToUnassign)

        assertEquals(emptySet(), result[0].homepageIds)
        assertEquals(setOf("home2"), result[1].homepageIds)
    }

    // ======= getUnassignedFeeds tests =======

    @Test
    fun `getUnassignedFeeds - returns feeds without any homepage assignment`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("home1")),
            createFeed("Plugin2", "Section2"), // No homepage
            createFeed("Plugin3", "Section3"), // No homepage
            createFeed("Plugin4", "Section4", homepageIds = setOf("home2"))
        )

        val result = FeedAssignmentService.getUnassignedFeeds(feeds)

        assertEquals(2, result.size)
        assertEquals("Plugin2", result[0].pluginName)
        assertEquals("Plugin3", result[1].pluginName)
    }

    @Test
    fun `getUnassignedFeeds - returns empty when all feeds are assigned`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("home1")),
            createFeed("Plugin2", "Section2", homepageIds = setOf("home2"))
        )

        val result = FeedAssignmentService.getUnassignedFeeds(feeds)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getUnassignedFeeds - returns all when no feeds are assigned`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1"),
            createFeed("Plugin2", "Section2")
        )

        val result = FeedAssignmentService.getUnassignedFeeds(feeds)

        assertEquals(2, result.size)
    }

    // ======= getFeedsInHomepage tests =======

    @Test
    fun `getFeedsInHomepage - returns feeds assigned to specific homepage`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("home1")),
            createFeed("Plugin2", "Section2", homepageIds = setOf("home1", "home2")),
            createFeed("Plugin3", "Section3", homepageIds = setOf("home2")),
            createFeed("Plugin4", "Section4") // No homepage
        )

        val result = FeedAssignmentService.getFeedsInHomepage(feeds, "home1")

        assertEquals(2, result.size)
        assertEquals("Plugin1", result[0].pluginName)
        assertEquals("Plugin2", result[1].pluginName)
    }

    @Test
    fun `getFeedsInHomepage - returns empty when no feeds in homepage`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("other"))
        )

        val result = FeedAssignmentService.getFeedsInHomepage(feeds, "nonexistent")

        assertTrue(result.isEmpty())
    }

    // ======= clearHomepageAssignments tests =======

    @Test
    fun `clearHomepageAssignments - removes all feeds from homepage`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("home1", "home2")),
            createFeed("Plugin2", "Section2", homepageIds = setOf("home1")),
            createFeed("Plugin3", "Section3", homepageIds = setOf("home2"))
        )

        val result = FeedAssignmentService.clearHomepageAssignments(feeds, "home1")

        assertEquals(setOf("home2"), result[0].homepageIds)
        assertEquals(emptySet(), result[1].homepageIds)
        assertEquals(setOf("home2"), result[2].homepageIds)
    }

    @Test
    fun `clearHomepageAssignments - no effect when homepage has no feeds`() {
        val feeds = listOf(
            createFeed("Plugin1", "Section1", homepageIds = setOf("other"))
        )

        val result = FeedAssignmentService.clearHomepageAssignments(feeds, "nonexistent")

        assertEquals(setOf("other"), result[0].homepageIds)
    }

    // ======= FeedItem tests =======

    @Test
    fun `FeedItem key - creates unique composite key`() {
        val feed = createFeed("PluginA", "SectionB", "/data/path")
        assertEquals("PluginA::SectionB::/data/path", feed.key())
    }

    @Test
    fun `FeedItem isInHomepage - returns true when homepage is assigned`() {
        val feed = createFeed("Plugin", "Section", homepageIds = setOf("home1", "home2"))
        assertTrue(feed.isInHomepage("home1"))
        assertTrue(feed.isInHomepage("home2"))
    }

    @Test
    fun `FeedItem isInHomepage - returns false when homepage not assigned`() {
        val feed = createFeed("Plugin", "Section", homepageIds = setOf("home1"))
        assertFalse(feed.isInHomepage("other"))
    }

    @Test
    fun `FeedItem isInHomepage - returns false when no homepages assigned`() {
        val feed = createFeed("Plugin", "Section")
        assertFalse(feed.isInHomepage("any"))
    }

    // ======= Integration-style tests =======

    @Test
    fun `full workflow - create homepage, assign feeds, remove, clear`() {
        // Start with some unassigned feeds
        var feeds = createFeeds(
            "HQPorner" to "Latest",
            "MissAV" to "Uncensored",
            "PornTrex" to "Popular"
        )

        // Create homepage and assign feeds
        val homepageId = "my_homepage"
        val toAssign = createFeeds("HQPorner" to "Latest", "PornTrex" to "Popular")
        feeds = FeedAssignmentService.addFeedsToHomepage(feeds, toAssign, homepageId)

        // Verify assignments
        assertEquals(2, FeedAssignmentService.getFeedsInHomepage(feeds, homepageId).size)
        assertEquals(1, FeedAssignmentService.getUnassignedFeeds(feeds).size)

        // Remove one feed from homepage
        feeds = FeedAssignmentService.removeFeedFromHomepage(
            feeds,
            createFeed("HQPorner", "Latest"),
            homepageId
        )
        assertEquals(1, FeedAssignmentService.getFeedsInHomepage(feeds, homepageId).size)

        // Clear all assignments when homepage is deleted
        feeds = FeedAssignmentService.clearHomepageAssignments(feeds, homepageId)
        assertEquals(0, FeedAssignmentService.getFeedsInHomepage(feeds, homepageId).size)
    }

    @Test
    fun `multi-homepage assignment - feed can be in multiple homepages`() {
        var feeds = listOf(createFeed("Plugin", "Section"))

        // Assign to first homepage
        feeds = FeedAssignmentService.addFeedsToHomepage(feeds, feeds, "home1")
        assertEquals(setOf("home1"), feeds[0].homepageIds)

        // Assign to second homepage (additive)
        feeds = FeedAssignmentService.addFeedsToHomepage(feeds, feeds, "home2")
        assertEquals(setOf("home1", "home2"), feeds[0].homepageIds)

        // Feed appears in both homepage queries
        assertEquals(1, FeedAssignmentService.getFeedsInHomepage(feeds, "home1").size)
        assertEquals(1, FeedAssignmentService.getFeedsInHomepage(feeds, "home2").size)

        // Remove from one homepage, still in other
        feeds = FeedAssignmentService.removeFeedFromHomepage(feeds, feeds[0], "home1")
        assertEquals(setOf("home2"), feeds[0].homepageIds)
        assertTrue(feeds[0].isInHomepage("home2"))
        assertFalse(feeds[0].isInHomepage("home1"))
    }
}
