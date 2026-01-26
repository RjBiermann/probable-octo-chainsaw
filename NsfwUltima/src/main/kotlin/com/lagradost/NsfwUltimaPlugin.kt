package com.lagradost

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import java.lang.ref.WeakReference

/**
 * NSFW Ultima Plugin - Aggregates content from multiple NSFW plugins.
 *
 * Each homepage (FeedGroup) is registered as a separate MainAPI provider.
 * Users create homepages in settings, then assign feeds to them.
 * No default homepage - users must create at least one to see content.
 */
@CloudstreamPlugin
class NsfwUltimaPlugin : Plugin() {

    companion object {
        private const val TAG = "NsfwUltimaPlugin"
    }

    /** Weak reference to avoid memory leaks when activity is destroyed */
    private var activityRef: WeakReference<AppCompatActivity>? = null

    /** Get activity if still valid, null otherwise */
    val activity: AppCompatActivity?
        get() = activityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    /** All registered homepage providers (one per FeedGroup) */
    val homepageProviders = mutableListOf<NsfwUltima>()

    override fun load(context: Context) {
        activityRef = (context as? AppCompatActivity)?.let { WeakReference(it) }

        // Load homepages (FeedGroups) from storage, sorted alphabetically
        val homepages = NsfwUltimaStorage.loadGroups().sortedBy { it.name.lowercase() }

        if (homepages.isEmpty()) {
            // No homepages defined - register a setup provider
            Log.d(TAG, "No homepages found, registering setup provider")
            val setupProvider = NsfwUltima(this, null)
            setupProvider.initialize() // Initialize immediately to avoid race conditions
            homepageProviders.add(setupProvider)
            registerMainAPI(setupProvider)
        } else {
            // Register one provider per homepage
            homepages.forEach { homepage ->
                val provider = NsfwUltima(this, homepage)
                provider.initialize() // Initialize immediately to avoid race conditions
                homepageProviders.add(provider)
                registerMainAPI(provider)
                Log.d(TAG, "Registered homepage: ${provider.name}")
            }
        }

        Log.d(TAG, "NSFW Ultima plugin loaded with ${homepageProviders.size} homepage(s)")

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

    /**
     * Refresh all homepage providers' feed lists.
     * Called from settings when feeds are modified.
     */
    fun refreshAllHomepages() {
        homepageProviders.forEach { it.refreshFeedList() }
    }

    /**
     * Get provider for a specific homepage ID.
     */
    fun getProviderForHomepage(homepageId: String): NsfwUltima? {
        return homepageProviders.find { it.homepageId == homepageId }
    }
}
