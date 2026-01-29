package com.lagradost

import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.ValidationResult

class JavGuruSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "jav.guru"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: jav.guru/category/jav-uncensored
        • Tags: jav.guru/tag/big-tits
        • Makers: jav.guru/maker/s1-no-1-style
        • Actresses: jav.guru/actress/kamiki-rei
        • Series: jav.guru/series/first-impression
        • Rankings: jav.guru/most-watched-rank
        • Search: jav.guru/?s=creampie
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, tag, maker, actress, series, ranking, or search pages)"

    override val logTag = "JavGuruSettings"

    override val repository = JavGuruPlugin.createRepository(logTag)

    override fun validateUrl(url: String): ValidationResult = JavGuruUrlValidator.validate(url)
}
