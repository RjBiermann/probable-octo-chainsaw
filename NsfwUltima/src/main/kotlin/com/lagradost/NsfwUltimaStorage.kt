package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Storage manager for NSFW Ultima using Cloudstream's global preferences.
 * Uses AcraApplication.getKey/setKey for cross-session persistence.
 * Note: Storage keys use NSFWULTIMA_ prefix for new installs,
 * but also checks PAGEMANAGER_ keys for migration from old versions.
 */
object NsfwUltimaStorage {

    private const val TAG = "NsfwUltimaStorage"
    private const val KEY_FEED_LIST = "NSFWULTIMA_FEED_LIST"
    private const val KEY_SETTINGS = "NSFWULTIMA_SETTINGS"
    // Legacy keys for migration from PageManager
    private const val LEGACY_KEY_FEED_LIST = "PAGEMANAGER_FEED_LIST"
    private const val LEGACY_KEY_SETTINGS = "PAGEMANAGER_SETTINGS"
    private const val LEGACY_KEY_PLUGIN_STATES = "PAGEMANAGER_PLUGIN_STATES"

    /**
     * Load the user's ordered feed list.
     */
    fun loadFeedList(): List<FeedItem> {
        return try {
            // Try new key first, then legacy key
            val json = getKey<String>(KEY_FEED_LIST)
                ?: getKey<String>(LEGACY_KEY_FEED_LIST)
                ?: return emptyList()
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                FeedItem.fromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load feed list", e)
            emptyList()
        }
    }

    /**
     * Save the user's ordered feed list.
     */
    fun saveFeedList(feeds: List<FeedItem>): Boolean {
        return try {
            val array = JSONArray().apply {
                feeds.forEach { put(it.toJson()) }
            }
            setKey(KEY_FEED_LIST, array.toString())
            // Clear legacy key if it exists
            setKey(LEGACY_KEY_FEED_LIST, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save feed list", e)
            false
        }
    }

    // Keep legacy methods for backward compatibility during migration
    fun loadPluginStates(): List<PluginState> {
        return try {
            val json = getKey<String>(LEGACY_KEY_PLUGIN_STATES) ?: return emptyList()
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                PluginState.fromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin states", e)
            emptyList()
        }
    }

    fun savePluginStates(states: List<PluginState>): Boolean {
        return try {
            val array = JSONArray().apply {
                states.forEach { put(it.toJson()) }
            }
            setKey(LEGACY_KEY_PLUGIN_STATES, array.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save plugin states", e)
            false
        }
    }

    /**
     * Migrate from old plugin-centric format to new feed-centric format.
     * Returns the migrated feed list, or null if no migration needed.
     */
    fun migrateFromPluginStates(): List<FeedItem>? {
        val oldStates = loadPluginStates()
        if (oldStates.isEmpty()) return null

        // Convert enabled sections to feed items
        val feeds = oldStates
            .filter { it.enabled }
            .flatMap { plugin ->
                plugin.sections
                    .filter { it.enabled }
                    .sortedByDescending { it.priority }
                    .map { section ->
                        FeedItem(
                            pluginName = plugin.pluginName,
                            sectionName = section.name,
                            sectionData = section.data
                        )
                    }
            }

        if (feeds.isNotEmpty()) {
            saveFeedList(feeds)
            // Clear old data after successful migration
            setKey(LEGACY_KEY_PLUGIN_STATES, null)
        }

        return feeds.ifEmpty { null }
    }

    /**
     * Load global settings.
     */
    fun loadSettings(): NsfwUltimaSettings {
        return try {
            // Try new key first, then legacy key
            val json = getKey<String>(KEY_SETTINGS)
                ?: getKey<String>(LEGACY_KEY_SETTINGS)
                ?: return NsfwUltimaSettings()
            NsfwUltimaSettings.fromJson(JSONObject(json))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load settings", e)
            NsfwUltimaSettings()
        }
    }

    /**
     * Save global settings.
     */
    fun saveSettings(settings: NsfwUltimaSettings): Boolean {
        return try {
            setKey(KEY_SETTINGS, settings.toJson().toString())
            // Clear legacy key if it exists
            setKey(LEGACY_KEY_SETTINGS, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save settings", e)
            false
        }
    }

    /**
     * Clear all stored data.
     */
    fun clearAll() {
        setKey(KEY_FEED_LIST, null)
        setKey(KEY_SETTINGS, null)
        // Also clear legacy keys
        setKey(LEGACY_KEY_FEED_LIST, null)
        setKey(LEGACY_KEY_PLUGIN_STATES, null)
        setKey(LEGACY_KEY_SETTINGS, null)
    }
}
