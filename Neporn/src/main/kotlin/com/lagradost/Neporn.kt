package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.common.CustomPage
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Neporn(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
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
                add(MainPageData(name = page.label, data = "$mainUrl${page.path}"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            val separator = if (request.data.contains("?")) "&" else "?"
            "${request.data}${separator}from=$page"
        } else request.data

        val response = app.get(url, referer = "$mainUrl/")

        // Detect 404 redirect - site shows fallback content but URL contains /404.php
        val is404 = response.url.contains("/404.php")

        val videos = if (is404) {
            emptyList()
        } else {
            response.document.select("div.list-videos div.item").mapNotNull { parseVideoElement(it) }
        }

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = videos.isNotEmpty() && !is404
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

        for (page in 1..5) {
            val url = if (page == 1) {
                "$mainUrl/search/?q=$encodedQuery"
            } else {
                "$mainUrl/search/?q=$encodedQuery&from_videos=$page&from_albums=$page"
            }

            val response = app.get(url, referer = "$mainUrl/")

            // Stop if redirected to 404 page
            if (response.url.contains("/404.php")) break

            val pageResults = response.document.select("div.list-videos div.item")
                .mapNotNull { parseVideoElement(it) }

            if (pageResults.isEmpty()) break
            results.addAll(pageResults)
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, referer = "$mainUrl/").document

        // Try JSON-LD first, then fallback to h1
        val jsonLd = document.selectFirst("script[type=application/ld+json]")?.html()
        val title = jsonLd?.let {
            Regex(""""name"\s*:\s*"([^"]+)"""").find(it)?.groupValues?.get(1)
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
        val document = app.get(data, referer = "$mainUrl/").document
        var linksFound = false

        // Method 1: JSON-LD schema
        val jsonLd = document.selectFirst("script[type=application/ld+json]")?.html()
        if (jsonLd != null) {
            val contentUrl = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
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
                    val videoUrl = Regex("""video_url\s*:\s*'([^']+)'""")
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

        return linksFound
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
