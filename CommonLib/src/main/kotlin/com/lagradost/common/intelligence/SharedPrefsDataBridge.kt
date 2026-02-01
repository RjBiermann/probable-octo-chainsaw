package com.lagradost.common.intelligence

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

/**
 * Reads Cloudstream's user engagement data directly from SharedPreferences.
 *
 * Cloudstream stores all user data as JSON strings in SharedPreferences,
 * prefixed by the current account index (e.g., "0/video_pos_dur/123").
 *
 * Video IDs are computed as: url.replace(mainUrl, "").replace("/", "").hashCode()
 */
class SharedPrefsDataBridge(
    private val prefs: SharedPreferences,
    private val accountIndex: Int = 0
) : CloudstreamDataBridge {

    companion object {
        private const val TAG = "SharedPrefsDataBridge"
        private const val VIDEO_POS_DUR = "video_pos_dur"
        private const val RESULT_WATCH_STATE = "result_watch_state"
        private const val RESULT_WATCH_STATE_DATA = "result_watch_state_data"
        private const val RESULT_FAVORITES_STATE_DATA = "result_favorites_state_data"
        private const val RESULT_RESUME_WATCHING = "result_resume_watching_2"
    }

    private fun key(folder: String, id: Any) = "$accountIndex/$folder/$id"

    private fun readJson(key: String): JSONObject? {
        return try {
            val json = prefs.getString(key, null) ?: return null
            JSONObject(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse $key: ${e.message}")
            null
        }
    }

    // Cache pref keys with TTL so new bookmarks/favorites are picked up
    @Volatile private var cachedKeys: Set<String>? = null
    @Volatile private var cachedKeysTime: Long = 0L
    private val keysCacheTtlMs = 60_000L // Refresh keys every 60s

    private fun getAllKeys(): Set<String> {
        val now = System.currentTimeMillis()
        val keys = cachedKeys
        if (keys != null && now - cachedKeysTime < keysCacheTtlMs) {
            return keys
        }
        return try {
            prefs.all.keys.also {
                cachedKeys = it
                cachedKeysTime = now
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read pref keys", e)
            cachedKeys ?: emptySet()
        }
    }

    private fun keysInFolder(folder: String): List<String> {
        val prefix = "$accountIndex/$folder/"
        return getAllKeys().filter { it.startsWith(prefix) }
    }

    override fun isFavorited(videoId: Int): Boolean {
        val key = key(RESULT_FAVORITES_STATE_DATA, videoId)
        return prefs.contains(key)
    }

    private fun idsInFolder(folder: String): List<Int> {
        val prefix = "$accountIndex/$folder/"
        return keysInFolder(folder).mapNotNull {
            it.removePrefix(prefix).toIntOrNull()
        }
    }

    override fun getVideoPosition(videoId: Int): VideoPosition? {
        val json = readJson(key(VIDEO_POS_DUR, videoId)) ?: return null
        return VideoPosition(json.optLong("position", 0), json.optLong("duration", 0))
    }

    private fun JSONObject.getStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it) }
    }

    override fun getResumeWatchingIds(): List<Int> = idsInFolder(RESULT_RESUME_WATCHING)

    override fun getResumeEntry(parentId: Int): ResumeEntry? {
        val json = readJson(key(RESULT_RESUME_WATCHING, parentId)) ?: return null
        return ResumeEntry(
            parentId = json.optInt("parentId", parentId),
            episodeId = if (json.has("episodeId") && !json.isNull("episodeId")) json.optInt("episodeId") else null,
            updateTime = json.optLong("updateTime", 0)
        )
    }

    override fun getBookmarks(): List<BookmarkEntry> {
        return idsInFolder(RESULT_WATCH_STATE_DATA).mapNotNull { id ->
            val json = readJson(key(RESULT_WATCH_STATE_DATA, id)) ?: return@mapNotNull null
            val watchType = getWatchType(id)
            val tags = json.getStringList("tags")
            BookmarkEntry(
                id = json.optInt("id", id),
                name = json.optString("name", ""),
                url = json.optString("url", ""),
                apiName = json.optString("apiName", ""),
                watchType = watchType,
                posterUrl = if (json.isNull("posterUrl")) null else json.optString("posterUrl"),
                tags = tags,
                bookmarkedTime = json.optLong("bookmarkedTime", 0L)
            )
        }
    }

    override fun getBookmarksByType(type: WatchType): List<BookmarkEntry> =
        getBookmarks().filter { it.watchType == type }

    override fun getFavorites(): List<FavoriteEntry> {
        return idsInFolder(RESULT_FAVORITES_STATE_DATA).mapNotNull { id ->
            val json = readJson(key(RESULT_FAVORITES_STATE_DATA, id)) ?: return@mapNotNull null
            val tags = json.getStringList("tags")
            FavoriteEntry(
                id = json.optInt("id", id),
                name = json.optString("name", ""),
                url = json.optString("url", ""),
                apiName = json.optString("apiName", ""),
                posterUrl = if (json.isNull("posterUrl")) null else json.optString("posterUrl"),
                tags = tags,
                favoritesTime = json.optLong("favoritesTime", 0L)
            )
        }
    }

    override fun getWatchType(videoId: Int): WatchType {
        val id = try {
            prefs.getInt(key(RESULT_WATCH_STATE, videoId), WatchType.NONE.internalId)
        } catch (e: ClassCastException) {
            Log.w(TAG, "ClassCastException reading watch type for video $videoId, defaulting to NONE", e)
            WatchType.NONE.internalId
        }
        return WatchType.fromId(id)
    }
}
