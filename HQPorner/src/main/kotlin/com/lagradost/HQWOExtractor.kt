package com.lagradost

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

/**
 * Extractor for hqwo.cc video player.
 * Videos can be served from:
 * - //hqwo.cc/pubs/{id}/{quality}.mp4
 * - bigcdn.cc/pubs/{id}/{quality}.mp4
 */
class HQWOExtractor : ExtractorApi() {
    override val name = "HQWO"
    override val mainUrl = "https://hqwo.cc"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer).text

        val seenUrls = mutableSetOf<String>()

        // Pattern 1: bigcdn.cc URLs (primary CDN)
        val bigcdnRegex = Regex("""((?:s\d+\.)?bigcdn\.cc/pubs/[a-zA-Z0-9.]+)/(\d+)\.mp4""")
        bigcdnRegex.findAll(response).forEach { match ->
            val basePath = match.groupValues[1]
            val quality = match.groupValues[2]
            val videoUrl = "https://$basePath/$quality.mp4"

            if (videoUrl !in seenUrls) {
                seenUrls.add(videoUrl)
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        videoUrl,
                        type = INFER_TYPE
                    ) {
                        this.referer = referer ?: ""
                        this.quality = getQualityInt(quality)
                    }
                )
            }
        }

        // Pattern 2: hqwo.cc URLs (fallback)
        val hqwoRegex = Regex("""//hqwo\.cc/pubs/([a-zA-Z0-9]+)/(\d+)\.mp4""")
        hqwoRegex.findAll(response).forEach { match ->
            val videoUrl = "https:${match.value}"

            if (videoUrl !in seenUrls) {
                seenUrls.add(videoUrl)
                val quality = match.groupValues[2]
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        videoUrl,
                        type = INFER_TYPE
                    ) {
                        this.referer = referer ?: ""
                        this.quality = getQualityInt(quality)
                    }
                )
            }
        }
    }

    private fun getQualityInt(quality: String): Int = when (quality) {
        "2160" -> Qualities.P2160.value
        "1080" -> Qualities.P1080.value
        "720" -> Qualities.P720.value
        "480" -> Qualities.P480.value
        "360" -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}
