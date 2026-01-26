package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPage
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

    override fun validateUrl(url: String): ValidationResult = FullpornerUrlValidator.validate(url)

    override fun loadPages(): List<CustomPage> {
        return try {
            val json = getKey<String>(FullpornerPlugin.STORAGE_KEY) ?: return emptyList()
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }

    override fun savePages(pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(FullpornerPlugin.STORAGE_KEY, json)
            // Verify write succeeded
            getKey<String>(FullpornerPlugin.STORAGE_KEY) == json
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }
}
