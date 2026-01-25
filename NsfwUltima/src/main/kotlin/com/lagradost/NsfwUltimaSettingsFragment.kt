package com.lagradost

import com.lagradost.common.TvFocusUtils

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
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
 * Settings fragment for NSFW Ultima with homepage-centric design.
 * Displays homepages directly with tap-to-edit functionality.
 * Supports drag-and-drop reordering (touch) and tap-to-reorder (TV).
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

    private lateinit var mainContainer: LinearLayout
    private lateinit var homepageAdapter: HomepageListAdapter
    private lateinit var homepageRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var homepageSubtitle: TextView
    private var reorderModeButton: MaterialButton? = null
    private var itemTouchHelper: ItemTouchHelper? = null

    // Tap-to-reorder state (TV mode)
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

        // Load feeds (or migrate from old format)
        feedList = NsfwUltimaStorage.loadFeedList().toMutableList()
        if (feedList.isEmpty()) {
            NsfwUltimaStorage.migrateFromPluginStates()?.let { migrated ->
                feedList = migrated.toMutableList()
            }
        }

        // Load homepages
        feedGroups = NsfwUltimaStorage.loadGroups().toMutableList()

        Log.d(TAG, "Loaded ${feedList.size} feeds, ${feedGroups.size} homepages")
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

        // Your Homepages header row with reorder mode toggle
        val homepagesHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(context, 20), 0, dp(context, 8))
        }

        homepagesHeaderRow.addView(TextView(context).apply {
            text = "Your Homepages"
            textSize = 16f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Reorder mode toggle button (TV mode only)
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
            homepagesHeaderRow.addView(reorderModeButton)
        }
        mainContainer.addView(homepagesHeaderRow)

        // Subtitle
        homepageSubtitle = TextView(context).apply {
            text = getSubtitleText()
            textSize = 13f
            setTextColor(grayTextColor)
            setPadding(0, 0, 0, dp(context, 12))
        }
        mainContainer.addView(homepageSubtitle)

        // Empty state text
        emptyStateText = TextView(context).apply {
            text = "No homepages yet.\nCreate one below to get started."
            textSize = 14f
            setTextColor(grayTextColor)
            gravity = Gravity.CENTER
            setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 24))
            visibility = if (feedGroups.isEmpty()) View.VISIBLE else View.GONE
        }
        mainContainer.addView(emptyStateText)

        // Homepage list RecyclerView
        homepageRecyclerView = createHomepageList(context)
        mainContainer.addView(homepageRecyclerView)

        // Create Homepage button
        mainContainer.addView(MaterialButton(context).apply {
            text = "Create New Homepage"
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 16)
            }
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setOnClickListener { showHomepageEditor(null) }
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
            plugin.refreshAllHomepages()
        })

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

    private fun createHomepageList(context: Context): RecyclerView {
        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            isNestedScrollingEnabled = false
            visibility = if (feedGroups.isEmpty()) View.GONE else View.VISIBLE
        }

        homepageAdapter = HomepageListAdapter(
            context = context,
            isTvMode = isTvMode,
            textColor = textColor,
            grayTextColor = grayTextColor,
            primaryColor = primaryColor,
            cardColor = cardColor,
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            },
            onReorderTap = { position ->
                onHomepageTappedForReorder(position)
            },
            onHomepageClick = { homepage ->
                if (!isReorderMode) {
                    showHomepageEditor(homepage)
                }
            },
            getFeedCount = { homepageId ->
                FeedAssignmentService.getFeedsInHomepage(feedList, homepageId).size
            }
        )
        recyclerView.adapter = homepageAdapter

        // Setup drag-and-drop (touch mode)
        if (!isTvMode) {
            val touchHelper = HomepageItemTouchHelper(homepageAdapter) {
                syncHomepagesFromAdapter()
            }
            itemTouchHelper = ItemTouchHelper(touchHelper)
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        }

        homepageAdapter.submitList(feedGroups)

        return recyclerView
    }

    private fun getSubtitleText(): String {
        return when {
            isReorderMode && selectedReorderPosition >= 0 ->
                "Tap destination to move homepage"
            isReorderMode ->
                "Tap a homepage to select, then tap destination"
            isTvMode ->
                "Tap to edit, or use Reorder button"
            else ->
                "Tap to edit. Drag ≡ to reorder. App restart required for changes."
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

        homepageSubtitle.text = getSubtitleText()
        homepageAdapter.setReorderMode(isReorderMode, -1)
    }

    private fun onHomepageTappedForReorder(position: Int) {
        if (!isReorderMode) return

        if (selectedReorderPosition < 0) {
            selectedReorderPosition = position
            homepageSubtitle.text = getSubtitleText()
            homepageAdapter.setReorderMode(true, position)
            restoreFocusToPosition(position)
        } else if (selectedReorderPosition == position) {
            selectedReorderPosition = -1
            homepageSubtitle.text = getSubtitleText()
            homepageAdapter.setReorderMode(true, -1)
            restoreFocusToPosition(position)
        } else {
            homepageAdapter.moveItem(selectedReorderPosition, position)
            syncHomepagesFromAdapter()

            selectedReorderPosition = -1
            homepageSubtitle.text = getSubtitleText()
            homepageAdapter.setReorderMode(true, -1)
            restoreFocusToPosition(position)
        }
    }

    private fun restoreFocusToPosition(position: Int) {
        homepageRecyclerView.post {
            if (!isAdded || !homepageRecyclerView.isAttachedToWindow) return@post
            val viewHolder = homepageRecyclerView.findViewHolderForAdapterPosition(position)
            viewHolder?.itemView?.requestFocus()
        }
    }

    private fun syncHomepagesFromAdapter() {
        feedGroups.clear()
        feedGroups.addAll(homepageAdapter.getHomepages())
        updatePriorities()
        saveData()
    }

    private fun updatePriorities() {
        feedGroups.forEachIndexed { index, group ->
            feedGroups[index] = group.copy(priority = feedGroups.size - index)
        }
    }

    private fun showHomepageEditor(existingHomepage: FeedGroup?) {
        val availableFeeds = getAvailableFeeds()

        val dialog = HomepageEditorDialog(
            existingGroup = existingHomepage,
            currentFeeds = feedList,
            availableFeeds = availableFeeds,
            showPluginNames = settings.showPluginNames,
            onSave = { group, feedsInHomepage, allFeeds ->
                val isNew = existingHomepage == null

                // Update homepages
                if (isNew) {
                    feedGroups.add(0, group)
                    updatePriorities()
                } else {
                    val index = feedGroups.indexOfFirst { it.id == group.id }
                    if (index >= 0) {
                        feedGroups[index] = group
                    }
                }

                // Update feeds
                feedList.clear()
                feedList.addAll(allFeeds)

                saveData()
                refreshUI()

                // Show restart reminder
                context?.let { ctx ->
                    android.widget.Toast.makeText(
                        ctx,
                        if (isNew) "Homepage created. Restart app to see it." else "Changes saved. Restart app to see updates.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDelete = if (existingHomepage != null) { { homepage ->
                deleteHomepage(homepage)
            } } else null
        )
        dialog.show(parentFragmentManager, "HomepageEditorDialog")
    }

    private fun deleteHomepage(homepage: FeedGroup) {
        // Remove homepage
        val index = feedGroups.indexOfFirst { it.id == homepage.id }
        if (index >= 0) {
            feedGroups.removeAt(index)
            updatePriorities()
        }

        // Clear feed assignments for this homepage
        feedList = FeedAssignmentService.clearHomepageAssignments(feedList, homepage.id).toMutableList()
        // Remove orphaned feeds
        feedList = feedList.filter { it.homepageIds.isNotEmpty() }.toMutableList()

        saveData()
        refreshUI()

        context?.let { ctx ->
            android.widget.Toast.makeText(
                ctx,
                "Homepage deleted. Restart app to see changes.",
                android.widget.Toast.LENGTH_LONG
            ).show()
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
        homepageAdapter.submitList(feedGroups.toList())
        updateEmptyState()
        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(mainContainer)
        }
    }

    private fun updateEmptyState() {
        val isEmpty = feedGroups.isEmpty()
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        homepageRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun saveData() {
        val feedsSaved = NsfwUltimaStorage.saveFeedList(feedList)
        val groupsSaved = NsfwUltimaStorage.saveGroups(feedGroups)

        if (!feedsSaved || !groupsSaved) {
            Log.e(TAG, "Failed to save: feeds=$feedsSaved, groups=$groupsSaved")
            showSaveErrorToast()
        }

        plugin.refreshAllHomepages()
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

    private fun showResetConfirmation(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Reset All Data")
            .setMessage("This will remove all your homepages, feeds, and settings. Are you sure?")
            .setPositiveButton("Reset") { _, _ ->
                val success = NsfwUltimaStorage.clearAll()
                if (success) {
                    feedList.clear()
                    feedGroups.clear()
                    settings = NsfwUltimaSettings()

                    homepageAdapter.submitList(emptyList())
                    updateEmptyState()
                    plugin.refreshAllHomepages()

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

/**
 * Adapter for displaying homepages with drag-and-drop (touch) and tap-to-reorder (TV).
 */
class HomepageListAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val textColor: Int,
    private val grayTextColor: Int,
    private val primaryColor: Int,
    private val cardColor: Int,
    private val onStartDrag: (viewHolder: RecyclerView.ViewHolder) -> Unit,
    private val onReorderTap: (position: Int) -> Unit,
    private val onHomepageClick: (FeedGroup) -> Unit,
    private val getFeedCount: (homepageId: String) -> Int
) : RecyclerView.Adapter<HomepageListAdapter.HomepageViewHolder>() {

    private val homepages = mutableListOf<FeedGroup>()
    private var reorderModeEnabled = false
    private var selectedPosition = -1

    fun setReorderMode(enabled: Boolean, selected: Int) {
        reorderModeEnabled = enabled
        selectedPosition = selected
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newHomepages: List<FeedGroup>) {
        homepages.clear()
        homepages.addAll(newHomepages)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = homepages.size

    fun getHomepages(): List<FeedGroup> = homepages.toList()

    fun moveItem(from: Int, to: Int) {
        if (from < 0 || from >= homepages.size || to < 0 || to >= homepages.size) return
        val item = homepages.removeAt(from)
        homepages.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomepageViewHolder {
        return createViewHolder()
    }

    override fun onBindViewHolder(holder: HomepageViewHolder, position: Int) {
        val homepage = homepages[position]
        bindHomepage(holder, homepage, position)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createViewHolder(): HomepageViewHolder {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            setBackgroundColor(cardColor)
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }

        // Drag handle (touch mode only)
        val dragHandle: TextView? = if (!isTvMode) {
            TextView(context).apply {
                text = "≡"
                textSize = 20f
                setTextColor(grayTextColor)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(12) }
            }.also { card.addView(it) }
        } else null

        // Content container
        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(context).apply {
            textSize = 16f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        contentContainer.addView(nameText)

        val countText = TextView(context).apply {
            textSize = 12f
            setTextColor(grayTextColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        }
        contentContainer.addView(countText)

        card.addView(contentContainer)

        // Arrow indicator
        val arrowText = TextView(context).apply {
            text = "→"
            textSize = 18f
            setTextColor(grayTextColor)
        }
        card.addView(arrowText)

        val holder = HomepageViewHolder(card, dragHandle, nameText, countText, arrowText)

        // Setup drag handle touch listener
        dragHandle?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }

        if (isTvMode) {
            TvFocusUtils.makeFocusable(card)
        }

        return holder
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindHomepage(holder: HomepageViewHolder, homepage: FeedGroup, position: Int) {
        val isSelected = reorderModeEnabled && position == selectedPosition
        val feedCount = getFeedCount(homepage.id)

        holder.itemView.tag = homepage.id
        holder.nameText.text = homepage.name
        holder.countText.text = "$feedCount feeds"
        holder.countText.setTextColor(if (feedCount > 0) primaryColor else grayTextColor)

        val card = holder.itemView as LinearLayout

        if (reorderModeEnabled) {
            if (isSelected) {
                card.setBackgroundColor((primaryColor and 0x00FFFFFF) or 0x30000000)
                holder.nameText.setTextColor(primaryColor)
            } else {
                card.setBackgroundColor(cardColor)
                holder.nameText.setTextColor(textColor)
            }

            holder.arrowText.visibility = View.GONE
            holder.dragHandle?.visibility = View.GONE

            card.isClickable = true
            card.isFocusable = true
            if (isTvMode) TvFocusUtils.makeFocusable(card, 8)
            card.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onReorderTap(pos)
                }
            }
        } else {
            card.setBackgroundColor(cardColor)
            holder.nameText.setTextColor(textColor)

            holder.arrowText.visibility = View.VISIBLE
            holder.dragHandle?.visibility = View.VISIBLE

            card.isClickable = true
            card.isFocusable = true
            if (isTvMode) TvFocusUtils.makeFocusable(card)
            card.setOnClickListener {
                onHomepageClick(homepage)
            }
        }
    }

    private fun dp(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    class HomepageViewHolder(
        itemView: View,
        val dragHandle: TextView?,
        val nameText: TextView,
        val countText: TextView,
        val arrowText: TextView
    ) : RecyclerView.ViewHolder(itemView)
}

/**
 * ItemTouchHelper callback for drag-and-drop reordering of homepages.
 */
class HomepageItemTouchHelper(
    private val adapter: HomepageListAdapter,
    private val onDragComplete: () -> Unit
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = false

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        onDragComplete()
    }
}
