package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
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

    override val repository = PornXpPlugin.createRepository(logTag)

    override fun validateUrl(url: String): ValidationResult = PornXpUrlValidator.validate(url)
}
