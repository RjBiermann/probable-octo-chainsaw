package com.lagradost

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PornHitsPlugin : Plugin() {
    override fun load(context: Context) {
        val customPages = CustomPage.load(context)

        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                PornHitsSettingsFragment().show(activity.supportFragmentManager, "PornHitsSettings")
            }
        }

        registerMainAPI(PornHits(customPages))
    }
}
