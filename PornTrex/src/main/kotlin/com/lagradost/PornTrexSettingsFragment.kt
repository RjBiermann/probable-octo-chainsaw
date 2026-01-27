package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.ValidationResult

class PornTrexSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "porntrex.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: porntrex.com/categories/amateur/
        • Tags: porntrex.com/tags/blonde/
        • Models: porntrex.com/models/jane-doe/
        • Channels: porntrex.com/channels/brazzers/
        • Search: porntrex.com/search/?query=fitness
        • Special: porntrex.com/top-rated/
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, tag, model, channel, or search pages)"

    override val logTag = "PornTrexSettings"

    override val repository = PornTrexPlugin.createRepository(logTag)

    override fun validateUrl(url: String): ValidationResult = PornTrexUrlValidator.validate(url)
}
