package com.lagradost

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import com.lagradost.common.CustomPage
import com.lagradost.common.WatchHistoryConfig
import com.lagradost.common.DurationUtils
import com.lagradost.common.PluginIntegrationHelper
import com.lagradost.common.cache.CacheAwareClient
import com.lagradost.common.cache.FetchResult
import com.lagradost.common.cache.fetchDocument
import org.jsoup.nodes.Element
import kotlin.coroutines.cancellation.CancellationException

class PornTrex(
    private val customPages: List<CustomPage> = emptyList(),
    private val cachedClient: CacheAwareClient? = null,
    private val appContext: Context? = null,
    private val watchHistoryConfig: WatchHistoryConfig? = null
) : MainAPI() {
    companion object {
        private const val TAG = "PornTrex"

        // Pre-compiled regex patterns for flashvars video URL extraction
        private val FLASHVARS_URL_PATTERNS = mapOf(
            "video_url" to Regex("""video_url\s*:\s*'([^']+)'"""),
            "video_alt_url" to Regex("""video_alt_url\s*:\s*'([^']+)'"""),
            "video_alt_url2" to Regex("""video_alt_url2\s*:\s*'([^']+)'""")
        )
        private val FLASHVARS_TEXT_PATTERNS = mapOf(
            "video_url" to Regex("""video_url_text\s*:\s*'([^']+)'"""),
            "video_alt_url" to Regex("""video_alt_url_text\s*:\s*'([^']+)'"""),
            "video_alt_url2" to Regex("""video_alt_url2_text\s*:\s*'([^']+)'""")
        )
    }

    override var mainUrl = "https://www.porntrex.com"
    override var name = "PornTrex"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            // Default pages - using correct URLs from site navigation
            add(MainPageData(name = "Latest", data = "$mainUrl/latest-updates/"))
            add(MainPageData(name = "Most Viewed", data = "$mainUrl/most-popular/"))
            add(MainPageData(name = "Top Rated", data = "$mainUrl/top-rated/"))
            add(MainPageData(name = "HD", data = "$mainUrl/categories/hd/"))
            add(MainPageData(name = "4K", data = "$mainUrl/categories/4k-porn/"))

            // Custom pages from user settings
            customPages.forEach { page ->
                val safePath = if (page.path.startsWith("/")) page.path else "/${page.path}"
                add(MainPageData(name = page.label, data = "$mainUrl$safePath"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            val separator = if (request.data.contains("?")) "&" else "?"
            "${request.data}${separator}page=$page"
        } else request.data

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

        val videos = document.select(".video-preview-screen")
            .mapNotNull { parseVideoElement(it) }

        // Check for pagination - look for next page link (don't assume hasNext from video count)
        val hasNextPage = document.selectFirst(".pagination a:contains(Next), .pagination .next") != null

        PluginIntegrationHelper.maybeRecordTagSource(appContext, request.name, request.data, TAG, videos.isNotEmpty())

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        PluginIntegrationHelper.recordSearch(appContext, query, TAG)
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            val url = if (page == 1) {
                "$mainUrl/search/?query=$encodedQuery"
            } else {
                "$mainUrl/search/?query=$encodedQuery&page=$page"
            }

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

            val pageResults = document.select(".video-preview-screen")
                .mapNotNull { parseVideoElement(it) }

            if (pageResults.isEmpty()) break
            results.addAll(pageResults)

            // Check if there's a next page
            if (document.selectFirst(".pagination a:contains(Next), .pagination .next") == null) break
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = try {
            app.get(url, referer = "$mainUrl/").document
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load '$url': ${e.message}")
            return null
        }

        val title = document.selectFirst(".headline h1, h1.title, p.title-video")?.text()?.trim()
            ?: document.selectFirst("title")?.text()?.substringBefore("|")?.trim()
            ?: return null

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst(".player-container img")?.attr("src")
        )

        val description = document.selectFirst(".block-description, meta[name=description]")
            ?.let { it.text().ifBlank { it.attr("content") } }?.trim()

        // Extract categories - scoped to video info section, using js-cat class
        val categories = document.select("div.info a.js-cat")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Extract tags - scoped to video info section, excluding the categories container
        val tags = document.select("div.info div.items-holder:not(.js-categories) a[href*='/tags/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .take(20)

        // Extract actors/models - scoped to video info section
        val actors = document.select("div.info a[href*='/models/'], div.info a[href*='/pornstars/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.contains("Suggest") }
            .distinct()

        // Parse duration from video info
        val duration = document.selectFirst(".info-block .duration, .video-info .duration, [class*=duration]")
            ?.text()?.let { DurationUtils.parseDuration(it) }

        // Get recommendations
        val recommendations = document.select(".related-videos .video-preview-screen")
            .mapNotNull { parseVideoElement(it) }

        return newMovieLoadResponse(title, url, if (watchHistoryConfig?.isEnabled(name) == true) TvType.Movie else TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = (categories + tags).distinct()
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
            app.get(data, referer = "$mainUrl/").document
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load links from '$data': ${e.message}")
            return false
        }
        var linksFound = false

        // Extract video URLs from flashvars JavaScript
        val videoKeys = listOf(
            "video_url" to "480p",
            "video_alt_url" to "720p HD",
            "video_alt_url2" to "1080p"
        )

        document.select("script").forEach { script ->
            val html = script.html()
            if (html.contains("flashvars")) {
                videoKeys.forEach { (key, defaultQuality) ->
                    val url = FLASHVARS_URL_PATTERNS[key]?.find(html)?.groupValues?.get(1)
                        ?.removePrefix("function/0/")
                        ?.substringBefore("?br=")
                    val qualityText = FLASHVARS_TEXT_PATTERNS[key]?.find(html)?.groupValues?.get(1) ?: defaultQuality

                    if (!url.isNullOrBlank() && url.startsWith("http")) {
                        callback(
                            newExtractorLink(name, "$name - $qualityText", url) {
                                this.referer = data
                                this.quality = getQualityFromText(qualityText)
                                this.headers = mapOf(
                                    "Accept" to "*/*",
                                    "Accept-Language" to "en-US,en;q=0.9",
                                    "Connection" to "keep-alive",
                                    "Range" to "bytes=0-"
                                )
                            }
                        )
                        linksFound = true
                    }
                }
            }
        }

        return linksFound
    }

    private fun parseVideoElement(element: Element): SearchResponse? {
        val anchor = element.selectFirst("a[href*='/video/']") ?: return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null

        // Extract title with fallback chain - handle nulls safely
        val title = anchor.attr("title").takeIf { it.isNotBlank() }
            ?: anchor.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() }
            ?: element.selectFirst(".title, .video-title, p")?.text()?.trim()
            ?: return null

        val img = element.selectFirst("img")?.let {
            // Prefer src if it's a full URL (browser-resolved), otherwise use data-src (lazy loading)
            val src = it.attr("src")
            val dataSrc = it.attr("data-src")
            when {
                src.startsWith("http") -> src
                dataSrc.isNotBlank() -> dataSrc
                src.isNotBlank() -> src
                else -> null
            }
        }
        // Handle protocol-relative URLs (e.g., //cdn.example.com/...)
        val posterUrl = img?.let {
            if (it.startsWith("//")) "https:$it" else fixUrlNull(it)
        }

        return newMovieSearchResponse(title.trim(), href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun getQualityFromText(text: String): Int = when {
        text.contains("2160") || text.contains("4k", ignoreCase = true) -> Qualities.P2160.value
        text.contains("1080") -> Qualities.P1080.value
        text.contains("720") -> Qualities.P720.value
        text.contains("480") -> Qualities.P480.value
        text.contains("360") -> Qualities.P360.value
        text.contains("HD", ignoreCase = true) -> Qualities.P720.value
        else -> Qualities.Unknown.value
    }
}
