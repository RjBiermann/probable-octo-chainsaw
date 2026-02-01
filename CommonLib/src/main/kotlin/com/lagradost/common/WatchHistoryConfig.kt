package com.lagradost.common

/**
 * Configuration for watch history TvType toggle.
 * When enabled, plugins report TvType.Movie instead of TvType.NSFW,
 * which enables Cloudstream's built-in watch progress tracking.
 *
 * Storage keys: nsfw_common/watch_history/global, nsfw_common/watch_history/plugin/{name}
 */
class WatchHistoryConfig(
    private val getKey: (String) -> String?,
    private val setKey: (String, String?) -> Unit
) {
    companion object {
        private const val PREFIX = "nsfw_common/watch_history"
        private const val GLOBAL_KEY = "$PREFIX/global"
        private fun pluginKey(pluginName: String) = "$PREFIX/plugin/$pluginName"
    }

    fun isEnabled(pluginName: String): Boolean {
        // Per-plugin override takes priority
        val override = getKey(pluginKey(pluginName))
        if (override != null) return override.toBoolean()
        // Fall back to global setting
        return getKey(GLOBAL_KEY)?.toBoolean() ?: false
    }

    fun setGlobalEnabled(enabled: Boolean) {
        setKey(GLOBAL_KEY, enabled.toString())
    }

    fun setPluginOverride(pluginName: String, enabled: Boolean) {
        setKey(pluginKey(pluginName), enabled.toString())
    }

    fun clearPluginOverride(pluginName: String) {
        setKey(pluginKey(pluginName), null)
    }

    fun isGlobalEnabled(): Boolean = getKey(GLOBAL_KEY)?.toBoolean() ?: false

    fun getPluginOverride(pluginName: String): Boolean? =
        getKey(pluginKey(pluginName))?.toBoolean()
}
