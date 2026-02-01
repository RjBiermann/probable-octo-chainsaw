package com.lagradost

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.common.CustomPage
import com.lagradost.common.WatchHistoryConfig
import com.lagradost.common.PluginIntegrationHelper
import com.lagradost.common.cache.CacheAwareClient
import com.lagradost.common.cache.FetchResult
import com.lagradost.common.cache.fetchDocument
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class MissAV(
    private val customPages: List<CustomPage> = emptyList(),
    private val cachedClient: CacheAwareClient? = null,
    private val appContext: Context? = null,
    private val watchHistoryConfig: WatchHistoryConfig? = null
) : MainAPI() {
    companion object {
        private const val TAG = "MissAV"

        // Pre-compiled regex pattern for video source extraction
        private val SOURCE_URL_REGEX = Regex("""source='([^']*)'""")
    }

    override var mainUrl = "https://missav.ws"
    override var name = "MissAV"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            // Default pages - MissAV uses /dm{number}/en/{category} URL format
            add(MainPageData(name = "Recent Update", data = "$mainUrl/dm515/en/new"))
            add(MainPageData(name = "New Release", data = "$mainUrl/dm590/en/release"))
            add(MainPageData(name = "Today Hot", data = "$mainUrl/dm291/en/today-hot"))
            add(MainPageData(name = "Weekly Hot", data = "$mainUrl/dm169/en/weekly-hot"))
            add(MainPageData(name = "Monthly Hot", data = "$mainUrl/dm263/en/monthly-hot"))

            // Custom pages from user settings
            customPages.forEach { page ->
                val safePath = if (page.path.startsWith("/")) page.path else "/${page.path}"
                add(MainPageData(name = page.label, data = "$mainUrl$safePath"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val url = "${request.data}?page=$page"
            val document = cachedClient.fetchDocument(url) { headers ->
                    val resp = app.get(url, headers = headers)
                    FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
                }
            if (document == null) {
                Log.w(TAG, "No data available for '${request.name}': $url")
                return newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
            }
            val videos = document.select(".thumbnail").mapNotNull { it.toSearchResult() }

            PluginIntegrationHelper.maybeRecordTagSource(appContext, request.name, request.data, TAG, videos.isNotEmpty())

            newHomePageResponse(
                HomePageList(request.name, videos, isHorizontalImages = true),
                hasNext = videos.isNotEmpty()
            )
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load main page: ${request.name}", e)
            newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        PluginIntegrationHelper.recordSearch(appContext, query, TAG)
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            try {
                val searchUrl = "$mainUrl/en/search/$encodedQuery?page=$page"
                val document = cachedClient.fetchDocument(searchUrl,
                    ttlMs = cachedClient?.searchTtlMs
                    ) { headers ->
                        val resp = app.get(searchUrl, headers = headers)
                        FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
                    }
                if (document == null) {
                    Log.w(TAG, "No data available for search page $page: $searchUrl")
                    break
                }
                val pageResults = document.select(".thumbnail").mapNotNull { it.toSearchResult() }

                if (pageResults.isEmpty()) break
                results.addAll(pageResults)
            } catch (e: CancellationException) {
                throw e  // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search page $page for query: $query", e)
                break
            }
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            // Fetch metadata via HTTP request
            val document = app.get(url).document

            val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            if (title == null) {
                Log.w(TAG, "Missing og:title meta tag for URL: $url")
                return null
            }

            val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

            val recommendations = fetchRecommendations(document, url)

            // Separate actresses from genre tags
            val actresses = document.select("a[href*=/actresses/]")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val genres = document.select("a[href*=/genres/]")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .distinct()

            newMovieLoadResponse(title, url, if (watchHistoryConfig?.isEnabled(name) == true) TvType.Movie else TvType.NSFW, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genres
                addActors(actresses)
                this.recommendations = recommendations
            }
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video page: $url", e)
            null
        }
    }

    /**
     * Fetch related videos from actress/genre pages.
     * Fetches from first actress and first genre page in parallel,
     * deduplicates results, excludes current video, and limits to 20 items.
     */
    private suspend fun fetchRecommendations(document: Document, currentUrl: String): List<SearchResponse> {
        val actressLink = document.select("a[href*=/actresses/]")
            .mapNotNull { fixUrlNull(it.attr("href")) }
            .firstOrNull()

        val genreLink = document.select("a[href*=/genres/]")
            .mapNotNull { fixUrlNull(it.attr("href")) }
            .firstOrNull()

        if (actressLink == null && genreLink == null) {
            Log.d(TAG, "No actress or genre links found on page for fallback recommendations")
            return emptyList()
        }

        return coroutineScope {
            val actressDeferred = actressLink?.let { async { fetchRelatedVideos(it) } }
            val genreDeferred = genreLink?.let { async { fetchRelatedVideos(it) } }

            val actressVideos = actressDeferred?.await().orEmpty()
            val genreVideos = genreDeferred?.await().orEmpty()

            (actressVideos + genreVideos)
                .distinctBy { it.url }
                .filter { it.url != currentUrl }
                .take(20)
        }
    }

    /** Fetches video thumbnails from an actress or genre listing page. */
    private suspend fun fetchRelatedVideos(pageUrl: String): List<SearchResponse> {
        return try {
            val document = app.get(pageUrl).document
            document.select(".thumbnail").mapNotNull { it.toSearchResult() }
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch related videos from: $pageUrl", e)
            emptyList()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val response = app.get(data)
            val unpackedText = getAndUnpack(response.text)

            val m3u8Url = SOURCE_URL_REGEX.find(unpackedText)?.groupValues?.get(1)
            if (m3u8Url == null) {
                Log.w(TAG, "No source URL found in unpacked script for: $data")
                return false
            }

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = Qualities.Unknown.value
                }
            )
            true
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract links from: $data", e)
            false
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = selectFirst(".text-secondary") ?: run {
            Log.d(TAG, "Missing .text-secondary element in thumbnail")
            return null
        }

        val title = linkElement.text()
        val href = fixUrlNull(linkElement.attr("href")) ?: run {
            Log.d(TAG, "Missing href in .text-secondary for: $title")
            return null
        }

        val posterUrl = fixUrlNull(selectFirst(".w-full")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }
}
