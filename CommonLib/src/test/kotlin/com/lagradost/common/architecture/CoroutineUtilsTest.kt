package com.lagradost.common.architecture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for coroutine utility functions.
 * Verifies safe API calls, retry logic, and cancellation handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineUtilsTest {

    @get:Rule
    val vmRule = ViewModelTestRule()

    @Test
    fun `safeApiCall returns success for successful operation`() = runTest {
        val result = safeApiCall("TEST", "operation") {
            "success-value"
        }

        assertTrue(result.isSuccess)
        assertEquals("success-value", result.getOrNull())
    }

    @Test
    fun `safeApiCall returns failure for exceptions`() = runTest {
        val result = safeApiCall("TEST", "failing-operation") {
            throw IllegalArgumentException("Test error")
        }

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertEquals("Test error", exception?.message)
    }

    @Test
    fun `safeApiCall propagates CancellationException`() = runTest {
        assertFailsWith<CancellationException> {
            safeApiCall("TEST", "cancelled-operation") {
                throw CancellationException("Coroutine cancelled")
            }
        }
    }

    @Test
    fun `safeApiCall handles network-like exceptions`() = runTest {
        val result = safeApiCall("TEST", "network-call") {
            throw java.net.SocketTimeoutException("Connection timeout")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.net.SocketTimeoutException)
    }

    @Test
    fun `runCatchingCancellable returns success`() = runTest {
        val result = runCatchingCancellable {
            42
        }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `runCatchingCancellable catches exceptions`() = runTest {
        val result = runCatchingCancellable {
            throw RuntimeException("Error")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `runCatchingCancellable propagates CancellationException`() = runTest {
        assertFailsWith<CancellationException> {
            runCatchingCancellable {
                throw CancellationException("Cancelled")
            }
        }
    }

    @Test
    fun `retryWithBackoff succeeds on first attempt`() = runTest {
        var attempts = 0
        val result = retryWithBackoff(maxAttempts = 3) {
            attempts++
            "success"
        }

        assertEquals(1, attempts)
        assertEquals("success", result)
    }

    @Test
    fun `retryWithBackoff retries on failure`() = runTest {
        var attempts = 0
        val result = retryWithBackoff(
            maxAttempts = 3,
            initialDelayMillis = 10
        ) {
            attempts++
            if (attempts < 3) {
                throw RuntimeException("Temporary failure")
            }
            "success-after-retries"
        }

        assertEquals(3, attempts)
        assertEquals("success-after-retries", result)
    }

    @Test
    fun `retryWithBackoff throws after max attempts`() = runTest {
        var attempts = 0
        val exception = assertFailsWith<RuntimeException> {
            retryWithBackoff(
                maxAttempts = 3,
                initialDelayMillis = 10
            ) {
                attempts++
                throw RuntimeException("Always fails")
            }
        }

        assertEquals(3, attempts)
        assertEquals("Always fails", exception.message)
    }

    @Test
    fun `retryWithBackoff propagates CancellationException immediately`() = runTest {
        var attempts = 0
        assertFailsWith<CancellationException> {
            retryWithBackoff(maxAttempts = 3) {
                attempts++
                throw CancellationException("Cancelled")
            }
        }

        assertEquals(1, attempts) // Should not retry cancellation
    }

    @Test
    fun `retryWithBackoff respects shouldRetry predicate`() = runTest {
        var attempts = 0
        val exception = assertFailsWith<IllegalStateException> {
            retryWithBackoff(
                maxAttempts = 5,
                initialDelayMillis = 10,
                shouldRetry = { it !is IllegalStateException }
            ) {
                attempts++
                throw IllegalStateException("Non-retryable error")
            }
        }

        assertEquals(1, attempts) // Should not retry when predicate returns false
        assertEquals("Non-retryable error", exception.message)
    }

    @Test
    fun `retryWithBackoff uses exponential backoff`() = runTest {
        val delays = mutableListOf<Long>()
        var attempts = 0

        try {
            retryWithBackoff(
                maxAttempts = 4,
                initialDelayMillis = 50,
                factor = 2.0
            ) {
                attempts++
                if (attempts > 1) {
                    delays.add(System.currentTimeMillis())
                }
                throw RuntimeException("Test")
            }
        } catch (_: RuntimeException) {
            // Expected
        }

        assertEquals(4, attempts)
        assertEquals(3, delays.size) // 3 retries after first attempt

        // Verify delays are increasing (with some tolerance for timing)
        if (delays.size >= 2) {
            val firstDelay = delays[1] - delays[0]
            val secondDelay = if (delays.size > 2) delays[2] - delays[1] else 0
            assertTrue(secondDelay >= firstDelay, "Exponential backoff should increase delays")
        }
    }

    @Test
    fun `safeApiCall can be used in concurrent contexts`() = runTest {
        val results = mutableListOf<Result<Int>>()

        val jobs = (1..10).map { i ->
            launch {
                val result = safeApiCall("TEST", "concurrent-$i") {
                    delay(10)
                    i * 2
                }
                synchronized(results) {
                    results.add(result)
                }
            }
        }

        jobs.forEach { it.join() }

        assertEquals(10, results.size)
        assertTrue(results.all { it.isSuccess })
        assertEquals((1..10).map { it * 2 }.toSet(), results.mapNotNull { it.getOrNull() }.toSet())
    }

    @Test
    fun `retryWithBackoff handles alternating success and failure`() = runTest {
        var attempts = 0
        val result = retryWithBackoff(
            maxAttempts = 5,
            initialDelayMillis = 10
        ) {
            attempts++
            when (attempts) {
                1, 2 -> throw RuntimeException("Fail $attempts")
                else -> "success"
            }
        }

        assertEquals(3, attempts)
        assertEquals("success", result)
    }
}
