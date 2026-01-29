package com.lagradost

import com.lagradost.common.StringUtils.capitalizeWords
import com.lagradost.common.ValidationResult
import java.net.URI
import java.net.URLDecoder

object SevenMmTvUrlValidator {
    private const val DOMAIN = "7mmtv.sx"
    private const val MAX_URL_LENGTH = 2048

    // Matches listing pages: /en/censored_list/all/1.html, /en/reducing-mosaic_list/all/1.html
    private val LIST_REGEX = Regex("^/en/([\\w-]+)_list/(\\w+)/(?:\\d+\\.html)?$")
    // Matches random pages: /en/censored_random/all/index.html
    private val RANDOM_REGEX = Regex("^/en/([\\w-]+)_random/(\\w+)/(?:index\\.html)?$")
    private val CATEGORY_REGEX = Regex("^/en/([\\w-]+)_category(?:list)?/(\\w+)/([^/]+?)(?:/\\d+\\.html)?$")
    private val MAKER_REGEX = Regex("^/en/([\\w-]+)_makersr?/(\\d+)/([^/]+?)(?:/\\d+\\.html)?$")
    private val PERFORMER_REGEX = Regex("^/en/([\\w-]+)_avperformer/(\\d+)/([^/]+?)(?:/\\d+\\.html)?$")
    private val ISSUER_REGEX = Regex("^/en/([\\w-]+)_issuer/(\\d+)/([^/]+?)(?:/\\d+\\.html)?$")
    private val DIRECTOR_REGEX = Regex("^/en/([\\w-]+)_director/(\\d+)/([^/]+?)(?:/\\d+\\.html)?$")

    fun validate(url: String): ValidationResult {
        if (url.length > MAX_URL_LENGTH) return ValidationResult.InvalidPath

        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return ValidationResult.InvalidPath
        }

        if (uri.host != DOMAIN) return ValidationResult.InvalidDomain

        val path = uri.rawPath

        // List pages (e.g., /en/censored_list/all/1.html)
        LIST_REGEX.find(path)?.let { match ->
            val type = match.groupValues[1]
            val sub = match.groupValues[2]
            val basePath = "/en/${type}_list/$sub/"
            val label = decodeLabel(type) + if (sub != "all") " - ${decodeLabel(sub)}" else ""
            return ValidationResult.Valid(basePath, label)
        }

        // Random pages (e.g., /en/censored_random/all/index.html)
        RANDOM_REGEX.find(path)?.let { match ->
            val type = match.groupValues[1]
            val basePath = "/en/${type}_random/${match.groupValues[2]}/"
            val label = "${decodeLabel(type)} Random"
            return ValidationResult.Valid(basePath, label)
        }

        // Category pages
        CATEGORY_REGEX.find(path)?.let { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[3]
            val basePath = "/en/${type}_category/${match.groupValues[2]}/$name/"
            val label = "${decodeLabel(type)} - ${decodeLabel(name)}"
            return ValidationResult.Valid(basePath, label)
        }

        // Maker/studio pages
        MAKER_REGEX.find(path)?.let { match ->
            val name = match.groupValues[3]
            val basePath = "/en/${match.groupValues[1]}_makersr/${match.groupValues[2]}/$name/"
            return ValidationResult.Valid(basePath, decodeLabel(name))
        }

        // Performer pages
        PERFORMER_REGEX.find(path)?.let { match ->
            val name = match.groupValues[3]
            val basePath = "/en/${match.groupValues[1]}_avperformer/${match.groupValues[2]}/$name/"
            return ValidationResult.Valid(basePath, decodeLabel(name))
        }

        // Issuer/label pages
        ISSUER_REGEX.find(path)?.let { match ->
            val name = match.groupValues[3]
            val basePath = "/en/${match.groupValues[1]}_issuer/${match.groupValues[2]}/$name/"
            return ValidationResult.Valid(basePath, decodeLabel(name))
        }

        // Director pages
        DIRECTOR_REGEX.find(path)?.let { match ->
            val name = match.groupValues[3]
            val basePath = "/en/${match.groupValues[1]}_director/${match.groupValues[2]}/$name/"
            return ValidationResult.Valid(basePath, decodeLabel(name))
        }

        return ValidationResult.InvalidPath
    }

    private fun decodeLabel(encoded: String): String = try {
        URLDecoder.decode(encoded.replace("-", " "), "UTF-8").capitalizeWords()
    } catch (e: Exception) {
        encoded.replace("-", " ").capitalizeWords()
    }
}
