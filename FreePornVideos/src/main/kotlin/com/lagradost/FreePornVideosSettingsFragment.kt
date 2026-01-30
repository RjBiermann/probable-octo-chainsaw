package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPagesViewModelFactory
import com.lagradost.common.ValidationResult

class FreePornVideosSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "freepornvideos.xxx"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: freepornvideos.xxx/categories/milf
        • Networks: freepornvideos.xxx/networks/brazzers-com
        • Sites: freepornvideos.xxx/sites/divine-bitches
        • Models: freepornvideos.xxx/models/mick-blue
        • Most Popular: freepornvideos.xxx/most-popular/week
        • Search: freepornvideos.xxx/search/blonde
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, network, site, or search pages)"

    override val logTag = "FreePornVideosSettings"

    override val validator: (String) -> ValidationResult = FreePornVideosUrlValidator::validate

    override val viewModel = CustomPagesViewModelFactory.create(
        repository = FreePornVideosPlugin.createRepository("FreePornVideosVM"),
        validator = validator,
        logTag = "FreePornVideosVM"
    )
}
