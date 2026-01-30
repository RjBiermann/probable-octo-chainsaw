package com.lagradost.common.architecture.usecases

import com.lagradost.common.CustomPage
import com.lagradost.common.InMemoryCustomPagesRepository
import com.lagradost.common.ValidationResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomPagesCrudUseCasesTest {

    private lateinit var repository: InMemoryCustomPagesRepository
    private lateinit var useCases: CustomPagesCrudUseCases

    private fun testValidator(url: String): ValidationResult {
        return when {
            url.contains("invalid-domain") -> ValidationResult.InvalidDomain
            url.contains("invalid-path") -> ValidationResult.InvalidPath
            else -> ValidationResult.Valid(
                path = "/validated" + url.substringAfter("example.com"),
                label = "Auto Label"
            )
        }
    }

    @Before
    fun setup() {
        repository = InMemoryCustomPagesRepository()
        useCases = CustomPagesCrudUseCases(repository, ::testValidator, "Test")
    }

    @Test
    fun `loadPages returns success with data`() = runTest {
        repository.setInitialData(listOf(CustomPage("/a", "A"), CustomPage("/b", "B")))

        val result = useCases.loadPages()

        assertTrue(result is UseCaseResult.Success)
        assertEquals(2, (result as UseCaseResult.Success).data.size)
    }

    @Test
    fun `loadPages returns empty list when no data`() = runTest {
        val result = useCases.loadPages()

        assertTrue(result is UseCaseResult.Success)
        assertTrue((result as UseCaseResult.Success).data.isEmpty())
    }

    @Test
    fun `addPage succeeds with valid URL`() = runTest {
        val result = useCases.addPage("https://example.com/test", "My Label", emptyList())

        assertTrue(result is UseCaseResult.Success)
        val pages = (result as UseCaseResult.Success).data
        assertEquals(1, pages.size)
        assertEquals("My Label", pages[0].label)
        assertEquals("/validated/test", pages[0].path)
    }

    @Test
    fun `addPage uses auto label when label is blank`() = runTest {
        val result = useCases.addPage("https://example.com/test", "", emptyList())

        assertTrue(result is UseCaseResult.Success)
        assertEquals("Auto Label", (result as UseCaseResult.Success).data[0].label)
    }

    @Test
    fun `addPage rejects invalid domain`() = runTest {
        val result = useCases.addPage("https://invalid-domain.com/test", "Test", emptyList())

        assertTrue(result is UseCaseResult.Error)
        assertEquals("Invalid domain", (result as UseCaseResult.Error).message)
    }

    @Test
    fun `addPage rejects invalid path`() = runTest {
        val result = useCases.addPage("https://example.com/invalid-path", "Test", emptyList())

        assertTrue(result is UseCaseResult.Error)
        assertEquals("Invalid URL path", (result as UseCaseResult.Error).message)
    }

    @Test
    fun `addPage rejects duplicate path`() = runTest {
        val existing = listOf(CustomPage("/validated/test", "Existing"))

        val result = useCases.addPage("https://example.com/test", "New", existing)

        assertTrue(result is UseCaseResult.Error)
        assertTrue((result as UseCaseResult.Error).message.contains("already exists"))
    }

    @Test
    fun `addPage appends to existing pages`() = runTest {
        val existing = listOf(CustomPage("/other", "Other"))

        val result = useCases.addPage("https://example.com/test", "New", existing)

        assertTrue(result is UseCaseResult.Success)
        val pages = (result as UseCaseResult.Success).data
        assertEquals(2, pages.size)
        assertEquals("Other", pages[0].label)
        assertEquals("New", pages[1].label)
    }

    @Test
    fun `addPage persists to repository`() = runTest {
        useCases.addPage("https://example.com/test", "Test", emptyList())

        assertEquals(1, repository.load().size)
    }

    @Test
    fun `deletePage removes correct item`() = runTest {
        val pages = listOf(CustomPage("/a", "A"), CustomPage("/b", "B"), CustomPage("/c", "C"))

        val result = useCases.deletePage(1, pages)

        assertTrue(result is UseCaseResult.Success)
        val updated = (result as UseCaseResult.Success).data
        assertEquals(listOf("A", "C"), updated.map { it.label })
    }

    @Test
    fun `deletePage rejects invalid index`() = runTest {
        val result = useCases.deletePage(5, listOf(CustomPage("/a", "A")))

        assertTrue(result is UseCaseResult.Error)
        assertEquals("Invalid position", (result as UseCaseResult.Error).message)
    }

    @Test
    fun `deletePage rejects negative index`() = runTest {
        val result = useCases.deletePage(-1, listOf(CustomPage("/a", "A")))

        assertTrue(result is UseCaseResult.Error)
    }

    @Test
    fun `clearAll removes all pages`() = runTest {
        repository.setInitialData(listOf(CustomPage("/a", "A"), CustomPage("/b", "B")))

        val result = useCases.clearAll()

        assertTrue(result is UseCaseResult.Success)
        assertTrue((result as UseCaseResult.Success).data.isEmpty())
        assertTrue(repository.load().isEmpty())
    }
}
