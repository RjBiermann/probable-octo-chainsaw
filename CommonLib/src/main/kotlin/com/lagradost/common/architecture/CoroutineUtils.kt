package com.lagradost.common.architecture

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine utilities for safe API calls and error handling.
 */

/**
 * Execute a suspend function safely with automatic error logging.
 * Properly propagates CancellationException (never catches it).
 *
 * Example:
 * ```kotlin
 * val result = safeApiCall(TAG, "loadVideo") {
 *     app.get(url).document
 * }
 * result.onSuccess { document -> parseVideo(document) }
 * result.onFailure { error -> showError(error.message) }
 * ```
 *
 * @param tag Log tag for error messages
 * @param operation Description of the operation (for logging)
 * @param context Coroutine context to run on (default: IO)
 * @param block The suspend function to execute
 * @return Result<T> with success value or exception
 */
suspend fun <T> safeApiCall(
    tag: String,
    operation: String,
    context: CoroutineContext = Dispatchers.IO,
    block: suspend () -> T
): Result<T> = withContext(context) {
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        // CRITICAL: Never catch CancellationException - it's used for coroutine cancellation
        throw e
    } catch (e: Exception) {
        Log.e(tag, "$operation failed: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Run a block of code that should never swallow CancellationException.
 * Use this for try-catch blocks that need to preserve coroutine cancellation.
 *
 * Example:
 * ```kotlin
 * val data = runCatchingCancellable {
 *     repository.load()
 * }.getOrElse { emptyList() }
 * ```
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e  // Re-throw cancellation
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/**
 * Retry a suspend operation with exponential backoff.
 *
 * @param maxAttempts Maximum number of retry attempts
 * @param initialDelayMillis Initial delay before first retry
 * @param factor Multiplier for delay on each retry
 * @param shouldRetry Predicate to determine if error is retryable
 * @param block The operation to retry
 * @return Result of the operation or throws last exception
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMillis: Long = 100,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = { it !is CancellationException },
    block: suspend () -> T
): T {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    var currentDelay = initialDelayMillis
    var lastException: Throwable? = null

    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: CancellationException) {
            throw e  // Never retry cancellation
        } catch (e: Throwable) {
            lastException = e
            if (!shouldRetry(e) || attempt == maxAttempts - 1) {
                throw e
            }
            kotlinx.coroutines.delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }

    throw lastException ?: IllegalStateException("Retry failed without exception")
}
