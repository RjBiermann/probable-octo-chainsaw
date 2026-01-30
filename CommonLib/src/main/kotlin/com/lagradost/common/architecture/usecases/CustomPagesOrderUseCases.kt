package com.lagradost.common.architecture.usecases

import android.util.Log
import com.lagradost.common.CustomPage
import com.lagradost.common.CustomPagesRepository
import com.lagradost.common.SuspendCustomPagesRepository
import com.lagradost.common.SuspendRepositoryAdapter
import kotlinx.coroutines.CancellationException

/**
 * Use cases for ordering custom pages.
 * Handles position validation and list reordering.
 *
 * @param repository Suspend-based data persistence layer
 * @param logTag Tag for logging operations
 */
class CustomPagesOrderUseCases(
    private val repository: SuspendCustomPagesRepository,
    private val logTag: String = "OrderUseCases"
) {
    /** Convenience constructor wrapping a synchronous repository. */
    constructor(
        syncRepository: CustomPagesRepository,
        logTag: String = "OrderUseCases"
    ) : this(SuspendRepositoryAdapter(syncRepository), logTag)

    /**
     * Move an item from one position to another.
     *
     * @param fromPosition Source position
     * @param toPosition Target position
     * @param currentPages Current pages list
     * @return Success with reordered list, or Error
     */
    suspend fun reorderPages(
        fromPosition: Int,
        toPosition: Int,
        currentPages: List<CustomPage>
    ): UseCaseResult<List<CustomPage>> = safeUseCaseCall("Failed to reorder pages") {
        if (fromPosition !in currentPages.indices || toPosition !in currentPages.indices) {
            Log.w(logTag, "Invalid reorder positions: from=$fromPosition, to=$toPosition, size=${currentPages.size}")
            return@safeUseCaseCall UseCaseResult.Error("Invalid positions")
        }

        val mutablePages = currentPages.toMutableList()
        val movedItem = mutablePages.removeAt(fromPosition)
        mutablePages.add(toPosition, movedItem)

        saveAndReturn(mutablePages.toList(), "Failed to save order")
    }

    /**
     * Save a complete reordered list (for drag-and-drop).
     *
     * @param reorderedPages The complete reordered list
     * @return Success with saved list, or Error
     */
    suspend fun saveOrder(
        reorderedPages: List<CustomPage>
    ): UseCaseResult<List<CustomPage>> = safeUseCaseCall("Failed to save order") {
        saveAndReturn(reorderedPages, "Failed to save order")
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
