package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class PornTrex(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
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
            app.get(url, referer = "$mainUrl/").document
        } catch (e: Exception) {
            Log.w("PornTrex", "Failed to load main page '${request.name}': ${e.message}")
            return newHomePageResponse(
                HomePageList(request.name, emptyList(), isHorizontalImages = true),
                hasNext = false
            )
        }

        val videos = document.select(".video-preview-screen")
            .mapNotNull { parseVideoElement(it) }

        // Check for pagination - look for next page link (don't assume hasNext from video count)
        val hasNextPage = document.selectFirst(".pagination a:contains(Next), .pagination .next") != null

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            val url = if (page == 1) {
                "$mainUrl/search/?query=$encodedQuery"
            } else {
                "$mainUrl/search/?query=$encodedQuery&page=$page"
            }

            val document = try {
                app.get(url, referer = "$mainUrl/").document
            } catch (e: Exception) {
                Log.w("PornTrex", "Search failed for '$query' page $page: ${e.message}")
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
        } catch (e: Exception) {
            Log.w("PornTrex", "Failed to load '$url': ${e.message}")
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
            ?.text()?.let { parseDuration(it) }

        // Get recommendations
        val recommendations = document.select(".related-videos .video-preview-screen")
            .mapNotNull { parseVideoElement(it) }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
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
        } catch (e: Exception) {
            Log.w("PornTrex", "Failed to load links from '$data': ${e.message}")
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
                    val url = Regex("""$key\s*:\s*'([^']+)'""").find(html)?.groupValues?.get(1)
                    val qualityText = Regex("""${key}_text\s*:\s*'([^']+)'""").find(html)?.groupValues?.get(1) ?: defaultQuality

                    if (!url.isNullOrBlank() && url.startsWith("http")) {
                        callback(
                            newExtractorLink(name, "$name - $qualityText", url) {
                                this.referer = data
                                this.quality = getQualityFromText(qualityText)
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

    /**
     * Parse duration from text like "25m 54s", "1h 30m 45s", "10:25", or "1:30:45"
     * Returns duration in minutes.
     */
    private fun parseDuration(text: String): Int? {
        var totalMinutes = 0

        // Handle format with h/m/s suffixes (e.g., "1h 30m 45s")
        val parts = text.split(" ")
        parts.forEach { part ->
            when {
                part.endsWith("h") -> totalMinutes += part.removeSuffix("h").toIntOrNull()?.times(60) ?: 0
                part.endsWith("m") -> totalMinutes += part.removeSuffix("m").toIntOrNull() ?: 0
                part.endsWith("min") -> totalMinutes += part.removeSuffix("min").toIntOrNull() ?: 0
            }
        }

        // If we found time parts, return the total
        if (totalMinutes > 0) return totalMinutes

        // Handle colon-separated format (e.g., "10:25" or "1:30:45")
        val colonParts = text.split(":")
        return try {
            when (colonParts.size) {
                2 -> {
                    // MM:SS format - return minutes (at least 1 for sub-minute videos)
                    val mins = colonParts[0].toIntOrNull() ?: 0
                    val secs = colonParts[1].toIntOrNull() ?: 0
                    if (mins == 0 && secs > 0) 1 else mins
                }
                3 -> {
                    // HH:MM:SS format - return hours*60 + minutes
                    val hours = colonParts[0].toIntOrNull() ?: 0
                    val mins = colonParts[1].toIntOrNull() ?: 0
                    hours * 60 + mins
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
