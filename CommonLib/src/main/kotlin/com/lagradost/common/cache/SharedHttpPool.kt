package com.lagradost.common.cache

import android.content.Context
import android.util.Log
/**
 * Shared HTTP pool across all plugins.
 * Manages CacheAwareClient instances per plugin with lifecycle management.
 *
 * Usage:
 * ```kotlin
 * // In Plugin.load():
 * val client = SharedHttpPool.getClient(context, "Neporn", config)
 *
 * // In Plugin (when unloading):
 * SharedHttpPool.releaseClient("Neporn")
 * ```
 */
object SharedHttpPool {
    private const val TAG = "SharedHttpPool"

    private val clients = mutableMapOf<String, CacheAwareClient>()
    private val prefetchers = mutableMapOf<String, Prefetcher>()

    /**
     * Get or create a CacheAwareClient for a plugin.
     * On repeated calls (e.g., plugin reload with new config), the old client is
     * released and a fresh one is created with the new config.
     */
    @Synchronized
    fun getClient(context: Context, pluginName: String, config: CacheConfig): CacheAwareClient {
        // Release stale client/prefetcher from a previous load (no-op if none)
        prefetchers.remove(pluginName)?.cancel()
        clients.remove(pluginName)?.close()
        return CacheAwareClient.create(context, pluginName, config).also {
            clients[pluginName] = it
        }
    }

    /**
     * Get or create a Prefetcher for a plugin.
     * Requires a client to already exist (call [getClient] first).
     */
    @Synchronized
    fun getPrefetcher(pluginName: String, config: CacheConfig): Prefetcher? {
        val client = clients[pluginName] ?: return null
        return prefetchers.getOrPut(pluginName) {
            Prefetcher(client, config)
        }
    }

    /**
     * Release a plugin's client and prefetcher. Call on plugin unload.
     */
    @Synchronized
    fun releaseClient(pluginName: String) {
        prefetchers.remove(pluginName)?.cancel()
        clients.remove(pluginName)?.close()
    }

    /**
     * Release all clients. Call on app shutdown.
     */
    @Synchronized
    fun releaseAll() {
        prefetchers.values.forEach { prefetcher ->
            try { prefetcher.cancel() } catch (e: Exception) { Log.w(TAG, "Failed to cancel prefetcher: ${e.message}") }
        }
        prefetchers.clear()
        clients.values.forEach { client ->
            try { client.close() } catch (e: Exception) { Log.w(TAG, "Failed to close client: ${e.message}") }
        }
        clients.clear()
    }
}
