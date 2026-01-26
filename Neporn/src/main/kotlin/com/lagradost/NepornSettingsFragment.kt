package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPage
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

    override fun validateUrl(url: String): ValidationResult = NepornUrlValidator.validate(url)

    override fun loadPages(): List<CustomPage> {
        return try {
            val json = getKey<String>(NepornPlugin.STORAGE_KEY) ?: return emptyList()
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }

    override fun savePages(pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(NepornPlugin.STORAGE_KEY, json)
            // Verify write succeeded
            getKey<String>(NepornPlugin.STORAGE_KEY) == json
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }
}
