package com.lagradost.common

import com.lagradost.common.architecture.ViewModelTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CustomPagesViewModelFactoryTest {

    @get:Rule
    val vmRule = ViewModelTestRule()

    private fun validator(url: String): ValidationResult {
        return ValidationResult.Valid(url.substringAfter(".com"), "Test Label")
    }

    @Test
    fun `create with sync repository returns working ViewModel`() = runTest {
        val repo = InMemoryCustomPagesRepository()
        repo.setInitialData(listOf(CustomPage("/a", "A")))

        val vm = CustomPagesViewModelFactory.create(repo, ::validator, "TestTag")

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(1, state.pages.size)
        assertEquals("A", state.pages[0].label)
    }

    @Test
    fun `create with suspend repository returns working ViewModel`() = runTest {
        val repo = InMemorySuspendRepository()
        repo.setInitialData(listOf(CustomPage("/b", "B")))

        val vm = CustomPagesViewModelFactory.create(repo, ::validator, "TestTag")

        val state = vm.uiState.first { !it.isLoading }
        assertEquals(1, state.pages.size)
        assertEquals("B", state.pages[0].label)
    }

    @Test
    fun `factory-created ViewModel supports CRUD`() = runTest {
        val repo = InMemoryCustomPagesRepository()
        val vm = CustomPagesViewModelFactory.create(repo, ::validator, "CrudTest")

        vm.addPage("https://example.com/path", "My Page")
        val state = vm.uiState.first {
            it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success
        }
        assertEquals(1, state.pages.size)

        vm.clearAll()
        val cleared = vm.uiState.first {
            it.pages.isEmpty() && it.saveStatus is BaseCustomPagesViewModel.SaveStatus.Success
        }
        assertTrue(cleared.pages.isEmpty())
    }
}
