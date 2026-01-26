package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPage
import com.lagradost.common.ValidationResult

class MissAVSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "missav.ws"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: missav.ws/uncensored or /censored
        • Genres: missav.ws/genres/amateur
        • Actresses: missav.ws/actresses/yua-mikami
        • Makers: missav.ws/makers/s1-no-1-style
        • Tags: missav.ws/tags/creampie
        • Search: missav.ws/search/keyword
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use categories, genres, actresses, makers, or tags)"

    override val logTag = "MissAVSettings"

    override fun validateUrl(url: String): ValidationResult = MissAVUrlValidator.validate(url)

    override fun loadPages(): List<CustomPage> {
        return try {
            val json = getKey<String>(MissAVPlugin.STORAGE_KEY) ?: return emptyList()
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }

    override fun savePages(pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(MissAVPlugin.STORAGE_KEY, json)
            // Verify write succeeded
            getKey<String>(MissAVPlugin.STORAGE_KEY) == json
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }
}
