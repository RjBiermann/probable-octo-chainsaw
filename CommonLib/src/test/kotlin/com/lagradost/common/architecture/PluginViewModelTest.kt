package com.lagradost.common.architecture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for PluginViewModel base class.
 * Verifies lifecycle management, coroutine scope handling, and safe execution patterns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginViewModelTest {

    @get:Rule
    val vmRule = ViewModelTestRule()

    private lateinit var viewModel: TestViewModel

    // Test ViewModel implementation
    private class TestViewModel : PluginViewModel() {
        var executionCount = 0
        var lastError: Throwable? = null

        fun executeWithLaunchSafe(shouldFail: Boolean = false) = launchSafe(
            onError = { e -> lastError = e }
        ) {
            executionCount++
            if (shouldFail) {
                throw IllegalStateException("Test error")
            }
        }
    }

    @Before
    fun setup() {
        viewModel = TestViewModel()
    }

    @After
    fun tearDown() {
        // ViewModel cleanup happens automatically, can't manually trigger onCleared()
    }

    @Test
    fun `launchSafe executes block successfully`() = runTest {
        viewModel.executeWithLaunchSafe(shouldFail = false).join()

        assertEquals(1, viewModel.executionCount)
        assertEquals(null, viewModel.lastError)
    }

    @Test
    fun `launchSafe catches exceptions and calls onError`() = runTest {
        viewModel.executeWithLaunchSafe(shouldFail = true).join()

        assertEquals(1, viewModel.executionCount)
        assertTrue(viewModel.lastError is IllegalStateException)
        assertEquals("Test error", viewModel.lastError?.message)
    }

    @Test
    fun `multiple launchSafe calls execute independently`() = runTest {
        val job1 = viewModel.executeWithLaunchSafe(shouldFail = false)
        val job2 = viewModel.executeWithLaunchSafe(shouldFail = false)
        val job3 = viewModel.executeWithLaunchSafe(shouldFail = true)

        job1.join()
        job2.join()
        job3.join()

        assertEquals(3, viewModel.executionCount)
        assertTrue(viewModel.lastError is IllegalStateException) // Last error is from job3
    }

    @Test
    fun `jobs complete successfully`() = runTest {
        val job = viewModel.executeWithLaunchSafe()
        job.join()

        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }
}
