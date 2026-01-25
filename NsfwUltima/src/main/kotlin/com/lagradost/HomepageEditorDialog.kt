package com.lagradost

import com.lagradost.common.DialogUtils
import com.lagradost.common.TvFocusUtils

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Combined dialog for editing a homepage: name, feeds (add/remove/reorder).
 * For creating new homepages, pass existingGroup = null.
 */
class HomepageEditorDialog(
    private val existingGroup: FeedGroup?,
    private val currentFeeds: List<FeedItem>,
    private val availableFeeds: List<AvailableFeed>,
    private val showPluginNames: Boolean,
    private val onSave: (group: FeedGroup, feedsInHomepage: List<FeedItem>, allFeeds: List<FeedItem>) -> Unit,
    private val onDelete: ((FeedGroup) -> Unit)? = null
) : DialogFragment() {

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    // Theme colors
    private lateinit var colors: DialogUtils.ThemeColors
    private val textColor get() = colors.textColor
    private val grayTextColor get() = colors.grayTextColor
    private val backgroundColor get() = colors.backgroundColor
    private val cardColor get() = colors.cardColor
    private val primaryColor get() = colors.primaryColor

    private lateinit var mainContainer: LinearLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var nameLayout: TextInputLayout
    private lateinit var feedsRecyclerView: RecyclerView
    private lateinit var feedsAdapter: HomepageFeedAdapter
    private lateinit var availableFeedsContainer: LinearLayout
    private lateinit var emptyFeedsText: TextView
    private lateinit var feedsCountHeader: TextView
    private var itemTouchHelper: ItemTouchHelper? = null

    // Working copies
    private val homepageFeeds = mutableListOf<FeedItem>()
    private var filterQuery = ""

    // Reorder mode (TV)
    private var isReorderMode = false
    private var selectedReorderPosition = -1
    private var reorderButton: MaterialButton? = null
    private var reorderSubtitle: TextView? = null

    // Working group ID - either from existing group or generated upfront for new homepages
    private val workingGroupId: String by lazy {
        existingGroup?.id ?: "group_${java.util.UUID.randomUUID().toString().take(8)}"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = DialogUtils.resolveThemeColors(context)

        // Initialize working copy of feeds in this homepage
        homepageFeeds.clear()
        homepageFeeds.addAll(
            currentFeeds.filter { it.isInHomepage(workingGroupId) }
        )

        val contentView = createDialogView(context)
        return DialogUtils.createTvOrBottomSheetDialog(context, isTvMode, theme, contentView, 0.95)
    }

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

    override fun onDismiss(dialog: android.content.DialogInterface) {
        // Autosave on dismiss
        saveHomepage()
        super.onDismiss(dialog)
    }

    private fun createDialogView(context: Context): View {
        val scrollView = NestedScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(backgroundColor)
        }

        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        // Title
        val title = if (existingGroup != null) "Edit Homepage" else "Create Homepage"
        mainContainer.addView(TextView(context).apply {
            text = title
            textSize = 20f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })

        // Homepage name input
        nameLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
            hint = "Homepage name"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        nameInput = TextInputEditText(context).apply {
            setTextColor(textColor)
            setText(existingGroup?.name ?: "")
        }
        nameLayout.addView(nameInput)

        if (isTvMode) {
            TvFocusUtils.makeFocusableTextInput(nameLayout, primaryColor)
        } else {
            nameLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(primaryColor))
        }

        mainContainer.addView(nameLayout)

        // Delete button (only for existing homepages)
        if (existingGroup != null && onDelete != null) {
            val deleteButton = MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "Delete Homepage"
                textSize = 14f
                minimumWidth = 0
                minimumHeight = 0
                minWidth = 0
                minHeight = dp(40)
                insetTop = 0
                insetBottom = 0
                setPadding(dp(16), 0, dp(16), 0)
                val warningColor = 0xFFE53935.toInt()
                strokeColor = ColorStateList.valueOf(warningColor)
                setTextColor(warningColor)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    DialogUtils.showDeleteConfirmation(
                        context = context,
                        itemName = existingGroup.name,
                        itemType = "homepage"
                    ) {
                        if (!isAdded) return@showDeleteConfirmation
                        onDelete(existingGroup)
                        dismiss()
                    }
                }
                if (isTvMode) TvFocusUtils.makeFocusable(this)
            }
            mainContainer.addView(deleteButton)
        }

        // Feed management section (shown for both new and existing homepages)
        addFeedManagementSection(context)

        scrollView.addView(mainContainer)

        if (isTvMode) {
            TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, feedsRecyclerView)
        }

        return scrollView
    }

    private fun addFeedManagementSection(context: Context) {
        // Divider
        mainContainer.addView(createDivider(context))

        // Feeds header row
        val feedsHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        feedsCountHeader = TextView(context).apply {
            text = "Feeds in This Homepage (${homepageFeeds.size})"
            textSize = 14f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        feedsHeaderRow.addView(feedsCountHeader)

        // Reorder button (TV mode)
        if (isTvMode) {
            reorderButton = MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "Reorder"
                textSize = 12f
                minimumWidth = 0
                minimumHeight = 0
                minWidth = 0
                minHeight = dp(32)
                insetTop = 0
                insetBottom = 0
                setPadding(dp(12), 0, dp(12), 0)
                strokeColor = ColorStateList.valueOf(primaryColor)
                setTextColor(primaryColor)
                setOnClickListener { toggleReorderMode(context) }
                TvFocusUtils.makeFocusable(this)
                visibility = if (homepageFeeds.size > 1) View.VISIBLE else View.GONE
            }
            feedsHeaderRow.addView(reorderButton)
        }

        mainContainer.addView(feedsHeaderRow)

        // Reorder subtitle
        reorderSubtitle = TextView(context).apply {
            text = if (isTvMode) "Tap Reorder to change order" else "Drag ≡ to reorder"
            textSize = 13f
            setTextColor(grayTextColor)
            setPadding(0, dp(4), 0, dp(8))
        }
        mainContainer.addView(reorderSubtitle!!)

        // Empty state
        emptyFeedsText = TextView(context).apply {
            text = "No feeds in this homepage yet.\nAdd feeds from below."
            textSize = 13f
            setTextColor(grayTextColor)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
            visibility = if (homepageFeeds.isEmpty()) View.VISIBLE else View.GONE
        }
        mainContainer.addView(emptyFeedsText)

        // Feeds RecyclerView
        setupFeedsRecyclerView(context)
        mainContainer.addView(feedsRecyclerView)

        // Divider before available feeds
        mainContainer.addView(createDivider(context))

        // Available feeds section
        mainContainer.addView(TextView(context).apply {
            text = "Add Feeds"
            textSize = 14f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        // Filter input
        val filterLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            hint = "Filter feeds..."
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        }

        val filterInput = TextInputEditText(context).apply {
            setTextColor(textColor)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filterQuery = s?.toString()?.lowercase()?.trim() ?: ""
                    rebuildAvailableFeeds(context)
                }
            })
        }
        filterLayout.addView(filterInput)

        if (isTvMode) {
            TvFocusUtils.makeFocusableTextInput(filterLayout, primaryColor)
        } else {
            filterLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(primaryColor))
        }

        mainContainer.addView(filterLayout)

        // Available feeds container
        availableFeedsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainContainer.addView(availableFeedsContainer)

        rebuildAvailableFeeds(context)
    }

    private fun setupFeedsRecyclerView(context: Context) {
        feedsAdapter = HomepageFeedAdapter(
            context = context,
            isTvMode = isTvMode,
            textColor = textColor,
            grayTextColor = grayTextColor,
            primaryColor = primaryColor,
            showPluginNames = showPluginNames,
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            },
            onReorderTap = { position ->
                onFeedTappedForReorder(position)
            },
            onRemove = { feed ->
                DialogUtils.showDeleteConfirmation(
                    context = context,
                    itemName = feed.sectionName,
                    itemType = "feed"
                ) {
                    if (!isAdded) return@showDeleteConfirmation
                    removeFeed(feed)
                    context.let { rebuildAvailableFeeds(it) }
                }
            }
        )

        feedsRecyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            adapter = feedsAdapter
            isNestedScrollingEnabled = false
            visibility = if (homepageFeeds.isEmpty()) View.GONE else View.VISIBLE
        }

        // Setup drag-and-drop (touch mode)
        if (!isTvMode) {
            val touchHelper = HomepageFeedTouchHelper(
                adapter = feedsAdapter,
                onMove = { fromPosition, toPosition ->
                    // Move in adapter and sync back to our homepageFeeds list
                    val updatedList = feedsAdapter.moveItem(fromPosition, toPosition)
                    homepageFeeds.clear()
                    homepageFeeds.addAll(updatedList)
                },
                onDragComplete = {
                    // UI is already synced from onMove calls
                }
            )
            itemTouchHelper = ItemTouchHelper(touchHelper)
            itemTouchHelper?.attachToRecyclerView(feedsRecyclerView)
        }

        feedsAdapter.submitList(homepageFeeds)
    }

    private fun rebuildAvailableFeeds(context: Context, restoreFocusToKey: String? = null) {
        availableFeedsContainer.removeAllViews()

        // Get feeds assigned to OTHER homepages (not the one being edited/created)
        val assignedToOtherHomepages = currentFeeds
            .filter { feed ->
                val otherHomepageIds = feed.homepageIds - workingGroupId
                otherHomepageIds.isNotEmpty()
            }
            .map { it.key() }
            .toSet()

        // Get feeds in this homepage's working copy
        val inCurrentHomepage = homepageFeeds.map { it.key() }.toSet()

        // Exclude feeds that are in any homepage
        val excludeKeys = assignedToOtherHomepages + inCurrentHomepage

        // Filter available feeds
        val filtered = availableFeeds
            .filter { it.key() !in excludeKeys }
            .filter {
                filterQuery.isEmpty() ||
                it.sectionName.lowercase().contains(filterQuery) ||
                it.pluginName.lowercase().contains(filterQuery)
            }

        if (filtered.isEmpty()) {
            availableFeedsContainer.addView(TextView(context).apply {
                text = if (filterQuery.isNotEmpty()) "No feeds match your filter" else "All available feeds have been added"
                textSize = 13f
                setTextColor(grayTextColor)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(16), dp(16), dp(16))
            })
            return
        }

        // Group by plugin
        val feedsByPlugin = filtered.groupBy { it.pluginName }

        feedsByPlugin.forEach { (pluginName, feeds) ->
            // Plugin header
            availableFeedsContainer.addView(TextView(context).apply {
                text = pluginName
                textSize = 12f
                setTextColor(grayTextColor)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(8), 0, dp(8))
            })

            // Chips for feeds
            val chipGroup = ChipGroup(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                isSingleLine = false
            }

            feeds.forEach { feed ->
                val chip = createFeedChip(context, feed)
                chipGroup.addView(chip)
            }

            availableFeedsContainer.addView(chipGroup)
        }

        if (isTvMode) {
            TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, feedsRecyclerView)

            // Restore focus to next chip (or first available if specified key not found)
            if (restoreFocusToKey != null) {
                mainContainer.post {
                    if (!isAdded || !mainContainer.isAttachedToWindow) return@post
                    val targetChip = findChipByKey(restoreFocusToKey)
                    if (targetChip != null) {
                        targetChip.requestFocus()
                    } else {
                        // Fall back to first available chip
                        findFirstChip()?.requestFocus()
                    }
                }
            }
        }
    }

    /**
     * Collect all chips from the available feeds container in order.
     * Returns list of pairs: (chip view, chip key).
     */
    private fun collectAllChips(): List<Pair<View, String>> {
        val chips = mutableListOf<Pair<View, String>>()
        for (i in 0 until availableFeedsContainer.childCount) {
            val child = availableFeedsContainer.getChildAt(i)
            if (child is ChipGroup) {
                for (j in 0 until child.childCount) {
                    val chip = child.getChildAt(j)
                    (chip.tag as? String)?.let { chips.add(chip to it) }
                }
            }
        }
        return chips
    }

    /**
     * Find a chip by its tag key in the available feeds container.
     */
    private fun findChipByKey(key: String): View? =
        collectAllChips().find { it.second == key }?.first

    /**
     * Find the first chip in the available feeds container.
     */
    private fun findFirstChip(): View? =
        collectAllChips().firstOrNull()?.first

    /**
     * Find the key of the next available chip after the clicked one.
     * Returns null if no more chips will be available.
     */
    private fun findNextChipKey(clickedKey: String): String? {
        val chipKeys = collectAllChips().map { it.second }
        val clickedIndex = chipKeys.indexOf(clickedKey)
        return when {
            clickedIndex < 0 -> chipKeys.firstOrNull()
            clickedIndex < chipKeys.size - 1 -> chipKeys[clickedIndex + 1]
            clickedIndex > 0 -> chipKeys[clickedIndex - 1]  // Last chip, go to previous
            else -> null  // Only one chip
        }
    }

    private fun createFeedChip(context: Context, feed: AvailableFeed): Chip {
        return Chip(context).apply {
            tag = feed.key()
            text = "+ ${feed.sectionName}"
            isCheckable = false
            isClickable = true
            setTextColor(primaryColor)
            chipBackgroundColor = ColorStateList.valueOf(cardColor)
            chipStrokeColor = ColorStateList.valueOf(primaryColor)
            chipStrokeWidth = dp(1).toFloat()

            setOnClickListener {
                // Find the next chip to focus before adding the feed
                val nextChipKey = if (isTvMode) findNextChipKey(feed.key()) else null
                addFeed(feed)
                rebuildAvailableFeeds(context, nextChipKey)
            }

            if (isTvMode) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    private fun addFeed(availableFeed: AvailableFeed) {
        val newFeed = FeedItem(
            pluginName = availableFeed.pluginName,
            sectionName = availableFeed.sectionName,
            sectionData = availableFeed.sectionData,
            homepageIds = setOf(workingGroupId)
        )
        homepageFeeds.add(newFeed)
        updateFeedsUI()
    }

    private fun removeFeed(feed: FeedItem) {
        homepageFeeds.removeAll { it.key() == feed.key() }
        updateFeedsUI()
    }

    private fun updateFeedsUI() {
        feedsAdapter.submitList(homepageFeeds.toList())
        emptyFeedsText.visibility = if (homepageFeeds.isEmpty()) View.VISIBLE else View.GONE
        feedsRecyclerView.visibility = if (homepageFeeds.isEmpty()) View.GONE else View.VISIBLE

        // Update feed count header
        if (::feedsCountHeader.isInitialized) {
            feedsCountHeader.text = "Feeds in This Homepage (${homepageFeeds.size})"
        }

        // Update reorder button visibility (only useful with 2+ feeds)
        reorderButton?.visibility = if (homepageFeeds.size > 1) View.VISIBLE else View.GONE

        // Re-enable focus loop after list mutation (TV mode)
        if (isTvMode) {
            TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, feedsRecyclerView)
        }
    }

    private fun toggleReorderMode(context: Context) {
        isReorderMode = !isReorderMode
        selectedReorderPosition = -1

        reorderButton?.apply {
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
                strokeWidth = dp(1)
            }
        }

        reorderSubtitle?.text = when {
            isReorderMode && selectedReorderPosition >= 0 -> "Tap destination to move feed"
            isReorderMode -> "Tap a feed to select, then tap destination"
            else -> "Tap Reorder to change order"
        }

        feedsAdapter.setReorderMode(isReorderMode, -1)
    }

    private fun onFeedTappedForReorder(position: Int) {
        if (!isReorderMode) return

        if (selectedReorderPosition < 0) {
            selectedReorderPosition = position
            reorderSubtitle?.text = "Tap destination to move feed"
            feedsAdapter.setReorderMode(true, position)
            restoreFocusToPosition(position)
        } else if (selectedReorderPosition == position) {
            selectedReorderPosition = -1
            reorderSubtitle?.text = "Tap a feed to select, then tap destination"
            feedsAdapter.setReorderMode(true, -1)
            restoreFocusToPosition(position)
        } else {
            // Move the feed
            val item = homepageFeeds.removeAt(selectedReorderPosition)
            homepageFeeds.add(position, item)
            updateFeedsUI()

            selectedReorderPosition = -1
            reorderSubtitle?.text = "Tap a feed to select, then tap destination"
            feedsAdapter.setReorderMode(true, -1)
            restoreFocusToPosition(position)
        }
    }

    private fun restoreFocusToPosition(position: Int) {
        feedsRecyclerView.post {
            if (!isAdded || !feedsRecyclerView.isAttachedToWindow) return@post
            val viewHolder = feedsRecyclerView.findViewHolderForAdapterPosition(position)
            viewHolder?.itemView?.requestFocus()
        }
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                setMargins(0, dp(16), 0, dp(16))
            }
            setBackgroundColor((grayTextColor and 0x00FFFFFF) or 0x30000000)
        }
    }

    private fun saveHomepage() {
        val name = nameInput.text?.toString()?.trim()

        // For new homepages, silently skip save if no name entered (discard)
        // For existing homepages, always save (use existing name as fallback)
        val finalName = when {
            !name.isNullOrBlank() -> name
            existingGroup != null -> existingGroup.name
            else -> return  // New homepage with no name - discard
        }

        val group = if (existingGroup != null) {
            existingGroup.copy(name = finalName)
        } else {
            FeedGroup(id = workingGroupId, name = finalName)
        }

        // Build the updated all-feeds list
        val allFeedsUpdated = currentFeeds.toMutableList()

        // Remove this homepage from all current feeds (only when editing existing homepage)
        if (existingGroup != null) {
            allFeedsUpdated.replaceAll { feed ->
                if (feed.isInHomepage(workingGroupId)) {
                    feed.copy(homepageIds = feed.homepageIds - workingGroupId)
                } else {
                    feed
                }
            }
        }

        // Add new feeds that don't exist yet, and assign homepage to existing ones
        val existingKeys = allFeedsUpdated.map { it.key() }.toSet()
        homepageFeeds.forEach { homepageFeed ->
            if (homepageFeed.key() in existingKeys) {
                // Update existing feed to include this homepage
                allFeedsUpdated.replaceAll { feed ->
                    if (feed.key() == homepageFeed.key()) {
                        feed.copy(homepageIds = feed.homepageIds + workingGroupId)
                    } else {
                        feed
                    }
                }
            } else {
                // Add new feed with this homepage
                allFeedsUpdated.add(homepageFeed.copy(homepageIds = setOf(workingGroupId)))
            }
        }

        // Remove orphaned feeds (no homepage assignments)
        val cleanedFeeds = allFeedsUpdated.filter { it.homepageIds.isNotEmpty() }

        onSave(group, homepageFeeds, cleanedFeeds)
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(requireContext(), dp)
}

