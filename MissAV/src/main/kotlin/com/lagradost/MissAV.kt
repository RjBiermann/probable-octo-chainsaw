package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class MissAV(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
    companion object {
        private const val TAG = "MissAV"
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search page $page for query: $query", e)
                break
            }
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val document = app.get(url).document

            val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            if (title == null) {
                Log.w(TAG, "Missing og:title meta tag for URL: $url")
                return null
            }

            val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

            newMovieLoadResponse(title, url, TvType.NSFW, url) {
                this.posterUrl = poster
                this.plot = description
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video page: $url", e)
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
            val response = app.get(data)
            val unpackedText = getAndUnpack(response.text)

            val m3u8Url = """source='([^']*)'""".toRegex().find(unpackedText)?.groupValues?.get(1)
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
