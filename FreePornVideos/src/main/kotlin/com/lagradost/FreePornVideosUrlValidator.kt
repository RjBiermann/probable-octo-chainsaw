package com.lagradost

import com.lagradost.common.StringUtils.slugToLabel
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URISyntaxException

object FreePornVideosUrlValidator {
    private const val DOMAIN = "freepornvideos.xxx"
    private const val WWW_DOMAIN = "www.freepornvideos.xxx"
    private const val MAX_URL_LENGTH = 2048

    private val CATEGORY_REGEX = Regex("^/categories/([\\w-]+)$")
    private val NETWORK_REGEX = Regex("^/networks/([\\w-]+)$")
    private val SITE_REGEX = Regex("^/sites/([\\w-]+)$")
    private val POPULAR_REGEX = Regex("^/most-popular/([\\w-]+)$")
    private val MODEL_REGEX = Regex("^/models/([\\w.()-]+)$")
    private val SEARCH_REGEX = Regex("^/search/([\\w-]+)$")

    fun validate(url: String): ValidationResult {
        if (url.length > MAX_URL_LENGTH) return ValidationResult.InvalidPath
        if (url.isBlank()) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return ValidationResult.InvalidPath
        }

        val host = uri.host?.lowercase() ?: return ValidationResult.InvalidPath
        if (host != DOMAIN && host != WWW_DOMAIN) return ValidationResult.InvalidDomain

        val path = uri.rawPath?.trimEnd('/') ?: ""

        // /categories/<slug>
        CATEGORY_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            return ValidationResult.Valid("/categories/$slug/", "Category: ${slug.slugToLabel()}")
        }

        // /networks/<slug>
        NETWORK_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            return ValidationResult.Valid("/networks/$slug/", "Network: ${slug.slugToLabel()}")
        }

        // /sites/<slug>
        SITE_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            return ValidationResult.Valid("/sites/$slug/", "Site: ${slug.slugToLabel()}")
        }

        // /latest-updates
        if (path == "/latest-updates") {
            return ValidationResult.Valid("/latest-updates/", "Latest Updates")
        }

        // /most-popular or /most-popular/<period>
        if (path == "/most-popular") {
            return ValidationResult.Valid("/most-popular/", "Most Popular")
        }
        POPULAR_REGEX.find(path)?.let { match ->
            val period = match.groupValues[1]
            return ValidationResult.Valid("/most-popular/$period/", "Most Popular: ${period.slugToLabel()}")
        }

        // /models/<slug> (pornstar pages - slugs may contain dots and parentheses)
        MODEL_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            return ValidationResult.Valid("/models/$slug/", "Model: ${slug.slugToLabel()}")
        }

        // /top-rated
        if (path == "/top-rated") {
            return ValidationResult.Valid("/top-rated/", "Top Rated")
        }

        // /search/<query>
        SEARCH_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            return ValidationResult.Valid("/search/$slug/", "Search: ${slug.slugToLabel()}")
        }

        return ValidationResult.InvalidPath
    }
}
