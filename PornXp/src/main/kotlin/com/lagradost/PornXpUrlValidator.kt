package com.lagradost

import com.lagradost.common.StringUtils.capitalizeWords
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URLDecoder

object PornXpUrlValidator {
    private const val DOMAIN = "pornxp.ph"
    /** Max URL length to prevent ReDoS attacks on regex processing */
    private const val MAX_URL_LENGTH = 2048
    private val MAIN_SECTIONS = listOf("/best/", "/hd/", "/released/")
    private val TAG_REGEX = Regex("^/tags/(.+)$")

    fun validate(url: String): ValidationResult {
        // ReDoS protection: reject excessively long URLs before regex processing
        if (url.length > MAX_URL_LENGTH) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return ValidationResult.InvalidPath
        }

        if (uri.host != DOMAIN) return ValidationResult.InvalidDomain

        val path = uri.rawPath

        // Check main sections
        MAIN_SECTIONS.forEach { section ->
            if (path == section || path == section.trimEnd('/')) {
                val label = section.trim('/').capitalizeWords()
                return ValidationResult.Valid(section, label)
            }
        }

        // Check tags
        TAG_REGEX.find(path)?.let { match ->
            val tag = match.groupValues[1]
            val label = try {
                URLDecoder.decode(tag, "UTF-8")
            } catch (e: Exception) {
                tag
            }
            return ValidationResult.Valid(path, label)
        }

        return ValidationResult.InvalidPath
    }
}
