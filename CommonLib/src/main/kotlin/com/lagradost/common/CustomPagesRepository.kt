package com.lagradost.common

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository interface for custom pages storage operations.
 * Follows the Repository pattern to abstract storage implementation.
 *
 * Benefits:
 * - Testable: Can be mocked in unit tests
 * - Flexible: Can swap implementations (global storage, SharedPreferences, database)
 * - Single Responsibility: Storage logic separated from business logic
 */
interface CustomPagesRepository {
    /**
     * Load all custom pages from storage.
     * @return List of custom pages, or empty list if none exist or on error
     */
    fun load(): List<CustomPage>

    /**
     * Save custom pages to storage.
     * @param pages The pages to save
     * @return true if save was successful, false otherwise
     */
    fun save(pages: List<CustomPage>): Boolean
}

/**
 * Suspend-based repository interface for coroutine-native storage operations.
 * Preferred over [CustomPagesRepository] in use cases and ViewModels.
 */
interface SuspendCustomPagesRepository {
    suspend fun load(): List<CustomPage>
    suspend fun save(pages: List<CustomPage>): Boolean
}

/**
 * Adapts a synchronous [CustomPagesRepository] to [SuspendCustomPagesRepository]
 * by running operations on [Dispatchers.IO].
 */
class SuspendRepositoryAdapter(
    private val delegate: CustomPagesRepository
) : SuspendCustomPagesRepository {
    override suspend fun load(): List<CustomPage> = withContext(Dispatchers.IO) {
        delegate.load()
    }

    override suspend fun save(pages: List<CustomPage>): Boolean = withContext(Dispatchers.IO) {
        delegate.save(pages)
    }
}

/**
 * Type alias for the getKey function from AcraApplication.
 * Narrowed from AcraApplication's generic `getKey<T>(key: String): T?` to String-only for JSON storage.
 */
typealias GetKeyFunction = (key: String) -> String?

/**
 * Type alias for the setKey function from AcraApplication.
 * Narrowed from AcraApplication's generic `setKey(key: String, value: Any?)` to String-only for JSON storage.
 */
typealias SetKeyFunction = (key: String, value: String?) -> Unit

/**
 * Repository implementation using Cloudstream's global storage.
 * Storage operations are passed as lambdas to avoid direct cloudstream3 dependency.
 *
 * Usage:
 * ```kotlin
 * val repository = GlobalStorageCustomPagesRepository(
 *     storageKey = "MY_CUSTOM_PAGES",
 *     legacyPrefsName = "my_plugin_prefs",
 *     getKey = { AcraApplication.getKey(it) },
 *     setKey = { key, value -> AcraApplication.setKey(key, value) }
 * )
 * ```
 *
 * @param storageKey The key for global storage (e.g., "FULLPORNER_CUSTOM_PAGES")
 * @param legacyPrefsName Legacy SharedPreferences name for migration
 * @param getKey Function to retrieve values from global storage
 * @param setKey Function to store values in global storage
 * @param legacyKey Legacy key within SharedPreferences (default: "custom_pages")
 * @param tag Log tag for debugging
 */
class GlobalStorageCustomPagesRepository(
    private val storageKey: String,
    private val legacyPrefsName: String,
    private val getKey: GetKeyFunction,
    private val setKey: SetKeyFunction,
    private val legacyKey: String = "custom_pages",
    private val tag: String = "CustomPagesRepo"
) : CustomPagesRepository {

    /**
     * Load pages from global storage.
     * Note: For migration from legacy storage, use loadWithMigration() instead.
     *
     * @throws Exception if storage is corrupted or cannot be read,
     *   so callers can distinguish "no data" from "load failed"
     */
    override fun load(): List<CustomPage> {
        val json = getKey(storageKey) ?: return emptyList()
        return CustomPage.listFromJson(json)
    }

    /**
     * Load pages with context for legacy migration.
     * Call this during plugin initialization to ensure migration happens.
     *
     * @throws Exception if storage is corrupted or cannot be read,
     *   so callers can distinguish "no data" from "migration failed"
     */
    fun loadWithMigration(context: Context): List<CustomPage> {
        // Try global storage first
        val json = getKey(storageKey)
        if (json != null) {
            return CustomPage.listFromJson(json)
        }

        // Fallback: Check legacy SharedPreferences and migrate
        return migrateFromLegacy(context)
    }

    override fun save(pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(storageKey, json)
            // Verify save succeeded
            getKey(storageKey) == json
        } catch (e: Exception) {
            Log.e(tag, "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }

    /**
     * Migrate data from legacy SharedPreferences to global storage.
     * Called automatically by loadWithMigration() if global storage is empty.
     *
     * @return The migrated pages, or empty list if no legacy data
     */
    private fun migrateFromLegacy(context: Context): List<CustomPage> {
        val prefs = context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        val legacyJson = prefs.getString(legacyKey, null) ?: return emptyList()

        val pages = CustomPage.listFromJson(legacyJson)
        if (pages.isEmpty()) return emptyList()

        // Migrate to global storage
        setKey(storageKey, legacyJson)

        // Verify migration succeeded before deleting legacy data
        if (getKey(storageKey) == legacyJson) {
            prefs.edit().remove(legacyKey).apply()
            Log.i(tag, "Migrated ${pages.size} custom pages to global storage")
        } else {
            Log.w(tag, "Migration verification failed, keeping legacy data")
        }

        return pages
    }
}

/**
 * In-memory repository for testing purposes.
 * Does not persist data - useful for unit tests.
 */
class InMemoryCustomPagesRepository : CustomPagesRepository {
    private var pages: List<CustomPage> = emptyList()

    override fun load(): List<CustomPage> = pages

    override fun save(pages: List<CustomPage>): Boolean {
        this.pages = pages
        return true
    }

    /** Set initial data for testing */
    fun setInitialData(pages: List<CustomPage>) {
        this.pages = pages
    }
}

/**
 * In-memory suspend repository for testing.
 */
class InMemorySuspendRepository : SuspendCustomPagesRepository {
    private var pages: List<CustomPage> = emptyList()
    var saveFailure: Boolean = false

    override suspend fun load(): List<CustomPage> = pages

    override suspend fun save(pages: List<CustomPage>): Boolean {
        if (saveFailure) return false
        this.pages = pages
        return true
    }

    fun setInitialData(pages: List<CustomPage>) {
        this.pages = pages
    }
}
