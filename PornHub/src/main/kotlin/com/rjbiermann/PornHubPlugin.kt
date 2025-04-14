package com.rjbiermann

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PornHubPlugin : Plugin() {
    override fun load(context: Context) {
        val sharedPref: SharedPreferences =
            context.getSharedPreferences(NSFWFiltersKey, Context.MODE_PRIVATE)
        registerMainAPI(PornHub(sharedPref))
    }
}