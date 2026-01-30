package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPagesViewModelFactory
import com.lagradost.common.ValidationResult

class PornHitsSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "pornhits.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: pornhits.com/videos.php?s=l&ct=milf
        • Pornstars: pornhits.com/videos.php?s=l&ps=mia-khalifa
        • Sites: pornhits.com/videos.php?s=l&spon=brazzers
        • Networks: pornhits.com/videos.php?s=l&csg=metart
        • Search: pornhits.com/videos.php?q=blonde
        • Sorted: pornhits.com/videos.php?s=bm (top rated)
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, pornstar, site, or search pages)"

    override val logTag = "PornHitsSettings"

    override val validator: (String) -> ValidationResult = PornHitsUrlValidator::validate

    override val viewModel = CustomPagesViewModelFactory.create(
        repository = PornHitsPlugin.createRepository("PornHitsVM"),
        validator = validator,
        logTag = "PornHitsVM"
    )
}
