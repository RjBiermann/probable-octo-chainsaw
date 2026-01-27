package com.lagradost.common

import java.net.URLDecoder

/**
 * Shared string utilities for URL slug processing and text formatting.
 */
object StringUtils {

    /** Maximum iterations for path sanitization loop to prevent infinite loops */
    private const val MAX_SANITIZE_ITERATIONS = 10

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
     * Replaces hyphens and underscores with spaces and capitalizes each word.
     * "adult-video" -> "Adult Video"
     * "big_tits" -> "Big Tits"
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
        return decoded.replace("-", " ").replace("_", " ").capitalizeWords()
    }

    /**
     * Sanitize a URL path to prevent path traversal attacks.
     * Handles URL-encoded sequences (%2e%2e%2f), removes "../", "..\", and trailing ".."
     * sequences, and normalizes slashes. Uses iterative decoding to catch double-encoded attacks.
     *
     * @return Sanitized path safe for use in URL construction
     */
    fun String.sanitizePath(): String {
        // URL-decode iteratively to catch double/triple-encoded attacks
        var decoded = this
        var iterations = 0
        while (iterations < MAX_SANITIZE_ITERATIONS) {
            val next = try {
                URLDecoder.decode(decoded, "UTF-8")
            } catch (e: Exception) {
                decoded
            }
            if (next == decoded) break
            decoded = next
            iterations++
        }

        return decoded
            .replace(Regex("""\.\.(?:[/\\]|$)"""), "")  // Remove ../ and ..\ and trailing ..
            .replace(Regex("""[/\\]+"""), "/")    // Normalize multiple slashes
            .replace(Regex("""^/+"""), "/")       // Single leading slash
            .trimEnd('/')                          // Remove trailing slash
            .ifEmpty { "/" }                       // Ensure at least root path
    }

    /**
     * Check if a path contains potentially dangerous traversal sequences.
     * Uses iterative URL decoding to catch double/triple-encoded attacks.
     *
     * @return true if path contains traversal sequences
     */
    fun String.containsPathTraversal(): Boolean {
        // Decode iteratively to catch multi-level encoded traversal attempts
        var decoded = this
        var iterations = 0
        while (iterations < MAX_SANITIZE_ITERATIONS) {
            val next = try {
                URLDecoder.decode(decoded, "UTF-8")
            } catch (e: Exception) {
                decoded
            }
            if (next == decoded) break
            decoded = next
            iterations++
        }
        // Check for ".." (traversal) and backslash (Windows path separator)
        // Note: "//" is not checked as it's common in URLs (http://) and normalized by sanitizePath
        return decoded.contains("..") || decoded.contains("\\")
    }
}
