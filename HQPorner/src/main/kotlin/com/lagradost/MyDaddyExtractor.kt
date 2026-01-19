package com.lagradost

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

/**
 * Extractor for mydaddy.cc video player.
 * Videos are actually served from bigcdn.cc with URLs like:
 * https://s{n}.bigcdn.cc/pubs/{id}/{quality}.mp4
 */
class MyDaddyExtractor : ExtractorApi() {
    override val name = "MyDaddy"
    override val mainUrl = "https://mydaddy.cc"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer).text

        // Primary pattern: bigcdn.cc URLs like bigcdn.cc/pubs/696c6bd5427405.98321240/720.mp4
        // Can have optional server prefix like s29.bigcdn.cc or just bigcdn.cc
        val bigcdnRegex = Regex("""((?:s\d+\.)?bigcdn\.cc/pubs/[a-zA-Z0-9.]+)/(\d+)\.mp4""")
        val bigcdnMatches = bigcdnRegex.findAll(response)
        val seenUrls = mutableSetOf<String>()

        bigcdnMatches.forEach { match ->
            val basePath = match.groupValues[1]
            val quality = match.groupValues[2]
            val videoUrl = "https://$basePath/$quality.mp4"

            if (videoUrl !in seenUrls) {
                seenUrls.add(videoUrl)

                val qualityInt = when (quality) {
                    "2160" -> Qualities.P2160.value
                    "1080" -> Qualities.P1080.value
                    "720" -> Qualities.P720.value
                    "480" -> Qualities.P480.value
                    "360" -> Qualities.P360.value
                    else -> Qualities.Unknown.value
                }

                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        videoUrl,
                        type = INFER_TYPE
                    ) {
                        this.referer = referer ?: ""
                        this.quality = qualityInt
                    }
                )
            }
        }

        // Fallback: try older anchor href pattern (a href='...')
        if (seenUrls.isEmpty()) {
            val hrefRegex = Regex("""a href='([^']*)'""", RegexOption.IGNORE_CASE)
            hrefRegex.findAll(response).forEach { match ->
                val videoUrl = match.groupValues[1]
                if (videoUrl.startsWith("http") && videoUrl !in seenUrls) {
                    seenUrls.add(videoUrl)
                    callback.invoke(
                        newExtractorLink(
                            name,
                            name,
                            videoUrl,
                            type = INFER_TYPE
                        ) {
                            this.referer = referer ?: ""
                            this.quality = getQualityFromName(
                                videoUrl.substringAfterLast("/").substringBefore(".")
                            )
                        }
                    )
                }
            }
        }
    }
}
