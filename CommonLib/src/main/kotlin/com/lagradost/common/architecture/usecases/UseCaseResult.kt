package com.lagradost.common.architecture.usecases

/**
 * Result type for use case operations.
 * Provides type-safe error handling with domain-specific error messages.
 *
 * @param T The success data type
 */
sealed class UseCaseResult<out T> {
    data class Success<T>(val data: T) : UseCaseResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : UseCaseResult<Nothing>()
}
