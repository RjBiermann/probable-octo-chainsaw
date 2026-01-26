package com.lagradost.common

/**
 * Shared utilities for parsing video duration strings.
 */
object DurationUtils {

    /**
     * Parse duration from various text formats and return duration in minutes.
     *
     * Supported formats:
     * - Suffix format: "1h 30m 45s", "25m 54s", "10min", "1h", "30m"
     * - Colon format: "10:25" (MM:SS), "1:30:45" (HH:MM:SS)
     *
     * @param text The duration string to parse
     * @return Duration in minutes, or null if parsing fails
     */
    fun parseDuration(text: String): Int? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null

        // Try suffix format first (e.g., "1h 30m 45s")
        val suffixResult = parseSuffixFormat(cleaned)
        if (suffixResult != null && suffixResult > 0) return suffixResult

        // Fall back to colon format (e.g., "10:25" or "1:30:45")
        return parseColonFormat(cleaned)
    }

    /**
     * Parse suffix-based duration format.
     * Examples: "1h 30m 45s", "25m 54s", "10min", "1h"
     */
    private fun parseSuffixFormat(text: String): Int? {
        var totalMinutes = 0
        var hasValidParts = false

        val parts = text.split(" ", "\u00A0") // Regular space and non-breaking space
        parts.forEach { part ->
            val trimmed = part.trim()
            when {
                trimmed.endsWith("h") -> {
                    trimmed.removeSuffix("h").toIntOrNull()?.let {
                        totalMinutes += it * 60
                        hasValidParts = true
                    }
                }
                trimmed.endsWith("min") -> {
                    trimmed.removeSuffix("min").toIntOrNull()?.let {
                        totalMinutes += it
                        hasValidParts = true
                    }
                }
                trimmed.endsWith("m") && !trimmed.endsWith("pm") -> {
                    trimmed.removeSuffix("m").toIntOrNull()?.let {
                        totalMinutes += it
                        hasValidParts = true
                    }
                }
                // Seconds are ignored for minute-based duration, but mark as valid
                trimmed.endsWith("s") && !trimmed.endsWith("ms") -> {
                    trimmed.removeSuffix("s").toIntOrNull()?.let {
                        hasValidParts = true
                        // If only seconds and no minutes yet, return at least 1 minute
                        if (totalMinutes == 0 && it > 0) {
                            totalMinutes = 1
                        }
                    }
                }
            }
        }

        return if (hasValidParts) totalMinutes else null
    }

    /**
     * Parse colon-separated duration format.
     * Examples: "10:25" (MM:SS), "1:30:45" (HH:MM:SS)
     */
    private fun parseColonFormat(text: String): Int? {
        val parts = text.split(":")
        return try {
            when (parts.size) {
                2 -> {
                    // MM:SS format
                    val mins = parts[0].trim().toIntOrNull() ?: return null
                    val secs = parts[1].trim().toIntOrNull() ?: return null
                    // Return at least 1 minute for any non-zero duration
                    if (mins == 0 && secs > 0) 1 else mins
                }
                3 -> {
                    // HH:MM:SS format
                    val hours = parts[0].trim().toIntOrNull() ?: return null
                    val mins = parts[1].trim().toIntOrNull() ?: return null
                    val secs = parts[2].trim().toIntOrNull() ?: 0
                    val totalMins = hours * 60 + mins
                    // Return at least 1 minute for any non-zero duration
                    if (totalMins == 0 && secs > 0) 1 else totalMins
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
