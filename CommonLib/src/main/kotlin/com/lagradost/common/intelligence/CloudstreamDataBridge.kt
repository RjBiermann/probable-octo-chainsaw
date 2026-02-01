package com.lagradost.common.intelligence

/**
 * Lightweight data classes mirroring Cloudstream's SharedPreferences structures.
 * We define our own instead of importing from the app module (which plugins can't access).
 */
data class VideoPosition(
    val positionMs: Long,
    val durationMs: Long
) {
    /** Watch progress as a 0.0–1.0 ratio. */
    val watchPercentage: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

enum class WatchType(val internalId: Int) {
    WATCHING(0),
    COMPLETED(1),
    ONHOLD(2),
    DROPPED(3),
    PLANTOWATCH(4),
    NONE(5);

    companion object {
        fun fromId(id: Int): WatchType = entries.firstOrNull { it.internalId == id } ?: NONE
    }
}

data class BookmarkEntry(
    val id: Int,
    val name: String,
    val url: String,
    val apiName: String,
    val watchType: WatchType,
    val posterUrl: String?,
    val tags: List<String>,
    val bookmarkedTime: Long = 0L
)

data class FavoriteEntry(
    val id: Int,
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?,
    val tags: List<String>,
    val favoritesTime: Long = 0L
)

data class ResumeEntry(
    val parentId: Int,
    val episodeId: Int?,
    val updateTime: Long
)

/**
 * Reads Cloudstream's user engagement data from SharedPreferences.
 * Interface enables test doubles.
 */
interface CloudstreamDataBridge {
    fun getVideoPosition(videoId: Int): VideoPosition?
    fun getResumeWatchingIds(): List<Int>
    fun getResumeEntry(parentId: Int): ResumeEntry?
    fun getBookmarks(): List<BookmarkEntry>
    fun getBookmarksByType(type: WatchType): List<BookmarkEntry>
    fun getFavorites(): List<FavoriteEntry>
    fun isFavorited(videoId: Int): Boolean
    fun getWatchType(videoId: Int): WatchType
}

/**
 * In-memory implementation for testing.
 */
class InMemoryCloudstreamDataBridge : CloudstreamDataBridge {
    private val positions = mutableMapOf<Int, VideoPosition>()
    private val bookmarks = mutableListOf<BookmarkEntry>()
    private val favorites = mutableListOf<FavoriteEntry>()
    private val resumeEntries = mutableMapOf<Int, ResumeEntry>()

    fun setVideoPosition(id: Int, pos: VideoPosition) { positions[id] = pos }
    fun addBookmark(entry: BookmarkEntry) { bookmarks.add(entry) }
    fun addFavorite(entry: FavoriteEntry) { favorites.add(entry) }
    fun addResumeEntry(entry: ResumeEntry) { resumeEntries[entry.parentId] = entry }

    override fun getVideoPosition(videoId: Int): VideoPosition? = positions[videoId]
    override fun getResumeWatchingIds(): List<Int> = resumeEntries.keys.toList()
    override fun getResumeEntry(parentId: Int): ResumeEntry? = resumeEntries[parentId]
    override fun getBookmarks(): List<BookmarkEntry> = bookmarks.toList()
    override fun getBookmarksByType(type: WatchType): List<BookmarkEntry> = bookmarks.filter { it.watchType == type }
    override fun getFavorites(): List<FavoriteEntry> = favorites.toList()
    override fun isFavorited(videoId: Int): Boolean = favorites.any { it.id == videoId }
    override fun getWatchType(videoId: Int): WatchType = bookmarks.firstOrNull { it.id == videoId }?.watchType ?: WatchType.NONE
}
