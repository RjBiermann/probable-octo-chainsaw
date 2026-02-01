package com.lagradost

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import com.lagradost.common.CustomPage
import com.lagradost.common.WatchHistoryConfig
import com.lagradost.common.PluginIntegrationHelper
import com.lagradost.common.cache.CacheAwareClient
import com.lagradost.common.cache.FetchResult
import com.lagradost.common.cache.fetchDocument
import org.jsoup.nodes.Element
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

class Porn36(
    private val customPages: List<CustomPage> = emptyList(),
    private val cachedClient: CacheAwareClient? = null,
    private val appContext: Context? = null,
    private val watchHistoryConfig: WatchHistoryConfig? = null
) : MainAPI() {
    companion object {
        private const val TAG = "Porn36"
        private val TITLE_DATE_SUFFIX = Regex("""\s*[/\-]\s*\d{2}\.\d{2}\.\d{4}\s*$""")
    }

    override var mainUrl = "https://www.porn36.com"
    override var name = "Porn36"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            add(MainPageData(name = "Latest", data = "$mainUrl/latest-updates/"))
            add(MainPageData(name = "Top Rated", data = "$mainUrl/top-rated/"))
            add(MainPageData(name = "Most Popular", data = "$mainUrl/most-popular/"))

            customPages.forEach { page ->
                val safePath = if (page.path.startsWith("/")) page.path else "/${page.path}"
                add(MainPageData(name = page.label, data = "$mainUrl$safePath"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page/"

        val document = try {
            cachedClient.fetchDocument(url) { headers ->
                    val resp = app.get(url, referer = "$mainUrl/", headers = headers)
                    FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
                }
        } catch (e: CancellationException) {
            throw e
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

        val videos = document.select("div.item").mapNotNull { parseVideoElement(it) }
        val hasNextPage = document.selectFirst("a.page:not(.page-current)") != null

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
            val url = "$mainUrl/search/$encodedQuery/relevance/$page/"

            val document = try {
                cachedClient.fetchDocument(url,
                    ttlMs = cachedClient?.searchTtlMs
                    ) { headers ->
                        val resp = app.get(url, referer = "$mainUrl/", headers = headers)
                        FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Search failed for '$query' page $page: ${e.message}")
                break
            }

            if (document == null) {
                Log.w(TAG, "No data available for search page $page: $url")
                break
            }

            val pageResults = document.select("div.item").mapNotNull { parseVideoElement(it) }
            if (pageResults.isEmpty()) break
            results.addAll(pageResults)

            if (document.selectFirst("a.page:not(.page-current)") == null) break
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = try {
            app.get(url, referer = "$mainUrl/").document
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load '$url': ${e.message}")
            return null
        }

        val rawTitle = document.selectFirst("h1")?.text()?.trim() ?: return null
        val title = rawTitle.replace(TITLE_DATE_SUFFIX, "").trim()
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("div.desc, div.description")?.text()?.trim()
        val tags = document.select("div.hidden_tags a").map { it.text().trim() }.filter { it.isNotBlank() }
        val actors = document.select("div.video-info div.item:has(span:containsOwn(Pornstars)) a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val recommendations = document.select("div.item").mapNotNull { parseVideoElement(it) }

        return newMovieLoadResponse(title, url, if (watchHistoryConfig?.isEnabled(name) == true) TvType.Movie else TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
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
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load links from '$data': ${e.message}")
            return false
        }

        var linksFound = false

        document.select("source").forEach { source ->
            val videoUrl = source.attr("src")
            val qualityLabel = source.attr("label")

            if (videoUrl.isNotBlank()) {
                val fullUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
                callback(
                    newExtractorLink(name, "$name - $qualityLabel", fullUrl) {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromLabel(qualityLabel)
                    }
                )
                linksFound = true
            }
        }

        return linksFound
    }

    private fun parseVideoElement(element: Element): SearchResponse? {
        val anchor = element.selectFirst("a") ?: return null
        val title = anchor.attr("title").ifBlank { anchor.text() }.trim()
        if (title.isBlank()) return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null

        val img = element.selectFirst("img")
        val posterUrl = img?.let {
            val src = it.attr("src")
            if (src.contains("data:image")) it.attr("data-src").ifBlank { it.attr("data-original") }
            else src
        }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    private fun getQualityFromLabel(label: String): Int = when {
        label.contains("2160") || label.contains("4k", ignoreCase = true) -> Qualities.P2160.value
        label.contains("1080") -> Qualities.P1080.value
        label.contains("720") -> Qualities.P720.value
        label.contains("480") -> Qualities.P480.value
        label.contains("360") -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}
