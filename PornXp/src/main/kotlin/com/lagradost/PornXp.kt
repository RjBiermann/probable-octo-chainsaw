package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.common.CustomPage
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import android.util.Log
import org.jsoup.nodes.Element
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

class PornXp(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
    companion object {
        private const val TAG = "PornXp"
    }

    override var mainUrl = "https://pornxp.ph"
    override var name = "PornXP"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            // Default pages
            add(MainPageData(name = "New Videos", data = "$mainUrl/"))
            add(MainPageData(name = "Best Videos", data = "$mainUrl/best/"))
            add(MainPageData(name = "New Releases", data = "$mainUrl/released/"))
            add(MainPageData(name = "HD", data = "$mainUrl/hd/"))

            // Custom pages from user settings
            customPages.forEach { page ->
                add(MainPageData(name = page.label, data = "$mainUrl${page.path}"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            val separator = if (request.data.contains("?")) "&" else "?"
            "${request.data}${separator}page=$page"
        } else request.data

        val document = try {
            app.get(url, referer = "$mainUrl/").document
        } catch (e: CancellationException) {
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load main page '${request.name}': ${e.message}")
            return newHomePageResponse(
                HomePageList(request.name, emptyList(), isHorizontalImages = true),
                hasNext = false
            )
        }

        val videos = document.select("div.item_cont").mapNotNull { parseVideoElement(it) }

        // Check if there's a "next page" link (>) in the pagination
        val hasNextPage = document.selectFirst("div#pages a:contains(>)") != null

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = videos.isNotEmpty() && hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            val url = if (page == 1) "$mainUrl/tags/$encodedQuery" else "$mainUrl/tags/$encodedQuery?page=$page"

            val document = try {
                app.get(url, referer = "$mainUrl/").document
            } catch (e: CancellationException) {
                throw e  // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Search failed for '$query' page $page: ${e.message}")
                break
            }

            val pageResults = document.select("div.item_cont").mapNotNull { parseVideoElement(it) }

            if (pageResults.isEmpty()) break
            results.addAll(pageResults)

            // Stop if no "next page" link exists
            if (document.selectFirst("div#pages a:contains(>)") == null) break
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

        val title = document.selectFirst("div.player_details h1")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: return null

        val poster = fixUrlNull(document.selectFirst("video#player")?.attr("poster"))
        val description = document.selectFirst("div#desc")?.text()?.trim()

        val tags = document.select("div.tags a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val actors = document.select("a[href*='/pornstar/'], a[href*='/model/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val recommendations = document.select("div.item_cont")
            .mapNotNull { parseVideoElement(it) }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
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
            throw e  // Don't swallow coroutine cancellation
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load links from '$data': ${e.message}")
            return false
        }
        var linksFound = false

        document.select("video#player source").forEach { source ->
            val videoUrl = source.attr("src")
            val qualityTitle = source.attr("title")

            if (videoUrl.isNotBlank()) {
                val fullUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
                callback(
                    newExtractorLink(name, "$name - $qualityTitle", fullUrl) {
                        this.referer = data
                        this.quality = getQualityFromTitle(qualityTitle)
                    }
                )
                linksFound = true
            }
        }

        return linksFound
    }

    private fun parseVideoElement(element: Element): SearchResponse? {
        val title = element.selectFirst("div.item_title")?.text()?.trim() ?: return null
        val href = fixUrlNull(element.selectFirst("div.item > a")?.attr("href")) ?: return null

        val img = element.selectFirst("img.item_img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }?.takeIf { !it.contains("fluid_spinner") }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            posterUrl = fixUrlNull(img)
        }
    }

    private fun getQualityFromTitle(title: String): Int = when {
        title.contains("2160") || title.contains("4k", ignoreCase = true) -> Qualities.P2160.value
        title.contains("1080") -> Qualities.P1080.value
        title.contains("720") -> Qualities.P720.value
        title.contains("480") -> Qualities.P480.value
        title.contains("360") -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}
