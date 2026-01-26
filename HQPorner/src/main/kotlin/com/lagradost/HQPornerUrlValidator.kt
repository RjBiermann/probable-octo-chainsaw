package com.lagradost

import com.lagradost.common.StringUtils.slugToLabel
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URLDecoder

object HQPornerUrlValidator {
    private const val DOMAIN = "hqporner.com"
    /** Max URL length to prevent ReDoS attacks on regex processing */
    private const val MAX_URL_LENGTH = 2048

    // Regex patterns for different URL types
    private val CATEGORY_REGEX = Regex("^/category/([^/]+)/?$")
    private val ACTRESS_REGEX = Regex("^/actress/([^/]+)/?$")
    private val STUDIO_REGEX = Regex("^/studio/([^/]+)/?$")
    private val TOP_REGEX = Regex("^/top(?:/(month|week))?/?$")
    private val HDPORN_REGEX = Regex("^/hdporn/?$")

    fun validate(url: String): ValidationResult {
        // ReDoS protection: reject excessively long URLs before regex processing
        if (url.length > MAX_URL_LENGTH) return ValidationResult.InvalidPath
        if (url.isBlank()) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return ValidationResult.InvalidPath
        }

        // No host means invalid URL structure
        val host = uri.host ?: return ValidationResult.InvalidPath
        if (host != DOMAIN) return ValidationResult.InvalidDomain

        val path = uri.rawPath ?: ""
        val query = uri.rawQuery

        // Check search query: /?q=something
        if (path == "/" && query != null && query.startsWith("q=")) {
            val searchTerm = try {
                URLDecoder.decode(query.removePrefix("q=").substringBefore("&"), "UTF-8")
            } catch (e: Exception) {
                query.removePrefix("q=").substringBefore("&")
            }
            return ValidationResult.Valid("/?q=$searchTerm", "Search: $searchTerm")
        }

        // Check /hdporn (main new videos page)
        HDPORN_REGEX.find(path)?.let {
            return ValidationResult.Valid("/hdporn", "New HD Videos")
        }

        // Check /top, /top/month, /top/week
        TOP_REGEX.find(path)?.let { match ->
            val period = match.groupValues[1]
            return when (period) {
                "month" -> ValidationResult.Valid("/top/month", "Top Month")
                "week" -> ValidationResult.Valid("/top/week", "Top Week")
                else -> ValidationResult.Valid("/top", "Top Videos")
            }
        }

        // Check /category/{slug}
        CATEGORY_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/category/$slug", label)
        }

        // Check /actress/{slug}
        ACTRESS_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/actress/$slug", label)
        }

        // Check /studio/{slug}
        STUDIO_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/studio/$slug", label)
        }

        return ValidationResult.InvalidPath
    }
}
