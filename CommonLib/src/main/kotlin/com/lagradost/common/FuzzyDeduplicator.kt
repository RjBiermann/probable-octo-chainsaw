package com.lagradost.common

import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlin.math.abs

/**
 * Fuzzy title matching for cross-plugin video deduplication.
 * Uses fuzzywuzzy (already a project dependency) for string similarity.
 */
object FuzzyDeduplicator {

    /** Max items to deduplicate. Beyond this, items are appended without checking. */
    private const val MAX_DEDUP_SIZE = 200

    /**
     * Deduplicate a list of items by fuzzy title matching + optional duration check.
     * First item in a duplicate group wins (preserve original order).
     */
    fun <T> deduplicate(
        items: List<T>,
        titleSelector: (T) -> String,
        durationSelector: (T) -> Int?,
        threshold: Int = 85,
        durationToleranceSec: Int = 5
    ): List<T> {
        require(threshold in 1..100) { "threshold must be 1..100, was $threshold" }
        val result = mutableListOf<T>()
        for (item in items) {
            // Skip expensive fuzzy comparison once result set is large enough
            if (result.size >= MAX_DEDUP_SIZE) {
                result.add(item)
                continue
            }
            val title2 = titleSelector(item)
            val dur2 = durationSelector(item)
            val isDuplicate = result.any { existing ->
                val title1 = titleSelector(existing)
                // Quick length-based pre-filter: if titles differ >50% in length, skip fuzzy
                if (title1.length > 0 && title2.length > 0) {
                    val ratio = title1.length.toFloat() / title2.length
                    if (ratio < 0.5f || ratio > 2.0f) return@any false
                }
                isSameVideo(title1, durationSelector(existing), title2, dur2, threshold, durationToleranceSec)
            }
            if (!isDuplicate) result.add(item)
        }
        return result
    }

    /**
     * Find the best matching item from candidates for a given title + duration.
     */
    fun <T> findMatch(
        candidates: List<T>,
        title: String,
        duration: Int?,
        titleSelector: (T) -> String,
        durationSelector: (T) -> Int?,
        threshold: Int = 85,
        durationToleranceSec: Int = 5
    ): T? {
        return candidates.firstOrNull { candidate ->
            isSameVideo(
                title, duration,
                titleSelector(candidate), durationSelector(candidate),
                threshold, durationToleranceSec
            )
        }
    }

    /** Check if two titles are fuzzy-matches (ignoring duration). Includes length pre-filter. */
    fun isTitleMatch(title1: String, title2: String, threshold: Int = 85): Boolean {
        if (title1.isNotEmpty() && title2.isNotEmpty()) {
            val ratio = title1.length.toFloat() / title2.length
            if (ratio < 0.5f || ratio > 2.0f) return false
        }
        return isSameVideo(title1, null, title2, null, threshold, 0)
    }

    private fun isSameVideo(
        title1: String, duration1: Int?,
        title2: String, duration2: Int?,
        threshold: Int, durationToleranceSec: Int
    ): Boolean {
        val titleScore = FuzzySearch.ratio(title1.lowercase(), title2.lowercase())
        if (titleScore < threshold) return false

        // If both have duration, they must be within tolerance
        if (duration1 != null && duration2 != null) {
            if (abs(duration1 - duration2) > durationToleranceSec) return false
        }
        return true
    }
}