/**
 * Adapter for feeds within a homepage (for reordering and removal).
 */
class HomepageFeedAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val textColor: Int,
    private val grayTextColor: Int,
    private val primaryColor: Int,
    private val showPluginNames: Boolean,
    private val onStartDrag: (viewHolder: RecyclerView.ViewHolder) -> Unit,
    private val onReorderTap: (position: Int) -> Unit,
    private val onRemove: (FeedItem) -> Unit
) : RecyclerView.Adapter<HomepageFeedAdapter.FeedViewHolder>() {

    private val feeds = mutableListOf<FeedItem>()
    private var reorderModeEnabled = false
    private var selectedPosition = -1

    fun setReorderMode(enabled: Boolean, selected: Int) {
        reorderModeEnabled = enabled
        selectedPosition = selected
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newFeeds: List<FeedItem>) {
        feeds.clear()
        feeds.addAll(newFeeds)
        notifyDataSetChanged()
    }

    /**
     * Move item from one position to another for drag-and-drop.
     * Returns the updated list for syncing back to the dialog's source list.
     */
    fun moveItem(fromPosition: Int, toPosition: Int): List<FeedItem> {
        if (fromPosition < 0 || fromPosition >= feeds.size ||
            toPosition < 0 || toPosition >= feeds.size) {
            return feeds.toList()
        }
        val item = feeds.removeAt(fromPosition)
        feeds.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
        return feeds.toList()
    }

    override fun getItemCount(): Int = feeds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        return createFeedViewHolder()
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val feed = feeds[position]
        bindFeed(holder, feed, position)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFeedViewHolder(): FeedViewHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
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
            }.also { row.addView(it) }
        } else null

        val nameText = TextView(context).apply {
            textSize = 14f
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        row.addView(nameText)

        val deleteButton = ImageButton(context).apply {
            val closeIconId = context.resources.getIdentifier(
                "ic_baseline_close_24", "drawable", context.packageName
            )
            if (closeIconId != 0) {
                val drawable = ContextCompat.getDrawable(context, closeIconId)
                drawable?.setTint(grayTextColor)
                setImageDrawable(drawable)
            } else {
                // Fallback to text
                setImageDrawable(null)
            }
            contentDescription = "Remove"
            setBackgroundColor(0)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        row.addView(deleteButton)

        if (isTvMode) {
            TvFocusUtils.makeFocusable(deleteButton)
        }

        val holder = FeedViewHolder(row, dragHandle, nameText, deleteButton)

        // Setup drag handle touch listener
        dragHandle?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }

        return holder
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindFeed(holder: FeedViewHolder, feed: FeedItem, position: Int) {
        val isSelected = reorderModeEnabled && position == selectedPosition

        holder.itemView.tag = feed.key()

        holder.nameText.text = if (showPluginNames) {
            "[${feed.pluginName}] ${feed.sectionName}"
        } else {
            feed.sectionName
        }

        val row = holder.itemView as LinearLayout

        if (reorderModeEnabled) {
            if (isSelected) {
                row.setBackgroundColor((primaryColor and 0x00FFFFFF) or 0x30000000)
                holder.nameText.setTextColor(primaryColor)
            } else {
                row.setBackgroundColor(0)
                holder.nameText.setTextColor(textColor)
            }

            holder.dragHandle?.visibility = View.GONE
            holder.deleteButton.visibility = View.GONE

            row.isClickable = true
            row.isFocusable = true
            if (isTvMode) TvFocusUtils.makeFocusable(row, 8)
            row.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onReorderTap(pos)
                }
            }
        } else {
            row.setBackgroundColor(0)
            holder.nameText.setTextColor(textColor)

            holder.dragHandle?.visibility = View.VISIBLE
            holder.deleteButton.visibility = View.VISIBLE

            holder.deleteButton.setOnClickListener { onRemove(feed) }

            row.isFocusable = false
            row.isClickable = false
            row.setOnClickListener(null)
        }
    }

    private fun dp(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    class FeedViewHolder(
        itemView: View,
        val dragHandle: TextView?,
        val nameText: TextView,
        val deleteButton: ImageButton
    ) : RecyclerView.ViewHolder(itemView)
}

/**
 * ItemTouchHelper callback for drag-and-drop reordering.
 */
class HomepageFeedTouchHelper(
    private val adapter: HomepageFeedAdapter,
    private val onMove: (fromPosition: Int, toPosition: Int) -> Unit,
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
        val fromPosition = viewHolder.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition
        if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
            onMove(fromPosition, toPosition)
        }
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
