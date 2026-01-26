package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPage
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

    override fun validateUrl(url: String): ValidationResult = PornXpUrlValidator.validate(url)

    override fun loadPages(): List<CustomPage> {
        return try {
            val json = getKey<String>(PornXpPlugin.STORAGE_KEY) ?: return emptyList()
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }

    override fun savePages(pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(PornXpPlugin.STORAGE_KEY, json)
            // Verify write succeeded
            getKey<String>(PornXpPlugin.STORAGE_KEY) == json
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }
}
