package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Fullporner(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
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
            app.get(url, referer = "$mainUrl/").document
        } catch (e: Exception) {
            Log.w("Fullporner", "Failed to load main page '${request.name}': ${e.message}")
            return newHomePageResponse(
                HomePageList(request.name, emptyList(), isHorizontalImages = true),
                hasNext = false
            )
        }

        val videos = document.select(".video-card").mapNotNull { it.toSearchResult() }

        // Check if there's a next page link (site uses "Next" or "＞" fullwidth character)
        // Only match specific next page indicators, not any pagination link
        val hasNextPage = document.selectFirst("a:contains(Next), a:contains(＞)") != null

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = videos.isNotEmpty() && hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            val url = "$mainUrl/search?q=$encodedQuery&p=$page"

            val document = try {
                app.get(url, referer = "$mainUrl/").document
            } catch (e: Exception) {
                Log.w("Fullporner", "Search failed for '$query' page $page: ${e.message}")
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
        } catch (e: Exception) {
            Log.w("Fullporner", "Failed to load video page '$url': ${e.message}")
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
        // Iframe URL pattern: //xiaoshenke.net/video/{id}/{quality}
        // The ID in iframe is stored reversed, so we reverse it back
        // Poster URL patterns differ by ID type:
        // - Numeric IDs: https://imgx.xiaoshenke.net/posterz/contents/videos_screenshots/{path}/{id}/preview_720p.mp4.jpg
        // - Hex IDs: https://imgs.xiaoshenke.net/thumb/{id}.jpg
        val poster = document.selectFirst("iframe[src*=xiaoshenke.net/video/]")?.attr("src")?.let { iframeSrc ->
            val idMatch = Regex("""/video/([^/]+)/""").find(iframeSrc)
            idMatch?.groupValues?.get(1)?.reversed()?.let { reversedId ->
                if (reversedId.all { it.isDigit() }) {
                    // Numeric ID - use imgx posterz pattern
                    val numericId = reversedId.toLongOrNull() ?: return@let null
                    val path = (numericId / 1000) * 1000
                    "https://imgx.xiaoshenke.net/posterz/contents/videos_screenshots/$path/$reversedId/preview_720p.mp4.jpg"
                } else {
                    // Hex ID - use imgs thumb pattern
                    "https://imgs.xiaoshenke.net/thumb/$reversedId.jpg"
                }
            }
        }

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
        val duration = durationText?.let { parseDuration(it) }

        // Get recommendations from related videos
        val recommendations = document.select(".video-card")
            .drop(1) // Skip the main video if it appears
            .take(20)
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
        } catch (e: Exception) {
            Log.w("Fullporner", "Failed to load links for '$data': ${e.message}")
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

    private fun parseDuration(text: String): Int? {
        val parts = text.split(":").reversed()
        val seconds = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val hours = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val totalMinutes = minutes + (hours * 60)
        // Return at least 1 minute for any non-zero duration
        return when {
            totalMinutes > 0 -> totalMinutes
            seconds > 0 -> 1
            else -> null
        }
    }
}
