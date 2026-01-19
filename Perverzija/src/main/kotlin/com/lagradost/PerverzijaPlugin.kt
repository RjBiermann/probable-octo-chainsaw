package com.lagradost

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PerverzijaPlugin : Plugin() {
    companion object {
        private const val TAG = "PerverzijaPlugin"
        private const val PREFS_NAME = "perverzija_plugin_prefs"
        private const val KEY_CUSTOM_PAGES = "custom_pages"
    }

    override fun load(context: Context) {
        val customPages = loadCustomPages(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                PerverzijaSettingsFragment().show(activity.supportFragmentManager, "PerverzijaSettings")
            }
        }

        registerMainAPI(Perverzija(customPages))
        registerExtractorAPI(Xtremestream())
        registerExtractorAPI(Playhydrax())
    }

    private fun loadCustomPages(context: Context): List<CustomPage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_PAGES, "[]") ?: "[]"
        return try {
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom pages, resetting to defaults", e)
            emptyList()
        }
    }
}
