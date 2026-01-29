package com.lagradost

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.GlobalStorageCustomPagesRepository

@CloudstreamPlugin
class FreePornVideosPlugin : Plugin() {
    companion object {
        internal const val STORAGE_KEY = "FREEPORNVIDEOS_CUSTOM_PAGES"
        internal const val LEGACY_PREFS_NAME = "freepornvideos_plugin_prefs"

        fun createRepository(tag: String = "FreePornVideos") = GlobalStorageCustomPagesRepository(
            storageKey = STORAGE_KEY,
            legacyPrefsName = LEGACY_PREFS_NAME,
            getKey = { key -> getKey(key) },
            setKey = { key, value -> setKey(key, value) },
            tag = tag
        )
    }

    override fun load(context: Context) {
        val repository = createRepository("FreePornVideosPlugin")

        val customPages = repository.loadWithMigration(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                FreePornVideosSettingsFragment().show(activity.supportFragmentManager, "FreePornVideosSettings")
            }
        }

        registerMainAPI(FreePornVideos(customPages))
    }
}
