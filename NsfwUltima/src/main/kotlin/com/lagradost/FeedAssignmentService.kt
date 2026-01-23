package com.lagradost

/**
 * Service for managing feed-to-group assignments.
 * Provides methods for manual assignment and ungrouping.
 *
 * This is a pure Kotlin object with no Android dependencies for testability.
 */
object FeedAssignmentService {

    /**
     * Manually assign specific feeds to a group.
     *
     * @param allFeeds All feeds in the system
     * @param feedsToAssign Feeds to assign (matched by key)
     * @param groupId Target group ID
     * @return Updated list with assignments applied
     */
    fun assignFeedsToGroup(
        allFeeds: List<FeedItem>,
        feedsToAssign: List<FeedItem>,
        groupId: String
    ): List<FeedItem> {
        val keysToAssign = feedsToAssign.map { it.key() }.toSet()
        return allFeeds.map { feed ->
            if (feed.key() in keysToAssign) {
                feed.copy(groupId = groupId)
            } else {
                feed
            }
        }
    }

    /**
     * Remove a feed from its group (set groupId to null).
     *
     * @param allFeeds All feeds in the system
     * @param feedToUngroup Feed to ungroup (by key)
     * @return Updated list with feed ungrouped
     */
    fun ungroupFeed(
        allFeeds: List<FeedItem>,
        feedToUngroup: FeedItem
    ): List<FeedItem> {
        return allFeeds.map { feed ->
            if (feed.key() == feedToUngroup.key()) {
                feed.copy(groupId = null)
            } else {
                feed
            }
        }
    }

    /**
     * Get all unassigned feeds (groupId == null).
     *
     * @param allFeeds All feeds in the system
     * @return List of feeds without group assignment
     */
    fun getUnassignedFeeds(allFeeds: List<FeedItem>): List<FeedItem> {
        return allFeeds.filter { it.groupId == null }
    }

    /**
     * Get feeds assigned to a specific group.
     *
     * @param allFeeds All feeds in the system
     * @param groupId Group ID to filter by
     * @return List of feeds in the group
     */
    fun getFeedsInGroup(allFeeds: List<FeedItem>, groupId: String): List<FeedItem> {
        return allFeeds.filter { it.groupId == groupId }
    }

    /**
     * Clear all assignments for a specific group (ungroup all feeds in it).
     *
     * @param allFeeds All feeds in the system
     * @param groupId Group ID to clear
     * @return Updated list with all feeds ungrouped from this group
     */
    fun clearGroupAssignments(
        allFeeds: List<FeedItem>,
        groupId: String
    ): List<FeedItem> {
        return allFeeds.map { feed ->
            if (feed.groupId == groupId) {
                feed.copy(groupId = null)
            } else {
                feed
            }
        }
    }
}
