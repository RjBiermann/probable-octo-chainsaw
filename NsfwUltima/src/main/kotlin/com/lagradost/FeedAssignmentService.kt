package com.lagradost

/**
 * Service for managing feed-to-homepage assignments.
 * Feeds can be assigned to multiple homepages.
 *
 * This is a pure Kotlin object with no Android dependencies for testability.
 */
object FeedAssignmentService {

    /**
     * Add feeds to a homepage (additive - doesn't remove existing assignments).
     *
     * @param allFeeds All feeds in the system
     * @param feedsToAssign Feeds to assign (matched by key)
     * @param homepageId Target homepage ID
     * @return Updated list with assignments applied
     */
    fun addFeedsToHomepage(
        allFeeds: List<FeedItem>,
        feedsToAssign: List<FeedItem>,
        homepageId: String
    ): List<FeedItem> {
        val keysToAssign = feedsToAssign.map { it.key() }.toSet()
        return allFeeds.map { feed ->
            if (feed.key() in keysToAssign) {
                feed.copy(homepageIds = feed.homepageIds + homepageId)
            } else {
                feed
            }
        }
    }

    /**
     * Set the exact homepages for a single feed (replaces all existing assignments).
     *
     * @param allFeeds All feeds in the system
     * @param feedToUpdate Feed to update (by key)
     * @param homepageIds New set of homepage IDs
     * @return Updated list with feed's homepage assignments replaced
     */
    fun setFeedHomepages(
        allFeeds: List<FeedItem>,
        feedToUpdate: FeedItem,
        homepageIds: Set<String>
    ): List<FeedItem> {
        return allFeeds.map { feed ->
            if (feed.key() == feedToUpdate.key()) {
                feed.copy(homepageIds = homepageIds)
            } else {
                feed
            }
        }
    }

    /**
     * Remove a feed from a specific homepage.
     *
     * @param allFeeds All feeds in the system
     * @param feedToRemove Feed to remove (by key)
     * @param homepageId Homepage to remove the feed from
     * @return Updated list with feed removed from the homepage
     */
    fun removeFeedFromHomepage(
        allFeeds: List<FeedItem>,
        feedToRemove: FeedItem,
        homepageId: String
    ): List<FeedItem> {
        return allFeeds.map { feed ->
            if (feed.key() == feedToRemove.key()) {
                feed.copy(homepageIds = feed.homepageIds - homepageId)
            } else {
                feed
            }
        }
    }

    /**
     * Remove a feed from all homepages (clear all assignments).
     *
     * @param allFeeds All feeds in the system
     * @param feedToUnassign Feed to unassign (by key)
     * @return Updated list with feed removed from all homepages
     */
    fun unassignFeedFromAll(
        allFeeds: List<FeedItem>,
        feedToUnassign: FeedItem
    ): List<FeedItem> {
        return allFeeds.map { feed ->
            if (feed.key() == feedToUnassign.key()) {
                feed.copy(homepageIds = emptySet())
            } else {
                feed
            }
        }
    }

    /**
     * Get all unassigned feeds (not assigned to any homepage).
     *
     * @param allFeeds All feeds in the system
     * @return List of feeds without any homepage assignment
     */
    fun getUnassignedFeeds(allFeeds: List<FeedItem>): List<FeedItem> {
        return allFeeds.filter { it.homepageIds.isEmpty() }
    }

    /**
     * Get feeds assigned to a specific homepage.
     *
     * @param allFeeds All feeds in the system
     * @param homepageId Homepage ID to filter by
     * @return List of feeds in the homepage
     */
    fun getFeedsInHomepage(allFeeds: List<FeedItem>, homepageId: String): List<FeedItem> {
        return allFeeds.filter { it.isInHomepage(homepageId) }
    }

    /**
     * Remove all feeds from a specific homepage (when homepage is deleted).
     *
     * @param allFeeds All feeds in the system
     * @param homepageId Homepage ID to clear
     * @return Updated list with all feeds removed from this homepage
     */
    fun clearHomepageAssignments(
        allFeeds: List<FeedItem>,
        homepageId: String
    ): List<FeedItem> {
        return allFeeds.map { feed ->
            if (feed.isInHomepage(homepageId)) {
                feed.copy(homepageIds = feed.homepageIds - homepageId)
            } else {
                feed
            }
        }
    }

    // Legacy compatibility aliases
    @Deprecated("Use addFeedsToHomepage instead", ReplaceWith("addFeedsToHomepage(allFeeds, feedsToAssign, groupId)"))
    fun assignFeedsToGroup(allFeeds: List<FeedItem>, feedsToAssign: List<FeedItem>, groupId: String) =
        addFeedsToHomepage(allFeeds, feedsToAssign, groupId)

