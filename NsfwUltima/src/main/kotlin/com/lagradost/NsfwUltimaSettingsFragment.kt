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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
 * Displays feeds organized into collapsible groups with drag-and-drop reordering.
 * Supports both touch mode (drag handle) and TV mode (pick-and-tap reorder).
 */
class NsfwUltimaSettingsFragment(
    private val plugin: NsfwUltimaPlugin
) : DialogFragment() {

    companion object {
        private const val TAG = "NsfwUltimaSettings"
    }

    private var feedList = mutableListOf<FeedItem>()
    private var settings = NsfwUltimaSettings()
    private var feedGroups = mutableListOf<FeedGroup>()
    private var collapsedGroupIds = mutableSetOf<String>()

    private lateinit var mainContainer: LinearLayout
    private lateinit var groupedFeedAdapter: GroupedFeedListAdapter
    private lateinit var feedRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var reorderSubtitle: TextView
    private var reorderModeButton: MaterialButton? = null  // TV mode reorder toggle
    private var manageFeedsButton: MaterialButton? = null  // Store reference for focus restoration
    private var itemTouchHelper: ItemTouchHelper? = null
    private var groupingOptionsContainer: LinearLayout? = null  // For group management options
    private var manageGroupsButton: MaterialButton? = null  // "Manage Groups" button

    // Pick-and-tap reorder state (TV mode)
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
                if (isAdded && ::mainContainer.isInitialized && mainContainer.isAttachedToWindow) {
                    TvFocusUtils.requestInitialFocus(mainContainer)
                }
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

        // Load grouping data
        feedGroups = NsfwUltimaStorage.loadGroups().toMutableList()
        collapsedGroupIds = NsfwUltimaStorage.loadCollapsedGroups().toMutableSet()

        Log.d(TAG, "Loaded ${feedList.size} feeds, ${feedGroups.size} groups")
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
        manageFeedsButton = MaterialButton(context).apply {
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
        }
        mainContainer.addView(manageFeedsButton!!)

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

        // Row 1: Show plugin names toggle
        cardContent.addView(createToggleRow(
            context,
            title = "Show Plugin Names",
            subtitle = "Prefix feeds with [PluginName]",
            isChecked = settings.showPluginNames
        ) { isChecked ->
            settings = settings.copy(showPluginNames = isChecked)
            if (!NsfwUltimaStorage.saveSettings(settings)) {
                Log.e(TAG, "Failed to save settings")
                showSaveErrorToast()
            }
            refreshGroupedView()
            plugin.nsfwUltima?.refreshPluginStates()
        })

        // Divider
        cardContent.addView(createDivider(context))

        // Row 2: Grouping options
        groupingOptionsContainer = createGroupingOptionsContainer(context)
        cardContent.addView(groupingOptionsContainer)

        card.addView(cardContent)
        return card
    }

    private fun createToggleRow(
        context: Context,
        title: String,
        subtitle: String,
        isChecked: Boolean,
        onChanged: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        textContainer.addView(TextView(context).apply {
            text = title
            textSize = 15f
            setTextColor(textColor)
        })

        textContainer.addView(TextView(context).apply {
            text = subtitle
            textSize = 12f
            setTextColor(grayTextColor)
        })

        val toggle = SwitchMaterial(context).apply {
            this.isChecked = isChecked
            setOnCheckedChangeListener { _, checked -> onChanged(checked) }
        }
        if (isTvMode) TvFocusUtils.makeFocusable(toggle)

        row.addView(textContainer)
        row.addView(toggle)
        return row
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 1)
            ).apply {
                setMargins(0, dp(context, 12), 0, dp(context, 12))
            }
            setBackgroundColor((grayTextColor and 0x00FFFFFF) or 0x30000000)
        }
    }

    private fun createGroupingOptionsContainer(context: Context): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Group status
        val groupCount = feedGroups.size
        val assignedCount = feedList.count { it.groupId != null }
        val statusText = when {
            groupCount == 0 -> "No groups defined yet"
            assignedCount == 0 -> "$groupCount group(s), no feeds assigned"
            else -> "$groupCount group(s), $assignedCount feeds assigned"
        }

        container.addView(TextView(context).apply {
            text = statusText
            textSize = 12f
            setTextColor(grayTextColor)
            setPadding(0, dp(context, 8), 0, dp(context, 8))
        })

        // Manage Groups button
        manageGroupsButton = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Manage Groups"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            strokeColor = ColorStateList.valueOf(primaryColor)
            setTextColor(primaryColor)
            setOnClickListener { showGroupManagerDialog() }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        }
        container.addView(manageGroupsButton)

        return container
    }

    private fun rebuildGroupingOptions(context: Context) {
        groupingOptionsContainer?.let { container ->
            container.removeAllViews()

            // Group status
            val groupCount = feedGroups.size
            val assignedCount = feedList.count { it.groupId != null }
            val statusText = when {
                groupCount == 0 -> "No groups defined yet"
                assignedCount == 0 -> "$groupCount group(s), no feeds assigned"
                else -> "$groupCount group(s), $assignedCount feeds assigned"
            }

            container.addView(TextView(context).apply {
                text = statusText
                textSize = 12f
                setTextColor(grayTextColor)
                setPadding(0, dp(context, 8), 0, dp(context, 8))
            })

            // Manage Groups button
            manageGroupsButton = MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "Manage Groups"
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                strokeColor = ColorStateList.valueOf(primaryColor)
                setTextColor(primaryColor)
                setOnClickListener { showGroupManagerDialog() }
                if (isTvMode) TvFocusUtils.makeFocusable(this)
            }
            container.addView(manageGroupsButton)
        }
    }

    private fun showGroupManagerDialog() {
        // Get available feeds from plugins
        val availableFeeds = getAvailableFeeds()

        val dialog = FeedGroupManagerDialog(
            initialGroups = feedGroups,
            allFeeds = feedList,
            availableFeeds = availableFeeds,
            showPluginNames = settings.showPluginNames,
            onGroupsChanged = { updatedGroups ->
                feedGroups.clear()
                feedGroups.addAll(updatedGroups)
                if (!NsfwUltimaStorage.saveGroups(feedGroups)) {
                    Log.e(TAG, "Failed to save groups from group manager")
                    showSaveErrorToast()
                }
                // Refresh the grouped view and options to reflect changes
                refreshGroupedView()
                context?.let { rebuildGroupingOptions(it) }
            },
            onFeedsUpdated = { updatedFeeds ->
                feedList.clear()
                feedList.addAll(updatedFeeds)
                if (!NsfwUltimaStorage.saveFeedList(feedList)) {
                    Log.e(TAG, "Failed to save feeds from group manager")
                    showSaveErrorToast()
                }
                refreshGroupedView()
                updateFeedListHeader()
                context?.let { rebuildGroupingOptions(it) }
                plugin.nsfwUltima?.refreshFeedList()
            }
        )
        dialog.show(parentFragmentManager, "FeedGroupManagerDialog")
    }

    private fun getAvailableFeeds(): List<AvailableFeed> {
        val feeds = mutableListOf<AvailableFeed>()
        val nsfwProviders = allProviders.filter { api ->
            api.supportedTypes.contains(TvType.NSFW) && api.name != "NSFW Ultima"
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

        // Deduplicate feeds by key (plugin + section + data)
        return feeds.distinctBy { it.key() }
    }

    private fun updateFeedListHeader() {
        // Update subtitle based on reorder state
        reorderSubtitle.text = when {
            isReorderMode && selectedReorderPosition >= 0 ->
                "Tap destination to move feed"
            isReorderMode ->
                "Tap a feed to select, then tap destination"
            isTvMode ->
                "Drag ≡ to reorder, or tap Reorder button"
            else ->
                "Drag ≡ to reorder"
        }
    }

    private fun refreshGroupedView() {
        // Group feeds by their assigned groupId
        val feedsByGroup = mutableMapOf<String, MutableList<FeedItem>>()
        val ungrouped = mutableListOf<FeedItem>()

        feedList.forEach { feed ->
            val groupId = feed.groupId
            if (groupId != null && feedGroups.any { it.id == groupId }) {
                feedsByGroup.getOrPut(groupId) { mutableListOf() }.add(feed)
            } else {
                ungrouped.add(feed)
            }
        }

        // Build result groups (only groups that have feeds)
        val resultGroups = feedGroups.filter { feedsByGroup[it.id]?.isNotEmpty() == true }

        val result = FeedGroupingEngine.GroupingResult(
            groups = resultGroups,
            feedsByGroup = feedsByGroup,
            ungroupedFeeds = ungrouped
        )
        val items = FeedGroupingEngine.toAdapterItems(result, collapsedGroupIds)
        if (::groupedFeedAdapter.isInitialized) {
            groupedFeedAdapter.submitList(items)
        }
    }

    private fun createFeedList(context: Context): RecyclerView {
        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            isNestedScrollingEnabled = false
            visibility = if (feedList.isEmpty()) View.GONE else View.VISIBLE
        }

        // Create grouped adapter with reorder support
        groupedFeedAdapter = GroupedFeedListAdapter(
            context = context,
            isTvMode = isTvMode,
            textColor = textColor,
            grayTextColor = grayTextColor,
            primaryColor = primaryColor,
            showPluginNames = settings.showPluginNames,
            onGroupToggle = { groupId -> toggleGroupExpansion(groupId) },
            onFeedRemove = { feedKey -> removeFeedByKey(feedKey) },
            onFeedLongPress = { feedKey -> showMoveToGroupDialog(feedKey) },
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            },
            onReorderTap = { position ->
                onFeedTappedForReorder(position)
            }
        )
        recyclerView.adapter = groupedFeedAdapter

        // Setup drag-and-drop for touch mode
        if (!isTvMode) {
            val touchHelper = GroupedFeedItemTouchHelper(groupedFeedAdapter) {
                // Sync internal feed list with adapter after drag
                syncFeedListFromAdapter()
            }
            itemTouchHelper = ItemTouchHelper(touchHelper)
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        }

        // Populate grouped data
        refreshGroupedView()

        return recyclerView
    }

    /**
     * Sync the internal feedList order from the adapter's current item order.
     * Called after drag-and-drop reordering.
     */
    private fun syncFeedListFromAdapter() {
        val newOrder = groupedFeedAdapter.getItems()
            .filterIsInstance<GroupedFeedItem.Feed>()
            .map { it.item }

        feedList.clear()
        feedList.addAll(newOrder)
        saveAndRefresh()
    }

    private fun toggleGroupExpansion(groupId: String) {
        if (collapsedGroupIds.contains(groupId)) {
            collapsedGroupIds.remove(groupId)
        } else {
            collapsedGroupIds.add(groupId)
        }
        if (!NsfwUltimaStorage.saveCollapsedGroups(collapsedGroupIds)) {
            Log.e(TAG, "Failed to save collapsed groups state")
            showSaveErrorToast()
        }
        refreshGroupedView()

        // Restore focus for TV
        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(mainContainer)
        }
    }

    private fun removeFeedByKey(feedKey: String) {
        val index = feedList.indexOfFirst { it.key() == feedKey }
        if (index >= 0) {
            feedList.removeAt(index)
            saveAndRefresh()
            updateEmptyState()
            refreshGroupedView()

            // Restore focus for TV
            if (isTvMode) {
                TvFocusUtils.enableFocusLoop(mainContainer)
            }
        } else {
            Log.w(TAG, "removeFeedByKey: feed not found with key=$feedKey")
        }
    }

    private fun showMoveToGroupDialog(feedKey: String) {
        val feed = feedList.find { it.key() == feedKey }
        if (feed == null) {
            Log.w(TAG, "showMoveToGroupDialog: feed not found for key=$feedKey")
            return
        }

        // If feed is in a group, show context menu with ungroup option
        if (feed.groupId != null) {
            showFeedContextMenu(feed)
            return
        }

        // Feed is ungrouped - show move to group dialog
        val dialog = MoveToGroupDialog(
            feedItem = feed,
            existingGroups = feedGroups,
            currentGroupId = null,
            onMoveToGroup = { movedFeed, targetGroupId ->
                // Update feed with new groupId
                val index = feedList.indexOfFirst { it.key() == movedFeed.key() }
                if (index >= 0) {
                    feedList[index] = movedFeed.copy(groupId = targetGroupId)
                    saveAndRefresh()
                    // Post to ensure UI updates after dialog callback completes
                    feedRecyclerView.post {
                        refreshGroupedView()
                    }
                } else {
                    Log.e(TAG, "onMoveToGroup: feed not found - key=${movedFeed.key()}")
                }
            },
            onCreateGroup = { groupName ->
                val newGroup = FeedGroup.create(groupName)
                feedGroups.add(newGroup)
                if (!NsfwUltimaStorage.saveGroups(feedGroups)) {
                    Log.e(TAG, "Failed to save new group")
                    showSaveErrorToast()
                }
                newGroup
            }
        )
        dialog.show(parentFragmentManager, "MoveToGroupDialog")
    }

    private fun showFeedContextMenu(feed: FeedItem) {
        val context = requireContext()
        val groupName = feedGroups.find { it.id == feed.groupId }?.name
            ?: "Unknown Group"

        val displayName = if (settings.showPluginNames) {
            "[${feed.pluginName}] ${feed.sectionName}"
        } else {
            feed.sectionName
        }

        AlertDialog.Builder(context)
            .setTitle(displayName)
            .setMessage("Currently in: $groupName")
            .setPositiveButton("Remove from Group") { _, _ ->
                ungroupFeed(feed)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun ungroupFeed(feed: FeedItem) {
        val updatedFeeds = FeedAssignmentService.ungroupFeed(feedList, feed)
        feedList.clear()
        feedList.addAll(updatedFeeds)
        val saveSucceeded = NsfwUltimaStorage.saveFeedList(feedList)
        refreshGroupedView()
        updateFeedListHeader()
        plugin.nsfwUltima?.refreshFeedList()

        // Show appropriate toast based on save result
        context?.let { ctx ->
            val message = if (saveSucceeded) {
                "Removed '${feed.sectionName}' from group"
            } else {
                Log.e(TAG, "Failed to save after ungrouping feed")
                "Failed to save changes"
            }
            android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
        }
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
                    saveAndRefresh()
                    updateEmptyState()
                    refreshGroupedView()
                    // Re-enable focus loop after list modification
                    if (isTvMode) {
                        TvFocusUtils.enableFocusLoop(mainContainer)
                    }
                }
            },
            onFeedRemoved = { removedFeed ->
                // Remove feed from list
                val key = removedFeed.key()
                val index = feedList.indexOfFirst { it.key() == key }
                if (index >= 0) {
                    feedList.removeAt(index)
                    saveAndRefresh()
                    updateEmptyState()
                    refreshGroupedView()
                    // Re-enable focus loop after list modification
                    if (isTvMode) {
                        TvFocusUtils.enableFocusLoop(mainContainer)
                    }
                }
            }
        )
        dialog.updateAddedFeeds(feedList)
        // Restore focus to Manage Feeds button when dialog closes (TV mode)
        if (isTvMode) {
            dialog.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (isAdded && view != null) {
                        manageFeedsButton?.post {
                            if (isAdded && manageFeedsButton?.isAttachedToWindow == true) {
                                manageFeedsButton?.requestFocus()
                            }
                        }
                    }
                }
            })
        }
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
        val feedsSaved = NsfwUltimaStorage.saveFeedList(feedList)
        val groupsSaved = NsfwUltimaStorage.saveGroups(feedGroups)
        val collapsedSaved = NsfwUltimaStorage.saveCollapsedGroups(collapsedGroupIds)

        if (!feedsSaved || !groupsSaved || !collapsedSaved) {
            Log.e(TAG, "Failed to save: feeds=$feedsSaved, groups=$groupsSaved, collapsed=$collapsedSaved")
            showSaveErrorToast()
        }

        plugin.nsfwUltima?.refreshFeedList()
    }

    private fun showSaveErrorToast() {
        context?.let { ctx ->
            android.widget.Toast.makeText(
                ctx,
                "Failed to save changes. Please try again.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getReorderSubtitleText(): String {
        return when {
            isReorderMode && selectedReorderPosition >= 0 ->
                "Tap destination to move feed"
            isReorderMode ->
                "Tap a feed to select, then tap destination"
            isTvMode ->
                "Drag ≡ to reorder, or tap Reorder button"
            else ->
                "Drag ≡ to reorder"
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
        groupedFeedAdapter.setReorderMode(isReorderMode, -1)
    }

    fun onFeedTappedForReorder(position: Int) {
        if (!isReorderMode) return

        // Only allow selecting/moving feed items, not headers
        if (!groupedFeedAdapter.isFeedPosition(position)) return

        if (selectedReorderPosition < 0) {
            // First tap - select the feed
            selectedReorderPosition = position
            reorderSubtitle.text = getReorderSubtitleText()
            groupedFeedAdapter.setReorderMode(true, position)
            // Restore focus to the tapped item after adapter refresh
            restoreFocusToPosition(position)
        } else if (selectedReorderPosition == position) {
            // Tapped same item - deselect
            selectedReorderPosition = -1
            reorderSubtitle.text = getReorderSubtitleText()
            groupedFeedAdapter.setReorderMode(true, -1)
            // Restore focus to the tapped item after adapter refresh
            restoreFocusToPosition(position)
        } else {
            // Second tap - move the feed (only if target is also a feed)
            if (!groupedFeedAdapter.isFeedPosition(position)) {
                // Can't move to a header position
                return
            }

            groupedFeedAdapter.moveItem(selectedReorderPosition, position)
            syncFeedListFromAdapter()

            // Reset selection
            selectedReorderPosition = -1
            reorderSubtitle.text = getReorderSubtitleText()
            groupedFeedAdapter.setReorderMode(true, -1)
            // Restore focus to the target position after move
            restoreFocusToPosition(position)
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
            .setMessage("This will remove all your feeds, groups, and settings. Are you sure?")
            .setPositiveButton("Reset") { _, _ ->
                val success = NsfwUltimaStorage.clearAll()
                if (success) {
                    feedList.clear()
                    feedGroups.clear()
                    collapsedGroupIds.clear()
                    settings = NsfwUltimaSettings()

                    // Reset adapter
                    if (::groupedFeedAdapter.isInitialized) {
                        groupedFeedAdapter.submitList(emptyList())
                    }

                    updateEmptyState()
                    plugin.nsfwUltima?.refreshFeedList()
                    Log.d(TAG, "All data reset")
                    android.widget.Toast.makeText(
                        context,
                        "All data reset successfully",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.e(TAG, "Failed to reset all data")
                    android.widget.Toast.makeText(
                        context,
                        "Failed to reset data. Please try again.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
