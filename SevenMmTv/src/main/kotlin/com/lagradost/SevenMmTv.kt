package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.common.CustomPage
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import android.util.Log
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.cancellation.CancellationException

private data class MvarrEntry(
    val serverPrefix: String,
    val token: String,
    val baseUrl: String,
    val suffix: String,
)

class SevenMmTv(private val customPages: List<CustomPage> = emptyList()) : MainAPI() {
    companion object {
        private const val TAG = "SevenMmTv"

        // upns.live AES-128-CBC params for decrypting /api/v1/video responses.
        // Derived deterministically by the obfuscated player JS (index-*.js) from
        // window.location.host ("7mmtv.upns.live") via unicode codepoint arithmetic
        // and a rotated string-array lookup. Runtime extraction is impractical because
        // the derivation logic uses obfuscated variable names that change per JS build.
        // To re-derive: intercept crypto.subtle.importKey/decrypt on the UPNS player
        // page (e.g., https://7mmtv.upns.live/#<hash>) and read the raw key/IV bytes.
        private const val UPNS_KEY = "kiemtienmua911ca"
        private const val UPNS_IV = "1234567890oiuytr"

        // Extracts mvarr entries from page source
        // Format: mvarr['key']=[['id','token','<iframe...','baseUrl','suffix','></iframe>','download'],];
        private val MVARR_ENTRY_REGEX = Regex(
            """mvarr\['(\d+_\d+)'\]\s*=\s*\[\[(.+?)'\s*],?\];""",
            RegexOption.DOT_MATCHES_ALL
        )

        // Crypto param regexes — these are set in plaintext after the packed eval script
        private val KEY_REGEX = Regex("""var\s+argdeqweqweqwe\s*=\s*'([0-9a-f]{16})'""")
        private val IV_REGEX = Regex("""var\s+hdddedg252\s*=\s*'([0-9a-f]{16})'""")
        private val BASE_REGEX = Regex("""hcdeedg252\s*=\s*(\d+)""")
        private val XOR_REGEX = Regex("""hadeedg252\s*=\s*(\d+)""")

        // Server name mapping: mvarr key prefix → server button label
        private val SERVER_NAMES = mapOf(
            "29" to "SW",  // StreamTape / tapewithadblock
            "40" to "TV",  // Turbovid / emturbovid
            "37" to "SH",  // StreamHide / mmsi01
            "38" to "VH",  // VidHide / mmvh01
            "42" to "SP",  // 7mmtv play.php
            "41" to "US",  // 7mmtv.upns.live
        )
    }

