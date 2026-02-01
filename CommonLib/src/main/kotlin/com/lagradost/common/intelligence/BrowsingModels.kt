package com.lagradost.common.intelligence

import kotlin.math.exp
import kotlin.math.ln

enum class TagType(val prefix: String, val weight: Float) {
    GENRE("g:", 1.0f),
    BODY_TYPE("b:", 1.2f),
    PERFORMER("p:", 0.6f),
    STUDIO("s:", 0.1f),
    OTHER("o:", 0.3f);

    companion object {
        fun fromPrefix(prefix: String): TagType? = entries.find { it.prefix == prefix }
    }
}

data class NormalizedTag(val canonical: String, val type: TagType) {
    fun prefixed(): String = "${type.prefix}$canonical"

    companion object {
        fun fromPrefixed(prefixed: String): NormalizedTag? {
            val type = TagType.entries.find { prefixed.startsWith(it.prefix) } ?: return null
            return NormalizedTag(prefixed.removePrefix(type.prefix), type)
        }
    }
}

data class ViewHistoryEntry(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val duration: Int?,
    val tags: List<String>,
    val sourcePlugin: String,
    val timestamp: Long,
    val tagTypes: String? = null
)

data class TagAffinity(
    val tag: String,
    val score: Float,
    val viewCount: Int,
    val lastSeen: Long
)

data class TagSource(
    val tag: String,
    val pluginName: String,
    val categoryUrl: String,
    val lastSeen: Long
)

data class SearchHistoryEntry(
    val query: String,
    val clickedUrl: String?,
    val sourcePlugin: String,
    val timestamp: Long
)

/**
 * Calculate tag affinity from engagement signals only (no browse/page-open counting).
 *
 * Signal weights (favorites strongest, watching weakest):
 * - Each favorite: 3.0
 * - Each completed: 2.0
 * - Each watching: 1.0
 *
 * Total is log-scaled and decay-weighted by recency.
 */
fun calculateEngagementAffinity(
    favoriteCount: Int,
    completedCount: Int,
    watchingCount: Int,
    daysSinceLastEngagement: Int
): Float {
    val rawScore = (favoriteCount * 3.0f) + (completedCount * 2.0f) + (watchingCount * 1.0f)
    if (rawScore == 0f) return 0f
    val frequencyWeight = ln(rawScore + 1)
    val recencyWeight = exp(-0.1 * daysSinceLastEngagement)
    return (recencyWeight * frequencyWeight).toFloat().coerceIn(0f, 1f)
}
