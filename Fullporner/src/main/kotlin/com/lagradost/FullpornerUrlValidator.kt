package com.lagradost

import com.lagradost.common.StringUtils.slugToLabel
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

object FullpornerUrlValidator {
    private const val DOMAIN = "fullporner.com"
    /** Max URL length to prevent ReDoS attacks on regex processing */
    private const val MAX_URL_LENGTH = 2048

    // Regex patterns for different URL types
    private val CATEGORY_REGEX = Regex("^/category/([^/]+)/?$")
    private val PORNSTAR_REGEX = Regex("^/pornstar/([^/]+)/?$")
    private val CHANNEL_REGEX = Regex("^/channel/([^/]+)/?$")
    private val HOME_REGEX = Regex("^/home/?$")
    private val SEARCH_REGEX = Regex("^/search$")

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
        if (host != DOMAIN && host != "www.$DOMAIN") return ValidationResult.InvalidDomain

        val path = uri.rawPath ?: ""
        val query = uri.rawQuery

        // Check search query: /search?q=something
        if (path == "/search" && query != null && query.contains("q=")) {
            val searchTerm = try {
                val qValue = query.split("&").find { it.startsWith("q=") }?.removePrefix("q=") ?: ""
                URLDecoder.decode(qValue, "UTF-8")
            } catch (e: Exception) {
                query.substringAfter("q=").substringBefore("&")
            }
            // Reject empty search terms
            if (searchTerm.isBlank()) return ValidationResult.InvalidPath
            // Re-encode for path to prevent URL injection, but keep decoded for label
            val encodedTerm = URLEncoder.encode(searchTerm, "UTF-8")
            return ValidationResult.Valid("/search?q=$encodedTerm", "Search: $searchTerm")
        }

        // Check /home (main featured videos)
        HOME_REGEX.find(path)?.let {
            return ValidationResult.Valid("/home", "Featured Videos")
        }

        // Check /category/{slug}
        CATEGORY_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/category/$slug", label)
        }

        // Check /pornstar/{slug}
        PORNSTAR_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/pornstar/$slug", label)
        }

        // Check /channel/{slug}
        CHANNEL_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/channel/$slug", label)
        }

        return ValidationResult.InvalidPath
    }
}
