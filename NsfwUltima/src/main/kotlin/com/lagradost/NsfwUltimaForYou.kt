package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.ContentOrientationConfig
import com.lagradost.common.FuzzyDeduplicator
import com.lagradost.common.SmartFeaturesConfig
import com.lagradost.common.intelligence.BrowsingIntelligence
import com.lagradost.common.intelligence.TagSource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.cancellation.CancellationException

/**
 * "NsfwUltima - For You" MainAPI.
 * Generates a personalized homepage based on BrowsingIntelligence data.
 *
 * Dynamic rows:
 * - Because You Favorited (content matching favorite videos' tags)
 * - Recommended For You (affinity-tag based, queries known category URLs)
 * - Top tag rows (one per high-affinity tag)
 *
 * Deduplicates across plugins using FuzzyDeduplicator.
 */
class NsfwUltimaForYou(
    val plugin: NsfwUltimaPlugin
) : MainAPI() {

    override var name = "NSFW Ultima - For You"
    override var mainUrl = "https://nsfwultima-foryou.local"
    override var supportedTypes = setOf(TvType.NSFW)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object {
        private const val TAG = "NsfwUltimaForYou"
        private const val HOMEPAGE_PREFS_PREFIX = "nsfw_common/homepage_prefs"
        /** Set to true for verbose debug logging during development. */
        private const val DEBUG = false
        private const val MAX_RECOMMENDED = 25
        private const val MAX_TAG_ROWS = 3
        private const val ITEMS_PER_TAG_ROW = 25
        private const val MAX_FAVORITES_RECS = 25

        // Section data keys used in mainPage and getMainPage routing
        private const val SECTION_FAVORITES_RECS = "__FAVORITES_RECS__"
        private const val SECTION_RECOMMENDED = "__RECOMMENDED__"
        private const val SECTION_TAG_PREFIX = "__TAG__:"
        private const val SECTION_DISCOVER = "__DISCOVER__"
        private const val SECTION_EMPTY = "__EMPTY__"
        private const val DISCOVER_LAST_SHOWN_PREFIX = "nsfw_ultima_fy/discover_last_shown/"
        private const val DISCOVER_MIN_RESULTS = 3
    }

    private val smartFeaturesConfig by lazy {
        SmartFeaturesConfig(
            getKey = { key -> getKey(key) },
            setKey = { _, _ -> } // Read-only in MainAPI
        )
    }

    private val contentOrientationConfig by lazy {
        ContentOrientationConfig(
            getKey = { key -> getKey(key) },
            setKey = { _, _ -> } // Read-only in MainAPI
        )
    }

    @Volatile private var cachedShowPluginNames: Boolean = true

    /** Prefix a SearchResponse name with [pluginName] if showPluginNames is enabled. */
    private fun prefixResult(result: SearchResponse, pluginName: String): SearchResponse {
        if (!cachedShowPluginNames) return result
        val prefix = "[$pluginName] "
        if (result.name.startsWith(prefix)) return result
        return when (result) {
            is MovieSearchResponse -> result.copy(name = prefix + result.name)
            is AnimeSearchResponse -> result.copy(name = prefix + result.name)
            is TvSeriesSearchResponse -> result.copy(name = prefix + result.name)
            is LiveSearchResponse -> result.copy(name = prefix + result.name)
            is TorrentSearchResponse -> result.copy(name = prefix + result.name)
            else -> {
                Log.w(TAG, "prefixResult: unknown SearchResponse type ${result::class.simpleName}")
                result
            }
        }
    }

    private fun isSectionEnabled(key: String, default: Boolean = true): Boolean {
        return getKey<String>("$HOMEPAGE_PREFS_PREFIX/$key")?.toBoolean() ?: default
    }

    @Volatile private var cachedMainPage: List<MainPageData>? = null
    @Volatile private var mainPageCacheTime: Long = 0L
    private val mainPageCacheTtlMs = 60_000L // 1 minute

    /** Cached affinity tags bundled for atomic volatile writes. Refreshed in getMainPage. */
    private data class AffinityCache(
        val affinityTags: List<com.lagradost.common.intelligence.TagAffinity> = emptyList(),
        val genreTags: List<com.lagradost.common.intelligence.TagAffinity> = emptyList(),
        val bodyTypeTags: List<com.lagradost.common.intelligence.TagAffinity> = emptyList(),
        val performerTags: List<com.lagradost.common.intelligence.TagAffinity> = emptyList()
    )
    @Volatile private var affinityCache: AffinityCache? = null

    /** Tracks sections that returned empty on last load, excluded from next buildMainPage().
     *  Entries expire after [emptySectionTtlMs] so sections are retried when user data changes. */
    private val emptySections = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val emptySectionTtlMs = 5 * 60 * 1000L // retry empty sections after 5 min

    /** Cross-section URL dedup: tracks URLs already shown in any section during
     *  the current homepage load cycle. Reset when mainPage cache rebuilds. */
    private val crossSectionSeenUrls = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Filter out items whose URL was already shown by another section.
     *  [targetCount] is the desired number of items after dedup — if the filtered
     *  list is shorter, items are already exhausted and no backfill is possible. */
    private fun filterCrossSectionDuplicates(items: List<SearchResponse>, targetCount: Int = Int.MAX_VALUE): List<SearchResponse> {
        val result = mutableListOf<SearchResponse>()
        for (item in items) {
            if (crossSectionSeenUrls.putIfAbsent(item.url, true) == null) {
                result.add(item)
                if (result.size >= targetCount) break
            }
        }
        return result
    }

    private fun isSectionEmpty(key: String): Boolean {
        val ts = emptySections[key] ?: return false
        if (System.currentTimeMillis() - ts > emptySectionTtlMs) {
            emptySections.remove(key)
            return false
        }
        return true
    }

    @Volatile private var freeOnesClassifier: FreeOnesTagClassifier? = null

    private fun ensureClassifier(context: android.content.Context): FreeOnesTagClassifier {
        return freeOnesClassifier ?: FreeOnesTagClassifier(context.applicationContext).also {
            freeOnesClassifier = it
            BrowsingIntelligence.getInstance(context)?.tagClassifier = it
        }
    }

    /** Pre-populate affinity caches so buildMainPage() has tag data on first read. */
    fun warmUpAffinityCache(context: android.content.Context) {
        try {
            val intelligence = BrowsingIntelligence.getInstance(context) ?: return
            ensureClassifier(context)
            affinityCache = AffinityCache(
                affinityTags = intelligence.getAffinityTags(MAX_TAG_ROWS),
                genreTags = intelligence.getAffinityGenres(MAX_TAG_ROWS),
                bodyTypeTags = intelligence.getAffinityBodyTypes(2),
                performerTags = intelligence.getAffinityPerformers(2)
            )
            if (DEBUG) Log.d(TAG, "Warm-up: genres=${affinityCache?.genreTags?.map { it.tag }}, body=${affinityCache?.bodyTypeTags?.map { it.tag }}, performers=${affinityCache?.performerTags?.map { it.tag }}")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Warm-up failed: ${e.message}", e)
        }
    }

    /** Call to force re-evaluation of dynamic mainPage rows. */
    fun invalidateMainPageCache() {
        cachedMainPage = null; affinityCache = null
        emptySections.clear()
        crossSectionSeenUrls.clear()
    }

    override val mainPage: List<MainPageData>
        get() {
            val cached = cachedMainPage
            if (cached != null && System.currentTimeMillis() - mainPageCacheTime < mainPageCacheTtlMs) return cached
            return buildMainPage().also { cachedMainPage = it; mainPageCacheTime = System.currentTimeMillis() }
        }

    private fun buildMainPage(): List<MainPageData> {
        val rows = mutableListOf<MainPageData>()

        if (isSectionEnabled("favorites_recs") && smartFeaturesConfig.tagRecommendationsEnabled && !isSectionEmpty(SECTION_FAVORITES_RECS)) {
            rows.add(MainPageData("Because You Favorited", SECTION_FAVORITES_RECS))
        }

        if (isSectionEnabled("recommended") && smartFeaturesConfig.tagRecommendationsEnabled && !isSectionEmpty(SECTION_RECOMMENDED)) {
            rows.add(MainPageData("Recommended For You", SECTION_RECOMMENDED))
        }

        if (isSectionEnabled("top_tags") && smartFeaturesConfig.tagRecommendationsEnabled) {
            // Type-specific tag rows when typed data is available
            val cache = affinityCache
            val genres = cache?.genreTags
            val bodyTypes = cache?.bodyTypeTags
            val performers = cache?.performerTags
            val hasTypedData = !genres.isNullOrEmpty() || !bodyTypes.isNullOrEmpty() || !performers.isNullOrEmpty()

            if (hasTypedData) {
                val excludedTags = contentOrientationConfig.getExcludedOrientationTags()
                genres?.filter { it.tag !in excludedTags }?.forEach { tag ->
                    rows.add(MainPageData("Because you like ${tag.tag.replaceFirstChar { it.uppercase() }}", "__TAG__:${tag.tag}"))
                }
                bodyTypes?.forEach { tag ->
                    rows.add(MainPageData("More ${tag.tag.replaceFirstChar { it.uppercase() }}", "__TAG__:${tag.tag}"))
                }
                performers?.forEach { tag ->
                    rows.add(MainPageData("More from ${tag.tag.replaceFirstChar { it.uppercase() }}", "__TAG__:${tag.tag}"))
                }
            } else {
                // Fallback to untyped affinity tags (cold start / legacy data)
                val tags = cache?.affinityTags
                if (tags != null) {
                    for (tag in tags) {
                        rows.add(MainPageData("More ${tag.tag.replaceFirstChar { it.uppercase() }}", "__TAG__:${tag.tag}"))
                    }
                }
            }
        }

        if (isSectionEnabled("discover") && !isSectionEmpty(SECTION_DISCOVER)) {
            rows.add(MainPageData("Discover New Sites", SECTION_DISCOVER))
        }

        if (rows.isEmpty()) {
            rows.add(MainPageData("Browse videos to get personalized recommendations", SECTION_EMPTY))
        }

        return rows
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (DEBUG) Log.d(TAG, "getMainPage: request.name='${request.name}' request.data='${request.data}'")
        // Refresh settings cache
        cachedShowPluginNames = NsfwUltimaStorage.loadSettings().showPluginNames
        // Refresh affinity tags cache on IO thread (safe in suspend context)
        // This populates data used by buildMainPage() without blocking the main thread
        if (affinityCache == null) {
            try {
                val context = plugin.activity?.applicationContext
                if (context != null) {
                    val intelligence = BrowsingIntelligence.getInstance(context)
                    if (intelligence != null) {
                        val classifier = ensureClassifier(context)
                        withContext(Dispatchers.IO) {
                            // Validate any pending unknown tags via FreeOnes
                            classifier.validatePending()
                            affinityCache = AffinityCache(
                                affinityTags = intelligence.getAffinityTags(MAX_TAG_ROWS),
                                genreTags = intelligence.getAffinityGenres(MAX_TAG_ROWS),
                                bodyTypeTags = intelligence.getAffinityBodyTypes(2),
                                performerTags = intelligence.getAffinityPerformers(2)
                            )
                        }
                        if (DEBUG) Log.d(TAG, "Affinity tags refreshed: ${affinityCache?.affinityTags?.map { "${it.tag}(${it.score})" }}")
                        if (DEBUG) Log.d(TAG, "Genre tags: ${affinityCache?.genreTags?.map { it.tag }}, body: ${affinityCache?.bodyTypeTags?.map { it.tag }}, performers: ${affinityCache?.performerTags?.map { it.tag }}")
                        // Invalidate mainPage cache so it rebuilds with fresh tags
                        cachedMainPage = null
                        crossSectionSeenUrls.clear()
                    } else {
                        Log.w(TAG, "BrowsingIntelligence.getInstance returned null")
                    }
                } else {
                    Log.w(TAG, "plugin.activity?.applicationContext is null")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh affinity tags: ${e.message}", e)
            }
        }
        val response = when {
            request.data == SECTION_FAVORITES_RECS -> getFavoritesRecs(request)
            request.data == SECTION_RECOMMENDED -> getRecommended(request)
            request.data.startsWith(SECTION_TAG_PREFIX) -> getTagRow(request)

            request.data == SECTION_DISCOVER -> getDiscoverNewSites(request)
            else -> newHomePageResponse(HomePageList(request.name, emptyList(), isHorizontalImages = true), hasNext = false)
        }
        // Track empty sections so buildMainPage() can exclude them next refresh (with TTL)
        // Skip __EMPTY__ placeholder to avoid cache invalidation loop
        if (request.data != SECTION_EMPTY) {
            val isEmpty = response.items.all { it.list.isEmpty() }
            if (isEmpty) {
                if (emptySections.put(request.data, System.currentTimeMillis()) == null) cachedMainPage = null
            } else {
                if (emptySections.remove(request.data) != null) cachedMainPage = null
            }
        }
        return response
    }

    private suspend fun getFavoritesRecs(request: MainPageRequest): HomePageResponse {
        val context = plugin.activity?.applicationContext
            ?: run { Log.w(TAG, "getFavoritesRecs: no context"); return emptyResponse(request) }

        val intelligence = BrowsingIntelligence.getInstance(context)
            ?: run { Log.w(TAG, "getFavoritesRecs: intelligence is null"); return emptyResponse(request) }
        val favorites = intelligence.getUserFavorites()
        if (DEBUG) Log.d(TAG, "getFavoritesRecs: ${favorites.size} favorites, tags: ${favorites.flatMap { it.tags }.distinct().take(10)}")
        if (favorites.isEmpty()) return emptyResponse(request)

        val favTags = favorites.flatMap { it.tags }.groupBy { it.lowercase() }
            .entries.sortedByDescending { it.value.size }
            .take(5)
            .map { it.key }

        if (DEBUG) Log.d(TAG, "getFavoritesRecs: top favTags=$favTags")
        if (favTags.isEmpty()) return emptyResponse(request)

        val providersCopy = safeProvidersCopy()
        val nsfwProviderNames = providersCopy.filter { isNsfwProvider(it) }.map { it.name }
        if (DEBUG) Log.d(TAG, "getFavoritesRecs: available NSFW providers: $nsfwProviderNames")
        val allItems = mutableListOf<SearchResponse>()
        val seen = mutableSetOf<String>()

        for (tag in favTags) {
            if (allItems.size >= MAX_FAVORITES_RECS * 2) break
            val sources = intelligence.getTagSources(tag)
            if (DEBUG) Log.d(TAG, "getFavoritesRecs: tag='$tag' has ${sources.size} tag sources: ${sources.map { "${it.pluginName}→${it.categoryUrl}" }}")

            if (sources.isNotEmpty()) {
                for (source in sources.take(2)) {
                    if (allItems.size >= MAX_FAVORITES_RECS * 2) break
                    try {
                        val items = fetchFromTagSource(source, providersCopy)
                        if (DEBUG) Log.d(TAG, "getFavoritesRecs: fetchFromTagSource('$tag', ${source.pluginName}) returned ${items.size} items")
                        for (item in items) {
                            if (seen.add(item.url) && allItems.size < MAX_FAVORITES_RECS * 2) {
                                allItems.add(item)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch favorites rec for tag '$tag': ${e.message}")
                    }
                }
            } else {
                // No tag sources recorded — fall back to searching across plugins
                if (DEBUG) Log.d(TAG, "getFavoritesRecs: no tag sources for '$tag', falling back to search")
                try {
                    val searchResults = searchAcrossPlugins(tag, providersCopy)
                    if (DEBUG) Log.d(TAG, "getFavoritesRecs: search fallback for '$tag' returned ${searchResults.size} items")
                    for (item in searchResults) {
                        if (seen.add(item.url) && allItems.size < MAX_FAVORITES_RECS * 2) {
                            allItems.add(item)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "getFavoritesRecs: search fallback failed for '$tag': ${e.message}")
                }
            }
        }

        val filtered = filterCrossSectionDuplicates(allItems, MAX_FAVORITES_RECS)
        if (DEBUG) Log.d(TAG, "getFavoritesRecs: returning ${filtered.size} items (${allItems.size} before cross-section dedup)")
        return newHomePageResponse(
            HomePageList(request.name, filtered, isHorizontalImages = true),
            hasNext = false
        )
    }

    private suspend fun getRecommended(request: MainPageRequest): HomePageResponse {
        val context = plugin.activity?.applicationContext
            ?: run { Log.w(TAG, "getRecommended: no context"); return emptyResponse(request) }

        val intelligence = BrowsingIntelligence.getInstance(context)
            ?: run { Log.w(TAG, "getRecommended: intelligence is null"); return emptyResponse(request) }
        val excludedTags = contentOrientationConfig.getExcludedOrientationTags()
        val topTags = intelligence.getAffinityTags(5 + excludedTags.size)
            .filter { it.tag !in excludedTags }
            .take(5)
        if (DEBUG) Log.d(TAG, "getRecommended: topTags=${topTags.map { "${it.tag}(${it.score})" }}, excluded=$excludedTags")
        if (topTags.isEmpty()) return emptyResponse(request)

        val allResults = mutableListOf<SearchResponse>()
        val providersCopy = safeProvidersCopy()
        val semaphore = Semaphore(3)

        coroutineScope {
            for (tag in topTags) {
                val tagSources = intelligence.getTagSources(tag.tag)
                if (DEBUG) Log.d(TAG, "getRecommended: tag='${tag.tag}' has ${tagSources.size} sources: ${tagSources.map { "${it.pluginName}→${it.categoryUrl}" }}")

                if (tagSources.isNotEmpty()) {
                    // Try the first available tag source
                    val source = tagSources.firstOrNull { src ->
                        providersCopy.any { it.name == src.pluginName }
                    }
                    if (source != null) {
                        launch(Dispatchers.IO) {
                            semaphore.withPermit {
                                try {
                                    val results = fetchFromTagSource(source, providersCopy)
                                    if (DEBUG) Log.d(TAG, "getRecommended: fetchFromTagSource('${tag.tag}', ${source.pluginName}) returned ${results.size} items")
                                    synchronized(allResults) { allResults.addAll(results) }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e(TAG, "getRecommended: fetchFromTagSource failed for '${tag.tag}': ${e.message}")
                                }
                            }
                        }
                        continue
                    }
                    if (DEBUG) Log.d(TAG, "getRecommended: tag='${tag.tag}' has sources but no matching provider loaded")
                }

                // No tag sources or no matching provider — fall back to search
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val results = searchAcrossPlugins(tag.tag, providersCopy)
                            if (DEBUG) Log.d(TAG, "getRecommended: search fallback for '${tag.tag}' returned ${results.size} items")
                            synchronized(allResults) { allResults.addAll(results) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "getRecommended: search fallback failed for '${tag.tag}': ${e.message}")
                        }
                    }
                }
            }
        }

        if (DEBUG) Log.d(TAG, "getRecommended: ${allResults.size} total results before dedup")

        // Deduplicate
        val deduped = if (getKey<String>("$HOMEPAGE_PREFS_PREFIX/deduplicate")?.toBoolean() != false) {
            FuzzyDeduplicator.deduplicate(
                allResults,
                titleSelector = { it.name },
                durationSelector = { null },
                threshold = 85
            )
        } else allResults

        val crossFiltered = filterCrossSectionDuplicates(deduped.take(MAX_RECOMMENDED * 2), MAX_RECOMMENDED)
        return newHomePageResponse(
            HomePageList(request.name, crossFiltered, isHorizontalImages = true),
            hasNext = false
        )
    }

    private suspend fun getTagRow(request: MainPageRequest): HomePageResponse {
        val tag = request.data.removePrefix(SECTION_TAG_PREFIX)
        val context = plugin.activity?.applicationContext
            ?: run { Log.w(TAG, "getTagRow('$tag'): no context"); return emptyResponse(request) }

        val intelligence = BrowsingIntelligence.getInstance(context)
            ?: run { Log.w(TAG, "getTagRow('$tag'): intelligence is null"); return emptyResponse(request) }
        val tagSources = intelligence.getTagSources(tag)
        val providersCopy = safeProvidersCopy()

        val source = tagSources.firstOrNull { src ->
            providersCopy.any { it.name == src.pluginName }
        }
        if (DEBUG) Log.d(TAG, "getTagRow('$tag'): ${tagSources.size} tag sources, matched provider: ${source?.pluginName ?: "NONE"}")

        val items = try {
            val primary = if (source != null) {
                fetchFromTagSource(source, providersCopy)
            } else {
                searchAcrossPlugins(tag, providersCopy)
            }
            if (DEBUG) Log.d(TAG, "getTagRow('$tag'): primary returned ${primary.size} items (source=${source != null})")

            // If primary source doesn't have enough unique items after dedup preview,
            // supplement with search results from other plugins
            val uniqueCount = primary.count { crossSectionSeenUrls[it.url] == null }
            if (uniqueCount < ITEMS_PER_TAG_ROW / 2 && source != null) {
                val supplemental = try {
                    searchAcrossPlugins(tag, providersCopy)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "getTagRow('$tag'): supplemental search failed: ${e.message}")
                    emptyList()
                }
                if (DEBUG) Log.d(TAG, "getTagRow('$tag'): supplementing with ${supplemental.size} search results (only $uniqueCount unique from primary)")
                primary + supplemental
            } else {
                primary
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "getTagRow('$tag'): failed: ${e.message}", e)
            emptyList()
        }

        val filtered = filterCrossSectionDuplicates(items, ITEMS_PER_TAG_ROW)
        return newHomePageResponse(
            HomePageList(request.name, filtered, isHorizontalImages = true),
            hasNext = false
        )
    }

    private suspend fun fetchFromTagSource(source: TagSource, providers: List<MainAPI>): List<SearchResponse> {
        val provider = providers.find { it.name == source.pluginName }
        if (provider == null) {
            Log.w(TAG, "fetchFromTagSource: provider '${source.pluginName}' not found in ${providers.size} providers")
            return emptyList()
        }
        // Try to use the category URL as a mainPage data source
        return try {
            if (DEBUG) Log.d(TAG, "fetchFromTagSource: calling ${provider.name}.getMainPage(tag='${source.tag}', url='${source.categoryUrl}')")
            val response = provider.getMainPage(1, MainPageRequest(source.tag, source.categoryUrl, false))
            val items = response?.items?.flatMap { it.list } ?: emptyList()
            if (DEBUG) Log.d(TAG, "fetchFromTagSource: ${provider.name} returned ${items.size} items for '${source.tag}'")
            items.map { prefixResult(it, provider.name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchFromTagSource: ${provider.name}.getMainPage failed for '${source.tag}': ${e.message}, falling back to search")
            // Fallback: search for the tag
            searchAcrossPlugins(source.tag, listOf(provider))
        }
    }

    private suspend fun searchAcrossPlugins(query: String, providers: List<MainAPI>): List<SearchResponse> {
        val nsfwProviders = providers.filter { isNsfwProvider(it) }.take(3)
        if (DEBUG) Log.d(TAG, "searchAcrossPlugins('$query'): searching ${nsfwProviders.map { it.name }}")

        return coroutineScope {
            nsfwProviders.map { provider ->
                async(Dispatchers.IO) {
                    try {
                        withTimeout(5000) {
                            val results = provider.search(query) ?: emptyList()
                            if (DEBUG) Log.d(TAG, "searchAcrossPlugins('$query'): ${provider.name} returned ${results.size} results")
                            results.map { prefixResult(it, provider.name) }
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "searchAcrossPlugins('$query'): ${provider.name} timed out")
                        emptyList()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "searchAcrossPlugins('$query'): ${provider.name} failed: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Search across all NSFW providers
        val providersCopy = safeProvidersCopy()
        return searchAcrossPlugins(query, providersCopy)
    }

    override suspend fun load(url: String): LoadResponse {
        val providersCopy = safeProvidersCopy()
        for (provider in findMatchingProviders(url, providersCopy)) {
            try {
                val response = provider.load(url)
                if (response != null) {
                    appendAlsoAvailableOn(response, provider.name, providersCopy)
                    return response
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Provider ${provider.name} failed to load $url: ${e.message}")
            }
        }
        throw ErrorLoadingException("No plugin could load this URL")
    }

    /**
     * Search other plugins for the same video by title and append matches
     * as "Also Available On: [PluginName]" recommendations.
     * Note: Intentionally mutates [response.recommendations] in-place for efficiency.
     */
    private suspend fun appendAlsoAvailableOn(
        response: LoadResponse,
        sourcePluginName: String,
        providers: List<MainAPI>
    ) {
        val title = response.name
        if (title.isBlank()) return

        val otherProviders = providers.filter {
            isNsfwProvider(it) && it.name != sourcePluginName
        }.take(4)

        if (otherProviders.isEmpty()) return

        val matches = mutableListOf<SearchResponse>()
        coroutineScope {
            otherProviders.map { provider ->
                async(Dispatchers.IO) {
                    try {
                        withTimeout(3000) {
                            val results = provider.search(title.take(40)) ?: emptyList()
                            FuzzyDeduplicator.findMatch(
                                results,
                                title,
                                duration = null,
                                titleSelector = { it.name },
                                durationSelector = { null },
                                threshold = 80
                            )
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "Also-available-on timed out for ${provider.name}")
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Also-available-on match failed for ${provider.name}: ${e.message}")
                        null
                    }
                }
            }.awaitAll().filterNotNull().let { synchronized(matches) { matches.addAll(it) } }
        }

        if (matches.isNotEmpty()) {
            val existing = response.recommendations?.toMutableList() ?: mutableListOf()
            existing.addAll(0, matches)
            response.recommendations = existing
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        for (provider in findMatchingProviders(data, safeProvidersCopy())) {
            try {
                if (provider.loadLinks(data, isCasting, subtitleCallback, callback)) return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Provider ${provider.name} failed to load links for $data: ${e.message}")
            }
        }
        return false
    }

    private suspend fun getDiscoverNewSites(request: MainPageRequest): HomePageResponse {
        val providersCopy = safeProvidersCopy()
        val nsfwProviders = providersCopy.filter { isNsfwProvider(it) }
        if (nsfwProviders.isEmpty()) return emptyResponse(request)

        // LRU rotation: select plugins least-recently shown in Discover
        val selectedPlugins = nsfwProviders
            .sortedBy { getKey<String>("$DISCOVER_LAST_SHOWN_PREFIX${it.name}")?.toLongOrNull() ?: 0L }
            .take(3)

        // Get affinity tags once (outside coroutineScope) for multi-tag fallthrough
        val context = plugin.activity?.applicationContext
        val intelligence = context?.let { BrowsingIntelligence.getInstance(it) }
        val affinityTags = intelligence?.getAffinityTags(5)?.map { it.tag } ?: emptyList()

        val results = coroutineScope {
            selectedPlugins.map { provider ->
                async(Dispatchers.IO) {
                    try {
                        withTimeout(5000) {
                            var best: List<SearchResponse> = emptyList()

                            // Try affinity tags in priority order
                            for (tag in affinityTags) {
                                val searchResults = provider.search(tag)?.take(ITEMS_PER_TAG_ROW) ?: emptyList()
                                if (searchResults.size >= DISCOVER_MIN_RESULTS) {
                                    best = searchResults
                                    break
                                }
                                if (searchResults.size > best.size) best = searchResults
                            }

                            // Fallback: homepage fetch if no tag produced results
                            if (best.isEmpty()) {
                                val mainPageData = provider.mainPage.firstOrNull()
                                if (mainPageData != null) {
                                    val response = provider.getMainPage(1, MainPageRequest(mainPageData.name, mainPageData.data, false))
                                    best = response?.items?.flatMap { it.list }?.take(ITEMS_PER_TAG_ROW) ?: emptyList()
                                }
                            }

                            best.map { prefixResult(it, provider.name) }
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "Discover timed out for ${provider.name}")
                        emptyList()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Discover failed for ${provider.name}: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        // Update LRU timestamps (even on empty results to avoid blocking rotation)
        val now = System.currentTimeMillis()
        for (plugin in selectedPlugins) {
            setKey("$DISCOVER_LAST_SHOWN_PREFIX${plugin.name}", now.toString())
        }

        // Shuffle to interleave providers, then cross-section dedup
        val filtered = filterCrossSectionDuplicates(results.shuffled(), ITEMS_PER_TAG_ROW)
        return newHomePageResponse(
            HomePageList(request.name, filtered, isHorizontalImages = true),
            hasNext = false
        )
    }

    /** Find NSFW providers whose domain matches the given URL. */
    private fun findMatchingProviders(url: String, providers: List<MainAPI>): List<MainAPI> {
        val urlDomain = extractDomain(url)
        return providers.filter { provider ->
            if (!isNsfwProvider(provider)) return@filter false
            val providerDomain = extractDomain(provider.mainUrl)
            urlDomain.equals(providerDomain, ignoreCase = true) ||
                urlDomain.endsWith(".$providerDomain", ignoreCase = true)
        }
    }

    private fun extractDomain(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/")

    private fun isNsfwProvider(provider: MainAPI): Boolean =
        provider.supportedTypes.contains(TvType.NSFW) && !provider.name.startsWith("NSFW Ultima")

    /** Safely snapshot allProviders to avoid ConcurrentModificationException. */
    private fun safeProvidersCopy(): List<MainAPI> =
        try { synchronized(allProviders) { allProviders.toList() } }
        catch (e: Exception) { Log.w(TAG, "Failed to copy allProviders: ${e.message}"); emptyList() }

    private fun emptyResponse(request: MainPageRequest): HomePageResponse {
        return newHomePageResponse(
            HomePageList(request.name, emptyList(), isHorizontalImages = true),
            hasNext = false
        )
    }
}
