package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.ValidationResult

class MissAVSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "missav.ws"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: missav.ws/uncensored or /censored
        • Genres: missav.ws/genres/amateur
        • Actresses: missav.ws/actresses/yua-mikami
        • Makers: missav.ws/makers/s1-no-1-style
        • Tags: missav.ws/tags/creampie
        • Search: missav.ws/search/keyword
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use categories, genres, actresses, makers, or tags)"

    override val logTag = "MissAVSettings"

    override val repository = MissAVPlugin.createRepository(logTag)

    override fun validateUrl(url: String): ValidationResult = MissAVUrlValidator.validate(url)
}
