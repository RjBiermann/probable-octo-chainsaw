package com.lagradost

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.GlobalStorageCustomPagesRepository

@CloudstreamPlugin
class PornTrexPlugin : Plugin() {
    companion object {
        internal const val STORAGE_KEY = "PORNTREX_CUSTOM_PAGES"
        internal const val LEGACY_PREFS_NAME = "porntrex_plugin_prefs"

        fun createRepository(tag: String = "PornTrex") = GlobalStorageCustomPagesRepository(
            storageKey = STORAGE_KEY,
            legacyPrefsName = LEGACY_PREFS_NAME,
            getKey = { key -> getKey(key) },
            setKey = { key, value -> setKey(key, value) },
            tag = tag
        )
    }

    override fun load(context: Context) {
        val repository = createRepository("PornTrexPlugin")

        val customPages = repository.loadWithMigration(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                PornTrexSettingsFragment().show(activity.supportFragmentManager, "PornTrexSettings")
            }
        }

        registerMainAPI(PornTrex(customPages))
    }
}
