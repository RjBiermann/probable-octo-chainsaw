package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.cancellation.CancellationException

/**
 * NsfwUltima MainAPI provider.
 *
 * Each instance represents a single homepage. The homepage parameter determines
 * which feeds are shown - only feeds assigned to this homepage's groupId are displayed.
 *
 * @param plugin The plugin instance
 * @param homepage The homepage/group this provider represents (null for setup/empty state)
 */
class NsfwUltima(
    val plugin: NsfwUltimaPlugin,
    private val homepage: Homepage? = null
) : MainAPI() {

    // Name is derived from homepage, or "NSFW Ultima" for setup state
    override var name = homepage?.let { "NSFW Ultima - ${it.name}" } ?: "NSFW Ultima"
    override var mainUrl = "https://nsfwultima.local"
    override var supportedTypes = setOf(TvType.NSFW)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object {
        private const val TAG = "NsfwUltima"
        /** Maximum number of fallback providers to try when URL doesn't match any provider */
        private const val MAX_FALLBACK_ATTEMPTS = 3
    }

    // Cached data
    private var cachedFeedList: List<FeedItem> = emptyList()
    private var cachedSettings: NsfwUltimaSettings = NsfwUltimaSettings()
    private var initialized = false

    /** The group ID this provider filters by (null means setup/empty state) */
    val homepageId: String? get() = homepage?.id

    /**
     * Initialize by loading feed list and settings.
     * Only feeds assigned to this homepage's groupId are cached.
     */
    fun initialize() {
        if (initialized) {
            Log.d(TAG, "initialize() skipped - already initialized for homepage: ${homepage?.name ?: "setup"}")
            return
        }

        cachedSettings = NsfwUltimaStorage.loadSettings()

        // Load all feeds, then filter to this homepage's group
        val allFeeds = NsfwUltimaStorage.loadFeedList()

        // Try migration from old format if feed list is empty
        val feedsToFilter = if (allFeeds.isEmpty()) {
            NsfwUltimaStorage.migrateFromPluginStates() ?: emptyList()
        } else {
            allFeeds
        }

        // Filter feeds to only those assigned to this homepage
        cachedFeedList = if (homepage != null) {
            feedsToFilter.filter { it.isInHomepage(homepage.id) }
        } else {
            // Setup state - no feeds (show configuration message)
            emptyList()
        }

        initialized = true
        Log.d(TAG, "Initialized homepage '${homepage?.name ?: "setup"}' with ${cachedFeedList.size} feeds. First 3: [${cachedFeedList.toPreviewString()}]")
    }

    /**
     * Refresh feed list (called from settings when user makes changes).
     * Reloads and filters feeds for this homepage.
     */
    fun refreshFeedList() {
        cachedSettings = NsfwUltimaStorage.loadSettings()

        // Load all feeds, then filter to this homepage's group
        val allFeeds = NsfwUltimaStorage.loadFeedList()
        cachedFeedList = if (homepage != null) {
            allFeeds.filter { it.isInHomepage(homepage.id) }
        } else {
            emptyList()
        }

        Log.d(TAG, "Refreshed homepage '${homepage?.name ?: "setup"}': ${cachedFeedList.size} feeds. First 3: [${cachedFeedList.toPreviewString()}]")
    }

    /**
     * Check if a plugin is NSFW based on name and type.
     */
    private fun isNsfwPlugin(provider: MainAPI): Boolean {
        if (provider.name in NsfwPluginConstants.KNOWN_NSFW_PLUGINS) return true
        if (provider.supportedTypes.contains(TvType.NSFW)) return true
        val nameLower = provider.name.lowercase()
        return NsfwPluginConstants.NSFW_KEYWORDS.any { nameLower.contains(it) }
    }

    /**
     * Extract the domain from a URL for matching against provider mainUrl.
     */
    private fun extractDomain(url: String): String {
        return url.removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }

    /**
     * Check if a URL belongs to a provider based on domain matching.
     */
    private fun urlMatchesProvider(url: String, provider: MainAPI): Boolean {
        val urlDomain = extractDomain(url)
        val providerDomain = extractDomain(provider.mainUrl)
        return urlDomain.equals(providerDomain, ignoreCase = true) ||
               urlDomain.endsWith(".$providerDomain", ignoreCase = true)
    }

    /**
     * Build mainPage from the user's feed list.
     * Only shows feeds assigned to this homepage's group.
     */
    override val mainPage: List<MainPageData>
        get() {
            if (!initialized) initialize()

            // Setup state (no homepage) - show configuration message
            if (homepage == null) {
                return listOf(
                    MainPageData(
                        name = "Create a Homepage in Settings",
                        data = "__CONFIGURE__"
                    )
                )
            }

            // Homepage exists but has no feeds assigned
            if (cachedFeedList.isEmpty()) {
                return listOf(
                    MainPageData(
                        name = "Add feeds to '${homepage.name}' in Settings",
                        data = "__CONFIGURE__"
                    )
                )
            }

            Log.d(TAG, "mainPage for '${homepage.name}': ${cachedFeedList.size} feeds. First 3: [${cachedFeedList.toPreviewString()}]")

            return cachedFeedList.map { feed ->
                val displayName = if (cachedSettings.showPluginNames) {
                    "[${feed.pluginName}] ${feed.sectionName}"
                } else {
                    feed.sectionName
                }

                val metadata = AggregatedSectionData(
                    pluginName = feed.pluginName,
                    originalName = feed.sectionName,
                    data = feed.sectionData
                )

                MainPageData(
                    name = displayName,
                    data = metadata.toJson()
                )
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (!initialized) initialize()

        // Handle configuration placeholder
        if (request.data == "__CONFIGURE__") {
            return newHomePageResponse(
                HomePageList(
                    name = request.name,
                    list = emptyList(),
                    isHorizontalImages = true
                ),
                hasNext = false
            )
        }

        // Parse section metadata
        val sectionData = AggregatedSectionData.fromJson(request.data)
            ?: throw ErrorLoadingException("Invalid section data")

        // Copy provider list to avoid holding lock during network calls
        val providersCopy = synchronized(allProviders) { allProviders.toList() }

        // Find the provider
        val provider = providersCopy.find { it.name == sectionData.pluginName }
            ?: throw ErrorLoadingException("Plugin '${sectionData.pluginName}' not found")

        return try {
            // Delegate to the actual plugin
            val response = provider.getMainPage(
                page,
                MainPageRequest(
                    name = sectionData.originalName,
                    data = sectionData.data,
                    horizontalImages = request.horizontalImages
                )
            ) ?: throw ErrorLoadingException("Plugin returned null")

            // Update the section name to use our display name with plugin prefix
            newHomePageResponse(
                response.items.map { homePageList ->
                    HomePageList(
                        name = request.name,  // Use our prefixed name
                        list = homePageList.list,
                        isHorizontalImages = homePageList.isHorizontalImages
                    )
                },
                hasNext = response.hasNext
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from ${sectionData.pluginName}: ${e.message}", e)
            throw ErrorLoadingException("Failed to load from ${sectionData.pluginName}: ${e.message}")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (!initialized) initialize()

        // Setup state - no search
        if (homepage == null) {
            return emptyList()
        }

        // Get unique plugin names from this homepage's feed list
        val enabledPluginNames = cachedFeedList.map { it.pluginName }.toSet()

        // Copy provider list to avoid holding lock during network calls
        val providersCopy = synchronized(allProviders) { allProviders.toList() }
        val providers = providersCopy.filter { it.name in enabledPluginNames }

        if (providers.isEmpty()) {
            return emptyList()
        }

        // Search in parallel with limited concurrency
        val semaphore = Semaphore(cachedSettings.maxSearchConcurrency)

        return coroutineScope {
            providers.map { provider ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            withTimeout(cachedSettings.searchTimeoutMs) {
                                provider.search(query)?.map { result ->
                                    // Prefix with plugin name for identification
                                    prefixSearchResult(result, provider.name)
                                } ?: emptyList()
                            }
                        } catch (e: CancellationException) {
                            throw e  // Don't swallow coroutine cancellation
                        } catch (e: Exception) {
                            Log.e(TAG, "Search failed for ${provider.name}: ${e.message}")
                            emptyList()
                        }
                    }
                }
            }.awaitAll().flatten()
        }
    }

    /**
     * Prefix search result name with plugin name.
     */
    private fun prefixSearchResult(result: SearchResponse, pluginName: String): SearchResponse {
        val prefix = "[$pluginName] "
        return when (result) {
            is MovieSearchResponse -> result.copy(name = prefix + result.name)
            is AnimeSearchResponse -> result.copy(name = prefix + result.name)
            is TvSeriesSearchResponse -> result.copy(name = prefix + result.name)
            is LiveSearchResponse -> result.copy(name = prefix + result.name)
            is TorrentSearchResponse -> result.copy(name = prefix + result.name)
            else -> result
        }
    }

    override suspend fun load(url: String): LoadResponse {
        if (!initialized) initialize()

        // Setup state - can't load
        if (homepage == null) {
            throw ErrorLoadingException("Please create a homepage in settings first")
        }

        // Get unique plugin names from this homepage's feed list
        val enabledPluginNames = cachedFeedList.map { it.pluginName }.toSet()

        // Copy provider list to avoid holding lock during network calls
        val providersCopy = synchronized(allProviders) { allProviders.toList() }
        val providers = providersCopy.filter { it.name in enabledPluginNames }

        // First try providers that match the URL domain (most likely to succeed)
        val matchingProvider = providers.find { urlMatchesProvider(url, it) }
        if (matchingProvider != null) {
            try {
                val response = matchingProvider.load(url)
                if (response != null &&
                    response.name.isNotBlank() &&
                    !response.posterUrl.isNullOrBlank()
                ) {
                    Log.d(TAG, "Loaded from ${matchingProvider.name}: ${response.name}")
                    return response
                }
            } catch (e: CancellationException) {
                throw e  // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                Log.d(TAG, "Load failed for matching provider ${matchingProvider.name}: ${e.message}")
            }
        }

        // Fallback: try other providers (for edge cases like redirects)
        // Limit to MAX_FALLBACK_ATTEMPTS to avoid excessive network calls
        var fallbackAttempts = 0
        for (provider in providers) {
            if (provider == matchingProvider) continue  // Already tried
            if (fallbackAttempts >= MAX_FALLBACK_ATTEMPTS) {
                Log.d(TAG, "Reached max fallback attempts ($MAX_FALLBACK_ATTEMPTS), stopping load attempts")
                break
            }
            try {
                fallbackAttempts++
                val response = provider.load(url)
                if (response != null &&
                    response.name.isNotBlank() &&
                    !response.posterUrl.isNullOrBlank()
                ) {
                    Log.d(TAG, "Loaded from ${provider.name}: ${response.name}")
                    return response
                }
            } catch (e: CancellationException) {
                throw e  // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                Log.d(TAG, "Load failed for ${provider.name}: ${e.message}")
                // Continue to next provider
            }
        }

        throw ErrorLoadingException("No plugin could load this URL")
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!initialized) initialize()

        // Setup state - can't load links
        if (homepage == null) {
            return false
        }

        // Get unique plugin names from this homepage's feed list
        val enabledPluginNames = cachedFeedList.map { it.pluginName }.toSet()

        // Copy provider list to avoid holding lock during network calls
        val providersCopy = synchronized(allProviders) { allProviders.toList() }
        val providers = providersCopy.filter { it.name in enabledPluginNames }

        // First try provider that matches the URL domain (most likely to succeed)
        val matchingProvider = providers.find { urlMatchesProvider(data, it) }
        if (matchingProvider != null) {
            try {
                val success = matchingProvider.loadLinks(data, isCasting, subtitleCallback, callback)
                if (success) {
                    Log.d(TAG, "loadLinks succeeded for matching provider ${matchingProvider.name}")
                    return true
                }
            } catch (e: CancellationException) {
                throw e  // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                Log.d(TAG, "loadLinks failed for matching provider ${matchingProvider.name}: ${e.message}")
            }
        }

        // Fallback: try other providers (for edge cases like redirects or extractors)
        // Limit to MAX_FALLBACK_ATTEMPTS to avoid excessive network calls
        var fallbackAttempts = 0
        for (provider in providers) {
            if (provider == matchingProvider) continue  // Already tried
            if (fallbackAttempts >= MAX_FALLBACK_ATTEMPTS) {
                Log.d(TAG, "Reached max fallback attempts ($MAX_FALLBACK_ATTEMPTS), stopping loadLinks attempts")
                break
            }
            try {
                fallbackAttempts++
                val success = provider.loadLinks(data, isCasting, subtitleCallback, callback)
                if (success) {
                    Log.d(TAG, "loadLinks succeeded for ${provider.name}")
                    return true
                }
            } catch (e: CancellationException) {
                throw e  // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                Log.d(TAG, "loadLinks failed for ${provider.name}: ${e.message}")
                // Continue to next provider
            }
        }

        return false
    }

    /**
     * Get current feed list for settings UI.
     */
    fun getFeedList(): List<FeedItem> {
        if (!initialized) initialize()
        return cachedFeedList
    }

    /**
     * Get current settings for settings UI.
     */
    fun getSettings(): NsfwUltimaSettings = cachedSettings
}
