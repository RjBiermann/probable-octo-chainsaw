package com.lagradost

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.CustomPage

@CloudstreamPlugin
class PornTrexPlugin : Plugin() {
    companion object {
        private const val TAG = "PornTrexPlugin"
        internal const val STORAGE_KEY = "PORNTREX_CUSTOM_PAGES"
        internal const val LEGACY_PREFS_NAME = "porntrex_plugin_prefs"
        private const val LEGACY_KEY = "custom_pages"
    }

    override fun load(context: Context) {
        val customPages = loadCustomPages(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                PornTrexSettingsFragment().show(activity.supportFragmentManager, "PornTrexSettings")
            }
        }

        registerMainAPI(PornTrex(customPages))
    }

    private fun loadCustomPages(context: Context): List<CustomPage> {
        return try {
            val json = getKey<String>(STORAGE_KEY)
            if (json != null) {
                return CustomPage.listFromJson(json)
            }

            val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val legacyJson = prefs.getString(LEGACY_KEY, null)
            if (legacyJson != null) {
                val pages = CustomPage.listFromJson(legacyJson)
                setKey(STORAGE_KEY, legacyJson)
                if (getKey<String>(STORAGE_KEY) == legacyJson) {
                    prefs.edit().remove(LEGACY_KEY).apply()
                    Log.i(TAG, "Migrated ${pages.size} custom pages to global storage")
                } else {
                    Log.w(TAG, "Migration verification failed, keeping legacy data")
                }
                return pages
            }

            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }
}
