package com.lagradost.common

/**
 * Toggle flags for intelligent features.
 * Each feature can be independently enabled/disabled from Plugin Manager.
 */
class SmartFeaturesConfig(
    private val getKey: (String) -> String?,
    private val setKey: (String, String?) -> Unit
) {
    companion object {
        private const val PREFIX = "nsfw_common/smart_features"
    }

    var searchSuggestionsEnabled: Boolean
        get() = getKey("$PREFIX/search_suggestions")?.toBoolean() ?: true
        set(value) = setKey("$PREFIX/search_suggestions", value.toString())

    var tagRecommendationsEnabled: Boolean
        get() = getKey("$PREFIX/tag_recommendations")?.toBoolean() ?: true
        set(value) = setKey("$PREFIX/tag_recommendations", value.toString())
}
