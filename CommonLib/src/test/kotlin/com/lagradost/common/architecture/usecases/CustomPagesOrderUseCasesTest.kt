package com.lagradost.common.architecture.usecases

import com.lagradost.common.CustomPage
import com.lagradost.common.InMemoryCustomPagesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomPagesOrderUseCasesTest {

    private lateinit var repository: InMemoryCustomPagesRepository
    private lateinit var useCases: CustomPagesOrderUseCases

    @Before
    fun setup() {
        repository = InMemoryCustomPagesRepository()
        useCases = CustomPagesOrderUseCases(repository, "Test")
    }

    @Test
    fun `reorderPages moves item forward`() = runTest {
        val pages = listOf(CustomPage("/a", "A"), CustomPage("/b", "B"), CustomPage("/c", "C"))

        val result = useCases.reorderPages(0, 2, pages)

        assertTrue(result is UseCaseResult.Success)
        assertEquals(listOf("B", "C", "A"), (result as UseCaseResult.Success).data.map { it.label })
    }

    @Test
    fun `reorderPages moves item backward`() = runTest {
        val pages = listOf(CustomPage("/a", "A"), CustomPage("/b", "B"), CustomPage("/c", "C"))

        val result = useCases.reorderPages(2, 0, pages)

        assertTrue(result is UseCaseResult.Success)
        assertEquals(listOf("C", "A", "B"), (result as UseCaseResult.Success).data.map { it.label })
    }

    @Test
    fun `reorderPages rejects out of bounds from`() = runTest {
        val pages = listOf(CustomPage("/a", "A"))

        val result = useCases.reorderPages(5, 0, pages)

        assertTrue(result is UseCaseResult.Error)
        assertEquals("Invalid positions", (result as UseCaseResult.Error).message)
    }

    @Test
    fun `reorderPages rejects out of bounds to`() = runTest {
        val pages = listOf(CustomPage("/a", "A"))

        val result = useCases.reorderPages(0, 5, pages)

        assertTrue(result is UseCaseResult.Error)
    }

    @Test
    fun `reorderPages persists to repository`() = runTest {
        val pages = listOf(CustomPage("/a", "A"), CustomPage("/b", "B"))

        useCases.reorderPages(0, 1, pages)

        assertEquals(listOf("B", "A"), repository.load().map { it.label })
    }

    @Test
    fun `saveOrder persists complete list`() = runTest {
        val pages = listOf(CustomPage("/c", "C"), CustomPage("/a", "A"), CustomPage("/b", "B"))

        val result = useCases.saveOrder(pages)

        assertTrue(result is UseCaseResult.Success)
        assertEquals(pages, (result as UseCaseResult.Success).data)
        assertEquals(pages, repository.load())
    }

    @Test
    fun `saveOrder with empty list`() = runTest {
        repository.setInitialData(listOf(CustomPage("/a", "A")))

        val result = useCases.saveOrder(emptyList())

        assertTrue(result is UseCaseResult.Success)
        assertTrue(repository.load().isEmpty())
    }
}
