package com.lagradost.common.intelligence

/**
 * Data access interface for browsing intelligence.
 * Abstracted for testability — production uses SQLite, tests use in-memory.
 */
interface BrowsingDao {
    fun insertView(entry: ViewHistoryEntry)
    fun insertSearch(query: String, clickedUrl: String?, sourcePlugin: String, timestamp: Long)
    fun insertOrUpdateTagSource(tag: String, pluginName: String, categoryUrl: String, lastSeen: Long)
    fun getRecentViews(limit: Int): List<ViewHistoryEntry>
    fun getSearchSuggestions(partial: String, limit: Int): List<String>
    fun getTagViewCounts(): Map<String, Pair<Int, Long>> // tag -> (count, lastSeen)
    fun getTagViewCounts(tagType: TagType): Map<String, Pair<Int, Long>>
    fun getViewsForTag(tag: String, limit: Int): List<ViewHistoryEntry>
    fun getTagSources(tag: String): List<TagSource>
    fun clearAll(): Boolean
    fun pruneOlderThan(timestampMs: Long)
    fun getDatabaseSize(): Long
    fun deleteViewHistoryEntry(url: String)
    fun deleteViewsByTag(tag: String)
    fun deleteViewsByPerformer(performer: String)
    fun clearViewHistory()
    fun clearTagSources()
    fun stripPerformerTags()
    fun close() {}
}

/**
 * In-memory DAO for testing.
 */
class InMemoryBrowsingDao : BrowsingDao {
    val viewHistory = mutableListOf<ViewHistoryEntry>()
    val searchHistory = mutableListOf<SearchHistoryEntry>()
    val tagSources = mutableListOf<TagSource>()

    override fun insertView(entry: ViewHistoryEntry) {
        viewHistory.add(entry)
    }

    override fun insertSearch(query: String, clickedUrl: String?, sourcePlugin: String, timestamp: Long) {
        searchHistory.add(SearchHistoryEntry(query, clickedUrl, sourcePlugin, timestamp))
    }

    override fun insertOrUpdateTagSource(tag: String, pluginName: String, categoryUrl: String, lastSeen: Long) {
        tagSources.removeAll { it.tag == tag && it.pluginName == pluginName }
        tagSources.add(TagSource(tag, pluginName, categoryUrl, lastSeen))
    }

    override fun getRecentViews(limit: Int): List<ViewHistoryEntry> =
        viewHistory.sortedByDescending { it.timestamp }.take(limit)

    override fun getSearchSuggestions(partial: String, limit: Int): List<String> =
        searchHistory.map { it.query }
            .filter { it.contains(partial, ignoreCase = true) }
            .groupBy { it.lowercase() }
            .entries.sortedByDescending { it.value.size }
            .take(limit)
            .map { it.key }

    override fun getTagViewCounts(): Map<String, Pair<Int, Long>> {
        val result = mutableMapOf<String, Pair<Int, Long>>()
        for (entry in viewHistory) {
            for (tag in entry.tags) {
                val key = tag.lowercase()
                val existing = result[key]
                val count = (existing?.first ?: 0) + 1
                val lastSeen = maxOf(existing?.second ?: 0L, entry.timestamp)
                result[key] = Pair(count, lastSeen)
            }
        }
        return result
    }

    override fun getTagViewCounts(tagType: TagType): Map<String, Pair<Int, Long>> {
        val result = mutableMapOf<String, Pair<Int, Long>>()
        for (entry in viewHistory) {
            val typedTags = entry.tagTypes
            if (typedTags.isNullOrBlank()) continue
            val prefixed = typedTags.trim(',').split(",").filter { it.isNotBlank() }
            for (p in prefixed) {
                val nt = NormalizedTag.fromPrefixed(p) ?: continue
                if (nt.type != tagType) continue
                val key = nt.canonical
                val existing = result[key]
                val count = (existing?.first ?: 0) + 1
                val lastSeen = maxOf(existing?.second ?: 0L, entry.timestamp)
                result[key] = Pair(count, lastSeen)
            }
        }
        return result
    }

    override fun getViewsForTag(tag: String, limit: Int): List<ViewHistoryEntry> =
        viewHistory.filter { entry -> entry.tags.any { it.equals(tag, ignoreCase = true) } }
            .sortedByDescending { it.timestamp }
            .take(limit)

    override fun getTagSources(tag: String): List<TagSource> =
        tagSources.filter { it.tag.equals(tag, ignoreCase = true) }

    override fun clearAll(): Boolean {
        viewHistory.clear()
        searchHistory.clear()
        tagSources.clear()
        return true
    }

    override fun pruneOlderThan(timestampMs: Long) {
        viewHistory.removeAll { it.timestamp < timestampMs }
        searchHistory.removeAll { it.timestamp < timestampMs }
        tagSources.removeAll { it.lastSeen < timestampMs }
    }

    override fun getDatabaseSize(): Long = 0L

    override fun deleteViewHistoryEntry(url: String) {
        viewHistory.removeAll { it.url == url }
    }

    override fun deleteViewsByTag(tag: String) {
        val key = tag.lowercase()
        viewHistory.removeAll { it.tags.any { t -> t.equals(key, ignoreCase = true) } || it.tagTypes?.lowercase()?.contains(",$key,") == true }
    }

    override fun deleteViewsByPerformer(performer: String) {
        val key = performer.lowercase()
        viewHistory.removeAll { it.tagTypes?.lowercase()?.contains(",p:$key,") == true }
    }

    override fun clearViewHistory() {
        viewHistory.clear()
    }

    override fun clearTagSources() {
        tagSources.clear()
    }

    override fun stripPerformerTags() {
        val updated = viewHistory.map { entry ->
            val cleanedTagTypes = entry.tagTypes?.let { types ->
                val parts = types.split(",").filter { it.isNotBlank() && !it.startsWith("p:") }
                if (parts.isEmpty()) null else ",${parts.joinToString(",")},"}
            entry.copy(tagTypes = cleanedTagTypes)
        }
        viewHistory.clear()
        viewHistory.addAll(updated)
    }
}
