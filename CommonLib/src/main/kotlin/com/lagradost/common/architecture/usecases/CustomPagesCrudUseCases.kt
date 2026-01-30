package com.lagradost.common.architecture.usecases

import android.util.Log
import com.lagradost.common.CustomPage
import com.lagradost.common.CustomPagesRepository
import com.lagradost.common.SuspendCustomPagesRepository
import com.lagradost.common.SuspendRepositoryAdapter
import com.lagradost.common.ValidationResult
import kotlinx.coroutines.CancellationException

/**
 * Use cases for CRUD operations on custom pages.
 * Handles validation, duplicate checking, and repository coordination.
 *
 * @param repository Suspend-based data persistence layer
 * @param validator URL validation function (plugin-specific)
 * @param logTag Tag for logging operations
 */
class CustomPagesCrudUseCases(
    private val repository: SuspendCustomPagesRepository,
    private val validator: (String) -> ValidationResult,
    private val logTag: String = "CrudUseCases"
) {
    /** Convenience constructor wrapping a synchronous repository. */
    constructor(
        syncRepository: CustomPagesRepository,
        validator: (String) -> ValidationResult,
        logTag: String = "CrudUseCases"
    ) : this(SuspendRepositoryAdapter(syncRepository), validator, logTag)

    /**
     * Load all custom pages from repository.
     */
    suspend fun loadPages(): UseCaseResult<List<CustomPage>> = safeUseCaseCall("Failed to load pages") {
        UseCaseResult.Success(repository.load())
    }

    /**
     * Add a new custom page after validation.
     *
     * @param url Full URL to validate
     * @param label Custom label (empty = auto-generate from URL)
     * @param existingPages Current pages list (for duplicate checking)
     * @return Success with updated pages list, or Error
     */
    suspend fun addPage(
        url: String,
        label: String,
        existingPages: List<CustomPage>
    ): UseCaseResult<List<CustomPage>> = safeUseCaseCall("Failed to add page") {
        val validationResult = validator(url)
        if (validationResult !is ValidationResult.Valid) {
            return@safeUseCaseCall UseCaseResult.Error(
                when (validationResult) {
                    is ValidationResult.InvalidDomain -> "Invalid domain"
                    is ValidationResult.InvalidPath -> "Invalid URL path"
                    is ValidationResult.Valid -> error("unreachable")
                }
            )
        }

        val finalLabel = label.ifBlank { validationResult.label }
        val newPage = CustomPage(validationResult.path, finalLabel)

        if (existingPages.any { it.path == newPage.path }) {
            return@safeUseCaseCall UseCaseResult.Error("This section already exists")
        }

        val updatedPages = existingPages + newPage
        saveAndReturn(updatedPages, "Failed to save changes")
    }

    /**
     * Delete a page by its index in the source list.
     *
     * @param sourceIndex Index in the full (unfiltered) pages list
     * @param currentPages Current pages list
     * @return Success with updated pages list, or Error
     */
    suspend fun deletePage(
        sourceIndex: Int,
        currentPages: List<CustomPage>
    ): UseCaseResult<List<CustomPage>> = safeUseCaseCall("Failed to delete page") {
        if (sourceIndex !in currentPages.indices) {
            Log.w(logTag, "Invalid delete index: $sourceIndex, size=${currentPages.size}")
            return@safeUseCaseCall UseCaseResult.Error("Invalid position")
        }

        val updatedPages = currentPages.toMutableList().apply { removeAt(sourceIndex) }
        saveAndReturn(updatedPages, "Failed to delete section")
    }

    /**
     * Clear all custom pages.
     */
    suspend fun clearAll(): UseCaseResult<List<CustomPage>> = safeUseCaseCall("Failed to clear pages") {
        saveAndReturn(emptyList(), "Failed to clear data")
    }

    /**
     * Save pages and return success or error based on repository result.
     */
    private suspend fun saveAndReturn(
        pages: List<CustomPage>,
        errorMessage: String
    ): UseCaseResult<List<CustomPage>> {
        val saved = repository.save(pages)
        return if (saved) {
            UseCaseResult.Success(pages)
        } else {
            Log.e(logTag, "Repository.save() returned false: $errorMessage")
            UseCaseResult.Error(errorMessage)
        }
    }

    /**
     * Execute a use case operation with CancellationException safety and logging.
     */
    private inline fun <T> safeUseCaseCall(
        errorPrefix: String,
        block: () -> UseCaseResult<T>
    ): UseCaseResult<T> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(logTag, "$errorPrefix: ${e.message}", e)
            UseCaseResult.Error("$errorPrefix: ${e.message}", e)
        }
    }
}
