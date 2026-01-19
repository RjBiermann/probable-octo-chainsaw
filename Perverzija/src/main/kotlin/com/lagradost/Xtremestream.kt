package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * Extractor for xtremestream.xyz video player.
 * Handles URLs like: pervl2.xtremestream.xyz/player/index.php?data=...
 */
open class Xtremestream : ExtractorApi() {
    companion object {
        private const val TAG = "Xtremestream"
    }

    override var name = "Xtremestream"
    override var mainUrl = "https://xtremestream.xyz"
    override val requiresReferer = true

    // Match any subdomain of xtremestream.xyz
    override fun getExtractorUrl(id: String): String = "$mainUrl/$id"

    // Custom URL matching to handle multiple subdomains
    fun canHandleUrl(url: String): Boolean {
        return url.contains("xtremestream.xyz")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (!canHandleUrl(url)) return

        val defaultReferer = referer ?: "https://tube.perverzija.com/"

        val response = try {
            app.get(
                url,
                referer = defaultReferer,
                headers = mapOf(
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                    "User-Agent" to USER_AGENT
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch player page: $url", e)
            return
        }

        val playerScript = response.document
            .selectXpath("//script[contains(text(),'var video_id')]")
            .html()

        if (playerScript.isBlank()) {
            Log.w(TAG, "Player script not found on page: $url")
            return
        }

        val videoId = playerScript
            .substringAfter("var video_id = `", "")
            .substringBefore("`;", "")

        val m3u8LoaderUrl = playerScript
            .substringAfter("var m3u8_loader_url = `", "")
            .substringBefore("`;", "")

        if (videoId.isBlank() || m3u8LoaderUrl.isBlank()) {
            Log.w(TAG, "Could not extract videoId or m3u8LoaderUrl from script")
            return
        }

        val resolutions = listOf(1080, 720, 480)

        resolutions.forEach { resolution ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    "${m3u8LoaderUrl}${videoId}&q=${resolution}",
                    type = ExtractorLinkType.M3U8
                ) {
                    this.quality = resolution
                    this.referer = url
                    this.headers = mapOf(
                        "Accept" to "*/*",
                        "Referer" to url,
                        "User-Agent" to USER_AGENT
                    )
                }
            )
        }
    }
}
