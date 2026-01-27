package com.lagradost

/**
 * Repository interface for homepage data access (Clean Architecture port).
 *
 * This interface defines the contract for persisting homepage and feed data.
 * The actual implementation can be swapped for testing or different storage backends.
 */
interface HomepageRepository {

    /**
     * Load all homepages from storage.
     */
    fun loadHomepages(): List<Homepage>

    /**
     * Save all homepages to storage.
     * @return true if save succeeded, false otherwise
     */
    fun saveHomepages(homepages: List<Homepage>): Boolean

    /**
     * Load all feed items from storage.
     */
    fun loadFeeds(): List<FeedItem>

    /**
     * Save all feed items to storage.
     * @return true if save succeeded, false otherwise
     */
    fun saveFeeds(feeds: List<FeedItem>): Boolean

    /**
     * Load global settings.
     */
    fun loadSettings(): NsfwUltimaSettings

    /**
     * Save global settings.
     * @return true if save succeeded, false otherwise
     */
    fun saveSettings(settings: NsfwUltimaSettings): Boolean

    /**
     * Clear all stored data.
     * @return true if clear succeeded, false otherwise
     */
    fun clearAll(): Boolean

    /**
     * Attempt to migrate from old data format.
     * @return Migrated feeds if migration occurred, null otherwise
     */
    fun migrateFromLegacy(): List<FeedItem>?
}

/**
 * Default implementation using NsfwUltimaStorage (Cloudstream SharedPreferences).
 */
class CloudstreamHomepageRepository : HomepageRepository {

    override fun loadHomepages(): List<Homepage> = NsfwUltimaStorage.loadHomepages()

    override fun saveHomepages(homepages: List<Homepage>): Boolean =
        NsfwUltimaStorage.saveHomepages(homepages)

    override fun loadFeeds(): List<FeedItem> = NsfwUltimaStorage.loadFeedList()

    override fun saveFeeds(feeds: List<FeedItem>): Boolean =
        NsfwUltimaStorage.saveFeedList(feeds)

    override fun loadSettings(): NsfwUltimaSettings = NsfwUltimaStorage.loadSettings()

    override fun saveSettings(settings: NsfwUltimaSettings): Boolean =
        NsfwUltimaStorage.saveSettings(settings)

    override fun clearAll(): Boolean = NsfwUltimaStorage.clearAll()

    override fun migrateFromLegacy(): List<FeedItem>? =
        NsfwUltimaStorage.migrateFromPluginStates()
}
