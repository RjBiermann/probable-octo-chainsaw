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
import kotlin.coroutines.cancellation.CancellationException

class FreePornVideos(
    private val customPages: List<CustomPage> = emptyList(),
    private val cachedClient: CacheAwareClient? = null,
    private val appContext: Context? = null,
    private val watchHistoryConfig: WatchHistoryConfig? = null
) : MainAPI() {
    companion object {
        private const val TAG = "FreePornVideos"

        private val TITLE_YEAR_SUFFIX = Regex("""\s*[-/]\s*\d{2}\.\d{2}\.\d{4}\s*$""")
        private val SLUG_SANITIZE = Regex("""[^\w\s]""")
        private val MULTI_SPACE = Regex("""\s+""")
    }

    override var mainUrl = "https://www.freepornvideos.xxx"
    override var name = "FreePornVideos"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            add(MainPageData(name = "Latest", data = "$mainUrl/latest-updates/%d/"))
            add(MainPageData(name = "Most Popular", data = "$mainUrl/most-popular/week/%d/"))
            add(MainPageData(name = "Brazzers", data = "$mainUrl/networks/brazzers-com/%d/"))
            add(MainPageData(name = "MYLF", data = "$mainUrl/networks/mylf-com/%d/"))
            add(MainPageData(name = "Adult Time", data = "$mainUrl/networks/adult-time/%d/"))
            add(MainPageData(name = "Reality Kings", data = "$mainUrl/networks/rk-com/%d/"))
            add(MainPageData(name = "Mom Lover", data = "$mainUrl/networks/mom-lover/%d/"))
            add(MainPageData(name = "JAV Uncensored", data = "$mainUrl/categories/jav-uncensored/%d/"))

            customPages.forEach { page ->
                val safePath = if (page.path.startsWith("/")) page.path else "/${page.path}"
                val dataUrl = if (safePath.contains("%d")) {
                    "$mainUrl$safePath"
                } else {
                    "$mainUrl${safePath.trimEnd('/')}/%d/"
                }
                add(MainPageData(name = page.label, data = dataUrl))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.replace("%d", page.toString())
        val document = try {
            cachedClient.fetchDocument(url) { headers ->
                    val resp = app.get(url, referer = "$mainUrl/", headers = headers)
                    FetchResult(resp.text, resp.code, resp.headers["ETag"], resp.headers["Last-Modified"])
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch main page: $url", e)
            throw ErrorLoadingException("Failed to load page. Check your internet connection.")
        }

        if (document == null) {
            Log.w(TAG, "Cache returned no data for: $url")
            return newHomePageResponse(
                HomePageList(request.name, emptyList(), isHorizontalImages = true),
                hasNext = false
            )
        }

        val videos = document.select("div.item").mapNotNull { it.toSearchResult() }

        PluginIntegrationHelper.maybeRecordTagSource(appContext, request.name, request.data, TAG, videos.isNotEmpty())

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = videos.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        PluginIntegrationHelper.recordSearch(appContext, query, TAG)
        val slug = query.trim()
            .replace(SLUG_SANITIZE, "")
            .replace(MULTI_SPACE, "-")
            .lowercase()

        if (slug.isBlank()) return emptyList()

        val results = mutableListOf<SearchResponse>()

        for (page in 1..2) {
            val url = "$mainUrl/search/$slug/$page/"
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
                Log.e(TAG, "Search request failed for page $page", e)
                break
            }

            if (document == null) break

            val pageResults = document.select("#custom_list_videos_videos_list_search_result_items > div.item")
                .mapNotNull { it.toSearchResult() }

            if (pageResults.isEmpty()) break
            results.addAll(pageResults)
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = try {
            app.get(url, referer = "$mainUrl/").document
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video page: $url", e)
            throw ErrorLoadingException("Failed to load video. Check your internet connection.")
        }

        val fullTitle = document.selectFirst("div.headline > h1")?.text()?.trim()
        if (fullTitle == null) {
            Log.w(TAG, "Could not extract title from: $url")
            return null
        }

        // Remove trailing date pattern like " / 28.01.2026" or " - 2024"
        val title = fullTitle
            .replace(TITLE_YEAR_SUFFIX, "")
            .let {
                val lastDash = it.lastIndexOf(" - ")
                if (lastDash != -1) it.substring(0, lastDash) else it
            }
            .trim()
            .removePrefix("- ").removeSuffix("-").trim()

        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val tags = document.select("div.video-info div.item:has(span:containsOwn(Categories)) a").map { it.text() }
        val description = document.selectFirst("div.desc, div.description")?.text()?.trim()?.ifBlank { null }
        val actors = document.select("div.video-info div.item:has(span:containsOwn(Pornstars)) a").map { it.text() }
        val recommendations = document.select("div#list_videos_related_videos_items div.item").mapNotNull { it.toSearchResult() }

        val year = TITLE_YEAR_SUFFIX.find(fullTitle)?.value
            ?.takeLast(4)?.toIntOrNull()
        val rating = document.selectFirst("div.rating span")?.text()
            ?.substringBefore("%")?.trim()?.toFloatOrNull()?.div(10)?.toString()

        return newMovieLoadResponse(title, url, if (watchHistoryConfig?.isEnabled(name) == true) TvType.Movie else TvType.NSFW, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
            this.score = Score.from10(rating)
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
            Log.e(TAG, "Failed to fetch video page for links: $data", e)
            return false
        }

        var linksFound = false

        document.select("video source").forEach { source ->
            val redirectUrl = source.attr("src").takeIf { it.isNotBlank() } ?: return@forEach
            val quality = source.attr("label")

            try {
                val response = app.get(redirectUrl, allowRedirects = true)
                val finalUrl = response.url

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = finalUrl,
                        type = if (finalUrl.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = data
                        this.quality = getQualityFromName(quality)
                    }
                )
                linksFound = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Error getting final URL for $quality: ${e.message}")
            }
        }

        return linksFound
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("strong.title")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val href = selectFirst("a[href]")?.attr("href")?.takeIf { it.isNotBlank() } ?: return null

        var posterUrl: String? = null
        for (img in select("img.thumb")) {
            val dataSrc = img.attr("data-src").takeIf { it.isNotBlank() }
            if (dataSrc != null) {
                posterUrl = dataSrc
                break
            }
            val src = img.attr("src").takeIf { it.isNotBlank() && !it.contains("data:image") }
            if (src != null) {
                posterUrl = src
                break
            }
        }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }
}
