package com.lagradost.common.architecture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule for ViewModel testing with coroutines.
 * Automatically sets up and tears down test dispatchers.
 *
 * Usage:
 * ```kotlin
 * class MyViewModelTest {
 *     @get:Rule
 *     val vmRule = ViewModelTestRule()
 *
 *     @Test
 *     fun `test something`() = runTest {
 *         val viewModel = MyViewModel(fakeRepository)
 *         viewModel.loadData()
 *         assertEquals(expectedData, viewModel.state.value.data)
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTestRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description?) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        super.finished(description)
        Dispatchers.resetMain()
    }
}
