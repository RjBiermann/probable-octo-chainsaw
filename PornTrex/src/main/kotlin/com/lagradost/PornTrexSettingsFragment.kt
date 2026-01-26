package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPage
import com.lagradost.common.ValidationResult

class PornTrexSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "porntrex.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: porntrex.com/categories/amateur/
        • Tags: porntrex.com/tags/blonde/
        • Models: porntrex.com/models/jane-doe/
        • Channels: porntrex.com/channels/brazzers/
        • Search: porntrex.com/search/?query=fitness
        • Special: porntrex.com/top-rated/
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, tag, model, channel, or search pages)"

    override val logTag = "PornTrexSettings"

    override fun validateUrl(url: String): ValidationResult = PornTrexUrlValidator.validate(url)

    override fun loadPages(): List<CustomPage> {
        return try {
            val json = getKey<String>(PornTrexPlugin.STORAGE_KEY) ?: return emptyList()
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }

    override fun savePages(pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(PornTrexPlugin.STORAGE_KEY, json)
            // Verify write succeeded
            getKey<String>(PornTrexPlugin.STORAGE_KEY) == json
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }
}
