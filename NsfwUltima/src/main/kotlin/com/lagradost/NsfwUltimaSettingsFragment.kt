package com.lagradost

import com.lagradost.common.CloudstreamUI
import com.lagradost.common.ContentOrientationConfig
import com.lagradost.common.SmartFeaturesConfig
import com.lagradost.common.TvFocusUtils
import com.lagradost.common.WatchHistoryConfig
import com.lagradost.common.cache.CacheConfig
import com.lagradost.common.cache.CacheLevel
import com.lagradost.common.intelligence.BrowsingIntelligence
import com.lagradost.common.intelligence.InfluenceLevel
import com.lagradost.common.intelligence.TagAffinity
import com.lagradost.common.intelligence.TagNormalizer
import com.lagradost.common.intelligence.TagType

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.lagradost.common.SafeNestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings fragment for NSFW Ultima with homepage-centric design.
 * Displays homepages alphabetically sorted with tap-to-edit functionality.
 *
 * Architecture: Uses repository pattern and use cases for clean separation:
 * - HomepageRepository: Abstracts storage access
 * - SaveHomepageUseCase/DeleteHomepageUseCase: Coordinate business logic
 * - FeedAssignmentService: Pure domain logic for feed transformations
 */
class NsfwUltimaSettingsFragment(
    private val plugin: NsfwUltimaPlugin,
    private val repository: HomepageRepository = CloudstreamHomepageRepository()
) : DialogFragment() {

    companion object {
        private const val TAG = "NsfwUltimaSettings"
    }

    // Use cases
    private val saveHomepageUseCase = SaveHomepageUseCase(repository)
    private val deleteHomepageUseCase = DeleteHomepageUseCase(repository)
    private val loadDataUseCase = LoadHomepageDataUseCase(repository)
    private val resetDataUseCase = ResetAllDataUseCase(repository)

    // State
    private var feedList = mutableListOf<FeedItem>()
    private var settings = NsfwUltimaSettings()
    private var homepages = mutableListOf<Homepage>()

    private lateinit var mainContainer: LinearLayout
    private lateinit var homepageAdapter: HomepageListAdapter
    private lateinit var homepageRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView

    // Tab containers — lazily built on first visit
    private lateinit var homepagesContent: LinearLayout
    private lateinit var forYouContent: LinearLayout
    private lateinit var settingsContent: LinearLayout
    private var forYouBuilt = false
    private var settingsBuilt = false

    // Dirty tracking (moved from RecommendationDataFragment)
    private var dirty = false

    // Recommendation data state
    private var pendingUndo: (() -> Unit)? = null
    private var pendingCommit: (() -> Unit)? = null
    private var searchEditText: EditText? = null
    private var debounceRunnable: Runnable? = null

    private lateinit var tagsSection: LinearLayout
    private lateinit var performersSection: LinearLayout
    private lateinit var tagsHeader: TextView
    private lateinit var performersHeader: TextView

    private var affinityTags = mutableListOf<TagAffinity>()
    private var affinityPerformers = mutableListOf<TagAffinity>()
    private var tagsCollapsed = false
    private var performersCollapsed = true
    private var searchQuery = ""

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    private val watchHistoryConfig by lazy {
        WatchHistoryConfig(
            getKey = { key -> getKey(key) },
            setKey = { key, value -> setKey(key, value) }
        )
    }

    private val smartFeaturesConfig by lazy {
        SmartFeaturesConfig(
            getKey = { key -> getKey(key) },
            setKey = { key, value -> setKey(key, value) }
        )
    }

    private val contentOrientationConfig by lazy {
        ContentOrientationConfig(
            getKey = { key -> getKey(key) },
            setKey = { key, value -> setKey(key, value) }
        )
    }

    // Theme colors using CloudstreamUI
    private lateinit var colors: CloudstreamUI.UIColors
    private val textColor get() = colors.text
    private val grayTextColor get() = colors.textGray
    private val backgroundColor get() = colors.background
    private val cardColor get() = colors.card
    private val primaryColor get() = colors.primary

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = CloudstreamUI.UIColors.fromContext(context)
        loadData()

        val contentView = createSettingsView(context)

        return if (isTvMode) {
            AlertDialog.Builder(context, theme)
                .setView(contentView)
                .create().apply {
                    window?.apply {
                        setLayout(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.WRAP_CONTENT
                        )
                    }
                }
        } else {
            BottomSheetDialog(context, theme).apply {
                setContentView(contentView)
                behavior.apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = null

    override fun onStart() {
        super.onStart()
        if (isTvMode) {
            dialog?.window?.decorView?.post {
                if (isAdded && ::mainContainer.isInitialized && mainContainer.isAttachedToWindow) {
                    TvFocusUtils.requestInitialFocus(mainContainer)
                }
            }
        }
    }

    private fun loadData() {
        val data = loadDataUseCase.execute()
        settings = data.settings
        feedList = data.feeds.toMutableList()
        homepages = data.homepages.toMutableList()

        Log.d(TAG, "Loaded ${feedList.size} feeds, ${homepages.size} homepages")
    }

    private fun createSettingsView(context: Context): View {
        val scrollView = SafeNestedScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(backgroundColor)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24))
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        // Title
        mainContainer.addView(CloudstreamUI.createDialogTitle(context, "NSFW Ultima Settings", colors).apply {
            setPadding(0, 0, 0, dp(context, 16))
        })

        // Pill toggle group for tabs
        val tabLabels = listOf("Homepages", "For You", "Settings")
        mainContainer.addView(CloudstreamUI.createPillToggleGroup(context, tabLabels, 0) { index ->
            switchTab(context, index)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 16) }
        })

        // Tab containers
        homepagesContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        mainContainer.addView(homepagesContent)

        forYouContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        mainContainer.addView(forYouContent)

        settingsContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        mainContainer.addView(settingsContent)

        // Build the initial tab
        buildHomepagesTab(context)

        scrollView.addView(mainContainer)

        return scrollView
    }

    private fun switchTab(context: Context, index: Int) {
        homepagesContent.visibility = if (index == 0) View.VISIBLE else View.GONE
        forYouContent.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsContent.visibility = if (index == 2) View.VISIBLE else View.GONE

        if (index == 1 && !forYouBuilt) {
            forYouBuilt = true
            buildForYouTab(context)
        }
        if (index == 2 && !settingsBuilt) {
            settingsBuilt = true
            buildSettingsTab(context)
        }

        if (isTvMode) {
            val activeContainer = when (index) {
                0 -> homepagesContent
                1 -> forYouContent
                2 -> settingsContent
                else -> return
            }
            activeContainer.post {
                if (isAdded && activeContainer.isAttachedToWindow) {
                    TvFocusUtils.requestInitialFocus(activeContainer)
                }
            }
        }
    }

    private fun buildHomepagesTab(context: Context) {
        // Settings card
        homepagesContent.addView(createSettingsCard(context))

        // Your Homepages header
        homepagesContent.addView(CloudstreamUI.createTitleText(context, "Your Homepages", colors).apply {
            setPadding(0, dp(context, 20), 0, dp(context, 8))
        })

        // Subtitle
        homepagesContent.addView(CloudstreamUI.createCaptionText(context, "Tap to edit. Sorted alphabetically. App restart required for changes.", colors).apply {
            textSize = 13f
            setPadding(0, 0, 0, dp(context, 12))
        })

        // Empty state text
        emptyStateText = CloudstreamUI.createEmptyState(context, "No homepages yet.\nCreate one below to get started.", colors).apply {
            visibility = if (homepages.isEmpty()) View.VISIBLE else View.GONE
        }
        homepagesContent.addView(emptyStateText)

        // Homepage list RecyclerView
        homepageRecyclerView = createHomepageList(context)
        homepagesContent.addView(homepageRecyclerView)

        // Create Homepage button
        homepagesContent.addView(CloudstreamUI.createPrimaryButton(context, "Create New Homepage", colors) {
            showHomepageEditor(null)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 16)
            }
        })

        // Reset All Data button
        homepagesContent.addView(CloudstreamUI.createSecondaryButton(context, "Reset All Data", colors) {
            if (isAdded) showResetConfirmation()
        }.apply {
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 12)
            }
        })

        if (isTvMode) {
            TvFocusUtils.enableFocusLoopWithRecyclerView(homepagesContent, homepageRecyclerView)
        }
    }

    private fun buildForYouTab(context: Context) {
        val prefix = "nsfw_common/homepage_prefs"

        // Homepage Sections header
        forYouContent.addView(CloudstreamUI.createTitleText(context, "Homepage Sections", colors).apply {
            setPadding(0, 0, 0, dp(context, 4))
        })
        forYouContent.addView(CloudstreamUI.createCaptionText(
            context,
            "Toggle sections on the 'For You' homepage.",
            colors
        ).apply { setPadding(0, 0, 0, dp(context, 12)) })

        // Section toggles
        val sections = listOf(
            SectionToggle("favorites_recs", "Because You Favorited", "Content matching your favorite videos' tags", true),
            SectionToggle("recommended", "Recommended For You", "Based on your viewing patterns", true),
            SectionToggle("top_tags", "Top Tags", "Your most-viewed categories", true),
            SectionToggle("discover", "Discover New Sites", "Content from plugins you rarely use", false)
        )

        for (section in sections) {
            val card = CloudstreamUI.createCard(context, colors, CloudstreamUI.Dimens.CORNER_RADIUS_LARGE)
            card.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 8) }

            val isEnabled = getKey<String>("$prefix/${section.key}")?.toBoolean() ?: section.defaultEnabled
            val row = createToggleRow(context, section.title, section.subtitle, isEnabled) { checked ->
                setKey("$prefix/${section.key}", checked.toString())
                dirty = true
            }
            row.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))

            card.addView(row)
            forYouContent.addView(card)
        }

        // Display section
        forYouContent.addView(CloudstreamUI.createTitleText(context, "Display", colors).apply {
            setPadding(0, dp(context, 20), 0, dp(context, 8))
        })

        val displayCard = CloudstreamUI.createCard(context, colors, CloudstreamUI.Dimens.CORNER_RADIUS_LARGE)
        displayCard.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 8) }

        val displayContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        }

        // Content Density
        displayContainer.addView(CloudstreamUI.createCaptionText(context, "Content Density", colors).apply {
            setPadding(0, 0, 0, dp(context, 8))
        })

        val densityLabels = listOf("Minimal", "Balanced", "Full")
        val currentDensity = getKey<String>("$prefix/density")?.toIntOrNull() ?: 1
        displayContainer.addView(CloudstreamUI.createPillToggleGroup(context, densityLabels, currentDensity) { index ->
            setKey("$prefix/density", index.toString())
            dirty = true
        })

        // Deduplicate Videos toggle
        displayContainer.addView(createToggleRow(
            context,
            "Deduplicate Videos",
            null,
            getKey<String>("$prefix/deduplicate")?.toBoolean() ?: true
        ) { checked ->
            setKey("$prefix/deduplicate", checked.toString())
            dirty = true
        }.apply {
            setPadding(0, dp(context, 12), 0, 0)
        })

        displayCard.addView(displayContainer)
        forYouContent.addView(displayCard)

        // Recommendation Data section
        forYouContent.addView(CloudstreamUI.createTitleText(context, "Recommendation Data", colors).apply {
            setPadding(0, dp(context, 20), 0, dp(context, 4))
        })
        forYouContent.addView(CloudstreamUI.createCaptionText(
            context,
            "Manage tags and performers used for personalized recommendations.",
            colors
        ).apply { setPadding(0, 0, 0, dp(context, 12)) })

        // Search bar
        forYouContent.addView(createRecommendationSearchBar(context))

        // Tags section
        tagsSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        tagsHeader = createRecommendationSectionHeader(context, "Tags", affinityTags.size, tagsCollapsed) {
            tagsCollapsed = !tagsCollapsed
            rebuildTagsSection(context)
        }
        forYouContent.addView(createCollapsibleSection(context, tagsHeader, tagsSection, "Clear All") {
            showRecommendationClearConfirmation("tag data") { clearTags() }
        })

        // Performers section
        performersSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        performersHeader = createRecommendationSectionHeader(context, "Performers", affinityPerformers.size, performersCollapsed) {
            performersCollapsed = !performersCollapsed
            rebuildPerformersSection(context)
        }
        forYouContent.addView(createCollapsibleSection(context, performersHeader, performersSection, "Clear All") {
            showRecommendationClearConfirmation("performer data") { clearPerformers() }
        })

        // Load data asynchronously
        loadRecommendationData(context)

        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(forYouContent)
        }
    }

    private data class SectionToggle(val key: String, val title: String, val subtitle: String, val defaultEnabled: Boolean)

    private fun buildSettingsTab(context: Context) {
        // Watch History section
        settingsContent.addView(createSectionCard(context, "Watch History", buildWatchHistoryContent(context)))

        // Cache Level section
        settingsContent.addView(createSectionCard(context, "Cache Level", buildCacheLevelContent(context)))

        // Smart Features section
        settingsContent.addView(createSectionCard(context, "Smart Features", buildSmartFeaturesContent(context)))

        // Content Preferences section
        settingsContent.addView(createSectionCard(context, "Content Preferences", buildContentPreferencesContent(context)))

        // Divider
        settingsContent.addView(CloudstreamUI.createDivider(context, colors).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 16) }
        })

        // Clear Browsing Data button
        settingsContent.addView(CloudstreamUI.createDangerButton(context, "Clear Browsing Data", colors) {
            if (!isAdded) return@createDangerButton
            showClearDataConfirmation()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 16) }
        })

        // Factory Reset button
        settingsContent.addView(CloudstreamUI.createDangerButton(context, "Factory Reset", colors) {
            if (!isAdded) return@createDangerButton
            showFactoryResetConfirmation()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 8) }
        })

        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(settingsContent)
        }
    }

    private fun createSectionCard(context: Context, title: String, content: View): View {
        val card = CloudstreamUI.createCard(context, colors, CloudstreamUI.Dimens.CORNER_RADIUS_LARGE)
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 12) }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        }

        container.addView(CloudstreamUI.createTitleText(context, title, colors).apply {
            setPadding(0, 0, 0, dp(context, 8))
        })
        container.addView(content)

        card.addView(container)
        return card
    }

    private fun buildWatchHistoryContent(context: Context): View {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        container.addView(CloudstreamUI.createCaptionText(
            context,
            "Enable to track watch progress via Cloudstream's built-in history. Changes TvType from NSFW to Movie.",
            colors
        ).apply { setPadding(0, 0, 0, dp(context, 8)) })

        container.addView(createToggleRow(context, "Enable for all plugins", null,
            watchHistoryConfig.isGlobalEnabled()
        ) { checked ->
            watchHistoryConfig.setGlobalEnabled(checked)
        })

        return container
    }

    private fun buildCacheLevelContent(context: Context): View {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val currentLevel = CacheLevel.fromString(getKey(CacheConfig.CACHE_LEVEL_KEY) ?: CacheConfig.DEFAULT_LEVEL)
        val levels = CacheLevel.entries.toList()
        val labels = listOf("Minimal", "Balanced", "Aggressive")

        container.addView(CloudstreamUI.createCaptionText(
            context,
            "Controls how aggressively pages and search results are cached.",
            colors
        ).apply { setPadding(0, 0, 0, dp(context, 8)) })

        val detailsText = CloudstreamUI.createCaptionText(context, "", colors).apply {
            setPadding(0, dp(context, 8), 0, 0)
        }

        fun updateDetails(level: CacheLevel) {
            val config = CacheConfig.forLevel(level)
            detailsText.text = buildString {
                append("Memory: ${config.memoryMaxEntries} entries")
                if (config.diskEnabled) append(" • Disk: ${config.diskMaxBytes / 1024 / 1024}MB")
                append(" • Page TTL: ${config.pageTtlMs / 60000}min")
            }
        }
        updateDetails(currentLevel)

        container.addView(CloudstreamUI.createPillToggleGroup(
            context, labels, levels.indexOf(currentLevel)
        ) { index ->
            val level = levels[index]
            setKey(CacheConfig.CACHE_LEVEL_KEY, level.name)
            updateDetails(level)
        })

        container.addView(detailsText)

        return container
    }

    private fun buildSmartFeaturesContent(context: Context): View {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        container.addView(CloudstreamUI.createCaptionText(
            context,
            "Toggle intelligent features powered by your local browsing data.",
            colors
        ).apply { setPadding(0, 0, 0, dp(context, 8)) })

        container.addView(createToggleRow(context, "Search Suggestions",
            "Autocomplete from your search history",
            smartFeaturesConfig.searchSuggestionsEnabled
        ) { checked -> smartFeaturesConfig.searchSuggestionsEnabled = checked })

        container.addView(createToggleRow(context, "Tag Recommendations",
            "Recommend content based on your viewing patterns",
            smartFeaturesConfig.tagRecommendationsEnabled
        ) { checked -> smartFeaturesConfig.tagRecommendationsEnabled = checked })

        return container
    }

    private fun buildContentPreferencesContent(context: Context): View {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        container.addView(CloudstreamUI.createCaptionText(
            context,
            "Filter recommendations by content type.",
            colors
        ).apply { setPadding(0, 0, 0, dp(context, 8)) })

        val warningText = CloudstreamUI.createCaptionText(
            context,
            "No content types selected — recommendations will be empty",
            colors
        ).apply {
            setPadding(0, dp(context, 8), 0, 0)
            setTextColor(CloudstreamUI.Colors.ERROR)
            visibility = View.GONE
        }

        fun updateWarning() {
            warningText.visibility = if (
                !contentOrientationConfig.straightEnabled &&
                !contentOrientationConfig.gayEnabled &&
                !contentOrientationConfig.lesbianEnabled &&
                !contentOrientationConfig.transEnabled
            ) View.VISIBLE else View.GONE
        }

        val chipGroup = CloudstreamUI.createChipGroup(context, isSingleSelection = false)

        val chips = listOf(
            "Straight" to contentOrientationConfig.straightEnabled,
            "Gay" to contentOrientationConfig.gayEnabled,
            "Lesbian" to contentOrientationConfig.lesbianEnabled,
            "Trans" to contentOrientationConfig.transEnabled
        )

        for ((label, isChecked) in chips) {
            chipGroup.addView(CloudstreamUI.createFilterChip(context, label, isChecked, colors) { checked ->
                when (label) {
                    "Straight" -> contentOrientationConfig.straightEnabled = checked
                    "Gay" -> contentOrientationConfig.gayEnabled = checked
                    "Lesbian" -> contentOrientationConfig.lesbianEnabled = checked
                    "Trans" -> contentOrientationConfig.transEnabled = checked
                }
                updateWarning()
            })
        }

        container.addView(chipGroup)
        container.addView(warningText)
        return container
    }

    private fun showClearDataConfirmation() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle("Clear Browsing Data")
            .setMessage("This will remove all search history and tag data used for recommendations. Cache will also be cleared.")
            .setPositiveButton("Clear") { _, _ ->
                val innerCtx = context ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val intelligence = com.lagradost.common.intelligence.BrowsingIntelligence.getInstance(innerCtx)
                        val cleared: Boolean = if (intelligence != null) withContext(Dispatchers.IO) {
                            intelligence.clearAllPausedState()
                            intelligence.clearAll()
                        } else true
                        if (isAdded) {
                            val msg = if (cleared) "Browsing data cleared" else "Failed to clear browsing data"
                            Toast.makeText(innerCtx, msg, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to clear browsing data", e)
                        val errCtx = context ?: return@launch
                        if (isAdded) Toast.makeText(errCtx, "Failed to clear data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFactoryResetConfirmation() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle("Factory Reset")
            .setMessage("This will erase ALL NsfwUltima data: browsing intelligence, paused tags, feed lists, groups, plugin states, homepage preferences, smart features, content preferences, watch history, and cache settings.\n\nThis cannot be undone.")
            .setPositiveButton("Reset Everything") { _, _ ->
                val resetCtx = context ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val intelligence = com.lagradost.common.intelligence.BrowsingIntelligence.getInstance(resetCtx)
                            if (intelligence != null) {
                                intelligence.clearAllPausedState()
                                if (!intelligence.clearAll()) {
                                    Log.w(TAG, "Failed to clear browsing intelligence during factory reset")
                                }
                            }

                            for (key in listOf(
                                "NSFWULTIMA_FEED_LIST", "NSFWULTIMA_SETTINGS", "NSFWULTIMA_GROUPS",
                                "PAGEMANAGER_FEED_LIST", "PAGEMANAGER_SETTINGS", "PAGEMANAGER_PLUGIN_STATES"
                            )) {
                                setKey(key, null)
                            }

                            for (k in listOf(
                                "favorites_recs", "recommended",
                                "top_tags", "popular", "discover", "density", "deduplicate"
                            )) {
                                setKey("nsfw_common/homepage_prefs/$k", null)
                            }

                            for (k in listOf(
                                "search_suggestions", "recently_viewed", "tag_recommendations", "cross_plugin_recs"
                            )) {
                                setKey("nsfw_common/smart_features/$k", null)
                            }
                            for (k in listOf("straight", "gay", "lesbian", "trans")) {
                                setKey("nsfw_common/content_orientation/$k", null)
                            }
                            setKey("nsfw_common/watch_history/global", null)
                            setKey("nsfw_common/cache_level", null)
                        }

                        NsfwUltimaPlugin.instance?.forYouProvider?.invalidateMainPageCache()

                        if (isAdded) {
                            val toastCtx = context ?: return@launch
                            Toast.makeText(toastCtx, "Factory reset complete", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Factory reset failed", e)
                        val errCtx = context ?: return@launch
                        if (isAdded) Toast.makeText(errCtx, "Factory reset failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createSettingsCard(context: Context): com.google.android.material.card.MaterialCardView {
        val card = CloudstreamUI.createCard(context, colors, CloudstreamUI.Dimens.CORNER_RADIUS_LARGE)

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        }

        // Show plugin names toggle
        cardContent.addView(createToggleRow(
            context,
            title = "Show Plugin Names",
            subtitle = "Prefix feeds with [PluginName]",
            isChecked = settings.showPluginNames
        ) { isChecked ->
            val originalSettings = settings
            settings = settings.copy(showPluginNames = isChecked)
            if (!repository.saveSettings(settings)) {
                Log.e(TAG, "Failed to save settings: showPluginNames=$isChecked (attempted change from ${originalSettings.showPluginNames})")
                settings = originalSettings  // Revert in-memory state
                showSaveErrorToast()
                return@createToggleRow
            }
            plugin.refreshAllHomepages()
        })

        card.addView(cardContent)
        return card
    }

    private fun createToggleRow(
        context: Context,
        title: String,
        subtitle: String?,
        isChecked: Boolean,
        onChanged: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 4), 0, dp(context, 4))
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        textContainer.addView(CloudstreamUI.createBodyText(context, title, colors).apply {
            textSize = 15f
        })

        if (subtitle != null) {
            textContainer.addView(CloudstreamUI.createCaptionText(context, subtitle, colors))
        }

        row.addView(textContainer)
        row.addView(CloudstreamUI.createSwitch(context, isChecked, colors) { checked -> onChanged(checked) })
        return row
    }

    private fun createHomepageList(context: Context): RecyclerView {
        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            isNestedScrollingEnabled = false
            visibility = if (homepages.isEmpty()) View.GONE else View.VISIBLE
        }

        homepageAdapter = HomepageListAdapter(
            context = context,
            isTvMode = isTvMode,
            textColor = textColor,
            grayTextColor = grayTextColor,
            primaryColor = primaryColor,
            cardColor = cardColor,
            onHomepageClick = { homepage ->
                showHomepageEditor(homepage)
            },
            getFeedCount = { homepageId ->
                FeedAssignmentService.getFeedsInHomepage(feedList, homepageId).size
            }
        )
        recyclerView.adapter = homepageAdapter

        // Display homepages sorted alphabetically
        homepageAdapter.submitList(homepages.sortedBy { it.name.lowercase() })

        return recyclerView
    }

    private fun showHomepageEditor(existingHomepage: Homepage?) {
        val availableFeeds = getAvailableFeeds()

        val dialog = HomepageEditorDialog(
            existingGroup = existingHomepage,
            currentFeeds = feedList,
            availableFeeds = availableFeeds,
            showPluginNames = settings.showPluginNames,
            onSave = onSave@{ group, _, allFeeds ->
                // Fragment may detach while dialog is open
                if (!isAdded) return@onSave
                val isNew = existingHomepage == null

                // Dialog already computed the final feeds using FeedAssignmentService
                // We just need to update local state and persist via repository

                // Update local state
                if (isNew) {
                    homepages.add(group)
                } else {
                    val index = homepages.indexOfFirst { it.id == group.id }
                    if (index >= 0) {
                        homepages[index] = group
                    }
                }
                feedList.clear()
                feedList.addAll(allFeeds)

                // Persist via repository
                val feedsSaved = repository.saveFeeds(feedList)
                val groupsSaved = repository.saveHomepages(homepages)

                if (!feedsSaved || !groupsSaved) {
                    Log.e(TAG, "Failed to save homepage '${group.name}' (id=${group.id}): feeds=$feedsSaved (${feedList.size} total), groups=$groupsSaved (${homepages.size} total), isNew=$isNew")
                    // Reload to restore consistent state
                    loadData()
                    showSaveErrorToast()
                    if (!isAdded) return@onSave
                    refreshUI()
                    return@onSave
                }

                // Re-check lifecycle before UI operations
                if (!isAdded) return@onSave
                refreshUI()
                plugin.refreshAllHomepages()

                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        if (isNew) "Homepage created. Restart app to see it." else "Changes saved. Restart app to see updates.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDelete = if (existingHomepage != null) { { homepage ->
                deleteHomepage(homepage)
            } } else null
        )
        dialog.show(parentFragmentManager, "HomepageEditorDialog")
    }

    private fun deleteHomepage(homepage: Homepage) {
        // Fragment may detach while dialog is open
        if (!isAdded) return
        // Use the delete use case for coordinated deletion
        when (val result = deleteHomepageUseCase.execute(homepage.id, homepages, feedList)) {
            is UseCaseResult.Success -> {
                homepages.clear()
                homepages.addAll(result.data.updatedHomepages)
                feedList.clear()
                feedList.addAll(result.data.updatedFeeds)

                // Re-check lifecycle before UI operations
                if (!isAdded) return
                refreshUI()
                plugin.refreshAllHomepages()

                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Homepage deleted. Restart app to see changes.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            is UseCaseResult.Failure -> {
                Log.e(TAG, "Delete homepage failed for '${homepage.name}' (id=${homepage.id}): ${result.error}")
                showSaveErrorToast()
            }
        }
    }

    private fun getAvailableFeeds(): List<AvailableFeed> {
        val feeds = mutableListOf<AvailableFeed>()
        val nsfwProviders = allProviders.filter { api ->
            api.supportedTypes.contains(TvType.NSFW) && !api.name.startsWith("NSFW Ultima")
        }

        nsfwProviders.forEach { api ->
            try {
                api.mainPage.forEach { mainPageData ->
                    feeds.add(AvailableFeed(
                        pluginName = api.name,
                        sectionName = mainPageData.name,
                        sectionData = mainPageData.data
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting feeds from ${api.name}", e)
            }
        }

        return feeds.distinctBy { it.key() }
    }

    private fun refreshUI() {
        // Force content refresh because feed counts may have changed
        homepageAdapter.submitList(homepages.sortedBy { it.name.lowercase() }, forceContentRefresh = true)
        updateEmptyState()
        if (isTvMode) {
            TvFocusUtils.enableFocusLoopWithRecyclerView(homepagesContent, homepageRecyclerView)
        }
    }

    private fun updateEmptyState() {
        val isEmpty = homepages.isEmpty()
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        homepageRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showSaveErrorToast() {
        if (isAdded) {
            Toast.makeText(
                requireContext(),
                "Failed to save changes. Please try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showResetConfirmation() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Reset All Data")
            .setMessage("This will remove all your homepages, feeds, and settings. Are you sure?")
            .setPositiveButton("Reset") { _, _ ->
                // Fragment may detach while dialog is open
                if (!isAdded) return@setPositiveButton
                when (val result = resetDataUseCase.execute()) {
                    is UseCaseResult.Success -> {
                        feedList.clear()
                        homepages.clear()
                        settings = NsfwUltimaSettings()

                        homepageAdapter.submitList(emptyList())
                        updateEmptyState()
                        plugin.refreshAllHomepages()

                        Log.d(TAG, "All data reset")
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "All data reset successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    is UseCaseResult.Failure -> {
                        Log.e(TAG, "Failed to reset all data: ${result.error}")
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to reset data. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Recommendation Data helpers ---

    private fun getIntelligence(): BrowsingIntelligence? {
        val ctx = context ?: return null
        return BrowsingIntelligence.getInstance(ctx)
    }

    private fun loadRecommendationData(context: Context) {
        val appContext = context.applicationContext
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val intelligence = BrowsingIntelligence.getInstance(appContext) ?: return@withContext null
                val classifier = intelligence.tagClassifier
                val tags = intelligence.getAllAffinityTags().filter {
                    val type = classifier?.classify(it.tag) ?: TagNormalizer.normalize(it.tag).type
                    type == TagType.GENRE || type == TagType.BODY_TYPE
                }.toMutableList()
                val performers = intelligence.getAllAffinityPerformers().toMutableList()
                Pair(tags, performers)
            }
            if (isAdded && data != null) {
                affinityTags = data.first
                affinityPerformers = data.second
                val ctx = this@NsfwUltimaSettingsFragment.context ?: return@launch
                rebuildTagsSection(ctx)
                rebuildPerformersSection(ctx)
            }
        }
    }

    private fun createRecommendationSearchBar(context: Context): View {
        return EditText(context).apply {
            searchEditText = this
            hint = "Search tags & performers..."
            setTextColor(colors.text)
            setHintTextColor(colors.textGray)
            textSize = 14f
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            background = GradientDrawable().also { gd ->
                gd.setColor(colors.card)
                gd.cornerRadius = dp(context, 8).toFloat()
                gd.setStroke(dp(context, 1), androidx.core.graphics.ColorUtils.setAlphaComponent(colors.textGray, 100))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 8) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    debounceRunnable?.let { removeCallbacks(it) }
                    debounceRunnable = Runnable {
                        searchQuery = s?.toString()?.lowercase() ?: ""
                        if (!isAdded) return@Runnable
                        val ctx = this@NsfwUltimaSettingsFragment.context ?: return@Runnable
                        rebuildTagsSection(ctx)
                        rebuildPerformersSection(ctx)
                    }
                    postDelayed(debounceRunnable!!, 300)
                }
            })
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        }
    }

    private fun createRecommendationSectionHeader(
        context: Context,
        title: String,
        count: Int,
        collapsed: Boolean,
        onClick: () -> Unit
    ): TextView {
        val arrow = if (collapsed) "\u25B8" else "\u25BE"
        return CloudstreamUI.createTitleText(context, "$arrow $title ($count)", colors).apply {
            setPadding(0, dp(context, 8), 0, dp(context, 8))
            setOnClickListener { onClick() }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        }
    }

    private fun updateRecommendationHeaderCount(
        header: TextView,
        title: String,
        count: Int,
        collapsed: Boolean,
        activeCount: Int? = null
    ) {
        val arrow = if (collapsed) "\u25B8" else "\u25BE"
        header.text = if (activeCount != null && activeCount != count) {
            "$arrow $title ($activeCount active / $count total)"
        } else {
            "$arrow $title ($count)"
        }
    }

    private fun createCollapsibleSection(
        context: Context,
        header: TextView,
        content: LinearLayout,
        clearLabel: String,
        onClear: () -> Unit
    ): View {
        val card = CloudstreamUI.createCard(context, colors, CloudstreamUI.Dimens.CORNER_RADIUS_LARGE)
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 12) }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        headerRow.addView(header)
        headerRow.addView(CloudstreamUI.createSmallButton(context, clearLabel, isPrimary = false, colors) {
            onClear()
        })

        container.addView(headerRow)
        container.addView(content)
        card.addView(container)
        return card
    }

    private fun rebuildTagsSection(context: Context) {
        tagsSection.removeAllViews()
        if (tagsCollapsed) return

        val intelligence = getIntelligence()
        val filtered = if (searchQuery.isBlank()) affinityTags
            else affinityTags.filter { it.tag.lowercase().contains(searchQuery) }

        val activeCount = affinityTags.count { intelligence?.isTagPaused(it.tag) != true }
        updateRecommendationHeaderCount(tagsHeader, "Tags", affinityTags.size, tagsCollapsed, activeCount)

        if (filtered.isEmpty()) {
            tagsSection.addView(CloudstreamUI.createEmptyState(context,
                if (searchQuery.isBlank()) "No tag data yet \u2014 browse videos to build recommendations" else "No matches", colors))
            return
        }

        val influenceLevels = InfluenceLevel.assign(filtered) { it.score }
        val chipGroup = CloudstreamUI.createChipGroup(context, isSingleSelection = false)

        for (tag in filtered) {
            val isPaused = intelligence?.isTagPaused(tag.tag) == true
            val level = influenceLevels[tag]?.label ?: "Moderate"
            val label = "${tag.tag} \u00B7 $level"

            val chip = CloudstreamUI.createFilterChip(context, label, !isPaused, colors) { checked ->
                intelligence?.setTagPaused(tag.tag, !checked)
                dirty = true
                val newActiveCount = affinityTags.count { intelligence?.isTagPaused(it.tag) != true }
                updateRecommendationHeaderCount(tagsHeader, "Tags", affinityTags.size, tagsCollapsed, newActiveCount)
            }

            chip.isCloseIconVisible = true
            chip.closeIconTint = android.content.res.ColorStateList.valueOf(colors.textGray)
            chip.setOnCloseIconClickListener { deleteTag(tag) }
            if (!isTvMode) {
                chip.setOnLongClickListener {
                    deleteTag(tag)
                    true
                }
            }

            chipGroup.addView(chip)
        }

        tagsSection.addView(chipGroup)
    }

    private fun rebuildPerformersSection(context: Context) {
        performersSection.removeAllViews()
        if (performersCollapsed) return

        val intelligence = getIntelligence()
        val filtered = if (searchQuery.isBlank()) affinityPerformers
            else affinityPerformers.filter { it.tag.lowercase().contains(searchQuery) }

        val activeCount = affinityPerformers.count { intelligence?.isPerformerPaused(it.tag) != true }
        updateRecommendationHeaderCount(performersHeader, "Performers", affinityPerformers.size, performersCollapsed, activeCount)

        if (filtered.isEmpty()) {
            performersSection.addView(CloudstreamUI.createEmptyState(context,
                if (searchQuery.isBlank()) "No performer data yet" else "No matches", colors))
            return
        }

        val influenceLevels = InfluenceLevel.assign(filtered) { it.score }

        for ((index, performer) in filtered.withIndex()) {
            if (index > 0) {
                performersSection.addView(CloudstreamUI.createDivider(context, colors, 0))
            }
            performersSection.addView(createPerformerRow(context, performer, influenceLevels[performer], intelligence))
        }
    }

    private fun createPerformerRow(
        context: Context,
        performer: TagAffinity,
        level: InfluenceLevel?,
        intelligence: BrowsingIntelligence?
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 10), 0, dp(context, 10))
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        textContainer.addView(CloudstreamUI.createBodyText(context,
            performer.tag.replaceFirstChar { it.uppercase() }, colors).apply {
            maxLines = 1
            textSize = 15f
        })
        textContainer.addView(CloudstreamUI.createCaptionText(context,
            level?.label ?: "Moderate", colors).apply {
            setPadding(0, dp(context, 2), 0, 0)
        })

        row.addView(textContainer)

        // Pause toggle
        val isPaused = intelligence?.isPerformerPaused(performer.tag) == true
        val switch = CloudstreamUI.createSwitch(context, !isPaused, colors) { checked ->
            intelligence?.setPerformerPaused(performer.tag, !checked)
            dirty = true
            val newActiveCount = affinityPerformers.count { intelligence?.isPerformerPaused(it.tag) != true }
            updateRecommendationHeaderCount(performersHeader, "Performers", affinityPerformers.size, performersCollapsed, newActiveCount)
        }
        switch.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(context, 8) }
        row.addView(switch)

        // Delete button
        row.addView(CloudstreamUI.createSmallButton(context, "\u2715", isPrimary = false, colors) {
            deletePerformer(performer)
        })

        return row
    }

    private fun deleteTag(tag: TagAffinity) = deleteAffinity(
        item = tag, list = affinityTags, label = "Tag",
        isPaused = { getIntelligence()?.isTagPaused(it.tag) == true },
        setPaused = { item, paused -> getIntelligence()?.setTagPaused(item.tag, paused) },
        commitDelete = { getIntelligence()?.deleteAffinityTag(it.tag) },
        rebuild = { ctx -> rebuildTagsSection(ctx) }
    )

    private fun deletePerformer(performer: TagAffinity) = deleteAffinity(
        item = performer, list = affinityPerformers, label = "Performer",
        isPaused = { getIntelligence()?.isPerformerPaused(it.tag) == true },
        setPaused = { item, paused -> getIntelligence()?.setPerformerPaused(item.tag, paused) },
        commitDelete = { getIntelligence()?.deleteAffinityPerformer(it.tag) },
        rebuild = { ctx -> rebuildPerformersSection(ctx) }
    )

    private fun deleteAffinity(
        item: TagAffinity,
        list: MutableList<TagAffinity>,
        label: String,
        isPaused: (TagAffinity) -> Boolean,
        setPaused: (TagAffinity, Boolean) -> Unit,
        commitDelete: suspend (TagAffinity) -> Unit,
        rebuild: (Context) -> Unit
    ) {
        val ctx = context ?: return
        val index = list.indexOf(item)
        val wasPaused = isPaused(item)
        list.remove(item)
        dirty = true
        rebuild(ctx)

        if (!isTvMode) {
            showUndoOrCommit(
                message = "$label '${item.tag}' removed",
                undoAction = {
                    val undoCtx = context ?: return@showUndoOrCommit
                    list.add(index.coerceIn(0, list.size), item)
                    if (wasPaused) setPaused(item, true)
                    rebuild(undoCtx)
                },
                commitAction = {
                    lifecycleScope.launch(Dispatchers.IO) { commitDelete(item) }
                }
            )
        } else {
            Toast.makeText(ctx, "$label removed", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch(Dispatchers.IO) { commitDelete(item) }
        }
    }

    private fun clearTags() {
        if (!isAdded) return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { getIntelligence()?.clearAffinityTags() }
            affinityTags.clear()
            dirty = true
            val ctx = context ?: return@launch
            if (isAdded) rebuildTagsSection(ctx)
        }
    }

    private fun clearPerformers() {
        if (!isAdded) return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { getIntelligence()?.clearAffinityPerformers() }
            affinityPerformers.clear()
            dirty = true
            val ctx = context ?: return@launch
            if (isAdded) rebuildPerformersSection(ctx)
        }
    }

    private fun showRecommendationClearConfirmation(dataType: String, onConfirm: () -> Unit) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle("Clear $dataType")
            .setMessage("This will permanently remove all $dataType. This cannot be undone.")
            .setPositiveButton("Clear") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUndoOrCommit(message: String, undoAction: () -> Unit, commitAction: () -> Unit) {
        // Commit any previous pending operation before starting a new one
        pendingCommit?.invoke()
        pendingUndo = null
        pendingCommit = null
        var undone = false
        val snackbar = Snackbar.make(mainContainer, message, Snackbar.LENGTH_LONG)
        snackbar.setAction("Undo") {
            undone = true
            pendingUndo = null
            pendingCommit = null
            undoAction()
        }
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                pendingUndo = null
                pendingCommit = null
                if (!undone) commitAction()
            }
        })
        pendingUndo = undoAction
        pendingCommit = commitAction
        snackbar.show()
    }

    private fun dp(context: Context, dp: Int): Int = TvFocusUtils.dpToPx(context, dp)

    override fun onDismiss(dialog: DialogInterface) {
        // Commit any pending delete operation (snackbar may not have timed out yet)
        pendingCommit?.invoke()
        pendingUndo = null
        pendingCommit = null
        if (dirty) {
            try {
                NsfwUltimaPlugin.instance?.forYouProvider?.invalidateMainPageCache()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to invalidate ForYou cache", e)
            }
        }
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        debounceRunnable?.let { searchEditText?.removeCallbacks(it) }
        debounceRunnable = null
        searchEditText = null
        // Clear RecyclerView adapter to prevent memory leaks
        if (::homepageRecyclerView.isInitialized) {
            homepageRecyclerView.adapter = null
        }
        super.onDestroyView()
    }
}
