package com.lagradost.common.cache

/**
 * User-selectable cache aggressiveness level.
 */
enum class CacheLevel {
    MINIMAL, BALANCED, AGGRESSIVE;

    companion object {
        fun fromString(value: String): CacheLevel =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: MINIMAL
    }
}

/**
 * Configuration for the 3-tier cache.
 * Created from a [CacheLevel] — users pick a level, not individual values.
 */
class CacheConfig internal constructor(
    val level: CacheLevel,
    val memoryMaxEntries: Int,
    val diskMaxBytes: Long,
    val pageTtlMs: Long,
    val searchTtlMs: Long
) {
    init {
        require(memoryMaxEntries > 0) { "memoryMaxEntries must be positive" }
        require(diskMaxBytes >= 0) { "diskMaxBytes must be non-negative" }
        require(pageTtlMs > 0) { "pageTtlMs must be positive" }
        require(searchTtlMs > 0) { "searchTtlMs must be positive" }
    }

    /** Whether disk cache is enabled */
    val diskEnabled: Boolean get() = diskMaxBytes > 0

    companion object {
        const val CACHE_LEVEL_KEY = "nsfw_common/cache_level"
        const val DEFAULT_LEVEL = "BALANCED"

        fun forLevel(level: CacheLevel): CacheConfig = when (level) {
            CacheLevel.MINIMAL -> CacheConfig(
                level = level,
                memoryMaxEntries = 20,
                diskMaxBytes = 0L,
                pageTtlMs = 2 * 60 * 1000L,
                searchTtlMs = 60 * 1000L
            )
            CacheLevel.BALANCED -> CacheConfig(
                level = level,
                memoryMaxEntries = 50,
                diskMaxBytes = 25 * 1024 * 1024L,
                pageTtlMs = 15 * 60 * 1000L,
                searchTtlMs = 5 * 60 * 1000L
            )
            CacheLevel.AGGRESSIVE -> CacheConfig(
                level = level,
                memoryMaxEntries = 100,
                diskMaxBytes = 100 * 1024 * 1024L,
                pageTtlMs = 60 * 60 * 1000L,
                searchTtlMs = 15 * 60 * 1000L
            )
        }
    }
}
