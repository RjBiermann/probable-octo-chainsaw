package com.lagradost

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.CustomPage

@CloudstreamPlugin
class MissAVPlugin : Plugin() {
    companion object {
        const val PREFS_NAME = "missav_plugin_prefs"
        const val KEY_CUSTOM_PAGES = "custom_pages"
    }

    override fun load(context: Context) {
        val customPages = loadCustomPages(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                MissAVSettingsFragment().show(activity.supportFragmentManager, "MissAVSettings")
            } else {
                Log.w("MissAVPlugin", "Cannot open settings: context is not a valid AppCompatActivity")
            }
        }

        registerMainAPI(MissAV(customPages))
    }

    private fun loadCustomPages(context: Context): List<CustomPage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_PAGES, "[]") ?: "[]"
        return try {
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e("MissAVPlugin", "Failed to load custom pages, resetting to defaults", e)
            emptyList()
        }
    }
}
