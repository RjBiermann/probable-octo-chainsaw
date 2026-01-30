package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPagesViewModelFactory
import com.lagradost.common.ValidationResult

class PornXpSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "pornxp.ph"

    override val siteExamples = """
        Examples of pages you can add:
        • Studios: pornxp.ph/tags/EvilAngel
        • Performers: pornxp.ph/tags/Jane%20Doe
        • Sections: pornxp.ph/best/ or pornxp.ph/hd/
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use tag or section pages)"

    override val logTag = "PornXpSettings"

    override val validator: (String) -> ValidationResult = PornXpUrlValidator::validate

    override val viewModel = CustomPagesViewModelFactory.create(
        repository = PornXpPlugin.createRepository("PornXpVM"),
        validator = validator,
        logTag = "PornXpVM"
    )
}
