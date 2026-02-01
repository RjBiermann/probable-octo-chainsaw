package com.lagradost.common

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartFeaturesConfigTest {

    private val storage = mutableMapOf<String, String?>()
    private val config = SmartFeaturesConfig(
        getKey = { storage[it] },
        setKey = { k, v -> storage[k] = v }
    )

    @Test
    fun `all features enabled by default`() {
        assertTrue(config.searchSuggestionsEnabled)
        assertTrue(config.tagRecommendationsEnabled)
    }

    @Test
    fun `can disable individual features`() {
        config.searchSuggestionsEnabled = false
        assertFalse(config.searchSuggestionsEnabled)
        assertTrue(config.tagRecommendationsEnabled)
    }
}
