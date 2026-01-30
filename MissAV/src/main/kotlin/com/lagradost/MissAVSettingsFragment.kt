package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPagesViewModelFactory
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

    override val validator: (String) -> ValidationResult = MissAVUrlValidator::validate

    override val viewModel = CustomPagesViewModelFactory.create(
        repository = MissAVPlugin.createRepository("MissAVVM"),
        validator = validator,
        logTag = "MissAVVM"
    )
}
