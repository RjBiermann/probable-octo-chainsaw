package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPage
import com.lagradost.common.ValidationResult

class HQPornerSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "hqporner.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: hqporner.com/category/milf
        • Actresses: hqporner.com/actress/malena-morgan
        • Studios: hqporner.com/studio/free-brazzers-videos
        • Top Videos: hqporner.com/top or /top/month or /top/week
        • Search: hqporner.com/?q=blonde
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, actress, studio, or top pages)"

    override val logTag = "HQPornerSettings"

    override fun validateUrl(url: String): ValidationResult = HQPornerUrlValidator.validate(url)

    override fun loadPages(): List<CustomPage> {
        return try {
            val json = getKey<String>(HQPornerPlugin.STORAGE_KEY) ?: return emptyList()
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }

    override fun savePages(pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(HQPornerPlugin.STORAGE_KEY, json)
            // Verify write succeeded
            getKey<String>(HQPornerPlugin.STORAGE_KEY) == json
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }
}
