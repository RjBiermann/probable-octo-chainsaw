package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.ValidationResult

class SevenMmTvSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "7mmtv.sx"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: 7mmtv.sx/en/censored_list/all/1.html
        • Genres: 7mmtv.sx/en/censored_category/5/Amateur/1.html
        • Studios: 7mmtv.sx/en/censored_makersr/5638/Name/1.html
        • Performers: 7mmtv.sx/en/censored_avperformer/14/name/1.html
        • Random: 7mmtv.sx/en/censored_random/all/index.html
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use list, category, studio, or performer pages)"

    override val logTag = "SevenMmTvSettings"

    override val repository = SevenMmTvPlugin.createRepository(logTag)

    override fun validateUrl(url: String): ValidationResult = SevenMmTvUrlValidator.validate(url)
}
