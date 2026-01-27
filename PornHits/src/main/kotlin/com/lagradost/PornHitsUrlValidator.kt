package com.lagradost

import com.lagradost.common.StringUtils.slugToLabel
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

object PornHitsUrlValidator {
    private const val DOMAIN = "pornhits.com"
    private const val WWW_DOMAIN = "www.pornhits.com"
    /** Max URL length to prevent ReDoS attacks on regex processing */
    private const val MAX_URL_LENGTH = 2048

    fun validate(url: String): ValidationResult {
        // ReDoS protection: reject excessively long URLs before regex processing
        if (url.length > MAX_URL_LENGTH) return ValidationResult.InvalidPath
        if (url.isBlank()) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return ValidationResult.InvalidPath
        }

        // No host means invalid URL structure
        val host = uri.host?.lowercase() ?: return ValidationResult.InvalidPath
        if (host != DOMAIN && host != WWW_DOMAIN) return ValidationResult.InvalidDomain

        val path = uri.rawPath ?: ""
        val query = uri.rawQuery ?: ""

        // Parse query parameters
        val params = parseQueryParams(query)

        // Check for category (ct parameter)
        params["ct"]?.let { category ->
            val label = category.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/videos.php?s=l&ct=$category", "Category: $label")
        }

        // Check for pornstar (ps parameter)
        params["ps"]?.let { pornstar ->
            val label = pornstar.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/videos.php?s=l&ps=$pornstar", "Pornstar: $label")
        }

        // Check for sponsor/site (spon parameter)
        params["spon"]?.let { sponsor ->
            val label = sponsor.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/videos.php?s=l&spon=$sponsor", "Site: $label")
        }

        // Check for network (csg parameter)
        params["csg"]?.let { network ->
            val label = network.slugToLabel(urlDecode = true)
            return ValidationResult.Valid("/videos.php?s=l&csg=$network", "Network: $label")
        }

        // Check for search query (q parameter)
        params["q"]?.let { searchQuery ->
            val decodedQuery = try {
                URLDecoder.decode(searchQuery, "UTF-8")
            } catch (e: IllegalArgumentException) {
                searchQuery
            }
            return ValidationResult.Valid("/videos.php?q=$searchQuery", "Search: $decodedQuery")
        }

        // Check for sort parameter alone (s parameter) - built-in pages
        if (path == "/videos.php" || path == "/videos.php/") {
            return when (params["s"]) {
                "l" -> ValidationResult.Valid("/videos.php?s=l", "Latest Videos")
                "bm" -> ValidationResult.Valid("/videos.php?s=bm", "Top Rated")
                "pm" -> ValidationResult.Valid("/videos.php?s=pm", "Most Viewed")
                else -> ValidationResult.InvalidPath
            }
        }

        // Check for /full-porn/ path (homepage)
        if (path == "/full-porn" || path == "/full-porn/") {
            return ValidationResult.Valid("/full-porn/", "Full Porn")
        }

        return ValidationResult.InvalidPath
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }
}
