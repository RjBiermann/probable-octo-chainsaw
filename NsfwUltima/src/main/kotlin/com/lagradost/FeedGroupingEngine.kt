package com.lagradost

import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.UUID

/**
 * Engine for fuzzy grouping of feeds by section name similarity.
 */
object FeedGroupingEngine {

    /**
     * Result of grouping operation.
     */
    data class GroupingResult(
        val groups: List<FeedGroup>,
        val feedsByGroup: Map<String, List<FeedItem>>,
        val ungroupedFeeds: List<FeedItem>
    )

    /**
     * Group feeds by fuzzy name matching.
     * Custom groups take priority, then fuzzy clustering is applied to remaining feeds.
     *
     * @param feeds List of all feeds
     * @param sensitivity Threshold (0-100). Higher = stricter matching, fewer groups
     * @param customGroups User-defined groups (feeds with matching groupId go here first)
     * @return GroupingResult with groups, feed assignments, and ungrouped feeds
     */
    fun groupFeeds(
        feeds: List<FeedItem>,
        sensitivity: Int,
        customGroups: List<FeedGroup>
    ): GroupingResult {
        if (feeds.isEmpty()) {
            return GroupingResult(emptyList(), emptyMap(), emptyList())
        }

        val effectiveSensitivity = sensitivity.coerceIn(30, 100)
        val feedsByGroup = mutableMapOf<String, MutableList<FeedItem>>()
        val resultGroups = mutableListOf<FeedGroup>()

        // Step 1: Assign feeds with explicit groupId to custom groups
        val customGroupMap = customGroups.associateBy { it.id }
        val assignedKeys = mutableSetOf<String>()

        feeds.forEach { feed ->
            if (feed.groupId != null && customGroupMap.containsKey(feed.groupId)) {
                feedsByGroup.getOrPut(feed.groupId) { mutableListOf() }.add(feed)
                assignedKeys.add(feed.key())
            }
        }

        // Add custom groups to results
        resultGroups.addAll(customGroups)

        // Step 2: Cluster remaining feeds by fuzzy matching (with stopwords filtered)
        val unassigned = feeds.filter { it.key() !in assignedKeys }
        val fuzzyClusters = computeFuzzyClusters(unassigned, effectiveSensitivity)

        // Step 3: Create fuzzy groups for multi-feed clusters
        fuzzyClusters.forEach { cluster ->
            if (cluster.size > 1) {
                val groupId = "fuzzy_${UUID.randomUUID().toString().take(8)}"
                val groupName = suggestGroupName(cluster.map { it.sectionName })
                val group = FeedGroup(
                    id = groupId,
                    name = groupName
                )
                resultGroups.add(group)
                feedsByGroup[groupId] = cluster.toMutableList()
            }
        }

        // Step 4: Collect ungrouped feeds (single-item clusters)
        val ungrouped = fuzzyClusters
            .filter { it.size == 1 }
            .flatten()

        return GroupingResult(
            groups = resultGroups.filter { feedsByGroup[it.id]?.isNotEmpty() == true },
            feedsByGroup = feedsByGroup,
            ungroupedFeeds = ungrouped
        )
    }

    /**
     * Compute fuzzy clusters using greedy single-linkage clustering.
     * Each feed joins the first cluster it matches, or starts a new one.
     */
    private fun computeFuzzyClusters(
        feeds: List<FeedItem>,
        sensitivity: Int
    ): List<List<FeedItem>> {
        if (feeds.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<FeedItem>>()
        val clusterRepresentatives = mutableListOf<String>()

        for (feed in feeds) {
            val feedName = feed.sectionName.lowercase().trim()

            // Find best matching cluster
            var bestClusterIndex = -1
            var bestScore = 0

            for ((index, representative) in clusterRepresentatives.withIndex()) {
                val score = FuzzySearch.tokenSetRatio(feedName, representative)
                if (score >= sensitivity && score > bestScore) {
                    bestScore = score
                    bestClusterIndex = index
                }
            }

            if (bestClusterIndex >= 0) {
                clusters[bestClusterIndex].add(feed)
            } else {
                clusters.add(mutableListOf(feed))
                clusterRepresentatives.add(feedName)
            }
        }

        return clusters
    }

    /**
     * Suggest a group name from a list of feed names.
     * Uses the shortest name or extracts common prefix.
     */
    private fun suggestGroupName(names: List<String>): String {
        if (names.isEmpty()) return "Group"
        if (names.size == 1) return names.first()

        // Try common prefix first
        val prefix = longestCommonPrefix(names.map { it.lowercase() })
        if (prefix.length >= 3) {
            return prefix.trim().replaceFirstChar { it.uppercase() }
        }

        // Fall back to shortest name
        return names.minByOrNull { it.length } ?: names.first()
    }

    private fun longestCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        val first = strings.first()
        var prefixLen = first.length

        for (s in strings.drop(1)) {
            prefixLen = minOf(prefixLen, s.length)
            for (i in 0 until prefixLen) {
                if (first[i] != s[i]) {
                    prefixLen = i
                    break
                }
            }
            if (prefixLen == 0) break
        }

        return if (prefixLen > 0) first.substring(0, prefixLen) else ""
    }

    /**
     * Convert grouping result to adapter items list.
     *
     * @param result The grouping result
     * @param collapsedGroupIds Set of group IDs that should be collapsed
     * @return Ordered list of GroupedFeedItem for the adapter
     */
    fun toAdapterItems(
        result: GroupingResult,
        collapsedGroupIds: Set<String>
    ): List<GroupedFeedItem> {
        val items = mutableListOf<GroupedFeedItem>()

        // Add groups with their feeds
        result.groups.forEach { group ->
            val groupFeeds = result.feedsByGroup[group.id] ?: emptyList()
            val isExpanded = group.id !in collapsedGroupIds

            // Add header
            items.add(GroupedFeedItem.Header(
                group = group,
                feedCount = groupFeeds.size,
                isExpanded = isExpanded
            ))

            // Add feeds if expanded
            if (isExpanded) {
                groupFeeds.forEach { feed ->
                    items.add(GroupedFeedItem.Feed(
                        item = feed,
                        groupId = group.id
                    ))
                }
            }
        }

        // Add ungrouped feeds at the end (no header)
        result.ungroupedFeeds.forEach { feed ->
            items.add(GroupedFeedItem.Feed(
                item = feed,
                groupId = null
            ))
        }

        return items
    }

    /**
     * Move a feed to a different group by updating its groupId.
     *
     * @param feed The feed to move
     * @param targetGroupId The target group ID (null to ungroup)
     * @return Updated feed with new groupId
     */
    fun moveFeedToGroup(feed: FeedItem, targetGroupId: String?): FeedItem {
        return feed.copy(groupId = targetGroupId)
    }

    /**
     * Create a new custom group.
     *
     * @param name Display name for the group
     * @return New FeedGroup with unique ID
     */
    fun createCustomGroup(name: String): FeedGroup {
        return FeedGroup.create(name)
    }
}
