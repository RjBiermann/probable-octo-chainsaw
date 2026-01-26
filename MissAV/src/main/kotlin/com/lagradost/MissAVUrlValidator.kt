package com.lagradost

import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

object MissAVUrlValidator {
    private const val DOMAIN = "missav.ws"
    /** Max URL length to prevent ReDoS attacks on regex processing */
    private const val MAX_URL_LENGTH = 2048

    // MissAV URL patterns - all use /dm{number}/en/{path} format
    // Examples:
    //   /dm127/en/genres/Creampie
    //   /dm248/en/actresses/波多野結衣
    //   /dm514/en/new
    //   /dm1109030/en/heyzo

    // Generic pattern for /dm{num}/en/{path} - accepts any valid feed URL
    private val DM_PATH_REGEX = Regex("^/dm(\\d+)/en/(.+?)/?$")

    // Pattern for /en/search/{keyword} (search doesn't use dm prefix)
    private val SEARCH_REGEX = Regex("^/en/search/([^/]+)/?$")

    // Pattern for /en/{type}/{name} without dm prefix (genres, actresses, makers, series, tags)
    private val SIMPLE_PATH_REGEX = Regex("^/en/(genres|actresses|makers|series|tags)/([^/]+)/?$")

    fun validate(url: String): ValidationResult {
        // ReDoS protection: reject excessively long URLs before regex processing
        if (url.length > MAX_URL_LENGTH) return ValidationResult.InvalidPath
        if (url.isBlank()) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return ValidationResult.InvalidPath
        }

        val host = uri.host?.removePrefix("www.") ?: return ValidationResult.InvalidPath
        if (!host.equals(DOMAIN, ignoreCase = true)) {
            return ValidationResult.InvalidDomain
        }

        val path = uri.rawPath ?: ""

        // Check /dm{num}/en/{path} - generic pattern for all feed URLs
        DM_PATH_REGEX.find(path)?.let { match ->
            val dmNum = match.groupValues[1]
            val subPath = match.groupValues[2]
            val label = generateLabel(subPath)
            return ValidationResult.Valid("/dm$dmNum/en/$subPath", label)
        }

        // Check /en/search/{keyword} (search doesn't use dm prefix)
        SEARCH_REGEX.find(path)?.let { match ->
            val keyword = match.groupValues[1]
            val label = "Search: ${keyword.slugToLabel()}"
            return ValidationResult.Valid("/en/search/$keyword", label)
        }

        // Check /en/{type}/{name} without dm prefix
        SIMPLE_PATH_REGEX.find(path)?.let { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            val label = generateLabelForType(type, name)
            return ValidationResult.Valid("/en/$type/$name", label)
        }

        return ValidationResult.InvalidPath
    }

    /**
     * Generate a human-readable label from the URL path.
     * Examples:
     *   "genres/Creampie" -> "Genre: Creampie"
     *   "actresses/JULIA" -> "JULIA"
     *   "new" -> "New"
     *   "heyzo" -> "Heyzo"
     */
    private fun generateLabel(subPath: String): String {
        val parts = subPath.split("/")
        return if (parts.size >= 2) {
            formatLabelWithType(parts[0], parts[1])
        } else {
            parts.last().slugToLabel()
        }
    }

    /**
     * Generate label for simple /en/{type}/{name} URLs without dm prefix.
     */
    private fun generateLabelForType(type: String, name: String): String =
        formatLabelWithType(type, name)

    private fun formatLabelWithType(type: String, name: String): String {
        val decodedName = name.slugToLabel()
        return when (type) {
            "genres" -> "Genre: $decodedName"
            "actresses" -> decodedName
            "makers" -> "Maker: $decodedName"
            "series" -> "Series: $decodedName"
            "tags" -> "Tag: $decodedName"
            else -> decodedName
        }
    }

    private fun String.slugToLabel(): String {
        val decoded = try {
            URLDecoder.decode(this, "UTF-8")
        } catch (e: IllegalArgumentException) {
            // Malformed percent-encoding, fall back to replacing %20 manually
            this.replace("%20", " ")
        }
        return decoded
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
