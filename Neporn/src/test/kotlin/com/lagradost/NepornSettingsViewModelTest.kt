package com.lagradost

import com.lagradost.common.BaseCustomPagesViewModel
import com.lagradost.common.CustomPage
import com.lagradost.common.CustomPagesViewModelFactory
import com.lagradost.common.InMemoryCustomPagesRepository
import com.lagradost.common.architecture.ViewModelTestRule
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
 * Tests for NepornSettingsViewModel.
 * Validates Neporn-specific URL handling and business logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NepornSettingsViewModelTest {

    @get:Rule
    val vmRule = ViewModelTestRule()

    private lateinit var repository: InMemoryCustomPagesRepository
    private lateinit var viewModel: BaseCustomPagesViewModel

    private fun createViewModel(repo: InMemoryCustomPagesRepository = repository): BaseCustomPagesViewModel {
        return CustomPagesViewModelFactory.create(
            repository = repo,
            validator = NepornUrlValidator::validate,
            logTag = "NepornTest"
        )
    }

    @Before
    fun setup() {
        repository = InMemoryCustomPagesRepository()
        viewModel = createViewModel()
    }

    @Test
    fun `validate accepts category URLs`() = runTest {
        viewModel.addPage("https://neporn.com/categories/hd-porn/", "HD Porn")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(1, state.pages.size)
        assertEquals("/categories/hd-porn/", state.pages[0].path)
        assertEquals("HD Porn", state.pages[0].label)
    }

    @Test
    fun `validate accepts tag URLs`() = runTest {
        viewModel.addPage("https://neporn.com/tags/european/", "European")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(1, state.pages.size)
        assertEquals("/tags/european/", state.pages[0].path)
        assertEquals("European", state.pages[0].label)
    }

    @Test
    fun `validate accepts model URLs`() = runTest {
        viewModel.addPage("https://neporn.com/models/jane-doe/", "Jane Doe")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(1, state.pages.size)
        assertEquals("/models/jane-doe/", state.pages[0].path)
        assertEquals("Jane Doe", state.pages[0].label)
    }

    @Test
    fun `validate accepts search path URLs`() = runTest {
        viewModel.addPage("https://neporn.com/search/fitness/", "")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(1, state.pages.size)
        assertEquals("/search/fitness/", state.pages[0].path)
        assertEquals("Search: Fitness", state.pages[0].label)
    }

    @Test
    fun `validate accepts search query URLs`() = runTest {
        viewModel.addPage("https://neporn.com/search/?q=massage", "")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(1, state.pages.size)
        assertEquals("/search/?q=massage", state.pages[0].path)
        assertEquals("Search: massage", state.pages[0].label)
    }

    @Test
    fun `validate accepts special pages`() = runTest {
        viewModel.addPage("https://neporn.com/top-rated/", "")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(1, state.pages.size)
        assertEquals("/top-rated/", state.pages[0].path)
        assertEquals("Top Rated", state.pages[0].label)
    }

    @Test
    fun `validate strips pagination from URLs`() = runTest {
        viewModel.addPage("https://neporn.com/categories/anal/2/", "Anal")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(1, state.pages.size)
        assertEquals("/categories/anal/", state.pages[0].path)
        assertEquals("Anal", state.pages[0].label)
    }

    @Test
    fun `validate rejects invalid domain`() = runTest {
        viewModel.addPage("https://wrongsite.com/categories/test/", "Test")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Error }

        assertTrue(state.pages.isEmpty())
        assertTrue(state.saveStatus is BaseCustomPagesViewModel.SaveStatus.Error)
        assertEquals("Invalid domain", (state.saveStatus as BaseCustomPagesViewModel.SaveStatus.Error).message)
    }

    @Test
    fun `validate rejects invalid path`() = runTest {
        viewModel.addPage("https://neporn.com/invalid/path/", "Test")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Error }

        assertTrue(state.pages.isEmpty())
        assertTrue(state.saveStatus is BaseCustomPagesViewModel.SaveStatus.Error)
        assertEquals("Invalid URL path", (state.saveStatus as BaseCustomPagesViewModel.SaveStatus.Error).message)
    }

    @Test
    fun `validate rejects excessively long URLs (ReDoS protection)`() = runTest {
        val longUrl = "https://neporn.com/categories/" + "a".repeat(3000)

        viewModel.addPage(longUrl, "Test")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Error }

        assertTrue(state.pages.isEmpty())
        assertTrue(state.saveStatus is BaseCustomPagesViewModel.SaveStatus.Error)
    }

    @Test
    fun `can add multiple pages`() = runTest {
        viewModel.addPage("https://neporn.com/categories/hd-porn/", "HD")
        viewModel.uiState.first { it.pages.size == 1 }

        viewModel.addPage("https://neporn.com/tags/european/", "European")
        viewModel.uiState.first { it.pages.size == 2 }

        viewModel.addPage("https://neporn.com/top-rated/", "Top")
        val state = viewModel.uiState.first { it.pages.size == 3 }

        assertEquals(3, state.pages.size)
        assertEquals(setOf("HD", "European", "Top"), state.pages.map { it.label }.toSet())
    }

    @Test
    fun `delete removes page correctly`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/categories/hd-porn/", "HD"),
            CustomPage("/tags/european/", "European"),
            CustomPage("/top-rated/", "Top")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.deletePage(1)

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Deleted }

        assertEquals(2, state.pages.size)
        assertEquals(listOf("HD", "Top"), state.pages.map { it.label })
    }

    @Test
    fun `reorder changes page order`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/categories/hd-porn/", "HD"),
            CustomPage("/tags/european/", "European"),
            CustomPage("/top-rated/", "Top")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.reorderPages(0, 2)

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }

        assertEquals(listOf("European", "Top", "HD"), state.pages.map { it.label })
    }

    @Test
    fun `clear removes all pages`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/categories/hd-porn/", "HD"),
            CustomPage("/tags/european/", "European")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.clearAll()

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }

        assertTrue(state.pages.isEmpty())
    }

    @Test
    fun `filter works with Neporn pages`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/categories/hd-porn/", "HD Porn"),
            CustomPage("/tags/european/", "European"),
            CustomPage("/models/jane-doe/", "Jane Doe"),
            CustomPage("/top-rated/", "Top Rated")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.filterPages("hd")

        val state = viewModel.uiState.first { it.filterQuery == "hd" }

        assertEquals(1, state.filteredPages.size)
        assertEquals("HD Porn", state.filteredPages[0].label)
    }

    @Test
    fun `initial state loads from repository`() = runTest {
        repository.setInitialData(listOf(
            CustomPage("/categories/test/", "Test Category")
        ))

        viewModel = createViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(1, state.pages.size)
        assertEquals("Test Category", state.pages[0].label)
        assertFalse(state.isLoading)
    }
}
