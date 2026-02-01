package com.lagradost.common

import android.content.Context
import com.lagradost.common.cache.CacheAwareClient
import com.lagradost.common.cache.CacheConfig
import com.lagradost.common.cache.CacheLevel
import com.lagradost.common.cache.Prefetcher
import com.lagradost.common.cache.SharedHttpPool

/**
 * Encapsulates the common plugin initialization for cache and watch history.
 * Every plugin needs a CacheAwareClient and WatchHistoryConfig with identical setup logic.
 * This eliminates that boilerplate.
 *
 * Usage in Plugin.load():
 * ```kotlin
 * val bootstrap = PluginBootstrap.create(context, "Neporn", { key -> getKey(key) }, { key, value -> setKey(key, value) })
 * registerMainAPI(Neporn(customPages, bootstrap.cachedClient, bootstrap.appContext, bootstrap.watchHistoryConfig))
 * ```
 */
class PluginBootstrap private constructor(
    val cachedClient: CacheAwareClient,
    val appContext: Context,
    val watchHistoryConfig: WatchHistoryConfig,
    /** Exposed for testing; not part of the public API. */
    val config: CacheConfig
) {
    /** Get or create a Prefetcher for this plugin's cached client. */
    fun getPrefetcher(pluginName: String): Prefetcher? =
        SharedHttpPool.getPrefetcher(pluginName, config)

    companion object {
        fun create(
            context: Context,
            pluginName: String,
            getKey: (String) -> String?,
            setKey: (String, String?) -> Unit
        ): PluginBootstrap {
            val cacheLevel = CacheLevel.fromString(
                getKey(CacheConfig.CACHE_LEVEL_KEY) ?: CacheConfig.DEFAULT_LEVEL
            )
            val config = CacheConfig.forLevel(cacheLevel)
            val cachedClient = SharedHttpPool.getClient(context, pluginName, config)
            val watchHistoryConfig = WatchHistoryConfig(getKey = getKey, setKey = setKey)

            return PluginBootstrap(
                cachedClient = cachedClient,
                appContext = context.applicationContext,
                watchHistoryConfig = watchHistoryConfig,
                config = config
            )
        }
    }
}
