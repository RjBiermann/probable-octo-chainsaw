package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import com.lagradost.common.CustomPage
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class JavGuru(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
    companion object {
        private const val TAG = "JavGuru"

        private val IFRAME_URL_REGEX = Regex(""""iframe_url":"([^"]*)"""", RegexOption.IGNORE_CASE)
        private val FRAME_BASE_REGEX = Regex("""var frameBase = '([^']*)'""")
        private val R_TYPE_REGEX = Regex("""var rType = '([^']*)'""")
        private val TOKEN_REGEX = Regex("""data-token="([^"]*)"""")
        private val HLS_REGEX = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
        private val IFRAME_SRC_REGEX = Regex("""iframe src="([^"]*)"""")
    }

    override var mainUrl = "https://jav.guru"
    override var name = "JavGuru"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    private val mainHeaders = mapOf(
        "user-agent" to "okhttp/4.12.0",
        "Referer" to "$mainUrl/",
    )

    /** cdn.javmiku.com is behind Cloudflare JS challenge; cdn.javnorth.com serves the same images without protection */
    private fun fixImageUrl(url: String?): String? = url?.replace("cdn.javmiku.com", "cdn.javnorth.com")

    override val mainPage: List<MainPageData>
        get() = buildList {
            add(MainPageData("Home", mainUrl))
            add(MainPageData("Most Watched", "$mainUrl/most-watched-rank"))
            add(MainPageData("Uncensored", "$mainUrl/category/jav-uncensored"))
            add(MainPageData("Amateur", "$mainUrl/category/amateur"))
            add(MainPageData("Idol", "$mainUrl/category/idol"))
            add(MainPageData("English Subbed", "$mainUrl/category/english-subbed"))
            add(MainPageData("Married", "$mainUrl/tag/married-woman"))
            add(MainPageData("Big Tits", "$mainUrl/tag/big-tits"))
            add(MainPageData("Hardcore", "$mainUrl/tag/hardcore"))
            add(MainPageData("Gangbang", "$mainUrl/tag/gangbang"))

            customPages.forEach { page ->
                val safePath = if (page.path.startsWith("/")) page.path else "/${page.path}"
                add(MainPageData(page.label, "$mainUrl$safePath"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val url = if (request.data.contains("?")) {
                // Search URLs: /?s=query → /page/N/?s=query
                val (basePart, queryPart) = request.data.split("?", limit = 2)
                val base = basePart.trimEnd('/')
                if (page == 1) "$base/?$queryPart" else "$base/page/$page/?$queryPart"
            } else {
                if (page == 1) "${request.data}/" else "${request.data}/page/$page/"
            }
            val document = app.get(url, headers = mainHeaders).document
            val items = document.select("div.inside-article, article, div.tabcontent li, .item-list li")
            val home = items.mapNotNull { it.toSearchResponse() }
            val hasNext = document.select("a.next, a.last, nav.pagination").isNotEmpty()

            newHomePageResponse(
                HomePageList(request.name, home, isHorizontalImages = true),
                hasNext = hasNext
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load main page: ${request.name}", e)
            newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val linkElement = this.selectFirst("div.imgg a, h2 a, a")
        val href = fixUrlNull(linkElement?.attr("href")) ?: return null

        val imgElement = this.selectFirst("img")
        val title = imgElement?.attr("alt")?.trim()?.ifBlank { null }
            ?: linkElement?.attr("title")?.trim()?.ifBlank { null }
            ?: linkElement?.text()?.trim()?.ifBlank { null }
            ?: this.selectFirst("h2")?.text()?.trim()
            ?: return null

        val posterUrl = fixImageUrl(fixUrlNull(imgElement?.attr("src") ?: imgElement?.attr("data-src")))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = if (page == 1) "$mainUrl/?s=$encodedQuery" else "$mainUrl/page/$page/?s=$encodedQuery"
            val document = app.get(url, headers = mainHeaders).document
            val items = document.select("div.inside-article, article")
            val results = items.mapNotNull { it.toSearchResponse() }
            val hasNext = document.select("a.next, a.last").isNotEmpty()

            newSearchResponseList(results, hasNext = hasNext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search for: $query", e)
            newSearchResponseList(emptyList(), hasNext = false)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val document = app.get(url, headers = mainHeaders).document

            val title = document.selectFirst("h1.tit1")?.text()?.trim()
                ?: document.selectFirst("h1")?.text()?.trim()
                ?: return null

            val poster = fixImageUrl(fixUrlNull(document.selectFirst("div.large-screenshot img")?.attr("src")))
            val description = document.select("div.wp-content p:not(:has(img))")
                .joinToString(" ") { it.text() }.ifBlank { null }

            val year = document.selectFirst("div.infometa li:contains(Release Date)")?.ownText()
                ?.substringBefore("-")?.toIntOrNull()

            val tags = document.select("li.w1 a[rel=tag]").mapNotNull { it.text().trim() }
            val actors = document.select("li.w1 strong:not(:contains(tags)) ~ a")
                .mapNotNull { Actor(it.text()) }
            val recommendations = document.select("li").mapNotNull { it.toRecommendationResult() }

            newMovieLoadResponse(title, url, TvType.NSFW, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video page: $url", e)
            null
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("a img")?.attr("alt")?.trim()
        if (title.isNullOrBlank()) return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixImageUrl(fixUrlNull(this.selectFirst("a img")?.attr("src")))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val responseText = app.get(data, headers = mainHeaders).text

            IFRAME_URL_REGEX.findAll(responseText).forEach { match ->
                val encodedUrl = match.groupValues[1]

                try {
                    val iframeUrl = base64Decode(encodedUrl)
                    extractFromIframe(iframeUrl, data, subtitleCallback, callback)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract from iframe: $encodedUrl", e)
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract links from: $data", e)
            false
        }
    }

    private suspend fun extractFromIframe(
        iframeUrl: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframeText = app.get(iframeUrl, headers = mainHeaders).text

        val frameBase = FRAME_BASE_REGEX.find(iframeText)?.groupValues?.get(1) ?: return
        val rType = R_TYPE_REGEX.find(iframeText)?.groupValues?.get(1) ?: return
        val token = TOKEN_REGEX.find(iframeText)?.groupValues?.get(1) ?: return

        val reversedToken = token.reversed()
        val constructedUrl = "$frameBase?${rType}r=$reversedToken"

        val response = app.get(constructedUrl, headers = mainHeaders)
        val finalUrl = response.url
        val finalText = response.text
        val sourceName = try {
            java.net.URI(finalUrl).host?.removeSuffix(".com")?.removeSuffix(".net")?.removeSuffix(".org") ?: name
        } catch (_: Exception) { name }

        val packedScript = Jsoup.parse(finalText).selectFirst("script:containsData(eval)")?.data()

        val searchText = if (packedScript != null) {
            getAndUnpack(packedScript)
        } else {
            finalText
        }

        val hlsUrls = HLS_REGEX.findAll(searchText).map { it.groupValues[1] }.distinct().toList()
        if (hlsUrls.isNotEmpty()) {
            hlsUrls.forEach { hlsUrl ->
                callback(
                    newExtractorLink(
                        source = sourceName,
                        name = sourceName,
                        url = hlsUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = finalUrl
                    }
                )
            }
        } else {
            // Try loadExtractor on the final URL for known embed hosts (VOE, FileLions, etc.)
            loadExtractor(finalUrl, referer, subtitleCallback, callback)
        }
    }
}
