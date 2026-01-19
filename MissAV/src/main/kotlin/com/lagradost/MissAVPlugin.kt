package com.lagradost

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MissAVPlugin : Plugin() {
    override fun load(context: Context) {
        val customPages = CustomPage.loadFromPrefs(context)

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
}
