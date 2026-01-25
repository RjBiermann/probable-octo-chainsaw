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
}
