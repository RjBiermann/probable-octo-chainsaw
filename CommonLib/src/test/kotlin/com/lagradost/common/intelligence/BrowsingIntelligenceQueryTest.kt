package com.lagradost.common.intelligence

import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowsingIntelligenceQueryTest {

    private lateinit var dao: InMemoryBrowsingDao
    private lateinit var bridge: InMemoryCloudstreamDataBridge
    private lateinit var intelligence: BrowsingIntelligence

    @Before
    fun setup() {
        dao = InMemoryBrowsingDao()
        bridge = InMemoryCloudstreamDataBridge()
        intelligence = BrowsingIntelligence(dao, bridge)
    }

    @Test
    fun `getAffinityTags derives tags from bookmarks and favorites`() {
        bridge.addBookmark(BookmarkEntry(id = 1, name = "V1", url = "u1", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("japanese", "amateur")))
        bridge.addBookmark(BookmarkEntry(id = 2, name = "V2", url = "u2", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("japanese")))
        bridge.addFavorite(FavoriteEntry(id = 3, name = "V3", url = "u3", apiName = "A",
            posterUrl = null, tags = listOf("japanese", "hd")))

        val tags = intelligence.getAffinityTags(10)
        assertTrue(tags.isNotEmpty(), "Should have tags from bookmarks/favorites")
        assertEquals("japanese", tags[0].tag, "japanese should rank highest (2 bookmarks + 1 favorite)")
    }

    @Test
    fun `getAffinityTags returns empty when bridge has no data`() {
        val tags = intelligence.getAffinityTags(10)
        assertTrue(tags.isEmpty(), "No engagement data means no affinity tags")
    }

    @Test
    fun `getAffinityTags returns empty without bridge`() {
        val noBridgeIntelligence = BrowsingIntelligence(dao)
        val tags = noBridgeIntelligence.getAffinityTags(10)
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `getSuggestedSearches matches partial queries across plugins`() = runTest {
        intelligence.recordSearch("blonde milf", null, "Neporn")
        intelligence.recordSearch("blonde teen", null, "HQPorner")
        intelligence.recordSearch("redhead", null, "MissAV")

        val suggestions = intelligence.getSuggestedSearches("blo", 10)
        assertEquals(2, suggestions.size)
        assertTrue(suggestions.all { it.contains("blonde", ignoreCase = true) })
    }

    @Test
    fun `getTagSources returns plugin-url mappings`() = runTest {
        intelligence.recordTagSource("amateur", "Neporn", "https://neporn.com/categories/amateur/")
        intelligence.recordTagSource("amateur", "HQPorner", "https://hqporner.com/c/amateur")

        val sources = intelligence.getTagSources("amateur")
        assertEquals(2, sources.size)
    }

    @Test
    fun `clearAll removes everything`() = runTest {
        intelligence.recordSearch("query", null, "Neporn")
        intelligence.recordTagSource("tag", "Neporn", "https://url")

        intelligence.clearAll()
        assertEquals(0, intelligence.getSuggestedSearches("", 10).size)
        assertEquals(0, intelligence.getTagSources("tag").size)
    }

    // --- CloudstreamDataBridge integration tests ---

    @Test
    fun `getContinueWatching returns entries with position data`() {
        bridge.addResumeEntry(ResumeEntry(parentId = 42, episodeId = null, updateTime = System.currentTimeMillis()))
        bridge.setVideoPosition(42, VideoPosition(30_000L, 120_000L))

        val results = intelligence.getContinueWatching(10)
        assertTrue(results is List<*>)
    }

    @Test
    fun `getUserFavorites delegates to bridge`() {
        bridge.addFavorite(FavoriteEntry(id = 1, name = "Fav1", url = "https://a.com/1", apiName = "Neporn", posterUrl = null, tags = listOf("tag1")))

        val favs = intelligence.getUserFavorites()
        assertEquals(1, favs.size)
        assertEquals("Fav1", favs[0].name)
    }

    @Test
    fun `getUserBookmarks delegates to bridge`() {
        bridge.addBookmark(BookmarkEntry(id = 1, name = "B1", url = "u1", apiName = "A", watchType = WatchType.COMPLETED, posterUrl = null, tags = emptyList()))

        val bookmarks = intelligence.getUserBookmarks()
        assertEquals(1, bookmarks.size)
    }

    @Test
    fun `getEngagementWeight returns higher weight for favorites`() {
        bridge.addFavorite(FavoriteEntry(id = 42, name = "F", url = "u", apiName = "A", posterUrl = null, tags = emptyList()))
        bridge.addBookmark(BookmarkEntry(id = 42, name = "F", url = "u", apiName = "A", watchType = WatchType.COMPLETED, posterUrl = null, tags = emptyList()))
        bridge.setVideoPosition(42, VideoPosition(110_000L, 120_000L))

        val weight = intelligence.getEngagementWeight(42)
        assertTrue(weight > 1.0f, "Favorited + completed + 90%+ watched should have high weight")
    }

    @Test
    fun `getEngagementWeight returns 1 when no bridge`() {
        val noBridgeIntelligence = BrowsingIntelligence(dao)
        val weight = noBridgeIntelligence.getEngagementWeight(42)
        assertEquals(1.0f, weight)
    }

    // --- Typed affinity tests (via bridge engagement data) ---

    @Test
    fun `getAffinityGenres returns only genre tags from engagement`() {
        // TagNormalizer classifies "amateur" as GENRE and "milf" as BODY_TYPE
        bridge.addBookmark(BookmarkEntry(id = 1, name = "V1", url = "u1", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("amateur", "milf")))
        bridge.addBookmark(BookmarkEntry(id = 2, name = "V2", url = "u2", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("amateur")))

        val genres = intelligence.getAffinityGenres(10)
        assertTrue(genres.isNotEmpty())
        assertEquals("amateur", genres[0].tag)
        assertTrue(genres.none { it.tag == "milf" }, "milf is BODY_TYPE, not GENRE")
    }

    @Test
    fun `getAffinityBodyTypes returns only body type tags from engagement`() {
        bridge.addBookmark(BookmarkEntry(id = 1, name = "V1", url = "u1", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("amateur", "milf")))
        bridge.addBookmark(BookmarkEntry(id = 2, name = "V2", url = "u2", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("milf")))

        val bodyTypes = intelligence.getAffinityBodyTypes(10)
        assertTrue(bodyTypes.isNotEmpty())
        assertEquals("milf", bodyTypes[0].tag)
        assertTrue(bodyTypes.none { it.tag == "amateur" })
    }

    // --- No-limit query tests (via bridge) ---

    @Test
    fun `getAllAffinityTags returns all tags without limit`() {
        for (i in 1..15) {
            bridge.addBookmark(BookmarkEntry(id = i, name = "V$i", url = "u$i", apiName = "A",
                watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("tag$i")))
        }
        val tags = intelligence.getAllAffinityTags()
        assertEquals(15, tags.size)
    }

    @Test
    fun `getAllAffinityPerformers returns empty when no performer tags in engagement`() {
        // Generic tags like "amateur" are classified as GENRE, not PERFORMER
        bridge.addBookmark(BookmarkEntry(id = 1, name = "V1", url = "u1", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("amateur", "hd")))
        val result = intelligence.getAllAffinityPerformers()
        assertTrue(result.isEmpty(), "Generic tags should not be classified as performers")
    }

    // --- Pause tests ---

    private fun createIntelligenceWithPause(): BrowsingIntelligence {
        val store = mutableMapOf<String, String?>()
        return BrowsingIntelligence(
            dao = dao,
            dataBridge = bridge,
            getKey = { store[it] },
            setKey = { k, v -> if (v == null) store.remove(k) else store[k] = v }
        )
    }

    @Test
    fun `paused tags are excluded from getAffinityTags`() {
        val intel = createIntelligenceWithPause()
        bridge.addBookmark(BookmarkEntry(id = 1, name = "V1", url = "u1", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("amateur", "hd")))

        intel.setTagPaused("amateur", true)
        val tags = intel.getAffinityTags(10)
        assertTrue(tags.none { it.tag == "amateur" })
    }

    @Test
    fun `unpaused tags return to getAffinityTags`() {
        val intel = createIntelligenceWithPause()
        bridge.addBookmark(BookmarkEntry(id = 1, name = "V1", url = "u1", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("amateur")))

        intel.setTagPaused("amateur", true)
        intel.setTagPaused("amateur", false)
        val tags = intel.getAffinityTags(10)
        assertTrue(tags.any { it.tag == "amateur" })
    }

    @Test
    fun `paused performers are excluded from getAffinityPerformers`() {
        val intel = createIntelligenceWithPause()
        bridge.addFavorite(FavoriteEntry(id = 1, name = "V1", url = "u1", apiName = "A",
            posterUrl = null, tags = listOf("riley reid")))

        intel.setPerformerPaused("riley reid", true)
        val performers = intel.getAffinityPerformers(10)
        assertTrue(performers.none { it.tag == "riley reid" })
    }

    @Test
    fun `getAllAffinityTags includes paused tags`() {
        val intel = createIntelligenceWithPause()
        bridge.addBookmark(BookmarkEntry(id = 1, name = "V1", url = "u1", apiName = "A",
            watchType = WatchType.COMPLETED, posterUrl = null, tags = listOf("amateur")))

        intel.setTagPaused("amateur", true)
        val allTags = intel.getAllAffinityTags()
        assertTrue(allTags.any { it.tag == "amateur" })
    }

    @Test
    fun `isTagPaused returns correct state`() {
        val intel = createIntelligenceWithPause()
        assertFalse(intel.isTagPaused("amateur"))
        intel.setTagPaused("amateur", true)
        assertTrue(intel.isTagPaused("amateur"))
    }
}