    override var mainUrl = "https://7mmtv.sx"
    override var name = "7mmTV"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage: List<MainPageData>
        get() = buildList {
            add(MainPageData(name = "Censored", data = "$mainUrl/en/censored_list/all/"))
            add(MainPageData(name = "Uncensored", data = "$mainUrl/en/uncensored_list/all/"))
            add(MainPageData(name = "Amateur JAV", data = "$mainUrl/en/amateurjav_list/all/"))

            customPages.forEach { page ->
                add(MainPageData(name = page.label, data = "$mainUrl${page.path}"))
            }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageNum = if (page > 0) page else 1
        val url = "${request.data}${pageNum}.html"

        val document = try {
            app.get(url, referer = "$mainUrl/en/").document
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load main page '${request.name}': ${e.message}")
            return newHomePageResponse(
                HomePageList(request.name, emptyList(), isHorizontalImages = true),
                hasNext = false
            )
        }

        val videos = document.select("div.video").mapNotNull { parseVideoElement(it) }

        val hasNextPage = document.selectFirst("nav.pagination-row li.page-item a")?.let { _ ->
            val currentPageItem = document.selectFirst("nav.pagination-row li.page-item.current")
            val allPages = document.select("nav.pagination-row li.page-item a")
            val currentText = currentPageItem?.text()?.trim()
            allPages.any { it.text().trim() != currentText && it.text().trim().toIntOrNull() != null && (it.text().trim().toIntOrNull() ?: 0) > (currentText?.toIntOrNull() ?: 0) }
        } ?: false

        return newHomePageResponse(
            HomePageList(request.name, videos, isHorizontalImages = true),
            hasNext = videos.isNotEmpty() && hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = try {
            app.post(
                "$mainUrl/en/searchform_search/all/index.html",
                referer = "$mainUrl/en/",
                data = mapOf(
                    "search_keyword" to query,
                    "search_type" to "searchall",
                    "op" to "search"
                )
            ).document
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Search failed for '$query': ${e.message}")
            return emptyList()
        }

        return document.select("div.video").mapNotNull { parseVideoElement(it) }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = try {
            app.get(url, referer = "$mainUrl/en/").document
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load '$url': ${e.message}")
            return null
        }

        // Parse JSON-LD for metadata
        val jsonLdText = document.selectFirst("script[type=application/ld+json]")?.data()
        val title: String
        val poster: String?
        val description: String?
        val tags: List<String>
        val actors: List<String>

        if (jsonLdText != null) {
            val jsonLd = try {
                AppUtils.parseJson<Map<String, Any?>>(jsonLdText)
            } catch (e: Exception) {
                null
            }
            title = (jsonLd?.get("name") as? String) ?: document.selectFirst("h1")?.text()?.trim() ?: return null
            poster = jsonLd?.get("thumbnailUrl") as? String ?: jsonLd?.get("image") as? String
            description = jsonLd?.get("description") as? String
            @Suppress("UNCHECKED_CAST")
            tags = (jsonLd?.get("genre") as? List<String>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val actorList = jsonLd?.get("actor") as? List<Map<String, Any?>>
            actors = actorList?.mapNotNull { it["name"] as? String }?.filter { it != "----" } ?: emptyList()
        } else {
            title = document.selectFirst("h1")?.text()?.trim() ?: return null
            poster = null
            description = null
            tags = emptyList()
            actors = emptyList()
        }

        val recommendations = document.select("div.video").mapNotNull { parseVideoElement(it) }

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
            app.get(data, referer = "$mainUrl/en/").document
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load links from '$data': ${e.message}")
            return false
        }

        var linksFound = false
        val embedUrls = mutableSetOf<String>()
        val pageSource = document.html()

        // 1. Decrypt mvarr entries to get embed URLs and internal entries
        val (externalUrls, internalEntries) = decryptMvarrEntries(pageSource)
        embedUrls.addAll(externalUrls)

        // 2. Handle internal sources (play.php, upns.live, Turbovid)
        for (entry in internalEntries) {
            try {
                val found = when (entry.serverPrefix) {
                    "42" -> extractPlayPhp(entry.token, data, callback)
                    "41" -> extractUpnsLive(entry.token, data, callback)
                    "40" -> extractTurbovid(entry.token, entry.baseUrl, entry.suffix, data, callback)
                    else -> false
                }
                if (found) linksFound = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Internal extractor failed for server ${entry.serverPrefix}: ${e.message}")
            }
        }

        // 3. Fallback: Parse JSON-LD contentUrl if no mvarr URLs were decrypted
        if (embedUrls.isEmpty() && !linksFound) {
            val jsonLdText = document.selectFirst("script[type=application/ld+json]")?.data()
            if (jsonLdText != null) {
                Regex(""""contentUrl"\s*:\s*"([^"]+)"""").find(jsonLdText)
                    ?.groupValues?.get(1)
                    ?.takeIf { it.isNotBlank() && !it.contains("7mmtv.sx") }
                    ?.let { embedUrls.add(it) }
            }
        }

        Log.d(TAG, "Found ${embedUrls.size} embed URLs to try")

        for (embedUrl in embedUrls) {
            try {
                val extracted = loadExtractor(embedUrl, data, subtitleCallback, callback)
                if (extracted) linksFound = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Extractor failed for '$embedUrl': ${e.message}")
            }
        }

        return linksFound
    }

    /**
     * Decrypts mvarr video entries from the page source.
     *
     * The page contains encrypted video tokens in mvarr JS objects.
     * Crypto params (AES key, IV, base, XOR) are set in plaintext after the packed eval script.
     *
     * Decryption chain:
     * 1. Split token by separator char (fromCharCode(base + 97), typically 'k' when base=10)
     * 2. Parse each part as base-N integer, XOR with hadeedg252 → character
     * 3. Join characters → base64-encoded AES ciphertext
     * 4. AES-128-CBC decrypt with key and IV → video ID
     * 5. Construct URL: baseUrl + videoId + suffix
     */
    private data class DecryptedEntries(
        val externalUrls: List<String>,
        val internalEntries: List<MvarrEntry>,
    )

    private fun decryptMvarrEntries(pageSource: String): DecryptedEntries {
        val empty = DecryptedEntries(emptyList(), emptyList())
        val key = KEY_REGEX.find(pageSource)?.groupValues?.get(1) ?: return empty
        val iv = IV_REGEX.find(pageSource)?.groupValues?.get(1) ?: return empty
        val base = BASE_REGEX.find(pageSource)?.groupValues?.get(1)?.toIntOrNull() ?: return empty
        val xor = XOR_REGEX.find(pageSource)?.groupValues?.get(1)?.toIntOrNull() ?: return empty

        val separator = (base + 97).toChar()
        val externalUrls = mutableListOf<String>()
        val internalEntries = mutableListOf<MvarrEntry>()

        MVARR_ENTRY_REGEX.findAll(pageSource).forEach { match ->
            val mvarrKey = match.groupValues[1]
            val entryContent = match.groupValues[2]
            val elements = parseMvarrElements(entryContent)
            if (elements.size < 5) return@forEach

            val token = elements[1]
            val baseUrl = elements[3]
            val suffix = elements[4]
            val serverPrefix = mvarrKey.substringBefore('_')

            try {
                val decrypted = decryptToken(token, separator, base, xor, key, iv)
                if (decrypted.isBlank()) return@forEach

                val isInternal = baseUrl.contains("7mmtv.") || baseUrl.startsWith("//7mmtv.")
                val fullUrl = "$baseUrl$decrypted$suffix"
                val pointsTo7mmtv = fullUrl.contains("7mmtv.")

                if (isInternal || pointsTo7mmtv) {
                    internalEntries.add(MvarrEntry(serverPrefix, decrypted, baseUrl, suffix))
                    Log.d(TAG, "Internal entry $serverPrefix ($mvarrKey): token=$decrypted")
                } else {
                    val serverName = SERVER_NAMES[serverPrefix] ?: serverPrefix
                    Log.d(TAG, "Decrypted $serverName ($mvarrKey): $fullUrl")
                    externalUrls.add(fullUrl)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decrypt mvarr entry $mvarrKey: ${e.message}")
            }
        }

        return DecryptedEntries(externalUrls, internalEntries)
    }

    /** Extract HLS sources from 7mmtv play.php (server 42). */
    private suspend fun extractPlayPhp(
        token: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val playUrl = "$mainUrl/assets/js/play/play.php?id=$token"
        val html = try {
            app.get(playUrl, referer = referer).text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "play.php fetch failed: ${e.message}")
            return false
        }

        var found = false
        // videoSources format: {src: 'https://...m3u8', size: '720', type: '...'}
        val srcRegex = Regex("""\{src:\s*'([^']+\.m3u8[^']*)'[^}]*size:\s*'(\d+)'""")
        for (match in srcRegex.findAll(html)) {
            val m3u8Url = match.groupValues[1]
            val quality = match.groupValues[2].toIntOrNull() ?: Qualities.Unknown.value
            callback(
                newExtractorLink(
                    "7mmTV-SP", "7mmTV-SP ${quality}p", m3u8Url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.quality = quality
                    this.referer = referer
                }
            )
            found = true
        }
        return found
    }

    /** Extract HLS source from upns.live API (server 41). */
    private suspend fun extractUpnsLive(
        videoHash: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val apiUrl = "https://7mmtv.upns.live/api/v1/video?id=$videoHash&w=1920&h=1080&r=7mmtv.sx"
        val encryptedHex = try {
            app.get(apiUrl, referer = "https://7mmtv.upns.live/").text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "upns.live API failed: ${e.message}")
            return false
        }

        val jsonStr = try {
            aesDecryptHex(encryptedHex.trim(), UPNS_KEY, UPNS_IV)
        } catch (e: Exception) {
            Log.w(TAG, "upns.live decrypt failed: ${e.message}")
            return false
        }

        // Parse "source" field from JSON
        val sourceUrl = Regex(""""source"\s*:\s*"([^"]+)"""").find(jsonStr)
            ?.groupValues?.get(1) ?: return false

        if (sourceUrl.isBlank() || !sourceUrl.contains(".m3u8")) return false

        callback(
            newExtractorLink(
                "7mmTV-US", "7mmTV-US", sourceUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://7mmtv.upns.live/"
            }
        )
        return true
    }

    /** Decrypt hex-encoded AES-128-CBC ciphertext. */
    private fun aesDecryptHex(hexCipher: String, key: String, iv: String): String {
        val cipherBytes = hexCipher.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        )
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    /** Extract HLS source from Turbovid iframe chain (server 40). */
    private suspend fun extractTurbovid(
        decryptedToken: String,
        baseUrl: String,
        suffix: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Token is a full 7mmtv URL like https://7mmtv.sx/en/censored_iframeencrypteda/...
        val iframeAUrl = if (decryptedToken.startsWith("http")) {
            decryptedToken
        } else {
            "$baseUrl$decryptedToken$suffix"
        }

        // Step 1: Fetch iframeencrypteda page → extract iframeencryptedb src
        val pageA = try {
            app.get(iframeAUrl, referer = referer).text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Turbovid iframe A fetch failed: ${e.message}")
            return false
        }

        val iframeBUrl = Regex("""src='([^']*iframeencryptedb[^']*)'""").find(pageA)
            ?.groupValues?.get(1)
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?: return false

        // Step 2: Fetch iframeencryptedb page → extract turboviplay.com URL
        val pageB = try {
            app.get(iframeBUrl, referer = iframeAUrl).text
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Turbovid iframe B fetch failed: ${e.message}")
            return false
        }

        // Look for urlPlay variable with m3u8 URL
        val m3u8Url = Regex("""urlPlay\s*=\s*'([^']+)'""").find(pageB)
            ?.groupValues?.get(1)
            ?: return false

        if (!m3u8Url.contains(".m3u8")) return false

        callback(
            newExtractorLink(
                "7mmTV-TV", "7mmTV-TV", m3u8Url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = iframeBUrl
            }
        )
        return true
    }

    /** Parse comma-separated single-quoted strings from mvarr array content. */
    private fun parseMvarrElements(content: String): List<String> {
        val elements = mutableListOf<String>()
        var i = 0
        while (i < content.length) {
            if (content[i] == '\'') {
                val end = findClosingQuote(content, i + 1)
                elements.add(content.substring(i + 1, end))
                i = end + 1
            } else {
                i++
            }
        }
        return elements
    }

    private fun findClosingQuote(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            if (s[i] == '\'' && (i == start || s[i - 1] != '\\')) return i
            i++
        }
        return s.length
    }

    /**
     * Decrypts an mvarr token:
     * 1. Split by separator, parse each as base-N, XOR → char → join = base64 ciphertext
     * 2. AES-128-CBC decrypt with key/IV → plaintext video ID
     */
    private fun decryptToken(
        token: String,
        separator: Char,
        base: Int,
        xor: Int,
        keyHex: String,
        ivHex: String
    ): String {
        // Step 1: Decode the obfuscated token to base64 ciphertext
        val parts = token.split(separator)
        val decoded = parts.mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val num = part.toIntOrNull(base) ?: return@mapNotNull null
            (num xor xor).toChar()
        }.joinToString("")

        if (decoded.isBlank()) return ""

        // Step 2: AES-128-CBC decrypt
        val keyBytes = keyHex.toByteArray(Charsets.UTF_8)
        val ivBytes = ivHex.toByteArray(Charsets.UTF_8)
        val cipherData = android.util.Base64.decode(decoded, android.util.Base64.DEFAULT)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        val plainBytes = cipher.doFinal(cipherData)

        return String(plainBytes, Charsets.UTF_8)
    }

    private fun parseVideoElement(element: Element): SearchResponse? {
        val link = element.selectFirst("figure.video-preview a[href*=content]")
            ?: element.selectFirst("a[href*=content]")
            ?: return null

        val href = fixUrlNull(link.attr("href")) ?: return null
        val title = element.selectFirst("h3 a")?.text()?.trim()
            ?: link.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        val img = element.selectFirst("img.lazyload")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }?.takeIf { !it.startsWith("data:") }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            posterUrl = fixUrlNull(img)
        }
    }
}
