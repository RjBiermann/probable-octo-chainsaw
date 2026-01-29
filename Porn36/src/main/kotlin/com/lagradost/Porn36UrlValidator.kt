package com.lagradost

import com.lagradost.common.StringUtils.capitalizeWords
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URLDecoder

object Porn36UrlValidator {
    private const val DOMAIN = "www.porn36.com"
    private const val DOMAIN_NO_WWW = "porn36.com"
    private const val MAX_URL_LENGTH = 2048

    private val CATEGORY_REGEX = Regex("^/categories/([^/]+)/?$")
    private val NETWORK_REGEX = Regex("^/networks/([^/]+)/?$")
    private val MODEL_REGEX = Regex("^/models/([^/]+)/?$")
    private val SITE_REGEX = Regex("^/sites/([^/]+)/?$")
    private val SEARCH_REGEX = Regex("^/search/([^/]+)(?:/relevance)?/?$")
    private val MAIN_SECTIONS = listOf("/latest-updates/", "/top-rated/", "/most-popular/")

    fun validate(url: String): ValidationResult {
        if (url.length > MAX_URL_LENGTH) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return ValidationResult.InvalidPath
        }

        val host = uri.host
        if (host != DOMAIN && host != DOMAIN_NO_WWW) return ValidationResult.InvalidDomain

        val path = uri.rawPath

        MAIN_SECTIONS.forEach { section ->
            if (path == section || path == section.trimEnd('/')) {
                val label = section.trim('/').replace("-", " ").capitalizeWords()
                return ValidationResult.Valid(section, label)
            }
        }

        CATEGORY_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = decodeSlug(slug)
            return ValidationResult.Valid(path.ensureTrailingSlash(), label)
        }

        NETWORK_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = decodeSlug(slug)
            return ValidationResult.Valid(path.ensureTrailingSlash(), label)
        }

        MODEL_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = decodeSlug(slug)
            return ValidationResult.Valid(path.ensureTrailingSlash(), label)
        }

        SITE_REGEX.find(path)?.let { match ->
            val slug = match.groupValues[1]
            val label = decodeSlug(slug)
            return ValidationResult.Valid(path.ensureTrailingSlash(), label)
        }

        SEARCH_REGEX.find(path)?.let { match ->
            val query = match.groupValues[1]
            val label = "Search: ${decodeSlug(query)}"
            return ValidationResult.Valid("/search/$query/relevance/", label)
        }

        return ValidationResult.InvalidPath
    }

    private fun decodeSlug(slug: String): String = try {
        URLDecoder.decode(slug, "UTF-8").replace("-", " ").capitalizeWords()
    } catch (e: Exception) {
        slug
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
