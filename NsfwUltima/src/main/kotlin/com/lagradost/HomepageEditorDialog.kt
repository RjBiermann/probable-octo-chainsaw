package com.lagradost

import com.lagradost.common.CloudstreamUI
import com.lagradost.common.DialogUtils
import com.lagradost.common.TvFocusUtils

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
 * Combined dialog for editing a homepage: name and feed selection.
 * Uses a single unified list where feeds are toggled on/off.
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

    // Theme colors using CloudstreamUI
    private lateinit var colors: CloudstreamUI.UIColors
    private val textColor get() = colors.text
    private val grayTextColor get() = colors.textGray
    private val backgroundColor get() = colors.background
    private val cardColor get() = colors.card
    private val primaryColor get() = colors.primary
    private val onPrimaryColor get() = colors.onPrimary

    private lateinit var mainContainer: LinearLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var nameLayout: TextInputLayout
    private lateinit var feedsContainer: LinearLayout
    private lateinit var selectedCountText: TextView
    private lateinit var reorderButton: MaterialButton

    // Working copies - selected feeds maintain order
    private val selectedFeeds = mutableListOf<AvailableFeed>()
    private var filterQuery = ""

    // Working group ID
    private val workingGroupId: String by lazy {
        existingGroup?.id ?: "group_${java.util.UUID.randomUUID().toString().take(8)}"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = CloudstreamUI.UIColors.fromContext(context)

        // Initialize selected feeds from existing homepage feeds
        selectedFeeds.clear()
        currentFeeds
            .filter { it.isInHomepage(workingGroupId) }
            .forEach { feedItem ->
                // Convert FeedItem back to AvailableFeed for unified handling
                selectedFeeds.add(AvailableFeed(
                    pluginName = feedItem.pluginName,
                    sectionName = feedItem.sectionName,
                    sectionData = feedItem.sectionData
                ))
            }

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
        if (isAdded) {
            saveHomepage()
        }
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
        mainContainer.addView(CloudstreamUI.createDialogTitle(context, title, colors).apply {
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
            val deleteButton = CloudstreamUI.createDangerButton(context, "Delete Homepage", colors) {
                DialogUtils.showDeleteConfirmation(
                    context = context,
                    itemName = existingGroup.name,
                    itemType = "homepage"
                ) {
                    if (!isAdded) return@showDeleteConfirmation
                    onDelete(existingGroup)
                    dismiss()
                }
            }.apply {
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            mainContainer.addView(deleteButton)
        }

        // Divider
        mainContainer.addView(CloudstreamUI.createDivider(context, colors))

        // Feeds header row with count and reorder button
        val feedsHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        selectedCountText = CloudstreamUI.createBodyText(context, "", colors).apply {
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        feedsHeaderRow.addView(selectedCountText)

        reorderButton = CloudstreamUI.createCompactOutlinedButton(context, "Reorder", colors) {
            showReorderDialog(context)
        }
        feedsHeaderRow.addView(reorderButton)

        mainContainer.addView(feedsHeaderRow)

        // Subtitle
        mainContainer.addView(CloudstreamUI.createCaptionText(context, "Tap feeds to add or remove them", colors).apply {
            textSize = 13f
            setPadding(0, dp(4), 0, dp(12))
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
                    rebuildFeedsList(context)
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

        // Unified feeds container
        feedsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainContainer.addView(feedsContainer)

        scrollView.addView(mainContainer)

        // Initial render
        rebuildFeedsList(context)
        updateHeader()

        return scrollView
    }

    private fun rebuildFeedsList(context: Context) {
        // Save focused chip tag for TV mode focus restoration
        val focusedTag = if (isTvMode) feedsContainer.findFocus()?.tag else null

        feedsContainer.removeAllViews()

        // Get feeds already assigned to OTHER homepages (can't be selected here)
        val assignedToOtherHomepages = currentFeeds
            .filter { feed ->
                val otherHomepageIds = feed.homepageIds - workingGroupId
                otherHomepageIds.isNotEmpty()
            }
            .map { it.key() }
            .toSet()

        // Filter available feeds
        val filteredFeeds = availableFeeds
            .filter { it.key() !in assignedToOtherHomepages }
            .filter {
                filterQuery.isEmpty() ||
                it.sectionName.lowercase().contains(filterQuery) ||
                it.pluginName.lowercase().contains(filterQuery)
            }

        if (filteredFeeds.isEmpty()) {
            val emptyMessage = if (filterQuery.isNotEmpty()) "No feeds match your filter" else "No feeds available"
            feedsContainer.addView(CloudstreamUI.createEmptyState(context, emptyMessage, colors).apply {
                textSize = 13f
            })
            return
        }

        // Group by plugin
        val feedsByPlugin = filteredFeeds.groupBy { it.pluginName }

        feedsByPlugin.forEach { (pluginName, feeds) ->
            // Plugin header
            feedsContainer.addView(CloudstreamUI.createCaptionText(context, pluginName, colors).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(8))
            })

            // Chips for feeds
            val chipGroup = CloudstreamUI.createChipGroup(context)

            feeds.forEach { feed ->
                val isSelected = selectedFeeds.any { it.key() == feed.key() }
                val chip = createFeedChip(context, feed, isSelected)
                chipGroup.addView(chip)
            }

            feedsContainer.addView(chipGroup)
        }

        // Restore focus for TV mode
        if (isTvMode && focusedTag != null) {
            feedsContainer.post {
                if (isAdded && feedsContainer.isAttachedToWindow) {
                    feedsContainer.findViewWithTag<View>(focusedTag)?.requestFocus()
                }
            }
        }
    }

    private fun createFeedChip(context: Context, feed: AvailableFeed, isSelected: Boolean): Chip {
        return Chip(context).apply {
            tag = feed.key()

            if (isSelected) {
                val position = selectedFeeds.indexOfFirst { it.key() == feed.key() } + 1
                text = "$position. ${feed.sectionName}"
                isChecked = true
                setTextColor(onPrimaryColor)
                chipBackgroundColor = ColorStateList.valueOf(primaryColor)
                chipStrokeWidth = 0f
            } else {
                text = "+ ${feed.sectionName}"
                isChecked = false
                setTextColor(primaryColor)
                chipBackgroundColor = ColorStateList.valueOf(cardColor)
                chipStrokeColor = ColorStateList.valueOf(primaryColor)
                chipStrokeWidth = dp(1).toFloat()
            }

            isCheckable = false
            isClickable = true

            // Content description for accessibility
            contentDescription = if (isSelected) {
                "${feed.sectionName}, selected. Tap to remove from homepage."
            } else {
                "${feed.sectionName}. Tap to add to homepage."
            }

            setOnClickListener {
                toggleFeed(feed)
                rebuildFeedsList(context)
                updateHeader()
            }

            if (isTvMode) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    private fun toggleFeed(feed: AvailableFeed) {
        val existingIndex = selectedFeeds.indexOfFirst { it.key() == feed.key() }
        if (existingIndex >= 0) {
            selectedFeeds.removeAt(existingIndex)
        } else {
            selectedFeeds.add(feed)
        }
    }

    private fun updateHeader() {
        val count = selectedFeeds.size
        selectedCountText.text = "Selected Feeds ($count)"
        reorderButton.visibility = if (count > 1) View.VISIBLE else View.GONE
    }

    private fun showReorderDialog(context: Context) {
        if (selectedFeeds.size < 2) return

        val dialog = ReorderFeedsDialog(
            feeds = selectedFeeds.toList(),
            showPluginNames = showPluginNames,
            onReorder = { reorderedFeeds ->
                selectedFeeds.clear()
                selectedFeeds.addAll(reorderedFeeds)
                rebuildFeedsList(context)
            }
        )
        dialog.show(parentFragmentManager, "ReorderFeedsDialog")
    }

    private fun saveHomepage() {
        if (!isAdded || !::nameInput.isInitialized) return
        val name = nameInput.text?.toString()?.trim()

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

        // Build FeedItem list for this homepage
        val homepageFeeds = selectedFeeds.map { availableFeed ->
            FeedItem(
                pluginName = availableFeed.pluginName,
                sectionName = availableFeed.sectionName,
                sectionData = availableFeed.sectionData,
                homepageIds = setOf(workingGroupId)
            )
        }

        // Build the updated all-feeds list
        val allFeedsUpdated = currentFeeds.toMutableList()

        // Remove this homepage from all current feeds
        if (existingGroup != null) {
            allFeedsUpdated.replaceAll { feed ->
                if (feed.isInHomepage(workingGroupId)) {
                    feed.copy(homepageIds = feed.homepageIds - workingGroupId)
                } else {
                    feed
                }
            }
        }

        // Add selected feeds
        val existingKeys = allFeedsUpdated.map { it.key() }.toSet()
        homepageFeeds.forEach { homepageFeed ->
            if (homepageFeed.key() in existingKeys) {
                allFeedsUpdated.replaceAll { feed ->
                    if (feed.key() == homepageFeed.key()) {
                        feed.copy(homepageIds = feed.homepageIds + workingGroupId)
                    } else {
                        feed
                    }
                }
            } else {
                allFeedsUpdated.add(homepageFeed.copy(homepageIds = setOf(workingGroupId)))
            }
        }

        // Remove orphaned feeds
        val cleanedFeeds = allFeedsUpdated.filter { it.homepageIds.isNotEmpty() }

        onSave(group, homepageFeeds, cleanedFeeds)
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(requireContext(), dp)
}

/**
 * Simple dialog for reordering selected feeds.
 * Touch mode: drag ≡ handle to reorder
 * TV mode: tap to select, tap destination to move
 * Changes are saved automatically on dismiss.
 */
class ReorderFeedsDialog(
    private val feeds: List<AvailableFeed>,
    private val showPluginNames: Boolean,
    private val onReorder: (List<AvailableFeed>) -> Unit
) : DialogFragment() {

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }
    private lateinit var colors: CloudstreamUI.UIColors
    private lateinit var adapter: ReorderAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var mainContainer: LinearLayout
    private lateinit var subtitle: TextView
    private var itemTouchHelper: ItemTouchHelper? = null

    // Working copy
    private val workingFeeds = feeds.toMutableList()

    // Selection state (TV mode)
    private var selectedPosition = -1

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = CloudstreamUI.UIColors.fromContext(context)

        val contentView = createDialogView(context)
        return DialogUtils.createTvOrBottomSheetDialog(context, isTvMode, theme, contentView, 0.9)
    }

    override fun onStart() {
        super.onStart()
        if (isTvMode) {
            dialog?.window?.decorView?.post {
                if (isAdded && ::recyclerView.isInitialized && recyclerView.isAttachedToWindow) {
                    // Focus first RV item directly
                    recyclerView.post {
                        if (!isAdded || !recyclerView.isAttachedToWindow) return@post
                        recyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        if (isAdded) {
            onReorder(workingFeeds.toList())
        }
        super.onDismiss(dialog)
    }

    private fun createDialogView(context: Context): View {
        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(colors.background)
        }

        // Title
        mainContainer.addView(CloudstreamUI.createHeaderText(context, "Reorder Feeds", colors).apply {
            setPadding(0, 0, 0, dp(4))
        })

        // Subtitle
        val subtitleText = if (isTvMode) "Tap to select, tap destination to move" else "Drag ≡ to reorder"
        subtitle = CloudstreamUI.createCaptionText(context, subtitleText, colors).apply {
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        }
        mainContainer.addView(subtitle)

        // RecyclerView
        adapter = ReorderAdapter(
            context = context,
            isTvMode = isTvMode,
            colors = colors,
            showPluginNames = showPluginNames,
            onStartDrag = { holder -> itemTouchHelper?.startDrag(holder) },
            onTap = { position -> onItemTapped(position) }
        )
        adapter.submitList(workingFeeds)

        recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            adapter = this@ReorderFeedsDialog.adapter
            isNestedScrollingEnabled = false
        }

        // Touch helper for drag (non-TV)
        if (!isTvMode) {
            val touchHelper = object : ItemTouchHelper.Callback() {
                override fun isLongPressDragEnabled() = false
                override fun isItemViewSwipeEnabled() = false
                override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

                override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                    val fromPos = from.bindingAdapterPosition
                    val toPos = to.bindingAdapterPosition
                    if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                        val item = workingFeeds.removeAt(fromPos)
                        workingFeeds.add(toPos, item)
                        adapter.notifyItemMoved(fromPos, toPos)
                    }
                    return true
                }

                override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
            }
            itemTouchHelper = ItemTouchHelper(touchHelper)
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        }

        mainContainer.addView(recyclerView)

        return mainContainer
    }

    private fun onItemTapped(position: Int) {
        if (!isTvMode) return

        if (selectedPosition < 0) {
            // First tap - select item
            selectedPosition = position
            subtitle.text = "Tap destination to move"
            adapter.setSelectedPosition(position)
        } else if (selectedPosition == position) {
            // Tap same item - deselect
            selectedPosition = -1
            subtitle.text = "Tap to select, tap destination to move"
            adapter.setSelectedPosition(-1)
        } else {
            // Tap different item - move selected to this position
            val item = workingFeeds.removeAt(selectedPosition)
            workingFeeds.add(position, item)
            adapter.submitList(workingFeeds)

            selectedPosition = -1
            subtitle.text = "Tap to select, tap destination to move"
            adapter.setSelectedPosition(-1)

            // Restore focus to moved item's new position
            restoreFocusToPosition(position)
        }
    }

    private fun restoreFocusToPosition(position: Int) {
        recyclerView.post {
            if (!isAdded || !recyclerView.isAttachedToWindow) return@post
            recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        if (::recyclerView.isInitialized) {
            recyclerView.adapter = null
        }
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(requireContext(), dp)
}

/**
 * Adapter for reorder dialog.
 */
class ReorderAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val colors: CloudstreamUI.UIColors,
    private val showPluginNames: Boolean,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onTap: (Int) -> Unit
) : RecyclerView.Adapter<ReorderAdapter.ViewHolder>() {

    private val items = mutableListOf<AvailableFeed>()
    private var selectedPos = -1

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<AvailableFeed>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        val oldPos = selectedPos
        selectedPos = position
        // Only update the specific items that changed, preserving focus
        if (oldPos >= 0 && oldPos < items.size) {
            notifyItemChanged(oldPos, PAYLOAD_SELECTION)
        }
        if (position >= 0 && position < items.size) {
            notifyItemChanged(position, PAYLOAD_SELECTION)
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            // Partial update - only update selection visuals, preserving focus
            val isSelected = position == selectedPos
            val row = holder.itemView as LinearLayout
            if (isSelected) {
                row.setBackgroundColor((colors.primary and 0x00FFFFFF) or 0x30000000)
                holder.nameText.setTextColor(colors.primary)
            } else {
                row.setBackgroundColor(0)
                holder.nameText.setTextColor(colors.text)
            }
        } else {
            // Full bind
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Drag handle (touch mode only)
        val dragHandle: TextView? = if (!isTvMode) {
            TextView(context).apply {
                text = "≡"
                textSize = 20f
                setTextColor(colors.textGray)
                contentDescription = "Drag to reorder"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(12) }
            }.also { row.addView(it) }
        } else null

        val numberText = TextView(context).apply {
            textSize = 14f
            setTextColor(colors.textGray)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        }
        row.addView(numberText)

        val nameText = TextView(context).apply {
            textSize = 14f
            setTextColor(colors.text)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(nameText)

        return ViewHolder(row, dragHandle, numberText, nameText)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feed = items[position]
        val isSelected = position == selectedPos

        holder.numberText.text = "${position + 1}."
        holder.nameText.text = if (showPluginNames) {
            "[${feed.pluginName}] ${feed.sectionName}"
        } else {
            feed.sectionName
        }

        val row = holder.itemView as LinearLayout

        // Visual selection state
        if (isSelected) {
            row.setBackgroundColor((colors.primary and 0x00FFFFFF) or 0x30000000)
            holder.nameText.setTextColor(colors.primary)
        } else {
            row.setBackgroundColor(0)
            holder.nameText.setTextColor(colors.text)
        }

        // Interaction setup
        if (isTvMode) {
            // TV: tap to select/move
            row.isClickable = true
            row.isFocusable = true
            TvFocusUtils.makeFocusable(row, 8)
            row.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onTap(pos)
            }
        } else {
            // Touch: drag handle only
            holder.dragHandle?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
            row.isFocusable = false
            row.isClickable = false
        }
    }

    private fun dp(dp: Int) = TvFocusUtils.dpToPx(context, dp)

    class ViewHolder(
        itemView: View,
        val dragHandle: TextView?,
        val numberText: TextView,
        val nameText: TextView
    ) : RecyclerView.ViewHolder(itemView)
}
