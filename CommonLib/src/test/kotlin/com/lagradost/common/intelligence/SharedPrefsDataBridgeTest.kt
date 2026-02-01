package com.lagradost.common.intelligence

import android.content.SharedPreferences
import io.mockk.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedPrefsDataBridgeTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var bridge: SharedPrefsDataBridge

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        every { prefs.all } returns mutableMapOf<String, Any?>()
        bridge = SharedPrefsDataBridge(prefs, accountIndex = 0)
    }

    private fun mockKey(key: String, json: String) {
        every { prefs.getString(key, null) } returns json
    }

    private fun mockAllKeys(vararg keys: String) {
        val map = mutableMapOf<String, Any?>()
        keys.forEach { map[it] = "placeholder" }
        every { prefs.all } returns map
    }

    @Test
    fun `getVideoPosition parses PosDur JSON`() {
        val json = JSONObject().apply {
            put("position", 30000L)
            put("duration", 60000L)
        }.toString()
        mockKey("0/video_pos_dur/123", json)
        val pos = bridge.getVideoPosition(123)
        assertNotNull(pos)
        assertEquals(30000L, pos.positionMs)
        assertEquals(60000L, pos.durationMs)
        assertEquals(0.5f, pos.watchPercentage, 0.01f)
    }

    @Test
    fun `getVideoPosition returns null for missing key`() {
        every { prefs.getString("0/video_pos_dur/999", null) } returns null
        assertNull(bridge.getVideoPosition(999))
    }

    @Test
    fun `getResumeWatchingIds enumerates keys`() {
        mockAllKeys("0/result_resume_watching_2/100", "0/result_resume_watching_2/200", "1/result_resume_watching_2/300")
        val ids = bridge.getResumeWatchingIds()
        assertEquals(listOf(100, 200), ids.sorted())
    }

    @Test
    fun `getWatchType parses integer state`() {
        every { prefs.getInt("0/result_watch_state/42", 5) } returns 1
        assertEquals(WatchType.COMPLETED, bridge.getWatchType(42))
    }

    @Test
    fun `getBookmarks parses bookmark data`() {
        val json = JSONObject().apply {
            put("bookmarkedTime", 123456L)
            put("id", 1)
            put("name", "TestVid")
            put("url", "https://example.com/1")
            put("apiName", "Neporn")
            put("posterUrl", JSONObject.NULL)
            put("tags", JSONArray(listOf("tag1")))
        }.toString()
        mockAllKeys("0/result_watch_state_data/1")
        mockKey("0/result_watch_state_data/1", json)
        every { prefs.getInt("0/result_watch_state/1", 5) } returns 0

        val bookmarks = bridge.getBookmarks()
        assertEquals(1, bookmarks.size)
        assertEquals("TestVid", bookmarks[0].name)
        assertEquals(WatchType.WATCHING, bookmarks[0].watchType)
        assertEquals(listOf("tag1"), bookmarks[0].tags)
    }

    @Test
    fun `getFavorites parses favorites data`() {
        val json = JSONObject().apply {
            put("favoritesTime", 123456L)
            put("id", 2)
            put("name", "FavVid")
            put("url", "https://example.com/2")
            put("apiName", "Neporn")
            put("posterUrl", "https://img.com/2.jpg")
            put("tags", JSONArray(listOf("fav-tag")))
        }.toString()
        mockAllKeys("0/result_favorites_state_data/2")
        mockKey("0/result_favorites_state_data/2", json)

        val favs = bridge.getFavorites()
        assertEquals(1, favs.size)
        assertEquals("FavVid", favs[0].name)
        assertEquals("https://img.com/2.jpg", favs[0].posterUrl)
    }

    @Test
    fun `malformed JSON returns empty lists gracefully`() {
        mockAllKeys("0/result_watch_state_data/1")
        mockKey("0/result_watch_state_data/1", "{invalid json")
        assertTrue(bridge.getBookmarks().isEmpty())
    }
}
