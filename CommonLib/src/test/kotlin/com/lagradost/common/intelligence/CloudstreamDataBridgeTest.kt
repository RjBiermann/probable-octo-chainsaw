package com.lagradost.common.intelligence

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudstreamDataBridgeTest {

    private lateinit var bridge: InMemoryCloudstreamDataBridge

    @Before
    fun setup() {
        bridge = InMemoryCloudstreamDataBridge()
    }

    @Test
    fun `getResumeWatching returns empty when no data`() {
        assertTrue(bridge.getResumeWatchingIds().isEmpty())
    }

    @Test
    fun `getVideoPosition returns null for unknown id`() {
        assertNull(bridge.getVideoPosition(99999))
    }

    @Test
    fun `getVideoPosition returns stored position`() {
        bridge.setVideoPosition(123, VideoPosition(60_000L, 120_000L))
        val pos = bridge.getVideoPosition(123)!!
        assertEquals(60_000L, pos.positionMs)
        assertEquals(120_000L, pos.durationMs)
        assertEquals(0.5f, pos.watchPercentage, 0.01f)
    }

    @Test
    fun `getBookmarks returns all bookmarked items`() {
        bridge.addBookmark(BookmarkEntry(id = 1, name = "Vid1", url = "https://a.com/1", apiName = "Neporn", watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("tag1")))
        bridge.addBookmark(BookmarkEntry(id = 2, name = "Vid2", url = "https://a.com/2", apiName = "Neporn", watchType = WatchType.WATCHING, posterUrl = null, tags = emptyList()))
        assertEquals(2, bridge.getBookmarks().size)
    }

    @Test
    fun `getFavorites returns only favorited items`() {
        bridge.addFavorite(FavoriteEntry(id = 1, name = "Vid1", url = "https://a.com/1", apiName = "Neporn", posterUrl = null, tags = listOf("tag1")))
        assertEquals(1, bridge.getFavorites().size)
    }

    @Test
    fun `getBookmarksByType filters correctly`() {
        bridge.addBookmark(BookmarkEntry(id = 1, name = "V1", url = "u1", apiName = "A", watchType = WatchType.COMPLETED, posterUrl = null, tags = emptyList()))
        bridge.addBookmark(BookmarkEntry(id = 2, name = "V2", url = "u2", apiName = "A", watchType = WatchType.WATCHING, posterUrl = null, tags = emptyList()))
        assertEquals(1, bridge.getBookmarksByType(WatchType.COMPLETED).size)
    }
}
