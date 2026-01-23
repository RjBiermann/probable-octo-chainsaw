package com.lagradost

import com.lagradost.common.TvFocusUtils

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

/**
 * Settings fragment for NSFW Ultima with feed-centric design.
 * Shows a flat reorderable list of feeds with an "Add Feed" button.
 */
class NsfwUltimaSettingsFragment(
    private val plugin: NsfwUltimaPlugin
) : DialogFragment() {

    companion object {
        private const val TAG = "NsfwUltimaSettings"
    }

    private var feedList = mutableListOf<FeedItem>()
    private var settings = NsfwUltimaSettings()

    private lateinit var mainContainer: LinearLayout
    private lateinit var feedListAdapter: FeedListAdapter
    private lateinit var feedRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var reorderSubtitle: TextView
    private var reorderModeButton: MaterialButton? = null  // Only used in TV mode
    private var itemTouchHelper: ItemTouchHelper? = null

    // Pick-and-tap reorder state
    private var isReorderMode = false
    private var selectedReorderPosition: Int = -1

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    // Theme colors
    private var textColor: Int = 0
    private var grayTextColor: Int = 0
    private var backgroundColor: Int = 0
    private var cardColor: Int = 0
    private var primaryColor: Int = 0

    private fun resolveThemeColors(context: Context) {
        val tv = TypedValue()
        val theme = context.theme

        textColor = resolveAttr(theme, tv, "textColor", android.R.attr.textColorPrimary, context)
        grayTextColor = resolveAttr(theme, tv, "grayTextColor", android.R.attr.textColorSecondary, context)
        backgroundColor = resolveAttr(theme, tv, "primaryBlackBackground", android.R.attr.colorBackground, context)
        cardColor = resolveAttr(theme, tv, "boxItemBackground", android.R.attr.colorBackgroundFloating, context)
        primaryColor = resolveAttr(theme, tv, "colorPrimary", android.R.attr.colorPrimary, context)
    }

    private fun resolveAttr(
        theme: android.content.res.Resources.Theme,
        tv: TypedValue,
        customAttr: String,
        fallbackAttr: Int,
        context: Context
    ): Int {
        val customId = context.resources.getIdentifier(customAttr, "attr", context.packageName)
        return if (customId != 0 && theme.resolveAttribute(customId, tv, true)) {
            tv.data
        } else if (theme.resolveAttribute(fallbackAttr, tv, true)) {
            tv.data
        } else {
            Log.w(TAG, "Failed to resolve theme attribute: $customAttr")
            0
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        resolveThemeColors(context)
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
                TvFocusUtils.requestInitialFocus(mainContainer)
            }
        }
    }

    private fun loadData() {
        settings = NsfwUltimaStorage.loadSettings()

        // Try to load feed list, or migrate from old format
        feedList = NsfwUltimaStorage.loadFeedList().toMutableList()
        if (feedList.isEmpty()) {
            // Try migration from old plugin-centric format
            NsfwUltimaStorage.migrateFromPluginStates()?.let { migrated ->
                feedList = migrated.toMutableList()
            }
        }

        Log.d(TAG, "Loaded ${feedList.size} feeds")
    }

    private fun createSettingsView(context: Context): View {
        val scrollView = NestedScrollView(context).apply {
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
        mainContainer.addView(TextView(context).apply {
            text = "NSFW Ultima Settings"
            textSize = 20f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(context, 16))
        })

        // Settings card
        mainContainer.addView(createSettingsCard(context))

        // Your Feeds header row with reorder mode toggle
        val feedsHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(context, 20), 0, dp(context, 8))
        }

        feedsHeaderRow.addView(TextView(context).apply {
            text = "Your Feeds"
            textSize = 16f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Reorder mode toggle button (TV mode only - touch mode uses drag handle)
        if (isTvMode) {
            reorderModeButton = MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "Reorder"
                textSize = 12f
                minimumWidth = 0
                minimumHeight = 0
                minWidth = 0
                minHeight = dp(context, 32)
                insetTop = 0
                insetBottom = 0
                setPadding(dp(context, 12), 0, dp(context, 12), 0)
                strokeColor = ColorStateList.valueOf(primaryColor)
                setTextColor(primaryColor)
                setOnClickListener { toggleReorderMode(context) }
                TvFocusUtils.makeFocusable(this)
            }
            feedsHeaderRow.addView(reorderModeButton)
        }
        mainContainer.addView(feedsHeaderRow)

        // Subtitle - changes based on reorder mode
        reorderSubtitle = TextView(context).apply {
            text = getReorderSubtitleText()
            textSize = 13f
            setTextColor(grayTextColor)
            setPadding(0, 0, 0, dp(context, 12))
        }
        mainContainer.addView(reorderSubtitle)

        // Empty state text
        emptyStateText = TextView(context).apply {
            text = "No feeds added yet.\nTap 'Manage Feeds' to get started."
            textSize = 14f
            setTextColor(grayTextColor)
            gravity = Gravity.CENTER
            setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 24))
            visibility = if (feedList.isEmpty()) View.VISIBLE else View.GONE
        }
        mainContainer.addView(emptyStateText)

        // Feed list RecyclerView
        feedRecyclerView = createFeedList(context)
        mainContainer.addView(feedRecyclerView)

        // Add Feed button
        mainContainer.addView(MaterialButton(context).apply {
            text = "Manage Feeds"
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 16)
            }
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setOnClickListener { showAddFeedDialog() }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        // Reset All Data button
        mainContainer.addView(MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Reset All Data"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 12)
            }
            strokeColor = ColorStateList.valueOf(grayTextColor)
            setTextColor(grayTextColor)
            setOnClickListener { showResetConfirmation(context) }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        // Close button
        mainContainer.addView(MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Close"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 12)
            }
            strokeColor = ColorStateList.valueOf(grayTextColor)
            setOnClickListener { dismiss() }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        scrollView.addView(mainContainer)

        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(mainContainer)
        }

        return scrollView
    }

    private fun createSettingsCard(context: Context): MaterialCardView {
        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCardBackgroundColor(cardColor)
            radius = dp(context, 12).toFloat()
            cardElevation = 0f
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        }

        // Show plugin names toggle
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        textContainer.addView(TextView(context).apply {
            text = "Show Plugin Names"
            textSize = 15f
            setTextColor(textColor)
        })

        textContainer.addView(TextView(context).apply {
            text = "Prefix feeds with [PluginName]"
            textSize = 12f
            setTextColor(grayTextColor)
        })

        val toggle = SwitchMaterial(context).apply {
            isChecked = settings.showPluginNames
            setOnCheckedChangeListener { _, isChecked ->
                settings = settings.copy(showPluginNames = isChecked)
                NsfwUltimaStorage.saveSettings(settings)
                // Refresh the list to update display names
                feedListAdapter.notifyDataSetChanged()
                plugin.nsfwUltima?.refreshPluginStates()
            }
        }
        if (isTvMode) TvFocusUtils.makeFocusable(toggle)

        row.addView(textContainer)
        row.addView(toggle)
        cardContent.addView(row)
        card.addView(cardContent)

        return card
    }

    private fun createFeedList(context: Context): RecyclerView {
        feedListAdapter = FeedListAdapter(
            context = context,
            isTvMode = isTvMode,
            textColor = textColor,
            grayTextColor = grayTextColor,
            primaryColor = primaryColor,
            showPluginNames = settings.showPluginNames,
            onRemove = { position ->
                if (position >= 0 && position < feedList.size) {
                    feedList.removeAt(position)
                    feedListAdapter.removeItem(position)
                    saveAndRefresh()
                    updateEmptyState()
                }
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            },
            onReorderTap = { position ->
                onFeedTappedForReorder(position)
            }
        )

        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            adapter = feedListAdapter
            isNestedScrollingEnabled = false
            visibility = if (feedList.isEmpty()) View.GONE else View.VISIBLE
        }

        // Setup drag-and-drop for touch mode
        if (!isTvMode) {
            val touchHelper = FeedItemTouchHelper(feedListAdapter) {
                // Sync internal list with adapter after drag
                feedList.clear()
                feedList.addAll(feedListAdapter.getFeeds())
                saveAndRefresh()
            }
            itemTouchHelper = ItemTouchHelper(touchHelper)
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        }

        feedListAdapter.submitList(feedList)

        return recyclerView
    }

    private fun showAddFeedDialog() {
        val availableFeeds = discoverAvailableFeeds()

        val dialog = AddFeedDialog(
            availableFeeds = availableFeeds,
            onFeedSelected = { selectedFeed ->
                // Add feed to list
                val newFeed = selectedFeed.toFeedItem()
                if (feedList.none { it.key() == newFeed.key() }) {
                    feedList.add(newFeed)
                    feedListAdapter.submitList(feedList.toList())
                    saveAndRefresh()
                    updateEmptyState()
                }
            },
            onFeedRemoved = { removedFeed ->
                // Remove feed from list
                val key = removedFeed.key()
                val index = feedList.indexOfFirst { it.key() == key }
                if (index >= 0) {
                    feedList.removeAt(index)
                    feedListAdapter.submitList(feedList.toList())
                    saveAndRefresh()
                    updateEmptyState()
                }
            }
        )
        dialog.updateAddedFeeds(feedList)
        dialog.show(parentFragmentManager, "AddFeedDialog")
    }

    private fun discoverAvailableFeeds(): List<AvailableFeed> {
        val addedKeys = feedList.map { it.key() }.toSet()

        return synchronized(allProviders) {
            allProviders
                .filter { isNsfwPlugin(it) && it.name != "NSFW Ultima" }
                .flatMap { provider ->
                    provider.mainPage.map { mainPageData ->
                        AvailableFeed(
                            pluginName = provider.name,
                            sectionName = mainPageData.name,
                            sectionData = mainPageData.data,
                            isAdded = addedKeys.contains("${provider.name}::${mainPageData.name}::${mainPageData.data}")
                        )
                    }
                }
        }
    }

    private fun isNsfwPlugin(provider: MainAPI): Boolean {
        if (provider.name in NsfwPluginConstants.KNOWN_NSFW_PLUGINS) return true
        if (provider.supportedTypes.contains(TvType.NSFW)) return true
        val nameLower = provider.name.lowercase()
        return NsfwPluginConstants.NSFW_KEYWORDS.any { nameLower.contains(it) }
    }

    private fun updateEmptyState() {
        val isEmpty = feedList.isEmpty()
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        feedRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun saveAndRefresh() {
        NsfwUltimaStorage.saveFeedList(feedList)
        plugin.nsfwUltima?.refreshFeedList()
    }

    private fun getReorderSubtitleText(): String {
        return when {
            isReorderMode && selectedReorderPosition >= 0 -> "Tap destination to move feed"
            isReorderMode -> "Tap a feed to select, then tap destination"
            isTvMode -> "Tap Reorder to arrange feeds"
            else -> "Drag ≡ to reorder"
        }
    }

    private fun toggleReorderMode(context: Context) {
        isReorderMode = !isReorderMode
        selectedReorderPosition = -1

        reorderModeButton?.apply {
            if (isReorderMode) {
                text = "Done"
                backgroundTintList = ColorStateList.valueOf(primaryColor)
                setTextColor(0xFFFFFFFF.toInt())
                strokeWidth = 0
            } else {
                text = "Reorder"
                backgroundTintList = ColorStateList.valueOf(0)
                setTextColor(primaryColor)
                strokeColor = ColorStateList.valueOf(primaryColor)
                strokeWidth = dp(context, 1)
            }
        }

        reorderSubtitle.text = getReorderSubtitleText()
        feedListAdapter.setReorderMode(isReorderMode, -1)
    }

    fun onFeedTappedForReorder(position: Int) {
        if (!isReorderMode) return

        if (selectedReorderPosition < 0) {
            // First tap - select the feed
            selectedReorderPosition = position
            reorderSubtitle.text = getReorderSubtitleText()
            feedListAdapter.setReorderMode(true, position)
            // Restore focus to the tapped item after adapter refresh
            restoreFocusToPosition(position)
        } else if (selectedReorderPosition == position) {
            // Tapped same item - deselect
            selectedReorderPosition = -1
            reorderSubtitle.text = getReorderSubtitleText()
            feedListAdapter.setReorderMode(true, -1)
            // Restore focus to the tapped item after adapter refresh
            restoreFocusToPosition(position)
        } else {
            // Second tap - move the feed
            val item = feedList.removeAt(selectedReorderPosition)
            val targetPosition = if (position > selectedReorderPosition) position else position
            feedList.add(targetPosition, item)
            feedListAdapter.submitList(feedList.toList())
            saveAndRefresh()

            // Reset selection
            selectedReorderPosition = -1
            reorderSubtitle.text = getReorderSubtitleText()
            feedListAdapter.setReorderMode(true, -1)
            // Restore focus to the target position after move
            restoreFocusToPosition(targetPosition)
        }
    }

    private fun restoreFocusToPosition(position: Int) {
        feedRecyclerView.post {
            val viewHolder = feedRecyclerView.findViewHolderForAdapterPosition(position)
            viewHolder?.itemView?.requestFocus()
        }
    }

    private fun showResetConfirmation(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Reset All Data")
            .setMessage("This will remove all your feeds and settings. Are you sure?")
            .setPositiveButton("Reset") { _, _ ->
                NsfwUltimaStorage.clearAll()
                feedList.clear()
                settings = NsfwUltimaSettings()
                feedListAdapter.submitList(emptyList())
                updateEmptyState()
                plugin.nsfwUltima?.refreshFeedList()
                Log.d(TAG, "All data reset")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
