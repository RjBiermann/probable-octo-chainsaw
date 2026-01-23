package com.lagradost

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import com.lagradost.common.CustomPage
import org.jsoup.nodes.Element
import java.net.URLEncoder

class HQPorner(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
    override var mainUrl = "https://hqporner.com"
    override var name = "HQPorner"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            // Default pages (minimal set)
            add(MainPageData(name = "New HD Videos", data = "$mainUrl/hdporn"))
            add(MainPageData(name = "Top Videos", data = "$mainUrl/top"))
            add(MainPageData(name = "4K Porn", data = "$mainUrl/category/4k-porn"))
            add(MainPageData(name = "1080p Porn", data = "$mainUrl/category/1080p-porn"))
            add(MainPageData(name = "60fps Porn", data = "$mainUrl/category/60fps-porn"))

            // Custom pages from user settings (with defensive path check)
            customPages.forEach { page ->
                val safePath = if (page.path.startsWith("/")) page.path else "/${page.path}"
                add(MainPageData(name = page.label, data = "$mainUrl$safePath"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}/$page" else request.data

        val document = try {
            app.get(url, referer = "$mainUrl/").document
        } catch (e: Exception) {
            Log.w("HQPorner", "Failed to load main page '${request.name}': ${e.message}")
            return newHomePageResponse(
                HomePageList(request.name, emptyList(), isHorizontalImages = true),
                hasNext = false
            )
        }

        val videos = document.select("div.row section.box.feature:has(span.icon)")
            .mapNotNull { it.toSearchResult() }

        // Check if there's content to determine hasNext
        val hasNextPage = videos.isNotEmpty()

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            val url = "$mainUrl/?q=$encodedQuery&p=$page"

            val document = try {
                app.get(url, referer = "$mainUrl/").document
            } catch (e: Exception) {
                Log.w("HQPorner", "Search failed for '$query' page $page: ${e.message}")
                break
            }

            val pageResults = document.select("div.row section.box.feature:has(span.icon)")
                .mapNotNull { it.toSearchResult() }

            if (pageResults.isEmpty()) break
            results.addAll(pageResults)
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, referer = "$mainUrl/").document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        // Try to get poster from iframe's img parameter (base64 encoded)
        // Falls back to og:image meta tag if not found
        val poster = extractPosterFromIframe(document)
            ?: fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))

        // Extract tags from categories section
        val tags = document.select("section h3 + p a, p.tag-link a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Extract actors/actresses
        val actors = document.select("li.icon.fa-star-o a, a[href*='/actress/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // Parse duration from format like "25m 54s" or "1h 30m 45s"
        val duration = document.selectFirst("li.icon.fa-clock-o")?.text()?.let { text ->
            var totalMinutes = 0
            val parts = text.split(" ")
            parts.forEach { part ->
                when {
                    part.endsWith("h") -> totalMinutes += part.removeSuffix("h").toIntOrNull()?.times(60) ?: 0
                    part.endsWith("m") -> totalMinutes += part.removeSuffix("m").toIntOrNull() ?: 0
                }
            }
            if (totalMinutes > 0) totalMinutes else null
        }

        // Get recommendations
        val recommendations = document.select("div.row section.box.feature:has(span.icon)")
            .mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
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
        val document = app.get(
            data,
            referer = "$mainUrl/",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "en-US,en;q=0.5"
            )
        ).document

        // Try to find any iframe (mydaddy.cc, hqwo.cc, or other embed)
        val iframes = document.select("iframe")
        var foundVideo = false

        for (iframe in iframes) {
            val src = iframe.attr("src")
            if (src.isBlank()) continue

            val iframeUrl = fixUrl(src)

            // Skip ad iframes
            if (iframeUrl.contains("ads") || iframeUrl.contains("banner")) continue

            // Extract from known embed domains
            when {
                iframeUrl.contains("mydaddy.cc") || iframeUrl.contains("hqwo.cc") -> {
                    loadExtractor(iframeUrl, "$mainUrl/", subtitleCallback, callback)
                    foundVideo = true
                }
            }
        }

        return foundVideo
    }

    /**
     * Extract poster URL from the video page.
     * Tries multiple sources in order of preference:
     * 1. Direct image from fastporndelivery.hqporner.com (most reliable)
     * 2. Base64-decoded URL from iframe's img parameter
     * 3. og:image meta tag
     */
    private fun extractPosterFromIframe(document: org.jsoup.nodes.Document): String? {
        // Try 1: Look for fastporndelivery.hqporner.com images (most reliable)
        val fastPornImg = document.select("img[src*=fastporndelivery.hqporner.com]").firstOrNull()
            ?.attr("src")
        if (!fastPornImg.isNullOrBlank()) {
            return fixUrl(fastPornImg)
        }

        // Try 2: Look for hqporner.com/imgs images
        val hqpornerImg = document.select("img[src*=hqporner.com/imgs]").firstOrNull()
            ?.attr("src")
        if (!hqpornerImg.isNullOrBlank()) {
            return fixUrl(hqpornerImg)
        }

        // Try 3: Extract from iframe's img parameter (base64 encoded)
        val iframeSrc = document.selectFirst("iframe")?.attr("src")
        if (iframeSrc != null) {
            val imgParam = Regex("""img=([^&]+)""").find(iframeSrc)?.groupValues?.get(1)
            if (imgParam != null) {
                try {
                    val decoded = String(Base64.decode(imgParam, Base64.DEFAULT))
                    val posterUrl = if (decoded.startsWith("//")) {
                        "https:$decoded"
                    } else if (decoded.startsWith("http")) {
                        decoded
                    } else {
                        null
                    }
                    if (posterUrl != null) return posterUrl
                } catch (e: Exception) {
                    Log.d("HQPorner", "Failed to decode poster base64: ${e.message}")
                }
            }
        }

        // Try 4: og:image meta tag
        return fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("img")?.attr("alt") ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }
}
