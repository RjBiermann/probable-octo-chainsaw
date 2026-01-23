package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class NsfwUltima(val plugin: NsfwUltimaPlugin) : MainAPI() {
    override var name = "NSFW Ultima"
    override var mainUrl = "https://nsfwultima.local"
    override var supportedTypes = setOf(TvType.NSFW)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object {
        private const val TAG = "NsfwUltima"
    }

    // Cached data - now feed-centric
    private var cachedFeedList: List<FeedItem> = emptyList()
    private var cachedSettings: NsfwUltimaSettings = NsfwUltimaSettings()
    private var initialized = false

    /**
     * Initialize by loading feed list and settings.
     */
    fun initialize() {
        if (initialized) return

        cachedSettings = NsfwUltimaStorage.loadSettings()
        cachedFeedList = NsfwUltimaStorage.loadFeedList()

        // Try migration from old format if feed list is empty
        if (cachedFeedList.isEmpty()) {
            NsfwUltimaStorage.migrateFromPluginStates()?.let { migrated ->
                cachedFeedList = migrated
            }
        }

        initialized = true
        Log.d(TAG, "Initialized with ${cachedFeedList.size} feeds")
    }

    /**
     * Refresh feed list (called from settings when user makes changes).
     */
    fun refreshFeedList() {
        cachedSettings = NsfwUltimaStorage.loadSettings()
        cachedFeedList = NsfwUltimaStorage.loadFeedList()
        Log.d(TAG, "Refreshed feed list: ${cachedFeedList.size} feeds")
    }

    /**
     * Legacy method for compatibility - redirects to refreshFeedList.
     */
    fun refreshPluginStates() {
        refreshFeedList()
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
     * Order is preserved exactly as user arranged it.
     */
    override val mainPage: List<MainPageData>
        get() {
            if (!initialized) initialize()

            if (cachedFeedList.isEmpty()) {
                // Return a placeholder section that shows setup instructions
                return listOf(
                    MainPageData(
                        name = "Configure NSFW Ultima",
                        data = "__CONFIGURE__"
                    )
                )
            }

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

        // Get unique plugin names from the feed list
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

        // Get unique plugin names from the feed list
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
            } catch (e: Exception) {
                Log.d(TAG, "Load failed for matching provider ${matchingProvider.name}: ${e.message}")
            }
        }

        // Fallback: try other providers (for edge cases like redirects)
        for (provider in providers) {
            if (provider == matchingProvider) continue  // Already tried
            try {
                val response = provider.load(url)
                if (response != null &&
                    response.name.isNotBlank() &&
                    !response.posterUrl.isNullOrBlank()
                ) {
                    Log.d(TAG, "Loaded from ${provider.name}: ${response.name}")
                    return response
                }
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

        // Get unique plugin names from the feed list
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
            } catch (e: Exception) {
                Log.d(TAG, "loadLinks failed for matching provider ${matchingProvider.name}: ${e.message}")
            }
        }

        // Fallback: try other providers (for edge cases like redirects or extractors)
        for (provider in providers) {
            if (provider == matchingProvider) continue  // Already tried
            try {
                val success = provider.loadLinks(data, isCasting, subtitleCallback, callback)
                if (success) {
                    Log.d(TAG, "loadLinks succeeded for ${provider.name}")
                    return true
                }
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
