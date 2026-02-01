package com.lagradost

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.common.intelligence.TagClassifier
import com.lagradost.common.intelligence.TagNormalizer
import com.lagradost.common.intelligence.TagType
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Classifies unknown tags by checking FreeOnes.com for performer/category/channel pages.
 *
 * Results are permanently cached in SharedPreferences under `nsfw_common/freeones_cache/`.
 * Tags already known by [TagNormalizer] are returned immediately without HTTP lookups.
 * Unknown tags are queued for async validation via [validatePending].
 */
class FreeOnesTagClassifier(private val context: Context) : TagClassifier {

    companion object {
        private const val TAG = "FreeOnesClassifier"
        private const val CACHE_PREFIX = "nsfw_common/freeones_cache/"
        private const val FREEONES_BASE = "https://www.freeones.com"
        private const val MAX_RETRY_ATTEMPTS = 2
    }

    private val prefs by lazy {
        context.getSharedPreferences("rebuild_preference", Context.MODE_PRIVATE)
    }

    private val pendingTags = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val pendingTagsSeen = ConcurrentHashMap<String, Boolean>()
    private val retryAttempts = ConcurrentHashMap<String, Int>()
    private val validationSemaphore = Semaphore(3)

    /**
     * Classify a tag. Returns immediately from cache or TagNormalizer.
     * If the tag is OTHER per TagNormalizer and not cached, queues it for async validation.
     */
    override fun classify(tag: String): TagType? {
        val normalized = TagNormalizer.normalize(tag)
        if (normalized.type != TagType.OTHER) return normalized.type

        // Check cache
        val cached = prefs.getString("$CACHE_PREFIX${toSlug(tag)}", null)
        if (cached != null) {
            return when (cached) {
                "performer" -> TagType.PERFORMER
                "category" -> TagType.GENRE
                "channel" -> TagType.STUDIO
                "none" -> TagType.OTHER
                else -> TagType.OTHER
            }
        }

        // Queue for async validation (atomic check-then-add)
        val key = tag.lowercase()
        if (pendingTagsSeen.putIfAbsent(key, true) == null) {
            pendingTags.add(key)
        }
        return null // null = fall back to TagNormalizer
    }

    /**
     * Validate all pending tags against FreeOnes. Call from a coroutine context.
     * Uses a semaphore to limit concurrent HTTP requests.
     */
    suspend fun validatePending() = withContext(Dispatchers.IO) {
        val batch = mutableListOf<String>()
        while (true) {
            val tag = pendingTags.poll() ?: break
            batch.add(tag)
        }
        if (batch.isEmpty()) return@withContext

        Log.d(TAG, "Validating ${batch.size} pending tags against FreeOnes")

        val editor = prefs.edit()
        try {
            for (tag in batch) {
                validationSemaphore.withPermit {
                    try {
                        val slug = toSlug(tag)
                        val result = checkFreeOnes(slug)
                        synchronized(editor) { editor.putString("$CACHE_PREFIX$slug", result) }
                        Log.d(TAG, "Classified '$tag' (slug=$slug) as: $result")
                        retryAttempts.remove(tag)
                        pendingTagsSeen.remove(tag)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to validate tag '$tag': ${e.message}")
                        val attempts = retryAttempts.merge(tag, 1) { old, inc -> old + inc } ?: 1
                        if (attempts < MAX_RETRY_ATTEMPTS) {
                            pendingTags.add(tag)
                        } else {
                            Log.w(TAG, "Giving up on tag '$tag' after $attempts attempts, caching as 'none'")
                            synchronized(editor) { editor.putString("$CACHE_PREFIX${toSlug(tag)}", "none") }
                            retryAttempts.remove(tag)
                            pendingTagsSeen.remove(tag)
                        }
                    }
                }
            }
        } finally {
            // Always persist partial results even if cancelled mid-batch
            editor.apply()
        }
    }

    /**
     * Check FreeOnes for performer, category, or channel page.
     * Returns "performer", "category", "channel", or "none".
     * Throws on network errors so the retry mechanism in [validatePending] can re-queue.
     */
    private suspend fun checkFreeOnes(slug: String): String {
        val checks = listOf(
            "" to "performer",       // /slug (most common for unknown tags)
            "category" to "category",
            "channel" to "channel"
        )
        var networkFailures = 0

        for ((path, label) in checks) {
            val url = if (path.isEmpty()) "$FREEONES_BASE/$slug" else "$FREEONES_BASE/$path/$slug"
            try {
                val resp = app.get(url, allowRedirects = false)
                if (resp.code == 200) return label
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${label.replaceFirstChar { it.uppercase() }} check failed for '$slug': ${e.message}")
                networkFailures++
            }
        }

        // If ANY check failed due to network error, we can't be sure the tag isn't that type.
        // Throw so the retry mechanism can re-queue rather than permanently caching "none".
        if (networkFailures > 0) {
            throw java.io.IOException("$networkFailures/${checks.size} FreeOnes checks failed for '$slug' (network error)")
        }
        return "none"
    }

    private fun toSlug(tag: String): String =
        tag.lowercase().trim().replace(Regex("\\s+"), "-")
}
