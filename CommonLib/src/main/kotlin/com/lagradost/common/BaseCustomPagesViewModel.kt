package com.lagradost.common

import android.util.Log
import com.lagradost.common.architecture.PluginViewModel
import com.lagradost.common.architecture.usecases.CustomPagesCrudUseCases
import com.lagradost.common.architecture.usecases.CustomPagesOrderUseCases
import com.lagradost.common.architecture.usecases.UseCaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Base ViewModel for custom pages settings screens.
 * Coordinates use cases for CRUD and ordering operations, owns UI state.
 *
 * Usage (via factory — no subclassing needed):
 * ```kotlin
 * val viewModel = CustomPagesViewModelFactory.create(
 *     repository = MyPlugin.createRepository("MyPluginVM"),
 *     validator = MyUrlValidator::validate,
 *     logTag = "MyPluginVM"
 * )
 * ```
 */
abstract class BaseCustomPagesViewModel(
    private val crudUseCases: CustomPagesCrudUseCases,
    private val orderUseCases: CustomPagesOrderUseCases
) : PluginViewModel() {

    /**
     * UI state for the settings screen.
     * Single source of truth for all UI data.
     *
     * Note: [filteredPages] is stored as a field rather than computed on-the-fly
     * because StateFlow collectors receive the full state object. Every mutation
     * of [pages] or [filterQuery] must call [applyFilter] to keep it in sync.
     */
    data class SettingsUiState(
        val pages: List<CustomPage> = emptyList(),
        val filteredPages: List<CustomPage> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val filterQuery: String = "",
        val isReorderMode: Boolean = false,
        val selectedReorderPosition: Int? = null,
        val saveStatus: SaveStatus = SaveStatus.Idle
    )

    /**
     * Status of save operations (for one-time UI events like Snackbar/Toast).
     */
    sealed class SaveStatus {
        object Idle : SaveStatus()
        data class Success(val message: String) : SaveStatus()
        data class Error(val message: String) : SaveStatus()
        /** Delete succeeded — Fragment can show Snackbar with "Undo" action. */
        data class Deleted(val message: String, val deletedPage: CustomPage, val sourceIndex: Int) : SaveStatus()
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    protected open val logTag: String = "BaseCustomPagesVM"

    init {
        loadPages()
    }

    /**
     * Load pages from repository and update state.
     */
    fun loadPages() = launchSafe(
        onError = { e ->
            Log.e(logTag, "Failed to load pages", e)
            _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load pages: ${e.message}") }
        }
    ) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        when (val result = crudUseCases.loadPages()) {
            is UseCaseResult.Success -> {
                _uiState.update {
                    it.copy(
                        pages = result.data,
                        filteredPages = applyFilter(result.data, it.filterQuery),
                        isLoading = false
                    )
                }
            }
            is UseCaseResult.Error -> {
                _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    /**
     * Add a new custom page after validation.
     */
    fun addPage(url: String, label: String) = launchSafe(
        onError = { e ->
            Log.e(logTag, "Failed to add page", e)
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to save: ${e.message}")) }
        }
    ) {
        val currentPages = _uiState.value.pages

        when (val result = crudUseCases.addPage(url, label, currentPages)) {
            is UseCaseResult.Success -> {
                _uiState.update {
                    it.copy(
                        pages = result.data,
                        filteredPages = applyFilter(result.data, it.filterQuery),
                        saveStatus = SaveStatus.Success("Section added successfully")
                    )
                }
            }
            is UseCaseResult.Error -> {
                _uiState.update { it.copy(saveStatus = SaveStatus.Error(result.message)) }
            }
        }
    }

    /**
     * Delete a page by its position in the filtered list.
     */
    fun deletePage(filteredPosition: Int) = launchSafe(
        onError = { e ->
            Log.e(logTag, "Failed to delete page", e)
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to delete: ${e.message}")) }
        }
    ) {
        val currentState = _uiState.value
        val pageToDelete = currentState.filteredPages.getOrNull(filteredPosition)

        if (pageToDelete == null) {
            Log.w(logTag, "Invalid delete position: $filteredPosition")
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Invalid position")) }
            return@launchSafe
        }

        val sourceIndex = currentState.pages.indexOf(pageToDelete)
        if (sourceIndex < 0) {
            Log.e(logTag, "Page not found in source list: ${pageToDelete.label}")
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Page not found")) }
            return@launchSafe
        }

        when (val result = crudUseCases.deletePage(sourceIndex, currentState.pages)) {
            is UseCaseResult.Success -> {
                _uiState.update {
                    it.copy(
                        pages = result.data,
                        filteredPages = applyFilter(result.data, it.filterQuery),
                        saveStatus = SaveStatus.Deleted(
                            message = "${pageToDelete.label} deleted",
                            deletedPage = pageToDelete,
                            sourceIndex = sourceIndex
                        )
                    )
                }
            }
            is UseCaseResult.Error -> {
                Log.e(logTag, "Delete failed: ${result.message}")
                _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to delete section")) }
            }
        }
    }

    /**
     * Reorder pages (move from one filtered-list position to another).
     * Translates filtered positions to source positions before operating on the full list.
     */
    fun reorderPages(fromFilteredPosition: Int, toFilteredPosition: Int) = launchSafe(
        onError = { e ->
            Log.e(logTag, "Failed to reorder pages", e)
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to reorder: ${e.message}")) }
        }
    ) {
        val currentState = _uiState.value
        val currentPages = currentState.pages
        val filtered = currentState.filteredPages

        // Translate filtered positions to source positions
        val fromPage = filtered.getOrNull(fromFilteredPosition)
        val toPage = filtered.getOrNull(toFilteredPosition)
        if (fromPage == null || toPage == null) {
            Log.w(logTag, "Invalid reorder positions: from=$fromFilteredPosition, to=$toFilteredPosition")
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Invalid reorder position")) }
            return@launchSafe
        }
        val fromSource = currentPages.indexOf(fromPage)
        val toSource = currentPages.indexOf(toPage)
        if (fromSource < 0 || toSource < 0) {
            Log.e(logTag, "Reorder page not found in source list")
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Page not found in list")) }
            return@launchSafe
        }

        when (val result = orderUseCases.reorderPages(fromSource, toSource, currentPages)) {
            is UseCaseResult.Success -> {
                _uiState.update {
                    it.copy(
                        pages = result.data,
                        filteredPages = applyFilter(result.data, it.filterQuery),
                        saveStatus = SaveStatus.Success("Order updated")
                    )
                }
            }
            is UseCaseResult.Error -> {
                Log.e(logTag, "Reorder failed: ${result.message}")
                _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to save order")) }
            }
        }
    }

    /**
     * Save a reordered list of pages (used for drag-and-drop operations).
     */
    fun saveOrder(reorderedPages: List<CustomPage>) = launchSafe(
        onError = { e ->
            Log.e(logTag, "Failed to save order", e)
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to save order: ${e.message}")) }
        }
    ) {
        when (val result = orderUseCases.saveOrder(reorderedPages)) {
            is UseCaseResult.Success -> {
                _uiState.update {
                    it.copy(
                        pages = result.data,
                        filteredPages = applyFilter(result.data, it.filterQuery),
                        saveStatus = SaveStatus.Success("Order saved")
                    )
                }
            }
            is UseCaseResult.Error -> {
                Log.e(logTag, "Save order failed: ${result.message}")
                _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to save order")) }
            }
        }
    }

    /**
     * Clear all custom pages.
     */
    fun clearAll() = launchSafe(
        onError = { e ->
            Log.e(logTag, "Failed to clear pages", e)
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to clear: ${e.message}")) }
        }
    ) {
        when (val result = crudUseCases.clearAll()) {
            is UseCaseResult.Success -> {
                _uiState.update {
                    it.copy(
                        pages = emptyList(),
                        filteredPages = emptyList(),
                        saveStatus = SaveStatus.Success("All data cleared")
                    )
                }
            }
            is UseCaseResult.Error -> {
                _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to clear data")) }
            }
        }
    }

    /**
     * Undo the last delete by re-inserting the page at its original position.
     */
    fun undoDelete(page: CustomPage, sourceIndex: Int) = launchSafe(
        onError = { e ->
            Log.e(logTag, "Failed to undo delete", e)
            _uiState.update { it.copy(saveStatus = SaveStatus.Error("Undo failed: ${e.message}")) }
        }
    ) {
        val currentPages = _uiState.value.pages.toMutableList()
        val insertIndex = sourceIndex.coerceIn(0, currentPages.size)
        currentPages.add(insertIndex, page)

        when (val result = orderUseCases.saveOrder(currentPages)) {
            is UseCaseResult.Success -> {
                _uiState.update {
                    it.copy(
                        pages = result.data,
                        filteredPages = applyFilter(result.data, it.filterQuery),
                        saveStatus = SaveStatus.Success("${page.label} restored")
                    )
                }
            }
            is UseCaseResult.Error -> {
                _uiState.update { it.copy(saveStatus = SaveStatus.Error("Failed to restore section")) }
            }
        }
    }

    /**
     * Filter pages by query string (case-insensitive label search).
     */
    fun filterPages(query: String) {
        _uiState.update {
            it.copy(
                filterQuery = query,
                filteredPages = applyFilter(it.pages, query)
            )
        }
    }

    /**
     * Toggle reorder mode (TV mode only).
     */
    fun toggleReorderMode() {
        _uiState.update {
            it.copy(
                isReorderMode = !it.isReorderMode,
                selectedReorderPosition = null
            )
        }
    }

    /**
     * Select a page for reordering (TV mode only).
     */
    fun selectForReorder(position: Int?) {
        _uiState.update {
            it.copy(selectedReorderPosition = position)
        }
    }

    /**
     * Clear save status after the UI has consumed the event (Snackbar or Toast).
     */
    fun clearSaveStatus() {
        _uiState.update { it.copy(saveStatus = SaveStatus.Idle) }
    }

    private fun applyFilter(pages: List<CustomPage>, query: String): List<CustomPage> {
        if (query.isBlank()) return pages
        return pages.filter { it.label.contains(query, ignoreCase = true) }
    }
}
