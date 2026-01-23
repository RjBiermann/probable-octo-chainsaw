package com.lagradost

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NsfwUltimaPlugin : Plugin() {

    companion object {
        private const val TAG = "NsfwUltimaPlugin"
    }

    var activity: AppCompatActivity? = null
    var nsfwUltima: NsfwUltima? = null

    override fun load(context: Context) {
        activity = context as? AppCompatActivity

        // Create and register NsfwUltima
        nsfwUltima = NsfwUltima(this)
        registerMainAPI(nsfwUltima!!)

        Log.d(TAG, "NSFW Ultima plugin loaded")

        // Setup settings handler
        openSettings = { ctx ->
            val act = ctx as? AppCompatActivity
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                try {
                    val fragment = NsfwUltimaSettingsFragment(this)
                    fragment.show(act.supportFragmentManager, "NsfwUltimaSettings")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to show settings", e)
                }
            } else {
                Log.e(TAG, "Activity is not valid, cannot show settings")
            }
        }
    }
}
