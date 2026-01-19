package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder

class PornHits(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
    companion object {
        private const val TAG = "PornHits"
    }

    override var mainUrl = "https://www.pornhits.com"
    override var name = "PornHits"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            // Default pages using %d for page number
            add(MainPageData(name = "Latest", data = "$mainUrl/videos.php?p=%d&s=l"))
            add(MainPageData(name = "Popular (Day)", data = "$mainUrl/videos.php?p=%d&s=pd"))
            add(MainPageData(name = "Top Rated (Day)", data = "$mainUrl/videos.php?p=%d&s=bd"))
            add(MainPageData(name = "Popular (Week)", data = "$mainUrl/videos.php?p=%d&s=pw"))
            add(MainPageData(name = "Top Rated (Week)", data = "$mainUrl/videos.php?p=%d&s=bw"))
            add(MainPageData(name = "Popular (Month)", data = "$mainUrl/videos.php?p=%d&s=pm"))
            add(MainPageData(name = "Top Rated (Month)", data = "$mainUrl/videos.php?p=%d&s=bm"))

            // Custom pages from user settings
            customPages.forEach { page ->
                val safePath = if (page.path.startsWith("/")) page.path else "/${page.path}"
                // Add %d for pagination if not present
                val dataUrl = if (safePath.contains("p=")) {
                    "$mainUrl$safePath"
                } else {
                    val separator = if (safePath.contains("?")) "&" else "?"
                    "$mainUrl$safePath${separator}p=%d"
                }
                add(MainPageData(name = page.label, data = dataUrl))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Use replace instead of format to avoid issues with URL-encoded characters (e.g., %20)
        val url = request.data.replace("%d", page.toString())
        val document = try {
            app.get(url, referer = "$mainUrl/").document
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch main page: $url", e)
            throw ErrorLoadingException("Failed to load page. Check your internet connection.")
        }

        val videos = document.select("article.item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = videos.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")

        for (page in 1..5) {
            val url = "$mainUrl/videos.php?p=$page&q=$encodedQuery"
            val document = try {
                app.get(url, referer = "$mainUrl/").document
            } catch (e: Exception) {
                Log.e(TAG, "Search request failed for page $page", e)
                break
            }

            val pageResults = document.select("article.item").mapNotNull { it.toSearchResult() }

            if (pageResults.isEmpty()) break
            results.addAll(pageResults)
        }

        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = try {
            app.get(url, referer = "$mainUrl/").document
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load video page: $url", e)
            throw ErrorLoadingException("Failed to load video. Check your internet connection.")
        }

        val title = document.selectFirst("section.video-holder div.video-info div.info-holder article#tab_video_info.tab-content div.headline h1")?.text()
            ?: document.selectFirst("h1")?.text()
        if (title == null) {
            Log.w(TAG, "Could not extract title from: $url")
            return null
        }

        // Get poster from schema JSON
        val poster = fixUrlNull(
            document.selectXpath("//script[contains(text(),'var schemaJson')]").first()?.data()
                ?.replace("\"", "")
                ?.substringAfter("thumbnailUrl:")
                ?.substringBefore(",uploadDate:")
                ?.trim()
        )

        val tags = document.select("section.video-holder div.video-info div.info-holder article#tab_video_info.tab-content div.block-details div.info h3.item a")
            .map { it.text() }

        val recommendations = document.select("div.related-videos div.list-videos article.item")
            .mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.tags = tags
            this.recommendations = recommendations
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
            Log.e(TAG, "Failed to fetch video page for links: $data", e)
            return false
        }

        val script = document.selectXpath("//script[contains(text(),'let vpage_data')]").first()?.html()
        if (script == null) {
            Log.w(TAG, "Video player script not found on page: $data")
            return false
        }

        val isVHQ = script.contains("VHQ")

        val pattern = Regex("""window\.initPlayer\((.*])\);""")
        val matchResult = pattern.find(script)
        val jsonArray = matchResult?.groups?.get(1)?.value
        if (jsonArray == null) {
            Log.w(TAG, "Could not find initPlayer call in script")
            return false
        }

        val encodedString = getEncodedString(jsonArray)
        if (encodedString == null) {
            Log.w(TAG, "Could not extract encoded string from JSON")
            return false
        }

        val decodedString = customBase64Decoder(encodedString)

        val videos = try {
            JSONObject("{ videos:$decodedString}").getJSONArray("videos")
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse video JSON: ${e.message}")
            return false
        }

        var linksFound = false

        for (i in 0 until videos.length()) {
            val video = try {
                videos.getJSONObject(i)
            } catch (e: JSONException) {
                Log.w(TAG, "Failed to parse video at index $i")
                continue
            }

            val format = video.optString("format", "")
            val quality = when {
                isVHQ -> Qualities.Unknown.value
                format.contains("hq") -> Qualities.P720.value
                format.contains("lq") -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }

            val rawVideoUrl = video.optString("video_url", "")
            if (rawVideoUrl.isEmpty()) continue

            var videoUrl = customBase64Decoder(rawVideoUrl)

            if (isVHQ) {
                videoUrl = "$videoUrl&f=video.m3u8"
            }

            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    fixUrl(videoUrl),
                    type = if (isVHQ) ExtractorLinkType.M3U8 else INFER_TYPE
                ) {
                    this.referer = mainUrl
                    this.quality = quality
                }
            )

            linksFound = true
            if (isVHQ) break
        }

        return linksFound
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("div.item-info h2.title")?.text() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null

        // Images use data-original for lazy loading, fallback to src
        var posterUrl = fixUrlNull(select("a div.img img").attr("data-original"))
        if (posterUrl.isNullOrEmpty()) {
            posterUrl = fixUrlNull(select("a div.img img").attr("src"))
        }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    /**
     * Custom base64 decoder used by PornHits.
     * Uses a character set with Cyrillic characters mixed in.
     */
    private fun customBase64Decoder(encodedString: String): String {
        // Character set includes Cyrillic А, В, С, Е, М mixed with Latin characters
        val base64CharacterSet = "АВСDЕFGHIJKLМNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,~"
        val builder = StringBuilder()
        var currentIndex = 0

        val sanitizedString = encodedString.replace("[^АВСЕМA-Za-z0-9.,~]".toRegex(), "")

        while (currentIndex < sanitizedString.length) {
            val firstCharIndex = base64CharacterSet.indexOf(sanitizedString[currentIndex++])
            val secondCharIndex = if (currentIndex < sanitizedString.length) base64CharacterSet.indexOf(sanitizedString[currentIndex++]) else -1
            val thirdCharIndex = if (currentIndex < sanitizedString.length) base64CharacterSet.indexOf(sanitizedString[currentIndex++]) else -1
            val fourthCharIndex = if (currentIndex < sanitizedString.length) base64CharacterSet.indexOf(sanitizedString[currentIndex++]) else -1

            // Skip if required characters not found in charset (indexOf returns -1)
            if (firstCharIndex == -1 || secondCharIndex == -1) continue

            val reconstructedFirstChar = (firstCharIndex shl 2) or (secondCharIndex shr 4)
            builder.append(reconstructedFirstChar.toChar())

            // Only calculate and append second char if thirdCharIndex is valid (not -1 and not padding marker 64)
            if (thirdCharIndex != -1 && thirdCharIndex != 64) {
                val reconstructedSecondChar = ((15 and secondCharIndex) shl 4) or (thirdCharIndex shr 2)
                builder.append(reconstructedSecondChar.toChar())

                // Only calculate and append third char if fourthCharIndex is also valid
                if (fourthCharIndex != -1 && fourthCharIndex != 64) {
                    val lastPart = ((3 and thirdCharIndex) shl 6) or fourthCharIndex
                    builder.append(lastPart.toChar())
                }
            }
        }

        return try {
            URLDecoder.decode(builder.toString(), "UTF-8")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "URL decoding failed, using raw string", e)
            builder.toString()
        }
    }

    private fun getEncodedString(json: String?): String? {
        val stringPattern = Regex("""'([^']+)',""")
        val stringMatch = stringPattern.find(json ?: "")
        return stringMatch?.groups?.get(1)?.value
    }
}
