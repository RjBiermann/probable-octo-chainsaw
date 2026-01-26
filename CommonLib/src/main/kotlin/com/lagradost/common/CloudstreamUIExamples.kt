package com.lagradost.common

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.chip.Chip

/**
 * Example usage patterns for CloudstreamUI components.
 * Copy and adapt these patterns for your plugin settings dialogs.
 *
 * This file demonstrates:
 * 1. Filter chips for category selection
 * 2. Label chips for displaying tags
 * 3. Button variants (primary, secondary, danger)
 * 4. Cards with content
 * 5. Status indicators
 */
object CloudstreamUIExamples {

    /**
     * Example: Category filter chips.
     * Common use case: filtering content by category in settings.
     */
    fun createCategoryFilterExample(context: Context): LinearLayout {
        val colors = CloudstreamUI.UIColors.fromContext(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 16)
            )
        }

        // Section header
        container.addView(CloudstreamUI.createTitleText(context, "Categories", colors).apply {
            setPadding(0, 0, 0, TvFocusUtils.dpToPx(context, 8))
        })

        // Chip group with filter chips
        val chipGroup = CloudstreamUI.createChipGroup(context, isSingleSelection = false)

        val categories = listOf("All", "Popular", "Recent", "HD", "Asian", "European")
        val selectedCategories = mutableSetOf("All")

        categories.forEach { category ->
            val chip = CloudstreamUI.createFilterChip(
                context = context,
                text = category,
                isChecked = category in selectedCategories,
                colors = colors
            ) { isChecked ->
                if (isChecked) {
                    selectedCategories.add(category)
                } else {
                    selectedCategories.remove(category)
                }
                // Handle selection change
            }
            chipGroup.addView(chip)
        }

        container.addView(chipGroup)
        return container
    }

    /**
     * Example: Quality selection chips (single selection).
     * Common use case: video quality preference.
     */
    fun createQualitySelectionExample(context: Context): LinearLayout {
        val colors = CloudstreamUI.UIColors.fromContext(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        container.addView(CloudstreamUI.createCaptionText(context, "Preferred Quality", colors).apply {
            setPadding(0, 0, 0, TvFocusUtils.dpToPx(context, 8))
        })

        val chipGroup = CloudstreamUI.createChipGroup(
            context = context,
            isSingleSelection = true,
            isSelectionRequired = true
        )

        val qualities = listOf("Auto", "1080p", "720p", "480p")
        var selectedQuality = "Auto"

        qualities.forEach { quality ->
            val chip = CloudstreamUI.createFilterChip(
                context = context,
                text = quality,
                isChecked = quality == selectedQuality,
                colors = colors
            ) { isChecked ->
                if (isChecked) {
                    selectedQuality = quality
                    // Update all other chips
                    for (i in 0 until chipGroup.childCount) {
                        (chipGroup.getChildAt(i) as? Chip)?.let { otherChip ->
                            if (otherChip.text != quality) {
                                otherChip.isChecked = false
                            }
                        }
                    }
                }
            }
            chipGroup.addView(chip)
        }

        container.addView(chipGroup)
        return container
    }

    /**
     * Example: Feed status labels.
     * Common use case: showing status in list items.
     */
    fun createFeedStatusExample(context: Context): LinearLayout {
        val colors = CloudstreamUI.UIColors.fromContext(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Feed name
        container.addView(CloudstreamUI.createBodyText(context, "Popular Videos", colors).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Status chip
        container.addView(CloudstreamUI.createColoredChip(
            context = context,
            text = "Active",
            chipColor = CloudstreamUI.Colors.SUCCESS
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        return container
    }

    /**
     * Example: Error state with badge.
     */
    fun createErrorStateExample(context: Context): LinearLayout {
        val colors = CloudstreamUI.UIColors.fromContext(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                TvFocusUtils.dpToPx(context, 12),
                TvFocusUtils.dpToPx(context, 8),
                TvFocusUtils.dpToPx(context, 12),
                TvFocusUtils.dpToPx(context, 8)
            )
        }

        // Status indicator dot
        container.addView(CloudstreamUI.createStatusIndicator(context, CloudstreamUI.Status.ERROR).apply {
            val margin = TvFocusUtils.dpToPx(context, 8)
            (layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = margin
        })

        // Message
        container.addView(CloudstreamUI.createBodyText(context, "Connection failed", colors).apply {
            setTextColor(colors.error)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Retry button
        container.addView(CloudstreamUI.createSmallButton(
            context = context,
            text = "Retry",
            isPrimary = false,
            colors = colors
        ) {
            // Handle retry
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        return container
    }

    /**
     * Example: Settings card with toggle and action buttons.
     */
    fun createSettingsCardExample(context: Context): LinearLayout {
        val colors = CloudstreamUI.UIColors.fromContext(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 16)
            )
            setBackgroundColor(colors.background)
        }

        // Header
        container.addView(CloudstreamUI.createHeaderText(context, "Plugin Settings", colors).apply {
            setPadding(0, 0, 0, TvFocusUtils.dpToPx(context, 16))
        })

        // Settings card
        val card = CloudstreamUI.createCard(context, colors)
        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 12),
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 12)
            )
        }

        // Title row with badge
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(CloudstreamUI.createTitleText(context, "Enable HD Mode", colors).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(CloudstreamUI.createBadge(context, "PRO", CloudstreamUI.Colors.PRIMARY))
        cardContent.addView(titleRow)

        // Description
        cardContent.addView(CloudstreamUI.createCaptionText(
            context,
            "Stream content in high definition when available",
            colors
        ).apply {
            setPadding(0, TvFocusUtils.dpToPx(context, 4), 0, 0)
        })

        card.addView(cardContent)
        container.addView(card)

        // Divider
        container.addView(CloudstreamUI.createDivider(context, colors))

        // Action buttons
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        buttonContainer.addView(CloudstreamUI.createPrimaryButton(
            context,
            "Save Changes",
            colors
        ) {
            // Handle save
        }.apply {
            val params = layoutParams as? ViewGroup.MarginLayoutParams
            params?.bottomMargin = TvFocusUtils.dpToPx(context, 8)
        })

        buttonContainer.addView(CloudstreamUI.createSecondaryButton(
            context,
            "Reset to Default",
            colors
        ) {
            // Handle reset
        }.apply {
            val params = layoutParams as? ViewGroup.MarginLayoutParams
            params?.bottomMargin = TvFocusUtils.dpToPx(context, 8)
        })

        buttonContainer.addView(CloudstreamUI.createDangerButton(
            context,
            "Delete All Data",
            colors
        ) {
            // Handle delete
        })

        container.addView(buttonContainer)

        return container
    }

    /**
     * Example: Pill toggle for view mode selection.
     */
    fun createViewModeToggleExample(context: Context): LinearLayout {
        val colors = CloudstreamUI.UIColors.fromContext(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 16)
            )
        }

        container.addView(CloudstreamUI.createCaptionText(context, "View Mode", colors).apply {
            setPadding(0, 0, 0, TvFocusUtils.dpToPx(context, 8))
        })

        val toggleGroup = CloudstreamUI.createPillToggleGroup(
            context = context,
            options = listOf("Grid", "List", "Compact"),
            selectedIndex = 0,
            colors = colors
        ) { selectedIndex ->
            // Handle view mode change
        }

        container.addView(toggleGroup)
        return container
    }

    /**
     * Example: Empty state message.
     */
    fun createEmptyStateExample(context: Context): LinearLayout {
        val colors = CloudstreamUI.UIColors.fromContext(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                TvFocusUtils.dpToPx(context, 24),
                TvFocusUtils.dpToPx(context, 48),
                TvFocusUtils.dpToPx(context, 24),
                TvFocusUtils.dpToPx(context, 48)
            )
        }

        container.addView(CloudstreamUI.createEmptyState(
            context,
            "No feeds configured yet.\nTap below to get started.",
            colors
        ))

        container.addView(CloudstreamUI.createPrimaryButton(
            context,
            "Add First Feed",
            colors
        ) {
            // Handle add feed
        }.apply {
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = TvFocusUtils.dpToPx(context, 16)
            layoutParams = params
        })

        return container
    }

    /**
     * Example: Scrollable horizontal chip list (for many categories).
     */
    fun createScrollableChipsExample(context: Context): LinearLayout {
        val colors = CloudstreamUI.UIColors.fromContext(context)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        container.addView(CloudstreamUI.createCaptionText(context, "Browse by Tag", colors).apply {
            setPadding(
                TvFocusUtils.dpToPx(context, 16),
                0,
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 8)
            )
        })

        val tags = listOf(
            "Amateur", "Asian", "Big", "Blonde", "Brunette",
            "Ebony", "European", "HD", "Japanese", "Korean",
            "Latina", "Mature", "MILF", "Teen", "Webcam"
        )

        val chips = tags.map { tag ->
            CloudstreamUI.createFilterChip(context, tag, false, colors) { isChecked ->
                // Handle tag selection
            }
        }

        container.addView(CloudstreamUI.createScrollableChipRow(context, chips))

        return container
    }
}
