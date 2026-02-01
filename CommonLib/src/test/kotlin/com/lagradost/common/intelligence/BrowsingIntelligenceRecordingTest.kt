package com.lagradost.common.intelligence

import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowsingIntelligenceRecordingTest {

    private lateinit var dao: InMemoryBrowsingDao
    private lateinit var intelligence: BrowsingIntelligence

    @Before
    fun setup() {
        dao = InMemoryBrowsingDao()
        intelligence = BrowsingIntelligence(dao)
    }

    @Test
    fun `recordSearch stores query`() = runTest {
        intelligence.recordSearch("test query", clickedUrl = null, sourcePlugin = "Neporn")
        assertEquals(1, dao.searchHistory.size)
        assertEquals("test query", dao.searchHistory[0].query)
    }

    @Test
    fun `recordTagSource stores mapping`() = runTest {
        intelligence.recordTagSource("amateur", "Neporn", "https://neporn.com/categories/amateur/")
        assertEquals(1, dao.tagSources.size)
        assertEquals("https://neporn.com/categories/amateur/", dao.tagSources[0].categoryUrl)
    }

    @Test
    fun `recordTagSource updates existing mapping`() = runTest {
        intelligence.recordTagSource("amateur", "Neporn", "https://neporn.com/old/")
        intelligence.recordTagSource("amateur", "Neporn", "https://neporn.com/new/")
        assertEquals(1, dao.tagSources.size)
        assertEquals("https://neporn.com/new/", dao.tagSources[0].categoryUrl)
    }

    @Test
    fun `deleteAffinityTag removes tag view data from DAO`() = runTest {
        dao.insertView(ViewHistoryEntry(
            url = "https://site.com/video1", title = "Video 1",
            thumbnail = null, duration = null, tags = listOf("amateur", "hd"),
            sourcePlugin = "TestPlugin", timestamp = System.currentTimeMillis()
        ))
        intelligence.deleteAffinityTag("amateur")
        assertTrue(dao.viewHistory.none { "amateur" in it.tags })
    }

    @Test
    fun `deleteAffinityPerformer removes performer view data from DAO`() {
        dao.insertView(ViewHistoryEntry(
            url = "https://site.com/video1", title = "Video 1",
            thumbnail = null, duration = null, tags = listOf("p:riley reid"),
            sourcePlugin = "TestPlugin", timestamp = System.currentTimeMillis(),
            tagTypes = ",p:riley reid,"
        ))
        intelligence.deleteAffinityPerformer("riley reid")
        assertTrue(dao.viewHistory.isEmpty())
    }

    @Test
    fun `clearAffinityTags removes tag sources`() = runTest {
        intelligence.recordTagSource("amateur", "Neporn", "https://url")
        intelligence.clearAffinityTags()
        assertTrue(dao.tagSources.isEmpty())
    }

    @Test
    fun `clearAll removes all data`() = runTest {
        intelligence.recordSearch("query", null, "Neporn")
        intelligence.recordTagSource("tag", "Neporn", "https://url")
        intelligence.clearAll()
        assertTrue(dao.searchHistory.isEmpty())
        assertTrue(dao.tagSources.isEmpty())
    }
}
