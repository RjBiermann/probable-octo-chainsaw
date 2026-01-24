package com.lagradost

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "NsfwUltimaModels"

/**
 * Shared constants for NSFW plugin detection.
 */
object NsfwPluginConstants {
    /** Known NSFW plugins by name - used for discovery heuristics */
    val KNOWN_NSFW_PLUGINS = setOf(
        "Fullporner", "HQPorner", "MissAV", "Neporn",
        "Perverzija", "PornHits", "PornTrex", "PornXp"
    )

    /** Keywords that indicate NSFW content */
    val NSFW_KEYWORDS = listOf("porn", "xxx", "adult", "nsfw", "av", "hentai", "jav")
}

/**
 * Represents the state of a discovered plugin with its sections.
 */
data class PluginState(
    val pluginName: String,
    val sections: List<SectionState>,
    val enabled: Boolean = true,
    val priority: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pluginName", pluginName)
        put("enabled", enabled)
        put("priority", priority)
        put("sections", JSONArray().apply {
            sections.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): PluginState? = try {
            val sectionsArray = json.getJSONArray("sections")
            val sections = (0 until sectionsArray.length()).mapNotNull { i ->
                SectionState.fromJson(sectionsArray.getJSONObject(i))
            }
            PluginState(
                pluginName = json.getString("pluginName"),
                sections = sections,
                enabled = json.optBoolean("enabled", true),
                priority = json.optInt("priority", 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PluginState from JSON: ${e.message}")
            null
        }
    }
}

/**
 * Represents a section within a plugin (a homepage category).
 */
data class SectionState(
    val name: String,
    val data: String,
    val enabled: Boolean = false,
    val priority: Int = 0,
    val customLabel: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("data", data)
        put("enabled", enabled)
        put("priority", priority)
        if (customLabel != null) put("customLabel", customLabel)
    }

    companion object {
        fun fromJson(json: JSONObject): SectionState? = try {
            SectionState(
                name = json.getString("name"),
                data = json.getString("data"),
                enabled = json.optBoolean("enabled", false),
                priority = json.optInt("priority", 0),
                customLabel = json.optString("customLabel").ifBlank { null }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SectionState from JSON: ${e.message}")
            null
        }
    }
}

/**
 * Global NSFW Ultima settings.
 */
data class NsfwUltimaSettings(
    val showPluginNames: Boolean = true,
    val searchTimeoutMs: Long = 5000L,
    val maxSearchConcurrency: Int = 4
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("showPluginNames", showPluginNames)
        put("searchTimeoutMs", searchTimeoutMs)
        put("maxSearchConcurrency", maxSearchConcurrency)
    }

    companion object {
        fun fromJson(json: JSONObject): NsfwUltimaSettings = try {
            NsfwUltimaSettings(
                showPluginNames = json.optBoolean("showPluginNames", true),
                searchTimeoutMs = json.optLong("searchTimeoutMs", 5000L),
                maxSearchConcurrency = json.optInt("maxSearchConcurrency", 4)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse NsfwUltimaSettings, using defaults: ${e.message}")
            NsfwUltimaSettings()
        }
    }
}

/**
 * Metadata for aggregated sections, serialized in MainPageRequest.data
 */
data class AggregatedSectionData(
    val pluginName: String,
    val originalName: String,
    val data: String
) {
    fun toJson(): String = JSONObject().apply {
        put("plugin", pluginName)
        put("originalName", originalName)
        put("data", data)
    }.toString()

    companion object {
        fun fromJson(json: String): AggregatedSectionData? = try {
            val obj = JSONObject(json)
            AggregatedSectionData(
                pluginName = obj.getString("plugin"),
                originalName = obj.getString("originalName"),
                data = obj.getString("data")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AggregatedSectionData from JSON: ${e.message}")
            null
        }
    }
}

/**
 * A single feed item in the user's ordered feed list.
 * This is the new feed-centric data model.
 */
data class FeedItem(
    val pluginName: String,
    val sectionName: String,
    val sectionData: String,
    val groupId: String? = null  // Optional group assignment for manual categorization
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pluginName", pluginName)
        put("sectionName", sectionName)
        put("sectionData", sectionData)
        if (groupId != null) put("groupId", groupId)
    }

    /** Create unique key for comparison */
    fun key(): String = "$pluginName::$sectionName::$sectionData"

    companion object {
        fun fromJson(json: JSONObject): FeedItem? = try {
            FeedItem(
                pluginName = json.getString("pluginName"),
                sectionName = json.getString("sectionName"),
                sectionData = json.getString("sectionData"),
                groupId = json.optString("groupId").ifBlank { null }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse FeedItem from JSON: ${e.message}")
            null
        }
    }
}

/**
 * Represents a user-created group of feeds.
 */
data class FeedGroup(
    val id: String,
    val name: String,
    val priority: Int = 0  // Reserved for future sorting (currently follows list order)
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("priority", priority)
    }

    companion object {
        fun fromJson(json: JSONObject): FeedGroup? = try {
            FeedGroup(
                id = json.getString("id"),
                name = json.getString("name"),
                priority = json.optInt("priority", 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse FeedGroup from JSON: ${e.message}")
            null
        }

        /**
         * Create a new group with generated ID.
         */
        fun create(name: String): FeedGroup {
            return FeedGroup(
                id = "group_${java.util.UUID.randomUUID().toString().take(8)}",
                name = name.trim()
            )
        }
    }
}

/**
 * Sealed class for adapter items in grouped view.
 */
sealed class GroupedFeedItem {
    data class Header(
        val group: FeedGroup,
        val feedCount: Int,
        val isExpanded: Boolean
    ) : GroupedFeedItem()

    data class Feed(
        val item: FeedItem,
        val groupId: String?
    ) : GroupedFeedItem()
}

/**
 * Represents an available feed from a discovered plugin (for the picker).
 */
data class AvailableFeed(
    val pluginName: String,
    val sectionName: String,
    val sectionData: String,
    val isAdded: Boolean = false
) {
    fun toFeedItem(): FeedItem = FeedItem(pluginName, sectionName, sectionData)
    fun key(): String = "$pluginName::$sectionName::$sectionData"
}

/**
 * Extension function to create a preview string for logging feed lists.
 * Shows the first few feeds in "plugin:section" format.
 */
fun List<FeedItem>.toPreviewString(count: Int = 3): String =
    take(count).joinToString(", ") { "${it.pluginName}:${it.sectionName}" }
