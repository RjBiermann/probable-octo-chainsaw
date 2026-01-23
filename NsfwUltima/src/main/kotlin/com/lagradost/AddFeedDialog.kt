package com.lagradost

import com.lagradost.common.TvFocusUtils

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Dialog for adding/removing feeds from available plugins.
 * Shows all available feeds as chips grouped by plugin, with filter support.
 * Supports bulk operations per plugin and for all plugins.
 */
class AddFeedDialog(
    private val availableFeeds: List<AvailableFeed>,
    private val onFeedSelected: (AvailableFeed) -> Unit,
    private val onFeedRemoved: (AvailableFeed) -> Unit
) : DialogFragment() {

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    // Theme colors
    private var textColor: Int = 0
    private var grayTextColor: Int = 0
    private var backgroundColor: Int = 0
    private var cardColor: Int = 0
    private var primaryColor: Int = 0

    private lateinit var contentContainer: LinearLayout
    private var currentFilter: String = ""

    // Track which feeds are added (updated when dialog opens)
    private val addedKeys = mutableSetOf<String>()

    fun updateAddedFeeds(addedFeeds: List<FeedItem>) {
        addedKeys.clear()
        addedKeys.addAll(addedFeeds.map { it.key() })
    }

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
            0
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        resolveThemeColors(context)

        val contentView = createDialogView(context)

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

    private fun createDialogView(context: Context): View {
        val scrollView = NestedScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(backgroundColor)
        }

        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        // Title row with close button
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        titleRow.addView(TextView(context).apply {
            text = "Manage Feeds"
            textSize = 20f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        titleRow.addView(TextView(context).apply {
            text = "×"
            textSize = 24f
            setTextColor(grayTextColor)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { dismiss() }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        mainContainer.addView(titleRow)

        // Filter input
        val filterLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
                bottomMargin = dp(16)
            }
            hint = "Filter feeds..."
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        }

        val filterInput = TextInputEditText(context).apply {
            setTextColor(textColor)
        }
        filterLayout.addView(filterInput)

        if (isTvMode) {
            TvFocusUtils.makeFocusableTextInput(filterLayout, primaryColor)
        } else {
            filterLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(primaryColor))
        }

        mainContainer.addView(filterLayout)

        // Content container (will be rebuilt on filter)
        contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        mainContainer.addView(contentContainer)

        // Build initial content
        rebuildContent(context)

        // Filter listener
        filterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentFilter = s?.toString()?.lowercase() ?: ""
                rebuildContent(context)
            }
        })

        scrollView.addView(mainContainer)

        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(mainContainer)
        }

        return scrollView
    }

    private fun rebuildContent(context: Context) {
        contentContainer.removeAllViews()

        // Group feeds by plugin
        val feedsByPlugin = availableFeeds
            .filter { feed ->
                if (currentFilter.isEmpty()) true
                else {
                    feed.pluginName.lowercase().contains(currentFilter) ||
                    feed.sectionName.lowercase().contains(currentFilter)
                }
            }
            .groupBy { it.pluginName }

        if (feedsByPlugin.isEmpty()) {
            contentContainer.addView(TextView(context).apply {
                text = if (currentFilter.isEmpty()) "No feeds available" else "No matches found"
                textSize = 14f
                setTextColor(grayTextColor)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(32), dp(16), dp(32))
            })
            return
        }

        // Global bulk operations
        val allFeeds = feedsByPlugin.values.flatten()
        val allAdded = allFeeds.all { addedKeys.contains(it.key()) }
        val noneAdded = allFeeds.none { addedKeys.contains(it.key()) }

        val globalButtonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(8), 0, dp(16))
        }

        globalButtonRow.addView(MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Add All Feeds"
            textSize = 12f
            isEnabled = !allAdded
            strokeColor = ColorStateList.valueOf(if (isEnabled) primaryColor else grayTextColor)
            setTextColor(if (isEnabled) primaryColor else grayTextColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener {
                allFeeds.filter { !addedKeys.contains(it.key()) }.forEach { feed ->
                    onFeedSelected(feed)
                    addedKeys.add(feed.key())
                }
                rebuildContent(context)
            }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        globalButtonRow.addView(MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Remove All Feeds"
            textSize = 12f
            isEnabled = !noneAdded
            strokeColor = ColorStateList.valueOf(if (isEnabled) grayTextColor else grayTextColor)
            setTextColor(if (isEnabled) grayTextColor else grayTextColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                allFeeds.filter { addedKeys.contains(it.key()) }.forEach { feed ->
                    onFeedRemoved(feed)
                    addedKeys.remove(feed.key())
                }
                rebuildContent(context)
            }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        contentContainer.addView(globalButtonRow)

        // Build plugin sections
        feedsByPlugin.forEach { (pluginName, feeds) ->
            val pluginAllAdded = feeds.all { addedKeys.contains(it.key()) }
            val pluginNoneAdded = feeds.none { addedKeys.contains(it.key()) }

            // Plugin header row with bulk buttons
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dp(12), 0, dp(8))
            }

            headerRow.addView(TextView(context).apply {
                text = pluginName
                textSize = 14f
                setTextColor(grayTextColor)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            // Per-plugin Add All button
            headerRow.addView(TextView(context).apply {
                text = "+ All"
                textSize = 12f
                setTextColor(if (pluginAllAdded) grayTextColor else primaryColor)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                isClickable = !pluginAllAdded
                setOnClickListener {
                    feeds.filter { !addedKeys.contains(it.key()) }.forEach { feed ->
                        onFeedSelected(feed)
                        addedKeys.add(feed.key())
                    }
                    rebuildContent(context)
                }
                if (isTvMode) TvFocusUtils.makeFocusable(this)
            })

            // Per-plugin Remove All button
            headerRow.addView(TextView(context).apply {
                text = "− All"
                textSize = 12f
                setTextColor(if (pluginNoneAdded) grayTextColor else grayTextColor)
                alpha = if (pluginNoneAdded) 0.5f else 1f
                setPadding(dp(8), dp(4), dp(8), dp(4))
                isClickable = !pluginNoneAdded
                setOnClickListener {
                    feeds.filter { addedKeys.contains(it.key()) }.forEach { feed ->
                        onFeedRemoved(feed)
                        addedKeys.remove(feed.key())
                    }
                    rebuildContent(context)
                }
                if (isTvMode) TvFocusUtils.makeFocusable(this)
            })

            contentContainer.addView(headerRow)

            // Chips for feeds
            val chipGroup = ChipGroup(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                isSingleLine = false
            }

            feeds.forEach { feed ->
                val isAdded = addedKeys.contains(feed.key())
                val chip = createFeedChip(context, feed, isAdded)
                chipGroup.addView(chip)
            }

            contentContainer.addView(chipGroup)
        }
    }

    private fun createFeedChip(context: Context, feed: AvailableFeed, isAdded: Boolean): Chip {
        return Chip(context).apply {
            text = if (isAdded) "✓ ${feed.sectionName}" else "+ ${feed.sectionName}"
            isCheckable = false
            isClickable = true  // Always clickable - add or remove

            if (isAdded) {
                // Already added - click to remove
                setTextColor(grayTextColor)
                chipBackgroundColor = ColorStateList.valueOf(cardColor)
                chipStrokeWidth = 0f

                setOnClickListener {
                    onFeedRemoved(feed)
                    addedKeys.remove(feed.key())
                    rebuildContent(context)
                }
            } else {
                // Available - click to add
                setTextColor(primaryColor)
                chipBackgroundColor = ColorStateList.valueOf(cardColor)
                chipStrokeColor = ColorStateList.valueOf(primaryColor)
                chipStrokeWidth = dp(1).toFloat()

                setOnClickListener {
                    onFeedSelected(feed)
                    addedKeys.add(feed.key())
                    rebuildContent(context)
                }
            }

            if (isTvMode) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
