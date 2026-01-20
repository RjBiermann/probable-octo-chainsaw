package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Perverzija(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
    companion object {
        private const val TAG = "Perverzija"
    }

    override var mainUrl = "https://tube.perverzija.com"
    override var name = "Perverzija"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    private val cfInterceptor = CloudflareKiller()

    override val mainPage: List<MainPageData>
        get() = buildList {
            add(MainPageData(name = "Home", data = "$mainUrl/page/%d/"))
            add(MainPageData(name = "Featured", data = "$mainUrl/featured-scenes/page/%d/?orderby=date"))

            customPages.forEach { page ->
                add(MainPageData(name = page.label, data = "$mainUrl${page.path}page/%d/"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.format(page)

        val response = try {
            app.get(url, interceptor = cfInterceptor, timeout = 30L)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load '${request.name}' from: $url", e)
            return newHomePageResponse(
                HomePageList(request.name, emptyList(), isHorizontalImages = true),
                hasNext = false
            )
        }

        val videos = response.document.select("div.row div div.post").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = videos.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = query.replace(" ", "+")

        for (page in 1..3) {
            val url = "$mainUrl/page/$page/?s=$encodedQuery&orderby=date"

            val response = try {
                app.get(url, interceptor = cfInterceptor, timeout = 30L)
            } catch (e: Exception) {
                Log.w(TAG, "Search request failed for page $page: $url", e)
                break
            }

            val pageResults = response.document.select("div.row div div.post")
                .mapNotNull { it.toSearchResult() }

            if (pageResults.isEmpty()) break
            results.addAll(pageResults)
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = try {
            app.get(url, interceptor = cfInterceptor, timeout = 30L).document
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video page: $url", e)
            return null
        }

        val title = document.selectFirst("div.title-info h1.light-title.entry-title")?.text()
            ?: document.selectFirst("h1")?.text()
            ?: return null

        val poster = document.selectFirst("div#featured-img-id img")?.attr("src")

        val description = document.select("div.item-content p")
            .joinToString("\n") { it.text() }
            .takeIf { it.isNotBlank() }

        val tags = document.select("div.item-tax-list div a[href*='/tag/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val actors = document.select("div.item-tax-list a[href*='/stars/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val recommendations = document.select("div.xs-related-item").mapNotNull {
            it.toRecommendationResult()
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
            if (actors.isNotEmpty()) {
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = try {
            app.get(data, interceptor = cfInterceptor, timeout = 30L).document
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch video page for links: $data", e)
            return false
        }

        val iframeUrl = document.selectFirst("div#player-embed iframe")?.attr("src")
        if (iframeUrl == null) {
            Log.w(TAG, "Could not find iframe on page: $data")
            return false
        }

        Log.d(TAG, "Found iframe URL: $iframeUrl")

        // Directly call extractors based on URL pattern for reliable matching
        // (loadExtractor may not match subdomains like pervl4.xtremestream.co)
        // Track whether any links were found via wrapped callback
        var foundLinks = false
        val trackingCallback: (ExtractorLink) -> Unit = { link ->
            foundLinks = true
            callback(link)
        }

        return when {
            iframeUrl.contains("xtremestream") -> {
                try {
                    Xtremestream().getUrl(iframeUrl, data, subtitleCallback, trackingCallback)
                    if (!foundLinks) {
                        Log.w(TAG, "Xtremestream extractor found no video links for: $iframeUrl")
                    }
                    foundLinks
                } catch (e: Exception) {
                    Log.e(TAG, "Xtremestream extractor failed for: $iframeUrl", e)
                    false
                }
            }
            iframeUrl.contains("playhydrax") || iframeUrl.contains("abysscdn") -> {
                try {
                    Playhydrax().getUrl(iframeUrl, data, subtitleCallback, trackingCallback)
                    if (!foundLinks) {
                        Log.w(TAG, "Playhydrax extractor found no video links for: $iframeUrl")
                    }
                    foundLinks
                } catch (e: Exception) {
                    Log.e(TAG, "Playhydrax extractor failed for: $iframeUrl", e)
                    false
                }
            }
            else -> {
                // Fallback to loadExtractor for unknown embeds
                Log.d(TAG, "Using generic loadExtractor for unknown embed: $iframeUrl")
                try {
                    loadExtractor(iframeUrl, data, subtitleCallback, trackingCallback)
                    if (!foundLinks) {
                        Log.w(TAG, "loadExtractor found no video links for unknown embed: $iframeUrl")
                    }
                    foundLinks
                } catch (e: Exception) {
                    Log.e(TAG, "loadExtractor failed for unknown embed: $iframeUrl", e)
                    false
                }
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val posterUrl = fixUrlNull(this.selectFirst("div.item-thumbnail img")?.attr("src"))
        val title = this.selectFirst("div.item-head a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("div.item-head a")?.attr("href")) ?: return null

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val titleLink = this.selectFirst("div.xs-related-title a") ?: return null
        val title = titleLink.text().takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(titleLink.attr("href")) ?: return null

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }
}
