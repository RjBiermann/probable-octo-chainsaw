package com.lagradost.common.architecture

import com.lagradost.common.CustomPage
import com.lagradost.common.InMemoryCustomPagesRepository
import com.lagradost.common.ValidationResult
import com.lagradost.common.architecture.usecases.CustomPagesCrudUseCases
import com.lagradost.common.architecture.usecases.CustomPagesOrderUseCases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for BaseCustomPagesViewModel.
 * Verifies CRUD operations, state management, validation, and error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseCustomPagesViewModelTest {

    @get:Rule
    val vmRule = ViewModelTestRule()

    private lateinit var repository: InMemoryCustomPagesRepository
    private lateinit var viewModel: TestCustomPagesViewModel

    private class TestCustomPagesViewModel(
        crudUseCases: CustomPagesCrudUseCases,
        orderUseCases: CustomPagesOrderUseCases
    ) : com.lagradost.common.BaseCustomPagesViewModel(crudUseCases, orderUseCases)

    private fun testValidator(url: String): ValidationResult {
        return when {
            url.contains("invalid-domain") -> ValidationResult.InvalidDomain
            url.contains("invalid-path") -> ValidationResult.InvalidPath
            else -> ValidationResult.Valid(
                path = url.substringAfter("example.com"),
                label = url.substringAfterLast("/").replace("-", " ").capitalize()
            )
        }
    }

    private fun createViewModel(repo: InMemoryCustomPagesRepository = repository): TestCustomPagesViewModel {
        return TestCustomPagesViewModel(
            CustomPagesCrudUseCases(repo, ::testValidator, "TestCrud"),
            CustomPagesOrderUseCases(repo, "TestOrder")
        )
    }

    @Before
    fun setup() {
        repository = InMemoryCustomPagesRepository()
        viewModel = createViewModel()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.uiState.first()

        assertTrue(state.pages.isEmpty())
        assertTrue(state.filteredPages.isEmpty())
        assertFalse(state.isLoading)
        assertEquals(null, state.errorMessage)
        assertEquals("", state.filterQuery)
        assertFalse(state.isReorderMode)
        assertEquals(null, state.selectedReorderPosition)
        assertTrue(state.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Idle)
    }

    @Test
    fun `loadPages loads data from repository`() = runTest {
        val testPages = listOf(
            CustomPage("/category/action", "Action"),
            CustomPage("/category/comedy", "Comedy")
        )
        repository.setInitialData(testPages)

        viewModel = createViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(testPages, state.pages)
        assertEquals(testPages, state.filteredPages)
        assertFalse(state.isLoading)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `addPage adds valid page successfully`() = runTest {
        viewModel.addPage("https://example.com/category/action", "Action Movies")

        val state = viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(1, state.pages.size)
        assertEquals("/category/action", state.pages[0].path)
        assertEquals("Action Movies", state.pages[0].label)
        assertTrue(state.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Success)
    }

    @Test
    fun `addPage uses auto-generated label when label is empty`() = runTest {
        viewModel.addPage("https://example.com/category/sci-fi", "")

        val state = viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(1, state.pages.size)
        assertEquals("Sci fi", state.pages[0].label)
    }

    @Test
    fun `addPage rejects invalid domain`() = runTest {
        viewModel.addPage("https://invalid-domain.com/path", "Test")

        val state = viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error }

        assertTrue(state.pages.isEmpty())
        assertTrue(state.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error)
        assertEquals("Invalid domain", (state.saveStatus as com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error).message)
    }

    @Test
    fun `addPage rejects invalid path`() = runTest {
        viewModel.addPage("https://example.com/invalid-path", "Test")

        val state = viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error }

        assertTrue(state.pages.isEmpty())
        assertTrue(state.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error)
        assertEquals("Invalid URL path", (state.saveStatus as com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error).message)
    }

    @Test
    fun `addPage rejects duplicate pages`() = runTest {
        viewModel.addPage("https://example.com/category/action", "Action")
        viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Success }

        viewModel.addPage("https://example.com/category/action", "Action Duplicate")

        val state = viewModel.uiState.first {
            it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error &&
            (it.saveStatus as com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error).message.contains("already exists")
        }

        assertEquals(1, state.pages.size)
        assertTrue(state.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error)
    }

    @Test
    fun `deletePage removes page by filtered position`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/action", "Action"),
            CustomPage("/comedy", "Comedy"),
            CustomPage("/drama", "Drama")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.deletePage(1)

        val state = viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Deleted }

        assertEquals(2, state.pages.size)
        assertEquals(listOf("Action", "Drama"), state.pages.map { it.label })
    }

    @Test
    fun `deletePage with filtering deletes correct page`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/action", "Action"),
            CustomPage("/comedy", "Comedy"),
            CustomPage("/drama", "Drama"),
            CustomPage("/scifi", "Sci-Fi")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.filterPages("a")
        var state = viewModel.uiState.first { it.filterQuery == "a" }
        assertEquals(listOf("Action", "Drama"), state.filteredPages.map { it.label })

        viewModel.deletePage(0)

        state = viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Deleted }

        assertEquals(3, state.pages.size)
        assertEquals(listOf("Comedy", "Drama", "Sci-Fi"), state.pages.map { it.label })
        assertEquals(listOf("Drama"), state.filteredPages.map { it.label })
    }

    @Test
    fun `reorderPages moves item correctly`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/action", "Action"),
            CustomPage("/comedy", "Comedy"),
            CustomPage("/drama", "Drama")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.reorderPages(0, 2)

        val state = viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(listOf("Comedy", "Drama", "Action"), state.pages.map { it.label })
    }

    @Test
    fun `clearAll removes all pages`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/action", "Action"),
            CustomPage("/comedy", "Comedy")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.clearAll()

        val state = viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Success }

        assertTrue(state.pages.isEmpty())
        assertTrue(state.filteredPages.isEmpty())
    }

    @Test
    fun `filterPages updates filtered list`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/action", "Action Movies"),
            CustomPage("/comedy", "Comedy Shows"),
            CustomPage("/drama", "Drama Series")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.filterPages("action")
        val state1 = viewModel.uiState.first { it.filterQuery == "action" }
        assertEquals(1, state1.filteredPages.size)
        assertEquals("Action Movies", state1.filteredPages[0].label)

        viewModel.filterPages("show")
        val state2 = viewModel.uiState.first { it.filterQuery == "show" }
        assertEquals(1, state2.filteredPages.size)
        assertEquals("Comedy Shows", state2.filteredPages[0].label)

        viewModel.filterPages("")
        val state3 = viewModel.uiState.first { it.filterQuery == "" }
        assertEquals(3, state3.filteredPages.size)
    }

    @Test
    fun `toggleReorderMode changes state`() = runTest {
        viewModel.toggleReorderMode()
        val state1 = viewModel.uiState.first { it.isReorderMode }
        assertTrue(state1.isReorderMode)
        assertEquals(null, state1.selectedReorderPosition)

        viewModel.toggleReorderMode()
        val state2 = viewModel.uiState.first { !it.isReorderMode }
        assertFalse(state2.isReorderMode)
        assertEquals(null, state2.selectedReorderPosition)
    }

    @Test
    fun `selectForReorder sets position`() = runTest {
        viewModel.selectForReorder(3)
        val state = viewModel.uiState.first { it.selectedReorderPosition == 3 }
        assertEquals(3, state.selectedReorderPosition)
    }

    @Test
    fun `clearSaveStatus resets to Idle`() = runTest {
        viewModel.addPage("https://invalid-domain.com/path", "Test")
        viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Error }

        viewModel.clearSaveStatus()

        val state = viewModel.uiState.first { it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Idle }
        assertTrue(state.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Idle)
    }

    @Test
    fun `undoDelete restores deleted page`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/action", "Action"),
            CustomPage("/comedy", "Comedy"),
            CustomPage("/drama", "Drama")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.deletePage(1)

        val deleteState = viewModel.uiState.first {
            it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Deleted
        }
        assertEquals(2, deleteState.pages.size)

        val deleted = deleteState.saveStatus as com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Deleted
        viewModel.undoDelete(deleted.deletedPage, deleted.sourceIndex)

        val restored = viewModel.uiState.first {
            it.saveStatus is com.lagradost.common.BaseCustomPagesViewModel.SaveStatus.Success &&
            it.pages.size == 3
        }
        assertEquals(listOf("Action", "Comedy", "Drama"), restored.pages.map { it.label })
    }

    @Test
    fun `multiple operations maintain consistency`() = runTest {
        viewModel.addPage("https://example.com/action", "Action")
        viewModel.uiState.first { it.pages.size == 1 }

        viewModel.addPage("https://example.com/comedy", "Comedy")
        viewModel.uiState.first { it.pages.size == 2 }

        viewModel.addPage("https://example.com/drama", "Drama")
        val state1 = viewModel.uiState.first { it.pages.size == 3 }
        assertEquals(listOf("Action", "Comedy", "Drama"), state1.pages.map { it.label })

        viewModel.reorderPages(0, 2)
        val state2 = viewModel.uiState.first {
            it.pages.size == 3 && it.pages[0].label == "Comedy"
        }
        assertEquals(listOf("Comedy", "Drama", "Action"), state2.pages.map { it.label })

        viewModel.deletePage(1)
        val state3 = viewModel.uiState.first { it.pages.size == 2 }
        assertEquals(listOf("Comedy", "Action"), state3.pages.map { it.label })

        assertEquals(2, repository.load().size)
    }
}
