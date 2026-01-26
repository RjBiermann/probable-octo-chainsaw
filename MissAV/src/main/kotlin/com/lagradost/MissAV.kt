package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.common.CustomPage
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class MissAV(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
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
            val document = app.get("${request.data}?page=$page").document
            val videos = document.select(".thumbnail").mapNotNull { it.toSearchResult() }

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
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            try {
                val document = app.get("$mainUrl/en/search/$encodedQuery?page=$page").document
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

            // Try WebView first, fall back to actress/genre pages
            var recommendations = fetchRecommendationsViaWebView(url)
            if (recommendations.isEmpty()) {
                Log.d(TAG, "WebView returned no recommendations, using fallback")
                recommendations = fetchRecommendationsFallback(document, url)
                if (recommendations.isEmpty()) {
                    Log.w(TAG, "Both WebView and fallback methods returned no recommendations for: $url")
                }
            }

            newMovieLoadResponse(title, url, TvType.NSFW, url) {
                this.posterUrl = poster
                this.plot = description
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
     * Fallback: fetch related videos from actress/genre pages when WebView
     * returns no recommendations (empty or failed).
     * Fetches from first actress and first genre page in parallel,
     * deduplicates results, excludes current video, and limits to 20 items.
     */
    private suspend fun fetchRecommendationsFallback(document: Document, currentUrl: String): List<SearchResponse> {
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

    /**
     * Uses WebView to load the page and execute JavaScript to extract recommendations
     * from the Recombee-powered recommendItems variable.
     *
     * Returns empty list if:
     * - WebView times out before Recombee loads (5 second limit)
     * - Page doesn't use Recombee for recommendations
     * - JavaScript execution fails or returns invalid JSON
     */
    private suspend fun fetchRecommendationsViaWebView(url: String): List<SearchResponse> {
        var recommendationsJson: String? = null

        // JavaScript to extract recommendItems after Recombee populates it
        // Returns JSON array of {dvd_id, title, duration} objects
        val extractScript = """
            (function() {
                if (window.recommendItems && window.recommendItems.length > 0 && window.recommendItems[0].dvd_id) {
                    var results = window.recommendItems.slice(0, 20).map(function(item) {
                        return {
                            dvd_id: item.dvd_id,
                            title: item.title_en || item.full_title || item.dvd_id,
                            duration: item.duration
                        };
                    }).filter(function(item) { return item.dvd_id; });
                    return JSON.stringify(results);
                }
                return null;
            })()
        """.trimIndent()

        try {
            val resolver = WebViewResolver(
                // interceptUrl pattern intentionally never matches any URL, ensuring
                // WebView runs for the full timeout duration to allow Recombee to load
                interceptUrl = Regex("""^${'$'}NEVER_MATCH_THIS^"""),
                additionalUrls = emptyList(),
                userAgent = null,
                useOkhttp = false,
                script = extractScript,
                scriptCallback = { result ->
                    // Script returns "null" string if not ready, or JSON array if ready
                    if (result != "null" && result.startsWith("[")) {
                        Log.d(TAG, "Got recommendations JSON: ${result.take(100)}...")
                        recommendationsJson = result
                    }
                },
                timeout = 5_000L  // 5 second timeout for Recombee to load
            )

            app.get(url, interceptor = resolver)

            val json = recommendationsJson
            if (json != null) {
                return parseRecommendationsJson(json)
            } else {
                Log.d(TAG, "WebView completed but no recommendations captured")
            }
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "WebView recommendation extraction failed for: $url", e)
        }

        return emptyList()
    }

    /**
     * Parses the JSON array from JavaScript into SearchResponse objects.
     * Uses org.json.JSONArray for robust parsing.
     */
    private fun parseRecommendationsJson(json: String): List<SearchResponse> {
        return try {
            val array = JSONArray(json)
            val items = mutableListOf<SearchResponse>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val dvdId = obj.optString("dvd_id", "").ifBlank { continue }
                val title = obj.optString("title", "").ifBlank { dvdId.uppercase() }

                val videoUrl = "$mainUrl/en/$dvdId"
                val posterUrl = "https://fourhoi.com/$dvdId/cover-t.jpg"

                items.add(
                    newMovieSearchResponse(title, videoUrl, TvType.NSFW) {
                        this.posterUrl = posterUrl
                    }
                )
            }

            Log.d(TAG, "Extracted ${items.size} recommendations via WebView")
            items
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse recommendations JSON (first 200 chars: ${json.take(200)})", e)
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
