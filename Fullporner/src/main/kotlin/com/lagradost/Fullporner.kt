package com.lagradost

import android.content.Context
import android.util.Log
import com.lagradost.common.CustomPage
import com.lagradost.common.WatchHistoryConfig
import com.lagradost.common.DurationUtils
import com.lagradost.common.PluginIntegrationHelper
import com.lagradost.common.cache.CacheAwareClient
import com.lagradost.common.cache.FetchResult
import com.lagradost.common.cache.fetchDocument
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

class Fullporner(
    private val customPages: List<CustomPage> = emptyList(),
    private val cachedClient: CacheAwareClient? = null,
    private val appContext: Context? = null,
    private val watchHistoryConfig: WatchHistoryConfig? = null
) : MainAPI() {
    companion object {
        private const val TAG = "Fullporner"

        // Pre-compiled regex patterns for video extraction
        private val VIDEO_ID_FROM_IFRAME_REGEX = Regex("""/video/([^/]+)/""")
        private val VIDEO_ID_FROM_URL_REGEX = Regex("""/watch/([a-zA-Z0-9]+)""")
        private val PREVIEW_URL_REGEX = Regex("""(?:preview_url|poster)\s*[=:]\s*['"]([^'"]+)['"]""")
        private val VAR_ID_REGEX = Regex("""var\s+id\s*=\s*["']([^"']+)["']""")
    }

    override var mainUrl = "https://fullporner.com"
    override var name = "Fullporner"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            // Default pages - Featured Videos uses root URL, pagination handled in getMainPage
            add(MainPageData(name = "Featured Videos", data = "$mainUrl/"))

            // Custom pages from user settings
            customPages.forEach { page ->
                val safePath = if (page.path.startsWith("/")) page.path else "/${page.path}"
                add(MainPageData(name = page.label, data = "$mainUrl$safePath"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Handle pagination for different URL patterns
        // Featured Videos (root URL): page 1 = /, page 2+ = /home/{page}
        val url = when {
            request.data.contains("/search") -> if (page > 1) "${request.data}&p=$page" else request.data
            request.data.endsWith("/") || request.data == mainUrl -> if (page > 1) "$mainUrl/home/$page" else request.data
            else -> if (page > 1) "${request.data}/$page" else request.data
        }

        val document = try {
            cachedClient.fetchDocument(url) { headers ->
                    val resp = app.get(url, referer = "$mainUrl/", headers = headers)
                    FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
                }
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load main page '${request.name}': ${e.message}")
            return newHomePageResponse(
                HomePageList(request.name, emptyList(), isHorizontalImages = true),
                hasNext = false
            )
        }

        if (document == null) {
            Log.w(TAG, "No data available for '${request.name}': $url")
            return newHomePageResponse(
                HomePageList(request.name, emptyList(), isHorizontalImages = true),
                hasNext = false
            )
        }

        val videos = document.select(".video-card").mapNotNull { it.toSearchResult() }

        // Check if there's a next page link (site uses "Next" or "＞" fullwidth character)
        // Only match specific next page indicators, not any pagination link
        val hasNextPage = document.selectFirst("a:contains(Next), a:contains(＞)") != null

        PluginIntegrationHelper.maybeRecordTagSource(appContext, request.name, request.data, TAG, videos.isNotEmpty())

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = videos.isNotEmpty() && hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        PluginIntegrationHelper.recordSearch(appContext, query, TAG)
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            val url = "$mainUrl/search?q=$encodedQuery&p=$page"

            val document = try {
                cachedClient.fetchDocument(url,
                    ttlMs = cachedClient?.searchTtlMs
                    ) { headers ->
                        val resp = app.get(url, referer = "$mainUrl/", headers = headers)
                        FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
                    }
            } catch (e: CancellationException) {
                throw e  // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Search failed for '$query' page $page: ${e.message}")
                break
            }

            if (document == null) {
                Log.w(TAG, "No data available for search page $page: $url")
                break
            }

            val pageResults = document.select(".video-card").mapNotNull { it.toSearchResult() }

            if (pageResults.isEmpty()) break
            results.addAll(pageResults)

            // Check if there's a next page
            val hasNext = document.selectFirst("a:contains(Next), a[href*=p=${page + 1}]") != null
            if (!hasNext) break
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = try {
            app.get(url, referer = "$mainUrl/").document
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load video page '$url': ${e.message}")
            return null
        }

        // Extract title from h2 heading or page title
        val title = document.selectFirst("h2")?.text()?.trim()
            ?: document.selectFirst("title")?.text()?.substringBeforeLast(" - ")?.trim()
        if (title.isNullOrBlank()) {
            Log.w("Fullporner", "Could not extract title from '$url' - page structure may have changed")
            return null
        }

        // Extract poster from iframe video ID
        // First try to find iframe in static HTML, then fallback to fetching iframe page
        val poster = extractPoster(document, url)

        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        // Extract tags from category links
        val tags = document.select("a[href*=/category/]")
            .map { it.text().trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinct()

        // Extract actors/pornstars
        val actors = document.select("a[href*=/pornstar/]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Parse duration from the page (format: MM:SS or HH:MM:SS)
        val durationText = document.selectFirst("*:matchesOwn(^\\d{1,2}:\\d{2}(:\\d{2})?$)")?.text()
        val duration = durationText?.let { DurationUtils.parseDuration(it) }

        // Get recommendations from related videos
        val recommendations = document.select(".video-card")
            .drop(1) // Skip the main video if it appears
            .take(20)
            .mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, if (watchHistoryConfig?.isEnabled(name) == true) TvType.Movie else TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = try {
            app.get(
                data,
                referer = "$mainUrl/",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.5"
                )
            ).document
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load links for '$data': ${e.message}")
            return false
        }

        // Find the video player iframe
        val iframes = document.select("iframe")
        var foundVideo = false

        for (iframe in iframes) {
            val src = iframe.attr("src")
            if (src.isBlank()) continue

            val iframeUrl = fixUrl(src)

            // Skip ad iframes
            if (iframeUrl.contains("ads") || iframeUrl.contains("banner")) continue

            // Use loadExtractor for xiaoshenke.net videos (delegating to FullpornerExtractor)
            if (iframeUrl.contains("xiaoshenke.net")) {
                val extracted = loadExtractor(iframeUrl, data, subtitleCallback, callback)
                if (extracted) {
                    foundVideo = true
                } else {
                    Log.w("Fullporner", "Extractor failed for iframe '$iframeUrl'")
                }
            }
        }

        return foundVideo
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst("a[href*=/watch/]") ?: return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null

        // Title from image alt text or link text
        val title = selectFirst("img")?.attr("alt")?.ifBlank { null }
            ?: anchor.text().trim().ifBlank { null }
            ?: return null

        // Poster from img tag
        val posterUrl = selectFirst("img")?.let { img ->
            val src = img.attr("src")
            // Handle lazy-loaded images
            if (src.contains("blank.gif")) {
                img.attr("data-src").ifBlank { null }
            } else {
                src.ifBlank { null }
            }
        }?.let { fixUrlNull(it) }

        return newMovieSearchResponse(title.trim(), href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    /**
     * Extract poster URL for a video.
     *
     * Strategies:
     * 1. Find iframe in static HTML, extract reversed video ID from path, construct poster URL
     * 2. If no iframe found but page URL contains video ID:
     *    a. Construct iframe URL, fetch it, look for preview_url/poster in JavaScript
     *    b. If not found, extract var id from JavaScript, reverse it, construct poster URL
     *    c. If fetch fails or parsing fails, use page video ID directly as fallback
     *
     * Note: Video IDs in iframe URL paths and JavaScript var id are stored reversed
     * (e.g., "abc123" becomes "321cba").
     *
     * @param document The parsed HTML document of the video page
     * @param pageUrl The URL of the video page (used to extract video ID)
     * @return Poster URL or null if extraction fails
     */
    private suspend fun extractPoster(document: org.jsoup.nodes.Document, pageUrl: String): String? {
        // Strategy 1: Try to find iframe in static HTML and extract video ID
        val iframeSrc = document.selectFirst("iframe[src*=xiaoshenke.net/video/]")?.attr("src")
            ?: document.selectFirst("div.single-video iframe")?.attr("src")

        if (iframeSrc != null && iframeSrc.contains("xiaoshenke.net/video/")) {
            val idMatch = VIDEO_ID_FROM_IFRAME_REGEX.find(iframeSrc)
            // Reverse the path ID to get the original video ID
            val videoId = idMatch?.groupValues?.get(1)?.reversed()
            if (videoId != null) {
                return buildPosterUrl(videoId)
            } else {
                Log.d("Fullporner", "Found xiaoshenke iframe but video ID regex didn't match: $iframeSrc")
            }
        }

        // Strategy 2: Construct iframe URL and fetch poster from JavaScript variables
        val pageVideoId = VIDEO_ID_FROM_URL_REGEX.find(pageUrl)?.groupValues?.get(1)
        if (pageVideoId != null) {
            // Page URL has video ID in normal form; iframe path uses reversed form
            val iframePathId = pageVideoId.reversed()
            val constructedIframeUrl = "https://xiaoshenke.net/video/$iframePathId/7"

            try {
                val iframeDoc = app.get(constructedIframeUrl, referer = pageUrl).text

                // Strategy 2a: Look for poster/preview URL in iframe JavaScript
                val previewMatch = PREVIEW_URL_REGEX.find(iframeDoc)
                if (previewMatch != null) {
                    val previewUrl = previewMatch.groupValues[1]
                    return if (previewUrl.startsWith("//")) "https:$previewUrl" else previewUrl
                }

                // Strategy 2b: Extract video ID from iframe JavaScript (var id = "xxx") - also stored reversed
                val idMatch = VAR_ID_REGEX.find(iframeDoc)
                val iframeVideoId = idMatch?.groupValues?.get(1)
                if (iframeVideoId != null) {
                    return buildPosterUrl(iframeVideoId.reversed())
                }

                // Fetched iframe successfully but couldn't extract poster info
                Log.d("Fullporner", "Fetched iframe but couldn't extract poster from JavaScript for: $pageUrl")
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Fullporner", "Failed to fetch iframe for poster from '$pageUrl': ${e.message}")
            }

            // Strategy 2c: Fall back to page video ID directly
            return buildPosterUrl(pageVideoId)
        }

        return null
    }

    /**
     * Build poster URL from video ID.
     * All thumbnails use imgs.xiaoshenke.net/thumb/{id}.jpg pattern.
     */
    private fun buildPosterUrl(videoId: String): String {
        return "https://imgs.xiaoshenke.net/thumb/$videoId.jpg"
    }
}
