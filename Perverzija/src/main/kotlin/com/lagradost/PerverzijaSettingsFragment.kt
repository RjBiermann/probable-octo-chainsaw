package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.ValidationResult

class PerverzijaSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "tube.perverzija.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Studios: tube.perverzija.com/studio/brazzers/
        • Tags: tube.perverzija.com/tag/anal/
        • Stars: tube.perverzija.com/stars/angela-white/
        • Search: tube.perverzija.com/?s=massage
        • Featured: tube.perverzija.com/featured-scenes/
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use studio, tag, stars, or search pages)"

    override val logTag = "PerverzijaSettings"

    override val repository = PerverzijaPlugin.createRepository(logTag)

    override fun validateUrl(url: String): ValidationResult = PerverzijaUrlValidator.validate(url)
}
