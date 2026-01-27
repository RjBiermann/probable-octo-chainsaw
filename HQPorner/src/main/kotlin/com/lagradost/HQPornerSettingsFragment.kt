package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.ValidationResult

class HQPornerSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "hqporner.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: hqporner.com/category/milf
        • Actresses: hqporner.com/actress/malena-morgan
        • Studios: hqporner.com/studio/free-brazzers-videos
        • Top Videos: hqporner.com/top or /top/month or /top/week
        • Search: hqporner.com/?q=blonde
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, actress, studio, or top pages)"

    override val logTag = "HQPornerSettings"

    override val repository = HQPornerPlugin.createRepository(logTag)

    override fun validateUrl(url: String): ValidationResult = HQPornerUrlValidator.validate(url)
}
