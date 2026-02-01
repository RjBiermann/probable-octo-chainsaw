package com.lagradost.common.intelligence

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pluggable tag classifier for runtime tag type resolution (e.g., via HTTP lookup).
 * Return null to fall back to [TagNormalizer] dictionary.
 */
fun interface TagClassifier {
    fun classify(tag: String): TagType?
}

/**
 * Main API for browsing intelligence.
 * All data is local-only (never leaves the device).
 *
 * Recording methods are suspend and run on [Dispatchers.IO] to avoid ANRs.
 * Query methods backed by SQLite (search suggestions, tag sources) should be called
 * from a background thread. Methods backed by SharedPreferences (affinity tags,
 * favorites, bookmarks) are lightweight and safe on the main thread for small data sets.
 *
 * Usage:
 * ```kotlin
 * val intelligence = BrowsingIntelligence.getInstance(context)
 * val suggestions = intelligence.getSuggestedSearches("blo", 5)
 * ```
 */
class BrowsingIntelligence internal constructor(
    private val dao: BrowsingDao,
    private val dataBridge: CloudstreamDataBridge? = null,
    private val getKey: ((String) -> String?)? = null,
    private val setKey: ((String, String?) -> Unit)? = null
) {

    /** Optional classifier for resolving unknown tags at runtime. */
    @Volatile
    private var _tagClassifier: TagClassifier? = null
    var tagClassifier: TagClassifier?
        get() = _tagClassifier
        @Synchronized set(value) { _tagClassifier = value }

    companion object {
        private const val PAUSED_TAG_PREFIX = "nsfw_common/paused_tags/"
        private const val PAUSED_PERFORMER_PREFIX = "nsfw_common/paused_performers/"

        // Cloudstream's internal SharedPreferences file and account index key.
        // These are Cloudstream implementation details — if Cloudstream changes them,
        // update these constants accordingly.
        private const val CLOUDSTREAM_PREFS_NAME = "rebuild_preference"
        private const val CLOUDSTREAM_ACCOUNT_KEY = "data_store_helper/account_key_index"

        @Volatile
        private var instance: BrowsingIntelligence? = null
        @Volatile
        private var initFailedAt: Long = 0L
        @Volatile
        private var lastAccountIndex: Int = -1
        private const val INIT_RETRY_MS = 60_000L // Retry after 60s

        fun getInstance(context: Context): BrowsingIntelligence? {
            if (initFailedAt != 0L && System.currentTimeMillis() - initFailedAt < INIT_RETRY_MS) {
                Log.w("BrowsingIntelligence", "Init previously failed, skipping until retry window expires")
                return null
            }
            return synchronized(this) {
                if (initFailedAt != 0L && System.currentTimeMillis() - initFailedAt < INIT_RETRY_MS) return null
                // Detect account switch: re-read account index and reset if changed
                val currentInstance = instance
                if (currentInstance != null) {
                    try {
                        val prefs = context.applicationContext.getSharedPreferences(CLOUDSTREAM_PREFS_NAME, Context.MODE_PRIVATE)
                        val currentAccount = prefs.getInt(CLOUDSTREAM_ACCOUNT_KEY, 0)
                        if (currentAccount != lastAccountIndex) {
                            Log.i("BrowsingIntelligence", "Account switched from $lastAccountIndex to $currentAccount, resetting instance")
                            resetInstance()
                        }
                    } catch (e: Exception) { Log.w("BrowsingIntelligence", "Account detection failed, using existing instance: ${e.message}") }
                }
                instance ?: try {
                    val appContext = context.applicationContext
                    val dbHelper = BrowsingDbHelper(appContext)
                    val dao = SqliteBrowsingDao(dbHelper)
                    val prefs = appContext.getSharedPreferences(
                        CLOUDSTREAM_PREFS_NAME, Context.MODE_PRIVATE
                    )
                    // Validate that the prefs file exists and is readable
                    // (if Cloudstream renames the file, prefs will be empty)
                    val accountIndex = prefs.getInt(CLOUDSTREAM_ACCOUNT_KEY, 0)
                    val bridge = SharedPrefsDataBridge(prefs, accountIndex)
                    // Verify bridge can read at least one data category; log warning if not
                    if (prefs.all.isEmpty()) {
                        Log.w("BrowsingIntelligence", "Cloudstream SharedPreferences '$CLOUDSTREAM_PREFS_NAME' is empty — data bridge will have no engagement data")
                    }
                    val getKeyFn: (String) -> String? = { key ->
                        prefs.getString(key, null)
                    }
                    val setKeyFn: (String, String?) -> Unit = { key, value ->
                        if (value == null) prefs.edit().remove(key).apply()
                        else prefs.edit().putString(key, value).apply()
                    }
                    lastAccountIndex = accountIndex
                    BrowsingIntelligence(dao, bridge, getKeyFn, setKeyFn).also { instance = it }
                } catch (e: Exception) {
                    Log.e("BrowsingIntelligence", "Failed to initialize browsing intelligence database (will retry after ${INIT_RETRY_MS/1000}s)", e)
                    initFailedAt = System.currentTimeMillis()
                    null
                }
            }
        }

        /**
         * Reset the singleton, closing the underlying database.
         * Useful for testing and plugin reload scenarios.
         */
        @Synchronized
        fun resetInstance() {
            instance?.close()
            instance = null
            initFailedAt = 0L
            lastAccountIndex = -1
        }
    }

    /** Close underlying database. Called by [resetInstance]. */
    fun close() {
        dao.close()
    }

    // --- Recording (suspend to avoid ANR) ---

    suspend fun recordSearch(query: String, clickedUrl: String?, sourcePlugin: String) = withContext(Dispatchers.IO) {
        dao.insertSearch(query, clickedUrl, sourcePlugin, System.currentTimeMillis())
    }

    suspend fun recordTagSource(tag: String, pluginName: String, categoryUrl: String) = withContext(Dispatchers.IO) {
        dao.insertOrUpdateTagSource(tag, pluginName, categoryUrl, System.currentTimeMillis())
    }

    // --- Querying ---

    fun getSuggestedSearches(partial: String, limit: Int = 8): List<String> =
        dao.getSearchSuggestions(partial, limit)

    /**
     * Get top affinity tags derived from Cloudstream engagement signals
     * (bookmarks, favorites). Returns empty if no bridge is available.
     */
    fun getAffinityTags(limit: Int = 10): List<TagAffinity> =
        getAffinityTagsInternal(limit, includePaused = false)

    private fun getAffinityTagsInternal(limit: Int, includePaused: Boolean): List<TagAffinity> {
        val bridge = dataBridge ?: return emptyList()
        return getEngagementAffinityTags(bridge, limit = Int.MAX_VALUE)
            .let { if (includePaused) it else it.filter { tag -> !isTagPaused(tag.tag) } }
            .take(limit)
    }

    private fun getEngagementAffinityTags(bridge: CloudstreamDataBridge, limit: Int): List<TagAffinity> {
        val now = System.currentTimeMillis()
        val tagStats = mutableMapOf<String, TagEngagementStats>()

        for (fav in bridge.getFavorites()) {
            val timestamp = if (fav.favoritesTime > 0) fav.favoritesTime else now
            for (tag in fav.tags) {
                val key = tag.lowercase()
                val stats = tagStats.getOrPut(key) { TagEngagementStats() }
                stats.favoriteCount++
                stats.lastEngagement = maxOf(stats.lastEngagement, timestamp)
            }
        }

        for (bookmark in bridge.getBookmarks()) {
            val timestamp = if (bookmark.bookmarkedTime > 0) bookmark.bookmarkedTime else now
            for (tag in bookmark.tags) {
                val key = tag.lowercase()
                val stats = tagStats.getOrPut(key) { TagEngagementStats() }
                when (bookmark.watchType) {
                    WatchType.COMPLETED -> stats.completedCount++
                    WatchType.WATCHING -> stats.watchingCount++
                    WatchType.PLANTOWATCH, WatchType.ONHOLD -> stats.watchingCount++
                    else -> {}
                }
                stats.lastEngagement = maxOf(stats.lastEngagement, timestamp)
            }
        }

        return tagStats.map { (tag, stats) ->
            val daysSince = ((now - stats.lastEngagement) / (24 * 60 * 60 * 1000)).toInt()
            val score = calculateEngagementAffinity(
                stats.favoriteCount, stats.completedCount, stats.watchingCount, daysSince
            )
            TagAffinity(tag, score, stats.totalCount, stats.lastEngagement)
        }.filter { it.score > 0f }
         .sortedByDescending { it.score }
         .take(limit)
    }

    private class TagEngagementStats {
        var favoriteCount = 0
        var completedCount = 0
        var watchingCount = 0
        var lastEngagement = 0L
        val totalCount get() = favoriteCount + completedCount + watchingCount
    }

    fun getAffinityGenres(limit: Int = 10): List<TagAffinity> =
        getTypedAffinityTags(TagType.GENRE, limit)

    fun getAffinityBodyTypes(limit: Int = 10): List<TagAffinity> =
        getTypedAffinityTags(TagType.BODY_TYPE, limit)

    fun getAffinityPerformers(limit: Int = 10): List<TagAffinity> =
        getTypedAffinityTagsInternal(TagType.PERFORMER, limit, includePaused = false)

    private fun getTypedAffinityTags(type: TagType, limit: Int): List<TagAffinity> =
        getTypedAffinityTagsInternal(type, limit, includePaused = false)

    private fun getTypedAffinityTagsInternal(type: TagType, limit: Int, includePaused: Boolean): List<TagAffinity> {
        val bridge = dataBridge ?: return emptyList()
        val classifier = tagClassifier
        return getEngagementAffinityTags(bridge, limit = Int.MAX_VALUE)
            .filter { (classifier?.classify(it.tag) ?: TagNormalizer.normalize(it.tag).type) == type }
            .map { it.copy(score = it.score * type.weight) }
            .let { if (includePaused) it else it.filter { tag ->
                when (type) {
                    TagType.PERFORMER -> !isPerformerPaused(tag.tag)
                    else -> !isTagPaused(tag.tag)
                }
            }}
            .take(limit)
    }

    fun getTagSources(tag: String): List<TagSource> =
        dao.getTagSources(tag)

    // --- Cross-plugin discovery ---

    /**
     * Get recommended plugins for a given tag, excluding the current plugin.
     * Returns tag sources from other plugins that have content for this tag.
     */
    fun getRecommendedPluginsForTag(tag: String, excludePlugin: String): List<TagSource> =
        dao.getTagSources(tag).filter { !it.pluginName.equals(excludePlugin, ignoreCase = true) }

    /**
     * Get plugin names the user hasn't explored, based on which plugins
     * are NOT present in their tag source history.
     *
     * @param allPluginNames all available plugin names
     * @param topTagCount number of top affinity tags to check for known plugins
     */
    fun getUnexploredPlugins(allPluginNames: List<String>, topTagCount: Int = 3): List<String> {
        val topTags = getAffinityTags(topTagCount)
        val knownPlugins = topTags.flatMap { dao.getTagSources(it.tag) }
            .map { it.pluginName }.toSet()
        return allPluginNames.filter { it !in knownPlugins }
    }

    // --- Cloudstream engagement data ---

    /** Videos the user is actively watching (has resume position in Cloudstream). */
    fun getContinueWatching(limit: Int = 12): List<ResumeEntry> {
        val bridge = dataBridge ?: return emptyList()
        return bridge.getResumeWatchingIds()
            .mapNotNull { bridge.getResumeEntry(it) }
            .sortedByDescending { it.updateTime }
            .take(limit)
    }

    /** User's explicitly favorited videos. */
    fun getUserFavorites(): List<FavoriteEntry> =
        dataBridge?.getFavorites() ?: emptyList()

    /** User's bookmarked videos (all watch types). */
    fun getUserBookmarks(): List<BookmarkEntry> =
        dataBridge?.getBookmarks() ?: emptyList()

    /**
     * Engagement weight for a video ID (Cloudstream's hashCode-based ID).
     * Returns a multiplier ≥ 0.1 that reflects how strongly the user engaged:
     * - 1.0 = no data (default, backwards compatible)
     * - Higher = more engagement (favorites, completed, watch progress)
     */
    fun getEngagementWeight(videoId: Int): Float {
        val bridge = dataBridge ?: return 1.0f
        var weight = 1.0f

        val pos = bridge.getVideoPosition(videoId)
        if (pos != null && pos.watchPercentage > 0.5f) {
            weight += 0.5f * pos.watchPercentage
        }

        when (bridge.getWatchType(videoId)) {
            WatchType.COMPLETED -> weight += 1.0f
            WatchType.WATCHING -> weight += 0.5f
            WatchType.PLANTOWATCH -> weight += 0.25f
            WatchType.DROPPED -> weight -= 0.5f
            else -> {}
        }

        if (bridge.isFavorited(videoId)) weight += 1.5f

        return weight.coerceAtLeast(0.1f)
    }

    // --- Management ---

    fun deleteAffinityTag(tag: String) = dao.deleteViewsByTag(tag)

    fun deleteAffinityPerformer(performer: String) = dao.deleteViewsByPerformer(performer)

    fun clearAffinityTags() {
        dao.clearTagSources()
    }

    fun clearAffinityPerformers() = dao.stripPerformerTags()

    fun clearAll(): Boolean = dao.clearAll()

    fun pruneOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        dao.pruneOlderThan(cutoff)
    }

    fun getDatabaseSize(): Long = dao.getDatabaseSize()

    // --- Pause support ---

    fun setTagPaused(tag: String, paused: Boolean) {
        if (paused) setKey?.invoke("$PAUSED_TAG_PREFIX$tag", "true")
        else setKey?.invoke("$PAUSED_TAG_PREFIX$tag", null)
    }

    fun setPerformerPaused(performer: String, paused: Boolean) {
        if (paused) setKey?.invoke("$PAUSED_PERFORMER_PREFIX$performer", "true")
        else setKey?.invoke("$PAUSED_PERFORMER_PREFIX$performer", null)
    }

    fun isTagPaused(tag: String): Boolean =
        getKey?.invoke("$PAUSED_TAG_PREFIX$tag")?.toBoolean() ?: false

    fun isPerformerPaused(performer: String): Boolean =
        getKey?.invoke("$PAUSED_PERFORMER_PREFIX$performer")?.toBoolean() ?: false

    // --- No-limit queries (include paused items for management UI) ---

    fun getAllAffinityTags(): List<TagAffinity> = getAffinityTagsInternal(Int.MAX_VALUE, includePaused = true)

    fun getAllAffinityPerformers(): List<TagAffinity> = getTypedAffinityTagsInternal(TagType.PERFORMER, Int.MAX_VALUE, includePaused = true)

    /**
     * Clears all paused tag/performer state from storage.
     * Used by factory reset to remove preference keys that [clearAll] doesn't touch.
     */
    fun clearAllPausedState() {
        val allTags = getAllAffinityTags()
        val allPerformers = getAllAffinityPerformers()
        for (t in allTags) setKey?.invoke("$PAUSED_TAG_PREFIX${t.tag}", null)
        for (p in allPerformers) setKey?.invoke("$PAUSED_PERFORMER_PREFIX${p.tag}", null)
    }
}
