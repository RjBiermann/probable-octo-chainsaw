package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

/**
 * Extractor for xiaoshenke.net video player used by fullporner.com.
 * Videos are served directly from xiaoshenke.net/vid/{id}/{quality}
 *
 * The video player uses JavaScript to build video URLs dynamically:
 * - var id = "xxx" (stored reversed in the HTML)
 * - var quality = N (bitmask: bit0=360p, bit1=480p, bit2=720p, bit3=1080p)
 * - URLs built as: //xiaoshenke.net/vid/{reversed_id}/{quality_number}
 */
class FullpornerExtractor : ExtractorApi() {
    companion object {
        // Pre-compiled regex patterns for video extraction
        private val VAR_ID_REGEX = Regex("""var\s+id\s*=\s*["']([^"']+)["']""")
        private val VAR_QUALITY_REGEX = Regex("""var\s+quality\s*=\s*parseInt\s*\(\s*["'](\d+)["']\s*\)""")
    }

    override val name = "Fullporner"
    override val mainUrl = "https://xiaoshenke.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = try {
            app.get(url, referer = referer).text
        } catch (e: Exception) {
            Log.w("FullpornerExtractor", "Failed to fetch iframe '$url': ${e.message}")
            return
        }

        // Extract the video ID from JavaScript: var id = "xxx"
        // The ID is stored reversed in the HTML and needs to be reversed back
        val idMatch = VAR_ID_REGEX.find(response)
        val reversedId = idMatch?.groupValues?.get(1)?.reversed()
        if (reversedId == null) {
            Log.w("FullpornerExtractor", "Could not extract video ID from '$url'")
            return
        }

        // Extract the quality bitmask from JavaScript: var quality = parseInt("N")
        // Bitmask: bit0=360p, bit1=480p, bit2=720p, bit3=1080p
        val qualityMatch = VAR_QUALITY_REGEX.find(response)
        val qualityBitmask = qualityMatch?.groupValues?.get(1)?.toIntOrNull()
        if (qualityBitmask == null) {
            Log.w("FullpornerExtractor", "Could not extract quality bitmask from '$url', defaulting to 720p")
        }
        val effectiveQuality = qualityBitmask ?: 4 // Default to 720p

        // Determine available qualities from bitmask (btq function logic)
        val availableQualities = mutableListOf<Int>()
        if (effectiveQuality and 1 != 0) availableQualities.add(360)
        if (effectiveQuality and 2 != 0) availableQualities.add(480)
        if (effectiveQuality and 4 != 0) availableQualities.add(720)
        if (effectiveQuality and 8 != 0) availableQualities.add(1080)

        // If no qualities detected, default to 720p
        if (availableQualities.isEmpty()) {
            availableQualities.add(720)
        }

        // Build video URLs for each available quality
        availableQualities.forEach { quality ->
            val videoUrl = "https://xiaoshenke.net/vid/$reversedId/$quality"

            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    videoUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = referer ?: ""
                    this.quality = when (quality) {
                        2160 -> Qualities.P2160.value
                        1080 -> Qualities.P1080.value
                        720 -> Qualities.P720.value
                        480 -> Qualities.P480.value
                        360 -> Qualities.P360.value
                        else -> Qualities.Unknown.value
                    }
                }
            )
        }
    }
}
