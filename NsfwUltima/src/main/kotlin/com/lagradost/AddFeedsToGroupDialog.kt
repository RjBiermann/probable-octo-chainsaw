package com.lagradost

import com.lagradost.common.DialogUtils
import com.lagradost.common.TvFocusUtils

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Dialog for adding feeds to a group.
 * Shows both unassigned feeds (already in feed list) and available feeds (not in list yet).
 */
class AddFeedsToGroupDialog(
    private val feedGroup: FeedGroup,
    private val unassignedFeeds: List<FeedItem>,
    private val availableFeeds: List<AvailableFeed>,
    private val showPluginNames: Boolean,
    private val onFeedsSelected: (existingFeeds: List<FeedItem>, newFeeds: List<AvailableFeed>) -> Unit
) : DialogFragment() {

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    // Theme colors (resolved in onCreateDialog)
    private lateinit var colors: DialogUtils.ThemeColors
    private val textColor get() = colors.textColor
    private val grayTextColor get() = colors.grayTextColor
    private val backgroundColor get() = colors.backgroundColor
    private val cardColor get() = colors.cardColor
    private val primaryColor get() = colors.primaryColor

    // Track selections by key
    private val selectedExistingKeys = mutableSetOf<String>()
    private val selectedAvailableKeys = mutableSetOf<String>()

    private var searchQuery = ""
    private var isUpdatingSelectAll = false  // Guard against checkbox listener recursion
    private lateinit var feedListContainer: LinearLayout
    private lateinit var addButton: MaterialButton
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var mainContainer: LinearLayout

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = DialogUtils.resolveThemeColors(context)
        val contentView = createDialogView(context)
        return DialogUtils.createTvOrBottomSheetDialog(context, isTvMode, theme, contentView, 0.9)
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
        mainContainer.addView(TextView(context).apply {
            text = "Add Feeds to: ${feedGroup.name}"
            textSize = 18f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        // Subtitle
        mainContainer.addView(TextView(context).apply {
            text = "Select feeds to add to this group"
            textSize = 13f
            setTextColor(grayTextColor)
            setPadding(0, 0, 0, dp(16))
        })

        val totalFeeds = unassignedFeeds.size + availableFeeds.size

        // Empty state
        if (totalFeeds == 0) {
            mainContainer.addView(TextView(context).apply {
                text = "No feeds available.\nAll feeds are already in groups."
                textSize = 14f
                setTextColor(grayTextColor)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
            })

            mainContainer.addView(MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "Close"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(16)
                }
                strokeColor = ColorStateList.valueOf(grayTextColor)
                setTextColor(grayTextColor)
                setOnClickListener { dismiss() }
                if (isTvMode) TvFocusUtils.makeFocusable(this)
            })

            scrollView.addView(mainContainer)
            return scrollView
        }

        // Search input
        val searchLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            hint = "Search feeds..."
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        val searchInput = TextInputEditText(context).apply {
            setTextColor(textColor)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString()?.lowercase()?.trim() ?: ""
                    rebuildFeedList(context)
                }
            })
        }
        searchLayout.addView(searchInput)

        if (isTvMode) {
            TvFocusUtils.makeFocusableTextInput(searchLayout, primaryColor)
        } else {
            searchLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(primaryColor))
        }

        mainContainer.addView(searchLayout)

        // Select All row
        val selectAllRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        selectAllCheckbox = CheckBox(context).apply {
            isChecked = false
            buttonTintList = ColorStateList.valueOf(primaryColor)
            setOnCheckedChangeListener { _, isChecked ->
                // Guard against recursion when programmatically setting checkbox state
                if (isUpdatingSelectAll) return@setOnCheckedChangeListener

                val (filteredExisting, filteredAvailable) = getFilteredFeeds()
                if (isChecked) {
                    selectedExistingKeys.addAll(filteredExisting.map { it.key() })
                    selectedAvailableKeys.addAll(filteredAvailable.map { it.key() })
                } else {
                    selectedExistingKeys.removeAll(filteredExisting.map { it.key() }.toSet())
                    selectedAvailableKeys.removeAll(filteredAvailable.map { it.key() }.toSet())
                }
                rebuildFeedList(context)
                updateAddButton()
                // Restore focus to checkbox after rebuild (TV mode)
                if (isTvMode) {
                    selectAllCheckbox.post {
                        if (isAdded && selectAllCheckbox.isAttachedToWindow) {
                            selectAllCheckbox.requestFocus()
                        }
                    }
                }
            }
        }
        if (isTvMode) TvFocusUtils.makeFocusable(selectAllCheckbox)

        selectAllRow.addView(selectAllCheckbox)
        selectAllRow.addView(TextView(context).apply {
            text = "Select All"
            textSize = 14f
            setTextColor(textColor)
            setPadding(dp(8), 0, 0, 0)
        })

        mainContainer.addView(selectAllRow)

        // Feed list container
        feedListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainContainer.addView(feedListContainer)

        rebuildFeedList(context)

        // Add button
        addButton = MaterialButton(context).apply {
            text = "Add Selected (0)"
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
            }
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            isEnabled = false
            setOnClickListener { executeAdd() }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        }
        mainContainer.addView(addButton)

        // Cancel button
        mainContainer.addView(MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Cancel"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            strokeColor = ColorStateList.valueOf(grayTextColor)
            setTextColor(grayTextColor)
            setOnClickListener { dismiss() }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        scrollView.addView(mainContainer)

        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(mainContainer)
        }

        return scrollView
    }

    private fun getFilteredFeeds(): Pair<List<FeedItem>, List<AvailableFeed>> {
        val filteredExisting = if (searchQuery.isEmpty()) {
            unassignedFeeds
        } else {
            unassignedFeeds.filter { feed ->
                feed.sectionName.lowercase().contains(searchQuery) ||
                feed.pluginName.lowercase().contains(searchQuery)
            }
        }

        val filteredAvailable = if (searchQuery.isEmpty()) {
            availableFeeds
        } else {
            availableFeeds.filter { feed ->
                feed.sectionName.lowercase().contains(searchQuery) ||
                feed.pluginName.lowercase().contains(searchQuery)
            }
        }

        return Pair(filteredExisting, filteredAvailable)
    }

    private fun rebuildFeedList(context: Context) {
        feedListContainer.removeAllViews()

        val (filteredExisting, filteredAvailable) = getFilteredFeeds()
        val totalFiltered = filteredExisting.size + filteredAvailable.size

        if (totalFiltered == 0) {
            feedListContainer.addView(TextView(context).apply {
                text = if (searchQuery.isNotEmpty()) "No feeds match your search" else "No feeds available"
                textSize = 14f
                setTextColor(grayTextColor)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(16), dp(16), dp(16))
            })
            return
        }

        // Section 1: Existing unassigned feeds (already in list)
        if (filteredExisting.isNotEmpty()) {
            feedListContainer.addView(createSectionHeader(context, "In Your List", filteredExisting.size))

            val feedsByPlugin = filteredExisting.groupBy { it.pluginName }
            feedsByPlugin.forEach { (pluginName, feeds) ->
                if (showPluginNames && feedsByPlugin.size > 1) {
                    feedListContainer.addView(createPluginSubheader(context, pluginName))
                }
                feeds.forEach { feed ->
                    feedListContainer.addView(createExistingFeedRow(context, feed))
                }
            }
        }

        // Section 2: Available feeds (not in list yet)
        if (filteredAvailable.isNotEmpty()) {
            feedListContainer.addView(createSectionHeader(context, "Available (not in list)", filteredAvailable.size))

            val feedsByPlugin = filteredAvailable.groupBy { it.pluginName }
            feedsByPlugin.forEach { (pluginName, feeds) ->
                if (showPluginNames && feedsByPlugin.size > 1) {
                    feedListContainer.addView(createPluginSubheader(context, pluginName))
                }
                feeds.forEach { feed ->
                    feedListContainer.addView(createAvailableFeedRow(context, feed))
                }
            }
        }

        // Update select all checkbox state (temporarily remove listener to prevent recursion)
        val allExistingSelected = filteredExisting.all { it.key() in selectedExistingKeys }
        val allAvailableSelected = filteredAvailable.all { it.key() in selectedAvailableKeys }
        val allSelected = allExistingSelected && allAvailableSelected && totalFiltered > 0
        isUpdatingSelectAll = true
        selectAllCheckbox.isChecked = allSelected
        isUpdatingSelectAll = false

        // Re-enable focus loop after content rebuild (TV mode)
        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(mainContainer)
        }
    }

    private fun createSectionHeader(context: Context, title: String, count: Int): View {
        return TextView(context).apply {
            text = "$title ($count)"
            textSize = 13f
            setTextColor(primaryColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        }
    }

    private fun createPluginSubheader(context: Context, pluginName: String): View {
        return TextView(context).apply {
            text = pluginName
            textSize = 11f
            setTextColor(grayTextColor)
            setPadding(0, dp(8), 0, dp(4))
        }
    }

    private fun createExistingFeedRow(context: Context, feed: FeedItem): View {
        val isSelected = feed.key() in selectedExistingKeys
        val feedKey = feed.key()
        return createFeedCard(
            context = context,
            feedKey = feedKey,
            name = feed.sectionName,
            pluginName = feed.pluginName,
            isSelected = isSelected,
            showBadge = false,
            onToggle = {
                if (isSelected) {
                    selectedExistingKeys.remove(feedKey)
                } else {
                    selectedExistingKeys.add(feedKey)
                }
                rebuildFeedList(context)
                updateAddButton()
                restoreFocusToFeed(feedKey)
            }
        )
    }

    private fun createAvailableFeedRow(context: Context, feed: AvailableFeed): View {
        val isSelected = feed.key() in selectedAvailableKeys
        val feedKey = feed.key()
        return createFeedCard(
            context = context,
            feedKey = feedKey,
            name = feed.sectionName,
            pluginName = feed.pluginName,
            isSelected = isSelected,
            showBadge = true,
            onToggle = {
                if (isSelected) {
                    selectedAvailableKeys.remove(feedKey)
                } else {
                    selectedAvailableKeys.add(feedKey)
                }
                rebuildFeedList(context)
                updateAddButton()
                restoreFocusToFeed(feedKey)
            }
        )
    }

    private fun restoreFocusToFeed(feedKey: String) {
        if (!isTvMode) return
        feedListContainer.post {
            if (!isAdded) return@post
            // Find card with matching tag
            for (i in 0 until feedListContainer.childCount) {
                val child = feedListContainer.getChildAt(i)
                if (child.tag == feedKey && child.isAttachedToWindow) {
                    child.requestFocus()
                    return@post
                }
                // Check nested children (plugin groups)
                if (child is ViewGroup) {
                    for (j in 0 until child.childCount) {
                        val nested = child.getChildAt(j)
                        if (nested.tag == feedKey && nested.isAttachedToWindow) {
                            nested.requestFocus()
                            return@post
                        }
                    }
                }
            }
        }
    }

    private fun createFeedCard(
        context: Context,
        feedKey: String,
        name: String,
        pluginName: String,
        isSelected: Boolean,
        showBadge: Boolean,
        onToggle: () -> Unit
    ): View {
        val card = MaterialCardView(context).apply {
            tag = feedKey  // Tag for focus restoration
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            setCardBackgroundColor(if (isSelected) (primaryColor and 0x00FFFFFF) or 0x15000000 else cardColor)
            strokeColor = if (isSelected) primaryColor else (grayTextColor and 0x00FFFFFF) or 0x30000000
            strokeWidth = dp(if (isSelected) 2 else 1)
            radius = dp(8).toFloat()
            cardElevation = 0f
            isClickable = true
            isFocusable = true
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val checkbox = CheckBox(context).apply {
            this.isChecked = isSelected
            buttonTintList = ColorStateList.valueOf(primaryColor)
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        }

        textContainer.addView(TextView(context).apply {
            text = name
            textSize = 14f
            setTextColor(textColor)
        })

        if (showPluginNames) {
            textContainer.addView(TextView(context).apply {
                text = pluginName
                textSize = 11f
                setTextColor(grayTextColor)
            })
        }

        cardContent.addView(checkbox)
        cardContent.addView(textContainer)

        // Badge for available feeds
        if (showBadge) {
            cardContent.addView(TextView(context).apply {
                text = "+"
                textSize = 16f
                setTextColor(primaryColor)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(8), 0, 0, 0)
            })
        }

        card.addView(cardContent)

        // Handle clicks
        val clickListener = View.OnClickListener { onToggle() }
        card.setOnClickListener(clickListener)
        checkbox.setOnClickListener(clickListener)

        if (isTvMode) TvFocusUtils.makeFocusable(card)

        return card
    }

    private fun updateAddButton() {
        val count = selectedExistingKeys.size + selectedAvailableKeys.size
        val newCount = selectedAvailableKeys.size

        addButton.text = if (newCount > 0) {
            "Add Selected ($count) · $newCount new"
        } else {
            "Add Selected ($count)"
        }
        addButton.isEnabled = count > 0
    }

    private fun executeAdd() {
        val existingToAdd = unassignedFeeds.filter { it.key() in selectedExistingKeys }
        val availableToAdd = availableFeeds.filter { it.key() in selectedAvailableKeys }

        if (existingToAdd.isEmpty() && availableToAdd.isEmpty()) {
            dismiss()
            return
        }

        // Save context reference before dismiss (fragment may detach)
        val ctx = requireContext()

        // Show toast before dismiss to ensure context is available
        val existingCount = existingToAdd.size
        val newCount = availableToAdd.size
        val message = when {
            existingCount > 0 && newCount > 0 ->
                "Added $existingCount feeds + $newCount new to '${feedGroup.name}'"
            newCount > 0 ->
                "Added $newCount new feeds to '${feedGroup.name}'"
            else ->
                "Added $existingCount feeds to '${feedGroup.name}'"
        }
        android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()

        onFeedsSelected(existingToAdd, availableToAdd)
        dismiss()
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(requireContext(), dp)
}
