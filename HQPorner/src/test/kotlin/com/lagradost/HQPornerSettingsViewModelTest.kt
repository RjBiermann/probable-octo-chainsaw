package com.lagradost

import com.lagradost.common.BaseCustomPagesViewModel
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HQPornerSettingsViewModelTest {

    @get:Rule
    val vmRule = ViewModelTestRule()

    private lateinit var repository: InMemoryCustomPagesRepository
    private lateinit var viewModel: BaseCustomPagesViewModel

    private fun createViewModel(repo: InMemoryCustomPagesRepository = repository): BaseCustomPagesViewModel {
        return CustomPagesViewModelFactory.create(
            repository = repo,
            validator = HQPornerUrlValidator::validate,
            logTag = "HQPornerTest"
        )
    }

    @Before
    fun setup() {
        repository = InMemoryCustomPagesRepository()
        viewModel = createViewModel()
    }

    @Test
    fun `state after init load is correct`() = runTest {
        val state = viewModel.uiState.first { !it.isLoading }

        assertTrue(state.pages.isEmpty())
        assertTrue(state.filteredPages.isEmpty())
    }

    @Test
    fun `validates and rejects invalid URLs`() = runTest {
        viewModel.addPage("https://wrongsite.com/test/", "Test")

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Error }

        assertTrue(state.pages.isEmpty())
        assertTrue(state.saveStatus is BaseCustomPagesViewModel.SaveStatus.Error)
    }

    @Test
    fun `can delete pages`() = runTest {
        repository.setInitialData(listOf(
            com.lagradost.common.CustomPage("/test1", "Test 1"),
            com.lagradost.common.CustomPage("/test2", "Test 2")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.deletePage(0)

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Deleted }
        assertEquals(1, state.pages.size)
    }

    @Test
    fun `can reorder pages`() = runTest {
        repository.setInitialData(listOf(
            com.lagradost.common.CustomPage("/test1", "Test 1"),
            com.lagradost.common.CustomPage("/test2", "Test 2")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.reorderPages(0, 1)

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }
        assertEquals("Test 2", state.pages[0].label)
    }

    @Test
    fun `can clear all pages`() = runTest {
        repository.setInitialData(listOf(
            com.lagradost.common.CustomPage("/test1", "Test 1")
        ))
        viewModel = createViewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.clearAll()

        val state = viewModel.uiState.first { it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success }
        assertTrue(state.pages.isEmpty())
    }
}
