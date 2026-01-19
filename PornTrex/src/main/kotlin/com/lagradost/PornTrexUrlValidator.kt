package com.lagradost

import java.net.URI

/**
 * Validates PornTrex URLs and extracts path/label information.
 * Supports categories, tags, models, channels, and search queries.
 */
object PornTrexUrlValidator {

    private val CATEGORY_REGEX = Regex("""/categories/([^/?]+)/?""")
    private val TAG_REGEX = Regex("""/tags/([^/?]+)/?""")
    private val MODEL_REGEX = Regex("""/models/([^/?]+)/?""")
    private val CHANNEL_REGEX = Regex("""/channels/([^/?]+)/?""")
    private val SEARCH_REGEX = Regex("""/search/\?query=([^&]+)""")
    // Path-based search format: /search/term/ (without query parameter)
    private val SEARCH_PATH_REGEX = Regex("""/search/([^/?]+)/?""")

    private val SPECIAL_PAGES = mapOf(
        "/top-rated/" to "Top Rated",
        "/most-popular/" to "Most Viewed",
        "/latest-updates/" to "Latest",
        "/longest/" to "Longest",
        "/newest/" to "Newest"
    )

    fun validate(url: String): ValidationResult {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return ValidationResult.InvalidPath

        val parsed = try {
            URI(trimmedUrl)
        } catch (e: Exception) {
            return ValidationResult.InvalidPath
        }

        // Check domain - strict validation to prevent spoofed domains
        val host = parsed.host?.lowercase() ?: return ValidationResult.InvalidDomain
        if (host != "porntrex.com" && host != "www.porntrex.com") {
            return ValidationResult.InvalidDomain
        }

        val path = parsed.path?.let { normalizePath(it) } ?: "/"

        // Strip pagination from path (e.g., /categories/amateur/2/ -> /categories/amateur/)
        val cleanPath = path.replace(Regex("""/\d+/?$"""), "/")

        // Check special pages first
        SPECIAL_PAGES.forEach { (pagePath, label) ->
            if (cleanPath == pagePath || cleanPath == pagePath.trimEnd('/')) {
                return ValidationResult.Valid(pagePath, label)
            }
        }

        // Try category pattern
        CATEGORY_REGEX.find(cleanPath)?.let { match ->
            val slug = match.groupValues[1]
            val label = formatLabel(slug)
            return ValidationResult.Valid("/categories/$slug/", "Category: $label")
        }

        // Try tag pattern
        TAG_REGEX.find(cleanPath)?.let { match ->
            val slug = match.groupValues[1]
            val label = formatLabel(slug)
            return ValidationResult.Valid("/tags/$slug/", "Tag: $label")
        }

        // Try model pattern
        MODEL_REGEX.find(cleanPath)?.let { match ->
            val slug = match.groupValues[1]
            val label = formatLabel(slug)
            return ValidationResult.Valid("/models/$slug/", "Model: $label")
        }

        // Try channel pattern
        CHANNEL_REGEX.find(cleanPath)?.let { match ->
            val slug = match.groupValues[1]
            val label = formatLabel(slug)
            return ValidationResult.Valid("/channels/$slug/", "Channel: $label")
        }

        // Try search pattern - extract query parameter manually
        val query = extractQueryParam(parsed.query, "query")
        if (cleanPath.startsWith("/search/") && !query.isNullOrBlank()) {
            val decodedQuery = java.net.URLDecoder.decode(query, "UTF-8")
            return ValidationResult.Valid(
                "/search/?query=$query",
                "Search: $decodedQuery"
            )
        }

        // Try path-based search pattern: /search/term/
        SEARCH_PATH_REGEX.find(cleanPath)?.let { match ->
            val term = match.groupValues[1]
            if (term.isNotBlank()) {
                val decodedTerm = java.net.URLDecoder.decode(term, "UTF-8")
                return ValidationResult.Valid("/search/$term/", "Search: $decodedTerm")
            }
        }

        return ValidationResult.InvalidPath
    }

    private fun extractQueryParam(query: String?, param: String): String? {
        if (query.isNullOrBlank()) return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .find { it.size == 2 && it[0] == param }
            ?.get(1)
    }

    /**
     * Normalize a URL path by ensuring it starts and ends with slashes.
     */
    private fun normalizePath(path: String): String {
        var result = path
        if (!result.startsWith("/")) result = "/$result"
        if (!result.endsWith("/") && !result.contains("?")) result = "$result/"
        return result
    }

    /**
     * Format a URL slug into a human-readable label.
     * Converts "big-tits" to "Big Tits", "amateur" to "Amateur", etc.
     */
    private fun formatLabel(slug: String): String {
        return slug
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }
}