    @Deprecated("Use unassignFeedFromAll instead", ReplaceWith("unassignFeedFromAll(allFeeds, feedToUngroup)"))
    fun ungroupFeed(allFeeds: List<FeedItem>, feedToUngroup: FeedItem) =
        unassignFeedFromAll(allFeeds, feedToUngroup)

    @Deprecated("Use getFeedsInHomepage instead", ReplaceWith("getFeedsInHomepage(allFeeds, groupId)"))
    fun getFeedsInGroup(allFeeds: List<FeedItem>, groupId: String) =
        getFeedsInHomepage(allFeeds, groupId)

    @Deprecated("Use clearHomepageAssignments instead", ReplaceWith("clearHomepageAssignments(allFeeds, groupId)"))
    fun clearGroupAssignments(allFeeds: List<FeedItem>, groupId: String) =
        clearHomepageAssignments(allFeeds, groupId)

    // ========================================================================
    // Complex operations (extracted from UI layer for testability)
    // ========================================================================

    /**
     * Result of updating a homepage's feed selection.
     *
     * @property updatedFeeds All feeds after transformation (reflects complete state)
     * @property feedsInHomepage Feeds selected for this homepage (created from AvailableFeed,
     *           may not reflect full homepageIds if feed exists in other homepages - use
     *           updatedFeeds for complete state)
     */
    data class HomepageUpdateResult(
        val updatedFeeds: List<FeedItem>,
        val feedsInHomepage: List<FeedItem>
    )

    /**
     * Updates the complete feed list when a homepage's feed selection changes.
     * This is a pure function with no side effects - ideal for testing.
     *
     * Logic:
     * 1. If editing existing homepage: remove this homepage from all current feeds
     * 2. Build homepage feeds from selected AvailableFeed objects
     * 3. Merge selected feeds with existing feeds (add homepage to existing or create new)
     * 4. Remove orphaned feeds (feeds with no homepage assignments)
     *
     * @param currentFeeds All feeds currently in the system
     * @param selectedFeeds The feeds selected for this homepage
     * @param homepageId The homepage being edited
     * @param isExistingHomepage True if editing, false if creating new
     * @return Updated feed list and the feeds specifically in this homepage
     */
    fun updateHomepageFeedSelection(
        currentFeeds: List<FeedItem>,
        selectedFeeds: List<AvailableFeed>,
        homepageId: String,
        isExistingHomepage: Boolean
    ): HomepageUpdateResult {
        // Use map for O(1) lookups instead of O(n) list scans
        val feedsByKey = currentFeeds.associateBy { it.key() }.toMutableMap()

        // Step 1: If editing existing, remove this homepage from all feeds
        if (isExistingHomepage) {
            feedsByKey.replaceAll { _, feed ->
                if (feed.isInHomepage(homepageId)) {
                    feed.copy(homepageIds = feed.homepageIds - homepageId)
                } else {
                    feed
                }
            }
        }

        // Step 2: Build homepage feeds from selection
        val homepageFeeds = selectedFeeds.map { availableFeed ->
            FeedItem(
                pluginName = availableFeed.pluginName,
                sectionName = availableFeed.sectionName,
                sectionData = availableFeed.sectionData,
                homepageIds = setOf(homepageId)
            )
        }

        // Step 3: Merge selected feeds - O(m) instead of O(n*m)
        homepageFeeds.forEach { homepageFeed ->
            val key = homepageFeed.key()
            val existing = feedsByKey[key]
            if (existing != null) {
                // Update existing feed to include this homepage
                feedsByKey[key] = existing.copy(homepageIds = existing.homepageIds + homepageId)
            } else {
                // Add new feed
                feedsByKey[key] = homepageFeed
            }
        }

        // Step 4: Remove orphaned feeds (no homepage assignments)
        val cleanedFeeds = feedsByKey.values.filter { it.homepageIds.isNotEmpty() }

        return HomepageUpdateResult(
            updatedFeeds = cleanedFeeds,
            feedsInHomepage = homepageFeeds
        )
    }

    /**
     * Deletes a homepage and cleans up all feed assignments.
     *
     * @param currentFeeds All feeds in the system
     * @param homepageId The homepage being deleted
     * @return Updated feed list with orphaned feeds removed
     */
    fun deleteHomepageAndCleanup(
        currentFeeds: List<FeedItem>,
        homepageId: String
    ): List<FeedItem> {
        // Remove homepage from all feeds
        val updatedFeeds = clearHomepageAssignments(currentFeeds, homepageId)
        // Remove orphaned feeds
        return updatedFeeds.filter { it.homepageIds.isNotEmpty() }
    }
}
