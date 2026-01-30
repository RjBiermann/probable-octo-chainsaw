package com.lagradost.common.architecture

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Base ViewModel for plugin settings and UI components.
 * Extends Jetpack's ViewModel for lifecycle awareness and automatic cleanup.
 *
 * Features:
 * - Inherits lifecycle-scoped coroutines via Jetpack's viewModelScope
 * - Safe coroutine launching with error handling
 * - Per-operation dispatcher parameter for testability
 *
 * Example:
 * ```kotlin
 * class MyViewModel(private val repository: MyRepository) : PluginViewModel() {
 *     private val _state = MutableStateFlow(MyState())
 *     val state: StateFlow<MyState> = _state.asStateFlow()
 *
 *     fun loadData() = launchSafe {
 *         val data = repository.load()
 *         _state.update { it.copy(data = data) }
 *     }
 * }
 * ```
 */
abstract class PluginViewModel : ViewModel() {

    /**
     * Public accessor for [ViewModel.onCleared] to allow manual cleanup
     * when the ViewModel is not managed by [ViewModelProvider].
     * Plugin settings dialogs create ViewModels directly (not via ViewModelProvider),
     * so the Fragment must call this in onDestroyView() to cancel viewModelScope coroutines.
     */
    public override fun onCleared() {
        super.onCleared()
    }

    companion object {
        private const val TAG = "PluginViewModel"
    }

    /**
     * Launch a coroutine in viewModelScope with automatic error handling.
     *
     * @param dispatcher The dispatcher to run on (default: IO for network/disk operations)
     * @param onError Callback for error handling (default: logs error)
     * @param block The suspend function to execute
     * @return Job that can be cancelled if needed
     */
    protected fun launchSafe(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        onError: (Throwable) -> Unit = { e ->
            Log.e(TAG, "${this::class.simpleName}: Operation failed", e)
        },
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewModelScope.launch(dispatcher) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

}
