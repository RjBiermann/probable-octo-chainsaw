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
class JavGuruPlugin : Plugin() {
    companion object {
        internal const val STORAGE_KEY = "JAVGURU_CUSTOM_PAGES"
        internal const val LEGACY_PREFS_NAME = "javguru_plugin_prefs"

        fun createRepository(tag: String = "JavGuru") = GlobalStorageCustomPagesRepository(
            storageKey = STORAGE_KEY,
            legacyPrefsName = LEGACY_PREFS_NAME,
            getKey = { key -> getKey(key) },
            setKey = { key, value -> setKey(key, value) },
            tag = tag
        )
    }

    override fun load(context: Context) {
        val repository = createRepository("JavGuruPlugin")
        val customPages = repository.loadWithMigration(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                JavGuruSettingsFragment().show(activity.supportFragmentManager, "JavGuruSettings")
            }
        }

        val bootstrap = PluginBootstrap.create(context, "JavGuru", { key -> getKey(key) }, { key, value -> setKey(key, value) })

        registerMainAPI(JavGuru(customPages, bootstrap.cachedClient, bootstrap.appContext, bootstrap.watchHistoryConfig))

        // Register custom extractors not built into Cloudstream
        registerExtractorAPI(DoodStream())
        registerExtractorAPI(DoodDoply())
        registerExtractorAPI(DoodVideo())
        registerExtractorAPI(D000d())
        registerExtractorAPI(Ds2Play())
        registerExtractorAPI(Javclan())
        registerExtractorAPI(Javggvideo())
        registerExtractorAPI(Javlion())
        registerExtractorAPI(VidhideVIP())
        registerExtractorAPI(Vidhidepro())
        registerExtractorAPI(Streamwish())
        registerExtractorAPI(Streamhihi())
        registerExtractorAPI(Javsw())
        registerExtractorAPI(Maxstream())
        registerExtractorAPI(Advertape())
        registerExtractorAPI(Javlesbians())
        registerExtractorAPI(Lauradaydo())
    }
    override fun beforeUnload() {
        SharedHttpPool.releaseClient("JavGuru")
    }
}
