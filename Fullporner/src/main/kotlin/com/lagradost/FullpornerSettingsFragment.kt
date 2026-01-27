package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.ValidationResult

class FullpornerSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "fullporner.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: fullporner.com/category/milf
        • Pornstars: fullporner.com/pornstar/riley-reid
        • Search: fullporner.com/search?q=blonde
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, pornstar, or search pages)"

    override val logTag = "FullpornerSettings"

    override val repository = FullpornerPlugin.createRepository(logTag)

    override fun validateUrl(url: String): ValidationResult = FullpornerUrlValidator.validate(url)
}
