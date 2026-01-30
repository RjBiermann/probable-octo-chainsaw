package com.lagradost.common

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuspendRepositoryAdapterTest {

    @Test
    fun `load delegates to sync repository`() = runTest {
        val syncRepo = InMemoryCustomPagesRepository()
        val pages = listOf(CustomPage("/a", "A"), CustomPage("/b", "B"))
        syncRepo.setInitialData(pages)

        val adapter = SuspendRepositoryAdapter(syncRepo)
        assertEquals(pages, adapter.load())
    }

    @Test
    fun `save delegates to sync repository`() = runTest {
        val syncRepo = InMemoryCustomPagesRepository()
        val adapter = SuspendRepositoryAdapter(syncRepo)

        val pages = listOf(CustomPage("/x", "X"))
        assertTrue(adapter.save(pages))
        assertEquals(pages, syncRepo.load())
    }

    @Test
    fun `save returns false when sync repo fails`() = runTest {
        val failingRepo = object : CustomPagesRepository {
            override fun load(): List<CustomPage> = emptyList()
            override fun save(pages: List<CustomPage>): Boolean = false
        }
        val adapter = SuspendRepositoryAdapter(failingRepo)
        assertFalse(adapter.save(listOf(CustomPage("/x", "X"))))
    }

    @Test
    fun `InMemorySuspendRepository saveFailure flag works`() = runTest {
        val repo = InMemorySuspendRepository()
        repo.setInitialData(listOf(CustomPage("/a", "A")))

        assertTrue(repo.save(listOf(CustomPage("/b", "B"))))
        assertEquals(1, repo.load().size)
        assertEquals("B", repo.load()[0].label)

        repo.saveFailure = true
        assertFalse(repo.save(listOf(CustomPage("/c", "C"))))
        // Data unchanged after failure
        assertEquals("B", repo.load()[0].label)
    }
}
