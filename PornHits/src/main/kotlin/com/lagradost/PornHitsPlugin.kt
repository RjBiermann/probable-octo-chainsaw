package com.lagradost

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.GlobalStorageCustomPagesRepository
import com.lagradost.common.PluginBootstrap
import com.lagradost.common.cache.SharedHttpPool

@CloudstreamPlugin
class PornHitsPlugin : Plugin() {
    companion object {
        internal const val STORAGE_KEY = "PORNHITS_CUSTOM_PAGES"
        internal const val LEGACY_PREFS_NAME = "pornhits_plugin_prefs"

        fun createRepository(tag: String = "PornHits") = GlobalStorageCustomPagesRepository(
            storageKey = STORAGE_KEY,
            legacyPrefsName = LEGACY_PREFS_NAME,
            getKey = { key -> getKey(key) },
            setKey = { key, value -> setKey(key, value) },
            tag = tag
        )
    }

    override fun load(context: Context) {
        val repository = createRepository("PornHitsPlugin")

        val customPages = repository.loadWithMigration(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                PornHitsSettingsFragment().show(activity.supportFragmentManager, "PornHitsSettings")
            }
        }

        val bootstrap = PluginBootstrap.create(context, "PornHits", { key -> getKey(key) }, { key, value -> setKey(key, value) })

        registerMainAPI(PornHits(customPages, bootstrap.cachedClient, bootstrap.appContext, bootstrap.watchHistoryConfig))
    }
    override fun beforeUnload() {
        SharedHttpPool.releaseClient("PornHits")
    }
}
