package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPage
import com.lagradost.common.ValidationResult

class PerverzijaSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "tube.perverzija.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Studios: tube.perverzija.com/studio/brazzers/
        • Tags: tube.perverzija.com/tag/anal/
        • Stars: tube.perverzija.com/stars/angela-white/
        • Search: tube.perverzija.com/?s=massage
        • Featured: tube.perverzija.com/featured-scenes/
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use studio, tag, stars, or search pages)"

    override val logTag = "PerverzijaSettings"

    override fun validateUrl(url: String): ValidationResult = PerverzijaUrlValidator.validate(url)

    override fun loadPages(): List<CustomPage> {
        return try {
            val json = getKey<String>(PerverzijaPlugin.STORAGE_KEY) ?: return emptyList()
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }

    override fun savePages(pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(PerverzijaPlugin.STORAGE_KEY, json)
            // Verify write succeeded
            getKey<String>(PerverzijaPlugin.STORAGE_KEY) == json
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }
}
