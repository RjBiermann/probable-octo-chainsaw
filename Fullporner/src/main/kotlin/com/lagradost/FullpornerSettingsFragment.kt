package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPagesViewModelFactory
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

    override val validator: (String) -> ValidationResult = FullpornerUrlValidator::validate

    override val viewModel = CustomPagesViewModelFactory.create(
        repository = FullpornerPlugin.createRepository("FullpornerVM"),
        validator = validator,
        logTag = "FullpornerVM"
    )
}
