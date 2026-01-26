package com.lagradost

import com.lagradost.common.StringUtils.slugToLabel
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URLDecoder

object PerverzijaUrlValidator {
    private const val DOMAIN = "tube.perverzija.com"
    /** Max URL length to prevent ReDoS attacks on regex processing */
    private const val MAX_URL_LENGTH = 2048

    // URL patterns for perverzija.com
    private val STUDIO_REGEX = Regex("^/studio/([\\w-]+)/?$")
    private val STUDIO_SUB_REGEX = Regex("^/studio/([\\w-]+)/([\\w-]+)/?$")
    private val TAG_REGEX = Regex("^/tag/([\\w-]+)/?$")
    private val STARS_REGEX = Regex("^/stars/([\\w-]+)/?$")
    private val SPECIAL_PAGES = listOf("/featured-scenes/")

    // Pre-compiled regex patterns for pagination stripping
    private val PAGE_PAGINATION_REGEX = Regex("/page/\\d+/$")
    private val TRAILING_NUMBER_REGEX = Regex("/\\d+/$")

    fun validate(url: String): ValidationResult {
        // ReDoS protection: reject excessively long URLs before regex processing
        if (url.length > MAX_URL_LENGTH) return ValidationResult.InvalidPath
        if (url.isBlank()) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return ValidationResult.InvalidPath
        }

        // Handle with and without www prefix
        val host = uri.host?.removePrefix("www.") ?: return ValidationResult.InvalidPath
        if (host != DOMAIN) return ValidationResult.InvalidDomain

        // Normalize path with trailing slash
        val rawPath = uri.path.let { if (it.endsWith("/")) it else "$it/" }

        // Strip pagination: /studio/brazzers/page/2/ -> /studio/brazzers/
        val path = rawPath
            .replace(PAGE_PAGINATION_REGEX, "/")
            .replace(TRAILING_NUMBER_REGEX, "/")

        // Check studios (e.g., /studio/brazzers/)
        STUDIO_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel()
            return ValidationResult.Valid(path, label)
        }

        // Check sub-studios (e.g., /studio/vxn/blacked/)
        STUDIO_SUB_REGEX.find(path)?.let { match ->
            val subSlug = match.groupValues[2]
            val label = subSlug.slugToLabel()
            return ValidationResult.Valid(path, label)
        }

        // Check tags (e.g., /tag/anal/)
        TAG_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel()
            return ValidationResult.Valid(path, label)
        }

        // Check stars (e.g., /stars/angela-white/)
        STARS_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel()
            return ValidationResult.Valid(path, label)
        }

        // Check search query param: /?s=fitness
        if (path == "/" || path == "") {
            uri.query?.let { queryString ->
                val sParam = queryString.split("&").find { it.startsWith("s=") }
                sParam?.substringAfter("s=")?.let { searchQuery ->
                    val decodedQuery = try {
                        URLDecoder.decode(searchQuery, "UTF-8")
                    } catch (e: Exception) {
                        searchQuery
                    }
                    return ValidationResult.Valid("/?s=$searchQuery&orderby=date", "Search: $decodedQuery")
                }
            }
        }

        // Check special pages
        SPECIAL_PAGES.forEach { specialPath ->
            if (path == specialPath || path.startsWith(specialPath)) {
                val label = specialPath.trim('/').slugToLabel()
                return ValidationResult.Valid(specialPath, label)
            }
        }

        return ValidationResult.InvalidPath
    }
}
