package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPagesViewModelFactory
import com.lagradost.common.ValidationResult

class Porn36SettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "www.porn36.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: www.porn36.com/categories/blonde/
        • Networks: www.porn36.com/networks/mylf-com/
        • Models: www.porn36.com/models/abella-danger/
        • Sites: www.porn36.com/sites/divine-bitches/
        • Search: www.porn36.com/search/blonde
        • Sections: www.porn36.com/top-rated/
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, network, model, site, search, or section pages)"

    override val logTag = "Porn36Settings"

    override val validator: (String) -> ValidationResult = Porn36UrlValidator::validate

    override val viewModel = CustomPagesViewModelFactory.create(
        repository = Porn36Plugin.createRepository("Porn36VM"),
        validator = validator,
        logTag = "Porn36VM"
    )
}
