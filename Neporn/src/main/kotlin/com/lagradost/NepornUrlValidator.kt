package com.lagradost

import com.lagradost.common.StringUtils.slugToLabel
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URLDecoder

object NepornUrlValidator {
    private const val DOMAIN = "neporn.com"
    /** Max URL length to prevent ReDoS attacks on regex processing */
    private const val MAX_URL_LENGTH = 2048

    // Existing patterns
    private val CATEGORY_REGEX = Regex("^/categories/([\\w-]+)/?$")
    private val TAG_REGEX = Regex("^/tags/([\\w-]+)/?$")
    private val SPECIAL_PAGES = listOf("/top-rated/", "/most-popular/", "/latest-updates/")

    // New patterns
    private val MODEL_REGEX = Regex("^/models/([\\w-]+)/?$")
    private val SEARCH_PATH_REGEX = Regex("^/search/([\\w-]+)/?$")

    fun validate(url: String): ValidationResult {
        // ReDoS protection: reject excessively long URLs before regex processing
        if (url.length > MAX_URL_LENGTH) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return ValidationResult.InvalidPath
        }

        if (uri.host != DOMAIN) return ValidationResult.InvalidDomain

        // Normalize path with trailing slash
        val rawPath = uri.path.let { if (it.endsWith("/")) it else "$it/" }

        // Strip pagination: /categories/anal/2/ -> /categories/anal/
        val path = rawPath.replace(Regex("/\\d+/$"), "/")

        // Check categories
        CATEGORY_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel()
            return ValidationResult.Valid(path, label)
        }

        // Check tags
        TAG_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel()
            return ValidationResult.Valid(path, label)
        }

        // Check models
        MODEL_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = slug.slugToLabel()
            return ValidationResult.Valid(path, label)
        }

        // Check search path: /search/massage/
        SEARCH_PATH_REGEX.find(path)?.let { match ->
            val query = match.groupValues[1]
            val label = "Search: ${query.slugToLabel()}"
            return ValidationResult.Valid(path, label)
        }

        // Check search query param: /search/?q=fitness
        if (path == "/search/" || path == "/search") {
            uri.query?.let { queryString ->
                val qParam = queryString.split("&").find { it.startsWith("q=") }
                qParam?.substringAfter("q=")?.let { searchQuery ->
                    val decodedQuery = try {
                        URLDecoder.decode(searchQuery, "UTF-8")
                    } catch (e: Exception) {
                        searchQuery
                    }
                    return ValidationResult.Valid("/search/?q=$searchQuery", "Search: $decodedQuery")
                }
            }
        }

        // Check special pages
        SPECIAL_PAGES.forEach { specialPath ->
            if (path == specialPath) {
                val label = specialPath.trim('/').slugToLabel()
                return ValidationResult.Valid(path, label)
            }
        }

        return ValidationResult.InvalidPath
    }
}
