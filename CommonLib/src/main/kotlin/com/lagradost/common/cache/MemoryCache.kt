package com.lagradost.common.cache

/**
 * Thread-safe in-memory LRU cache with TTL expiry.
 * Keyed by URL string, values are deserialized response objects.
 */
class MemoryCache<T>(
    private val maxEntries: Int,
    private val defaultTtlMs: Long
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(defaultTtlMs > 0) { "defaultTtlMs must be positive" }
    }

    private data class Entry<T>(val value: T, val expiresAt: Long)

    // LinkedHashMap with accessOrder=true gives LRU behavior
    private val map = object : LinkedHashMap<String, Entry<T>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<T>>): Boolean {
            return size > maxEntries
        }
    }

    val size: Int get() = synchronized(map) { map.size }

    fun get(key: String): T? = synchronized(map) {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            map.remove(key)
            return null
        }
        entry.value
    }

    fun put(key: String, value: T, ttlMs: Long = defaultTtlMs) = synchronized(map) {
        require(ttlMs > 0) { "ttlMs must be positive" }
        map[key] = Entry(value, System.currentTimeMillis() + ttlMs)
    }

    fun invalidate(key: String) {
        synchronized(map) { map.remove(key) }
    }

    fun clear() = synchronized(map) {
        map.clear()
    }
}
