package com.lagradost

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.GlobalStorageCustomPagesRepository

@CloudstreamPlugin
class NepornPlugin : Plugin() {
    companion object {
        internal const val STORAGE_KEY = "NEPORN_CUSTOM_PAGES"
        internal const val LEGACY_PREFS_NAME = "neporn_plugin_prefs"

        fun createRepository(tag: String = "Neporn") = GlobalStorageCustomPagesRepository(
            storageKey = STORAGE_KEY,
            legacyPrefsName = LEGACY_PREFS_NAME,
            getKey = { key -> getKey(key) },
            setKey = { key, value -> setKey(key, value) },
            tag = tag
        )
    }

    override fun load(context: Context) {
        val repository = createRepository("NepornPlugin")

        val customPages = repository.loadWithMigration(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                NepornSettingsFragment().show(activity.supportFragmentManager, "NepornSettings")
            }
        }

        registerMainAPI(Neporn(customPages))
    }
}
