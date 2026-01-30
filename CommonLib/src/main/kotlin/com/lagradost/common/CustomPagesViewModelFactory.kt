package com.lagradost.common

import com.lagradost.common.architecture.usecases.CustomPagesCrudUseCases
import com.lagradost.common.architecture.usecases.CustomPagesOrderUseCases

/**
 * Concrete ViewModel for plugins that don't need custom behavior.
 * Created via [CustomPagesViewModelFactory].
 */
class CustomPagesViewModel(
    crudUseCases: CustomPagesCrudUseCases,
    orderUseCases: CustomPagesOrderUseCases,
    override val logTag: String
) : BaseCustomPagesViewModel(crudUseCases, orderUseCases)

/**
 * Factory for creating custom pages ViewModels with dependency injection.
 * Eliminates per-plugin ViewModel boilerplate by centralizing use case wiring.
 *
 * Usage:
 * ```kotlin
 * override val viewModel = CustomPagesViewModelFactory.create(
 *     repository = MyPlugin.createRepository("MyPluginVM"),
 *     validator = MyUrlValidator::validate,
 *     logTag = "MyPluginVM"
 * )
 * ```
 */
object CustomPagesViewModelFactory {

    /**
     * Create a ViewModel with all dependencies wired.
     *
     * @param repository Storage backend (sync — auto-wrapped to suspend)
     * @param validator Plugin-specific URL validation function
     * @param logTag Tag for logging operations
     */
    fun create(
        repository: CustomPagesRepository,
        validator: (String) -> ValidationResult,
        logTag: String
    ): CustomPagesViewModel {
        return CustomPagesViewModel(
            CustomPagesCrudUseCases(repository, validator, "${logTag}Crud"),
            CustomPagesOrderUseCases(repository, "${logTag}Order"),
            logTag
        )
    }

    /**
     * Create a ViewModel with a suspend-native repository.
     */
    fun create(
        repository: SuspendCustomPagesRepository,
        validator: (String) -> ValidationResult,
        logTag: String
    ): CustomPagesViewModel {
        return CustomPagesViewModel(
            CustomPagesCrudUseCases(repository, validator, "${logTag}Crud"),
            CustomPagesOrderUseCases(repository, "${logTag}Order"),
            logTag
        )
    }
}
