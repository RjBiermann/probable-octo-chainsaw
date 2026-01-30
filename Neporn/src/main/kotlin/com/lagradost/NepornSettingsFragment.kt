package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPagesViewModelFactory
import com.lagradost.common.ValidationResult

class NepornSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "neporn.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: neporn.com/categories/hd-porn/
        • Tags: neporn.com/tags/european/
        • Models: neporn.com/models/jane-doe/
        • Search: neporn.com/search/fitness/
        • Special: neporn.com/top-rated/
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, tag, model, or search pages)"

    override val logTag = "NepornSettings"

    override val validator: (String) -> ValidationResult = NepornUrlValidator::validate

    override val viewModel = CustomPagesViewModelFactory.create(
        repository = NepornPlugin.createRepository("NepornVM"),
        validator = validator,
        logTag = "NepornVM"
    )
}
