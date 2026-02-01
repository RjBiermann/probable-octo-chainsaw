package com.lagradost.common

import android.content.Context
import android.util.Log
import com.lagradost.common.intelligence.BrowsingIntelligence
import com.lagradost.common.intelligence.TagNormalizer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Helper for integrating BrowsingIntelligence into plugins.
 * Each plugin calls these static methods from getMainPage() and search() with minimal boilerplate.
 *
 * For TvType, plugins create a WatchHistoryConfig with their own getKey/setKey lambdas
 * (since CommonLib cannot reference cloudstream3.AcraApplication).
 */
object PluginIntegrationHelper {

    private const val TAG = "PluginIntegration"

    /**
     * Record a search query from search().
     * Call at the beginning of search() before executing the search.
     */
    suspend fun recordSearch(context: Context?, query: String, pluginName: String) {
        if (context == null) {
            Log.w(TAG, "[$pluginName] Skipping recordSearch — no context")
            return
        }
        try {
            val intelligence = BrowsingIntelligence.getInstance(context) ?: return
            intelligence.recordSearch(query, clickedUrl = null, sourcePlugin = pluginName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[$pluginName] Failed to record search: ${e.message}", e)
        }
    }

    /**
     * Record a tag-to-plugin mapping from a category/tag page.
     * Call from getMainPage() when loading a category page with results.
     * This populates the data used by NsfwUltimaForYou's tag-based recommendations.
     */
    suspend fun recordTagSource(context: Context?, tag: String, pluginName: String, categoryUrl: String) {
        if (context == null) {
            Log.w(TAG, "[$pluginName] Skipping recordTagSource — no context")
            return
        }
        if (tag.isBlank()) return
        try {
            val intelligence = BrowsingIntelligence.getInstance(context) ?: return
            val normalized = TagNormalizer.normalize(tag)
            intelligence.recordTagSource(normalized.canonical, pluginName, categoryUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[$pluginName] Failed to record tag source: ${e.message}", e)
        }
    }

    /** Generic section names that shouldn't be recorded as tag sources. */
    private val GENERIC_SECTION_NAMES = setOf(
        "latest", "newest", "recent", "new", "being watched",
        "most popular", "popular", "top rated", "trending", "featured",
        "most viewed", "best", "hot", "homepage", "home"
    )

    /**
     * Record tag source from any homepage section with results.
     * Generic section names (e.g., "Latest", "Most Popular") are skipped since they
     * don't represent meaningful category tags for recommendations.
     */
    suspend fun maybeRecordTagSource(
        context: Context?,
        requestName: String,
        requestData: String,
        pluginName: String,
        hasResults: Boolean
    ) {
        if (!hasResults) return
        if (requestName.lowercase().trim() in GENERIC_SECTION_NAMES) return
        recordTagSource(context, requestName, pluginName, requestData)
    }
}
