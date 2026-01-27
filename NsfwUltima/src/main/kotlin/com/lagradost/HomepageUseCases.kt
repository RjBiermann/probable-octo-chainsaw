package com.lagradost

/**
 * Use cases for homepage operations (Clean Architecture application layer).
 *
 * These classes coordinate business logic and persistence, keeping
 * UI code thin and domain logic testable.
 */

/**
 * Result of a use case execution.
 */
sealed class UseCaseResult<out T> {
    data class Success<T>(val data: T) : UseCaseResult<T>()
    data class Failure(val error: String) : UseCaseResult<Nothing>()
}

/**
 * Data needed for saving a homepage.
 */
data class SaveHomepageRequest(
    val homepage: Homepage,
    val selectedFeeds: List<AvailableFeed>,
    val isNew: Boolean
)

/**
 * Result data from saving a homepage.
 */
data class SaveHomepageData(
    val updatedHomepages: List<Homepage>,
    val updatedFeeds: List<FeedItem>
)

/**
 * Use case: Save a homepage with its feed selection.
 *
 * Coordinates:
 * 1. Updating homepage list (add new or update existing)
 * 2. Transforming feed assignments (via FeedAssignmentService)
 * 3. Persisting both to storage
 *
 * Note: On partial save failure, returns Failure result. Caller should reload data to restore consistency.
 */
class SaveHomepageUseCase(private val repository: HomepageRepository) {

    fun execute(
        request: SaveHomepageRequest,
        currentHomepages: List<Homepage>,
        currentFeeds: List<FeedItem>
    ): UseCaseResult<SaveHomepageData> {
        // Step 1: Update homepage list
        val updatedHomepages = if (request.isNew) {
            currentHomepages + request.homepage
        } else {
            currentHomepages.map { hp ->
                if (hp.id == request.homepage.id) request.homepage else hp
            }
        }

        // Step 2: Update feed assignments using domain service
        val feedResult = FeedAssignmentService.updateHomepageFeedSelection(
            currentFeeds = currentFeeds,
            selectedFeeds = request.selectedFeeds,
            homepageId = request.homepage.id,
            isExistingHomepage = !request.isNew
        )

        // Step 3: Persist both
        val feedsSaved = repository.saveFeeds(feedResult.updatedFeeds)
        val homepagesSaved = repository.saveHomepages(updatedHomepages)

        return if (feedsSaved && homepagesSaved) {
            UseCaseResult.Success(SaveHomepageData(
                updatedHomepages = updatedHomepages,
                updatedFeeds = feedResult.updatedFeeds
            ))
        } else {
            UseCaseResult.Failure("Failed to save changes")
        }
    }
}

/**
 * Result data from deleting a homepage.
 */
data class DeleteHomepageData(
    val updatedHomepages: List<Homepage>,
    val updatedFeeds: List<FeedItem>
)

/**
 * Use case: Delete a homepage and clean up feed assignments.
 *
 * Coordinates:
 * 1. Removing homepage from list
 * 2. Clearing feed assignments (via FeedAssignmentService)
 * 3. Removing orphaned feeds
 * 4. Persisting changes
 */
class DeleteHomepageUseCase(private val repository: HomepageRepository) {

    fun execute(
        homepageId: String,
        currentHomepages: List<Homepage>,
        currentFeeds: List<FeedItem>
    ): UseCaseResult<DeleteHomepageData> {
        // Step 1: Remove homepage from list
        val updatedHomepages = currentHomepages.filter { it.id != homepageId }

        // Step 2: Clean up feed assignments
        val updatedFeeds = FeedAssignmentService.deleteHomepageAndCleanup(currentFeeds, homepageId)

        // Step 3: Persist both
        val feedsSaved = repository.saveFeeds(updatedFeeds)
        val homepagesSaved = repository.saveHomepages(updatedHomepages)

        return if (feedsSaved && homepagesSaved) {
            UseCaseResult.Success(DeleteHomepageData(
                updatedHomepages = updatedHomepages,
                updatedFeeds = updatedFeeds
            ))
        } else {
            UseCaseResult.Failure("Failed to save changes")
        }
    }
}

/**
 * Use case: Load all homepage data from storage.
 */
class LoadHomepageDataUseCase(private val repository: HomepageRepository) {

    data class HomepageData(
        val homepages: List<Homepage>,
        val feeds: List<FeedItem>,
        val settings: NsfwUltimaSettings
    )

    fun execute(): HomepageData {
        var feeds = repository.loadFeeds()

        // Attempt migration if no feeds
        if (feeds.isEmpty()) {
            repository.migrateFromLegacy()?.let { migrated ->
                feeds = migrated
            }
        }

        return HomepageData(
            homepages = repository.loadHomepages(),
            feeds = feeds,
            settings = repository.loadSettings()
        )
    }
}

/**
 * Use case: Reset all data.
 */
class ResetAllDataUseCase(private val repository: HomepageRepository) {

    fun execute(): UseCaseResult<Unit> {
        return if (repository.clearAll()) {
            UseCaseResult.Success(Unit)
        } else {
            UseCaseResult.Failure("Failed to reset data")
        }
    }
}
