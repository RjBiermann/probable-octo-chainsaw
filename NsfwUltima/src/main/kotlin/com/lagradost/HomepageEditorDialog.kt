package com.lagradost

import com.lagradost.common.CloudstreamUI
import com.lagradost.common.DialogUtils
import com.lagradost.common.TvFocusUtils

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.lagradost.common.SafeNestedScrollView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Combined dialog for editing a homepage: name and feed selection.
 * Uses a single unified list where feeds are toggled on/off.
 * For creating new homepages, pass existingGroup = null.
 */
class HomepageEditorDialog(
    private val existingGroup: Homepage?,
    private val currentFeeds: List<FeedItem>,
    private val availableFeeds: List<AvailableFeed>,
    private val showPluginNames: Boolean,
    private val onSave: (group: Homepage, feedsInHomepage: List<FeedItem>, allFeeds: List<FeedItem>) -> Unit,
    private val onDelete: ((Homepage) -> Unit)? = null
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
        colors = CloudstreamUI.UIColors.fromContext(requireContext())

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

        val contentView = createDialogView()
        return DialogUtils.createTvOrBottomSheetDialog(requireContext(), isTvMode, theme, contentView, 0.95)
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

    private fun createDialogView(): View {
        val context = requireContext()
        val scrollView = SafeNestedScrollView(context).apply {
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
            if (isAdded) showReorderDialog()
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
                    rebuildFeedsList()
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
        rebuildFeedsList()
        updateHeader()

        return scrollView
    }

    private fun rebuildFeedsList() {
        if (!isAdded) return
        val context = requireContext()

        // Save focused chip tag for TV mode focus restoration
        val focusedTag = if (isTvMode) feedsContainer.findFocus()?.tag else null

        feedsContainer.removeAllViews()

        // Filter available feeds by search query only
        // (same feed CAN be added to multiple homepages)
        val filteredFeeds = availableFeeds
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
                val chip = createFeedChip(feed, isSelected)
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

    private fun createFeedChip(feed: AvailableFeed, isSelected: Boolean): Chip {
        return Chip(requireContext()).apply {
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
                rebuildFeedsList()
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

    private fun showReorderDialog() {
        if (!isAdded || selectedFeeds.size < 2) return

        val dialog = ReorderFeedsDialog(
            feeds = selectedFeeds.toList(),
            showPluginNames = showPluginNames,
            onReorder = { reorderedFeeds ->
                if (!isAdded) return@ReorderFeedsDialog
                selectedFeeds.clear()
                selectedFeeds.addAll(reorderedFeeds)
                rebuildFeedsList()
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
            else -> {
                // New homepage with no name - show feedback and discard
                if (isAdded) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Homepage not saved - name required",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }

        val group = if (existingGroup != null) {
            existingGroup.copy(name = finalName)
        } else {
            Homepage(id = workingGroupId, name = finalName)
        }

        // Delegate feed transformation to domain service
        val result = FeedAssignmentService.updateHomepageFeedSelection(
            currentFeeds = currentFeeds,
            selectedFeeds = selectedFeeds,
            homepageId = workingGroupId,
            isExistingHomepage = existingGroup != null
        )

        onSave(group, result.feedsInHomepage, result.updatedFeeds)
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(requireContext(), dp)
}
