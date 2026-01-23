package com.lagradost

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.CustomPage

@CloudstreamPlugin
class PornHitsPlugin : Plugin() {
    companion object {
        const val PREFS_NAME = "pornhits_plugin_prefs"
        const val KEY_CUSTOM_PAGES = "custom_pages"
    }

    override fun load(context: Context) {
        val customPages = loadCustomPages(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                PornHitsSettingsFragment().show(activity.supportFragmentManager, "PornHitsSettings")
            }
        }

        registerMainAPI(PornHits(customPages))
    }

    private fun loadCustomPages(context: Context): List<CustomPage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_PAGES, "[]") ?: "[]"
        return try {
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e("PornHitsPlugin", "Failed to load custom pages, resetting to defaults", e)
            emptyList()
        }
    }
}
