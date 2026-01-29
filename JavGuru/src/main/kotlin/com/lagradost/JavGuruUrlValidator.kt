package com.lagradost

import com.lagradost.common.StringUtils.slugToLabel
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URISyntaxException

object JavGuruUrlValidator {
    private const val DOMAIN = "jav.guru"
    private const val MAX_URL_LENGTH = 2048

    // /category/{name}
    private val CATEGORY_REGEX = Regex("^/category/([^/]+)/?$")
    // /tag/{name}
    private val TAG_REGEX = Regex("^/tag/([^/]+)/?$")
    // /maker/{name} (studio)
    private val MAKER_REGEX = Regex("^/maker/([^/]+)/?$")
    // /studio/{name} (label)
    private val STUDIO_REGEX = Regex("^/studio/([^/]+)/?$")
    // /actor/{name}
    private val ACTOR_REGEX = Regex("^/actor/([^/]+)/?$")
    // /actress/{name}
    private val ACTRESS_REGEX = Regex("^/actress/([^/]+)/?$")
    // /series/{name}
    private val SERIES_REGEX = Regex("^/series/([^/]+)/?$")
    // /most-watched-rank or other top-level listing pages
    private val LISTING_REGEX = Regex("^/(most-watched-rank|most-watched|top-rated)/?$")

    fun validate(url: String): ValidationResult {
        if (url.length > MAX_URL_LENGTH || url.isBlank()) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return ValidationResult.InvalidPath
        }

        val host = uri.host?.removePrefix("www.") ?: return ValidationResult.InvalidPath
        if (!host.equals(DOMAIN, ignoreCase = true)) return ValidationResult.InvalidDomain

        val path = uri.rawPath ?: ""
        val query = uri.rawQuery

        CATEGORY_REGEX.find(path)?.let { match ->
            val category = match.groupValues[1]
            val label = "Category: ${category.slugToLabel(urlDecode = true)}"
            return ValidationResult.Valid("/category/$category", label)
        }

        TAG_REGEX.find(path)?.let { match ->
            val tag = match.groupValues[1]
            val label = "Tag: ${tag.slugToLabel(urlDecode = true)}"
            return ValidationResult.Valid("/tag/$tag", label)
        }

        MAKER_REGEX.find(path)?.let { match ->
            val maker = match.groupValues[1]
            val label = "Studio: ${maker.slugToLabel(urlDecode = true)}"
            return ValidationResult.Valid("/maker/$maker", label)
        }

        STUDIO_REGEX.find(path)?.let { match ->
            val studio = match.groupValues[1]
            val label = "Label: ${studio.slugToLabel(urlDecode = true)}"
            return ValidationResult.Valid("/studio/$studio", label)
        }

        ACTOR_REGEX.find(path)?.let { match ->
            val actor = match.groupValues[1]
            val label = "Actor: ${actor.slugToLabel(urlDecode = true)}"
            return ValidationResult.Valid("/actor/$actor", label)
        }

        ACTRESS_REGEX.find(path)?.let { match ->
            val actress = match.groupValues[1]
            val label = "Actress: ${actress.slugToLabel(urlDecode = true)}"
            return ValidationResult.Valid("/actress/$actress", label)
        }

        SERIES_REGEX.find(path)?.let { match ->
            val series = match.groupValues[1]
            val label = "Series: ${series.slugToLabel(urlDecode = true)}"
            return ValidationResult.Valid("/series/$series", label)
        }

        LISTING_REGEX.find(path)?.let { match ->
            val listing = match.groupValues[1]
            val label = listing.slugToLabel(urlDecode = false)
            return ValidationResult.Valid("/$listing", label)
        }

        // Search: /?s=query (WordPress search)
        if ((path == "/" || path.isEmpty()) && query != null) {
            val searchParam = query.split("&").find { it.startsWith("s=") }
            if (searchParam != null) {
                val searchQuery = searchParam.removePrefix("s=")
                if (searchQuery.isNotBlank()) {
                    val label = "Search: ${searchQuery.slugToLabel(urlDecode = true)}"
                    return ValidationResult.Valid("/?s=$searchQuery", label)
                }
            }
        }

        return ValidationResult.InvalidPath
    }
}
