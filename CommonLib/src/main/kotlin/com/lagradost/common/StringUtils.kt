package com.lagradost.common

import java.net.URLDecoder

/**
 * Shared string utilities for URL slug processing and text formatting.
 */
object StringUtils {

    /**
     * Capitalize each word in a string.
     * "hello world" -> "Hello World"
     */
    fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    /**
     * Convert a URL slug to a human-readable label.
     * Replaces hyphens with spaces and capitalizes each word.
     * "adult-video" -> "Adult Video"
     *
     * @param urlDecode If true, URL-decode the string first (for percent-encoded slugs)
     */
    fun String.slugToLabel(urlDecode: Boolean = false): String {
        val decoded = if (urlDecode) {
            try {
                URLDecoder.decode(this, "UTF-8")
            } catch (e: Exception) {
                this
            }
        } else {
            this
        }
        return decoded.replace("-", " ").capitalizeWords()
    }
}
