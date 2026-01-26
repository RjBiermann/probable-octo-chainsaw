package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.common.BaseCustomPagesSettingsFragment
import com.lagradost.common.CustomPage
import com.lagradost.common.ValidationResult

class PornHitsSettingsFragment : BaseCustomPagesSettingsFragment() {

    override val siteDomain = "pornhits.com"

    override val siteExamples = """
        Examples of pages you can add:
        • Categories: pornhits.com/videos.php?s=l&ct=milf
        • Pornstars: pornhits.com/videos.php?s=l&ps=mia-khalifa
        • Sites: pornhits.com/videos.php?s=l&spon=brazzers
        • Networks: pornhits.com/videos.php?s=l&csg=metart
        • Search: pornhits.com/videos.php?q=blonde
        • Sorted: pornhits.com/videos.php?s=bm (top rated)
    """.trimIndent()

    override val invalidPathMessage = "Invalid URL (use category, pornstar, site, or search pages)"

    override val logTag = "PornHitsSettings"

    override fun validateUrl(url: String): ValidationResult = PornHitsUrlValidator.validate(url)

    override fun loadPages(): List<CustomPage> {
        return try {
            val json = getKey<String>(PornHitsPlugin.STORAGE_KEY) ?: return emptyList()
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }

    override fun savePages(pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(PornHitsPlugin.STORAGE_KEY, json)
            // Verify write succeeded
            getKey<String>(PornHitsPlugin.STORAGE_KEY) == json
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }
}
