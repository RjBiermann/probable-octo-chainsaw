package com.lagradost

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.common.CustomPage
import com.lagradost.common.WatchHistoryConfig
import com.lagradost.common.PluginIntegrationHelper
import com.lagradost.common.cache.CacheAwareClient
import com.lagradost.common.cache.FetchResult
import com.lagradost.common.cache.Prefetcher
import com.lagradost.common.cache.fetchDocument
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.coroutines.cancellation.CancellationException

/** Detect neporn.com 404 pages (server redirects to /404.php with these DOM markers). */
private fun Document.is404Page(): Boolean =
    selectFirst("div.error-404") != null
        || selectFirst("div.no-results") != null
        || (title().contains("404") && select("div.list-videos div.item").isEmpty())

class Neporn(
    private val customPages: List<CustomPage> = emptyList(),
    private val cachedClient: CacheAwareClient? = null,
    private val appContext: Context? = null,
    private val watchHistoryConfig: WatchHistoryConfig? = null,
    private val prefetcher: Prefetcher? = null
) : MainAPI() {
    companion object {
        private const val TAG = "Neporn"

        // Pre-compiled regex patterns for JSON-LD and flashvars parsing
        private val JSON_LD_NAME_REGEX = Regex(""""name"\s*:\s*"([^"]+)"""")
        private val JSON_LD_CONTENT_URL_REGEX = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
        private val FLASHVARS_VIDEO_URL_REGEX = Regex("""video_url\s*:\s*'([^']+)'""")
    }

    override var mainUrl = "https://neporn.com"
    override var name = "Neporn"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            // Default pages
            add(MainPageData(name = "Being Watched", data = "$mainUrl/"))
            add(MainPageData(name = "Latest", data = "$mainUrl/latest-updates/"))
            add(MainPageData(name = "Top Rated", data = "$mainUrl/top-rated/"))
            add(MainPageData(name = "Most Popular", data = "$mainUrl/most-popular/"))

            // Custom pages from user settings
            customPages.forEach { page ->
                val safePath = if (page.path.startsWith("/")) page.path else "/${page.path}"
                add(MainPageData(name = page.label, data = "$mainUrl$safePath"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val url = if (page > 1) {
                val separator = if (request.data.contains("?")) "&" else "?"
                "${request.data}${separator}from=$page"
            } else request.data

            val document = cachedClient.fetchDocument(url) { headers ->
                val resp = app.get(url, referer = "$mainUrl/", headers = headers)
                FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
            }

            if (document == null) Log.w(TAG, "No data available for '${request.name}': $url")
            val is404 = document != null && document.is404Page()
            val videos = if (document == null || is404) {
                emptyList()
            } else {
                document.select("div.list-videos div.item").mapNotNull { parseVideoElement(it) }
            }

            // Prefetch next page in background
            if (videos.isNotEmpty() && !is404) {
                val nextPage = page + 1
                val nextUrl = run {
                    val separator = if (request.data.contains("?")) "&" else "?"
                    "${request.data}${separator}from=$nextPage"
                }
                prefetcher?.prefetchUrl(nextUrl) { headers ->
                    val resp = app.get(nextUrl, referer = "$mainUrl/", headers = headers)
                    FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
                }
            }

            PluginIntegrationHelper.maybeRecordTagSource(appContext, request.name, request.data, TAG, videos.isNotEmpty())

            newHomePageResponse(
                HomePageList(request.name, videos, isHorizontalImages = true),
                hasNext = videos.isNotEmpty() && !is404
            )
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load main page '${request.name}': ${e.message}")
            newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        PluginIntegrationHelper.recordSearch(appContext, query, TAG)
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            try {
                val url = if (page == 1) {
                    "$mainUrl/search/?q=$encodedQuery"
                } else {
                    "$mainUrl/search/?q=$encodedQuery&from_videos=$page&from_albums=$page"
                }

                val document = cachedClient.fetchDocument(url,
                    ttlMs = cachedClient?.searchTtlMs
                ) { headers ->
                    val resp = app.get(url, referer = "$mainUrl/", headers = headers)
                    FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
                }

                if (document == null) {
                    Log.w(TAG, "No data available for search page $page: $url")
                    break
                }

                if (document.is404Page()) break

                val pageResults = document.select("div.list-videos div.item")
                    .mapNotNull { parseVideoElement(it) }

                if (pageResults.isEmpty()) break
                results.addAll(pageResults)
            } catch (e: CancellationException) {
                throw e  // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Search failed for '$query' page $page: ${e.message}")
                break
            }
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val document = app.get(url, referer = "$mainUrl/").document

            // Try JSON-LD first, then fallback to h1
            val jsonLd = document.selectFirst("script[type=application/ld+json]")?.html()
            val title = jsonLd?.let {
                JSON_LD_NAME_REGEX.find(it)?.groupValues?.get(1)
            } ?: document.selectFirst("div.headline h1")?.text()?.removePrefix("Video: ")?.trim()
            ?: return null

            val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()

            val tags = document.select("meta[property=video:tag]")
                .map { it.attr("content").trim() }
                .filter { it.isNotBlank() }

            val actors = document.select("div.info a[href*='/models/']")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val recommendations = document.select("div.related-videos div.list-videos div.item")
                .mapNotNull { parseVideoElement(it) }

            newMovieLoadResponse(title, url, if (watchHistoryConfig?.isEnabled(name) == true) TvType.Movie else TvType.NSFW, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
            }
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load video page '$url': ${e.message}")
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(data, referer = "$mainUrl/").document
            var linksFound = false

            // Method 1: JSON-LD schema
            val jsonLd = document.selectFirst("script[type=application/ld+json]")?.html()
            if (jsonLd != null) {
                val contentUrl = JSON_LD_CONTENT_URL_REGEX
                    .find(jsonLd)?.groupValues?.get(1)
                if (!contentUrl.isNullOrBlank()) {
                    callback(
                        newExtractorLink(name, name, contentUrl) {
                            this.referer = data
                            this.quality = getQualityFromUrl(contentUrl)
                        }
                    )
                    linksFound = true
                }
            }

            // Method 2: flashvars fallback
            if (!linksFound) {
                document.select("script").forEach { script ->
                    val html = script.html()
                    if (html.contains("flashvars")) {
                        val videoUrl = FLASHVARS_VIDEO_URL_REGEX
                            .find(html)?.groupValues?.get(1)
                            ?.removePrefix("function/0/")
                            ?.substringBefore("?br=")

                        if (!videoUrl.isNullOrBlank() && videoUrl.startsWith("http")) {
                            callback(
                                newExtractorLink(name, "$name - Stream", videoUrl) {
                                    this.referer = data
                                    this.quality = getQualityFromUrl(videoUrl)
                                }
                            )
                            linksFound = true
                        }
                    }
                }
            }

            linksFound
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load links from '$data': ${e.message}")
            false
        }
    }

    private fun parseVideoElement(element: Element): SearchResponse? {
        val anchor = element.selectFirst("a") ?: return null
        val title = anchor.attr("title").ifBlank {
            element.selectFirst("strong.title")?.text()
        } ?: return null

        val href = fixUrlNull(anchor.attr("href")) ?: return null
        val posterUrl = fixUrlNull(element.selectFirst("img.thumb")?.attr("src"))

        return newMovieSearchResponse(title.trim(), href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun getQualityFromUrl(url: String): Int = when {
        url.contains("1080p") -> Qualities.P1080.value
        url.contains("720p") -> Qualities.P720.value
        url.contains("480p") -> Qualities.P480.value
        url.contains("360p") -> Qualities.P360.value
        else -> Qualities.P720.value
    }
}
