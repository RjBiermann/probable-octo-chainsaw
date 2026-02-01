package com.lagradost.common

/**
 * Content orientation preference.
 * Users select which orientations to include in For You recommendations.
 * All enabled by default (no filtering).
 */
class ContentOrientationConfig(
    private val getKey: (String) -> String?,
    private val setKey: (String, String?) -> Unit
) {
    companion object {
        private const val PREFIX = "nsfw_common/content_orientation"

        /** Maps canonical tags to their orientation category. */
        private val TAG_TO_ORIENTATION = mapOf(
            "gay" to "gay", "solo male" to "gay", "bisexual" to "gay",
            "lesbian" to "lesbian",
            "transgender" to "trans"
        )
    }

    var straightEnabled: Boolean
        get() = getKey("$PREFIX/straight")?.toBoolean() ?: true
        set(value) = setKey("$PREFIX/straight", value.toString())

    var gayEnabled: Boolean
        get() = getKey("$PREFIX/gay")?.toBoolean() ?: true
        set(value) = setKey("$PREFIX/gay", value.toString())

    var lesbianEnabled: Boolean
        get() = getKey("$PREFIX/lesbian")?.toBoolean() ?: true
        set(value) = setKey("$PREFIX/lesbian", value.toString())

    var transEnabled: Boolean
        get() = getKey("$PREFIX/trans")?.toBoolean() ?: true
        set(value) = setKey("$PREFIX/trans", value.toString())

    /** Returns true if all orientations are enabled (no filtering needed). */
    fun isAllEnabled(): Boolean = straightEnabled && gayEnabled && lesbianEnabled && transEnabled

    private fun isOrientationEnabled(orientation: String): Boolean = when (orientation) {
        "gay" -> gayEnabled
        "lesbian" -> lesbianEnabled
        "trans" -> transEnabled
        else -> true
    }

    /** Returns the set of canonical tags to exclude based on disabled orientations. */
    fun getExcludedOrientationTags(): Set<String> =
        TAG_TO_ORIENTATION.filterValues { !isOrientationEnabled(it) }.keys

    /**
     * Check if content with the given canonical tags is allowed by orientation preferences.
     *
     * Logic:
     * - Find which orientations this content belongs to (by checking orientation tags)
     * - Content with no orientation tags is treated as "straight"
     * - Content is allowed if ANY of its orientations are enabled
     */
    fun isContentAllowed(canonicalTags: List<String>): Boolean {
        if (isAllEnabled()) return true

        val contentOrientations = canonicalTags.mapNotNullTo(mutableSetOf()) { TAG_TO_ORIENTATION[it] }

        // No orientation tags → straight content
        if (contentOrientations.isEmpty()) {
            return straightEnabled
        }

        // Content is allowed if ANY of its orientations are enabled
        return contentOrientations.any { isOrientationEnabled(it) }
    }
}
