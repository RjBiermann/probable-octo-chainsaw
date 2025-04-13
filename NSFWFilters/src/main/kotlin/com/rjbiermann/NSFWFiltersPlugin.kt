package com.rjbiermann

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TestPlugin : Plugin() {
    var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("NSFWFilters", Context.MODE_PRIVATE)
        activity = context as AppCompatActivity

        openSettings = {
            val frag = NSFWFiltersFragment(this, sharedPref)
            frag.show(activity!!.supportFragmentManager, "Frag")
        }
    }
}