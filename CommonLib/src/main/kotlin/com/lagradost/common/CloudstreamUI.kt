package com.lagradost.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Cloudstream UI component library.
 * Provides programmatic UI components that match Cloudstream's design system.
 *
 * Design tokens from Cloudstream:
 * - Primary: #3d50fa (Indigo Blue)
 * - Background: #111111 (Deep Black) / #2B2C30 (Dark Gray)
 * - Card: #161616 (Box Item Background)
 * - Text: #e9eaee (Light Gray)
 * - Gray Text: #9ba0a4 (Medium Gray)
 * - Error/Ongoing: #F53B66 (Red/Pink)
 */
object CloudstreamUI {

    // ==================== COLOR PALETTE ====================

    /**
     * Cloudstream's default dark theme colors.
     * These are fallback values when theme attributes can't be resolved.
     */
    object Colors {
        // Primary colors
        const val PRIMARY = 0xFF3D50FA.toInt()           // Indigo Blue
        const val PRIMARY_DARK = 0xFF3700B3.toInt()      // Deep Purple
        const val ACCENT = 0xFF3B65F5.toInt()            // Bright Blue
        const val ON_PRIMARY = 0xFFFFFFFF.toInt()        // White

        // Background colors
        const val BACKGROUND = 0xFF111111.toInt()        // Deep Black
        const val BACKGROUND_GRAY = 0xFF2B2C30.toInt()   // Dark Gray
        const val CARD = 0xFF161616.toInt()              // Box Item Background
        const val ICON_GRAY = 0xFF1C1C20.toInt()         // Charcoal

        // Text colors
        const val TEXT = 0xFFE9EAEE.toInt()              // Light Gray
        const val TEXT_GRAY = 0xFF9BA0A4.toInt()         // Medium Gray
        const val ICON = 0xFF9BA0A6.toInt()              // Icon Gray

        // Semantic colors
        const val ERROR = 0xFFF53B66.toInt()             // Red/Pink (ongoing/error)
        const val SUCCESS = 0xFF4CAF50.toInt()           // Green
        const val WARNING = 0xFFFFA726.toInt()           // Orange

        // Search
        const val SEARCH = 0xFF303135.toInt()            // Search Background

        // Transparent variants
        const val PRIMARY_20 = 0x333D50FA                // 20% Primary
        const val WHITE_20 = 0x33FFFFFF                  // 20% White
        const val BLACK_40 = 0x66000000                  // 40% Black
    }

    // ==================== DIMENSIONS ====================

    object Dimens {
        const val CORNER_RADIUS_SMALL = 4     // Buttons
        const val CORNER_RADIUS_MEDIUM = 8    // Cards
        const val CORNER_RADIUS_LARGE = 12    // Dialogs
        const val CORNER_RADIUS_PILL = 50     // Chips/Pills

        const val BUTTON_HEIGHT = 40
        const val BUTTON_HEIGHT_SMALL = 24
        const val CHIP_HEIGHT = 32

        const val PADDING_TINY = 4
        const val PADDING_SMALL = 8
        const val PADDING_MEDIUM = 12
        const val PADDING_LARGE = 16
        const val PADDING_XLARGE = 24

        const val STROKE_WIDTH = 1
        const val STROKE_WIDTH_FOCUS = 2

        const val TEXT_SIZE_SMALL = 12f
        const val TEXT_SIZE_BODY = 14f
        const val TEXT_SIZE_BUTTON = 15f
        const val TEXT_SIZE_TITLE = 16f
        const val TEXT_SIZE_HEADER = 18f
        const val TEXT_SIZE_LARGE = 20f
        const val TEXT_SIZE_BADGE = 10f
    }

    // ==================== RESOLVED THEME COLORS ====================

    /**
     * Extended theme colors for UI components.
     * Includes additional colors beyond DialogUtils.ThemeColors.
     */
    data class UIColors(
        val primary: Int,
        val primaryDark: Int,
        val onPrimary: Int,
        val background: Int,
        val card: Int,
        val text: Int,
        val textGray: Int,
        val icon: Int,
        val error: Int,
        val success: Int,
        val warning: Int
    ) {
        companion object {
            /**
             * Resolve UI colors from theme with fallback to Cloudstream defaults.
             */
            fun fromContext(context: Context): UIColors {
                val dialogColors = DialogUtils.resolveThemeColors(context)
                return UIColors(
                    primary = dialogColors.primaryColor.takeIf { it != 0 } ?: Colors.PRIMARY,
                    primaryDark = resolveColorAttr(context, "colorPrimaryDark", Colors.PRIMARY_DARK),
                    onPrimary = dialogColors.onPrimaryColor.takeIf { it != 0 } ?: Colors.ON_PRIMARY,
                    background = dialogColors.backgroundColor.takeIf { it != 0 } ?: Colors.BACKGROUND,
                    card = dialogColors.cardColor.takeIf { it != 0 } ?: Colors.CARD,
                    text = dialogColors.textColor.takeIf { it != 0 } ?: Colors.TEXT,
                    textGray = dialogColors.grayTextColor.takeIf { it != 0 } ?: Colors.TEXT_GRAY,
                    icon = resolveColorAttr(context, "iconColor", Colors.ICON),
                    error = dialogColors.errorColor.takeIf { it != 0 } ?: Colors.ERROR,
                    success = Colors.SUCCESS,
                    warning = Colors.WARNING
                )
            }

            private fun resolveColorAttr(context: Context, attrName: String, fallback: Int): Int {
                val tv = TypedValue()
                val attrId = context.resources.getIdentifier(attrName, "attr", context.packageName)
                return if (attrId != 0 && context.theme.resolveAttribute(attrId, tv, true)) {
                    tv.data
                } else {
                    fallback
                }
            }
        }
    }

    // ==================== CHIP COMPONENTS ====================

    /**
     * Create a Cloudstream-styled filter chip.
     * Matches the ChipFilled style from Cloudstream.
     *
     * @param context The context
     * @param text The chip label
     * @param isChecked Initial checked state
     * @param colors UI colors (resolved from theme)
     * @param onCheckedChange Callback when chip state changes
     */
    fun createFilterChip(
        context: Context,
        text: String,
        isChecked: Boolean = false,
        colors: UIColors = UIColors.fromContext(context),
        onCheckedChange: ((Boolean) -> Unit)? = null
    ): Chip {
        return Chip(context).apply {
            this.text = text
            this.isCheckable = true
            this.isChecked = isChecked
            isClickable = true
            isFocusable = true

            // Match Cloudstream ChipFilled style
            chipBackgroundColor = createChipBackgroundColorStateList(colors)
            setTextColor(createChipTextColorStateList(colors))
            chipStrokeColor = createChipStrokeColorStateList(colors)
            chipStrokeWidth = TvFocusUtils.dpToPx(context, Dimens.STROKE_WIDTH).toFloat()

            // Typography
            textSize = Dimens.TEXT_SIZE_BODY
            typeface = Typeface.DEFAULT_BOLD

            // Remove chip icon space
            isChipIconVisible = false
            isCheckedIconVisible = false

            // Compact sizing
            chipMinHeight = TvFocusUtils.dpToPx(context, Dimens.CHIP_HEIGHT).toFloat()
            setEnsureMinTouchTargetSize(false)

            // Corner radius (pill shape)
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(TvFocusUtils.dpToPx(context, Dimens.CORNER_RADIUS_PILL).toFloat())
                .build()

            // Callback
            setOnCheckedChangeListener { _, checked ->
                onCheckedChange?.invoke(checked)
            }

            // TV focus support
            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    /**
     * Create a simple non-checkable chip (label/tag).
     */
    fun createLabelChip(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context),
        backgroundColor: Int = colors.card,
        textColor: Int = colors.text
    ): Chip {
        return Chip(context).apply {
            this.text = text
            isCheckable = false
            isClickable = false

            chipBackgroundColor = ColorStateList.valueOf(backgroundColor)
            setTextColor(textColor)

            textSize = Dimens.TEXT_SIZE_SMALL
            chipMinHeight = TvFocusUtils.dpToPx(context, 28).toFloat()
            setEnsureMinTouchTargetSize(false)

            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(TvFocusUtils.dpToPx(context, Dimens.CORNER_RADIUS_PILL).toFloat())
                .build()
        }
    }

    /**
     * Create a chip with a colored indicator (like a category tag).
     */
    fun createColoredChip(
        context: Context,
        text: String,
        chipColor: Int,
        textColor: Int = Colors.ON_PRIMARY
    ): Chip {
        return Chip(context).apply {
            this.text = text
            isCheckable = false
            isClickable = false

            chipBackgroundColor = ColorStateList.valueOf(chipColor)
            setTextColor(textColor)

            textSize = Dimens.TEXT_SIZE_SMALL
            typeface = Typeface.DEFAULT_BOLD
            chipMinHeight = TvFocusUtils.dpToPx(context, 24).toFloat()
            setEnsureMinTouchTargetSize(false)

            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(TvFocusUtils.dpToPx(context, Dimens.CORNER_RADIUS_SMALL).toFloat())
                .build()
        }
    }

    /**
     * Create a ChipGroup container for filter chips.
     * Matches Cloudstream's ChipParent style.
     */
    fun createChipGroup(
        context: Context,
        isSingleSelection: Boolean = false,
        isSelectionRequired: Boolean = false
    ): ChipGroup {
        return ChipGroup(context).apply {
            this.isSingleSelection = isSingleSelection
            this.isSelectionRequired = isSelectionRequired

            // Match Cloudstream ChipParent spacing
            chipSpacingHorizontal = TvFocusUtils.dpToPx(context, 5)
            chipSpacingVertical = TvFocusUtils.dpToPx(context, -5)

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Create a horizontally scrollable chip row.
     */
    fun createScrollableChipRow(
        context: Context,
        chips: List<Chip>
    ): HorizontalScrollView {
        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val chipGroup = createChipGroup(context)
        chips.forEach { chipGroup.addView(it) }

        scrollView.addView(chipGroup)
        return scrollView
    }

    // Color state lists for chips
    private fun createChipBackgroundColorStateList(colors: UIColors): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val colorValues = intArrayOf(
            colors.primary,                              // Checked: Primary
            ColorUtils.setAlphaComponent(colors.textGray, 40)  // Unchecked: Subtle gray
        )
        return ColorStateList(states, colorValues)
    }

    private fun createChipTextColorStateList(colors: UIColors): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val colorValues = intArrayOf(
            colors.onPrimary,  // Checked: White
            colors.text        // Unchecked: Regular text
        )
        return ColorStateList(states, colorValues)
    }

    private fun createChipStrokeColorStateList(colors: UIColors): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val colorValues = intArrayOf(
            colors.primary,                              // Checked: Primary
            ColorUtils.setAlphaComponent(colors.text, 80)  // Unchecked: Subtle border
        )
        return ColorStateList(states, colorValues)
    }

    // ==================== BUTTON COMPONENTS ====================

    /**
     * Create a primary filled button (matches Cloudstream's WhiteButton inverted).
     */
    fun createPrimaryButton(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context),
        onClick: (() -> Unit)? = null
    ): MaterialButton {
        return MaterialButton(context).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_BUTTON
            setTextColor(colors.onPrimary)
            backgroundTintList = ColorStateList.valueOf(colors.primary)

            // Match Cloudstream NiceButton styling
            cornerRadius = TvFocusUtils.dpToPx(context, Dimens.CORNER_RADIUS_SMALL)
            insetTop = 0
            insetBottom = 0
            minHeight = TvFocusUtils.dpToPx(context, Dimens.BUTTON_HEIGHT)

            // No all caps
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            onClick?.let { setOnClickListener { it() } }

            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    /**
     * Create a secondary/outlined button (matches Cloudstream's BlackButton).
     */
    fun createSecondaryButton(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context),
        onClick: (() -> Unit)? = null
    ): MaterialButton {
        return MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_BUTTON
            setTextColor(colors.text)
            strokeColor = ColorStateList.valueOf(colors.textGray)
            strokeWidth = TvFocusUtils.dpToPx(context, Dimens.STROKE_WIDTH)

            cornerRadius = TvFocusUtils.dpToPx(context, Dimens.CORNER_RADIUS_SMALL)
            insetTop = 0
            insetBottom = 0
            minHeight = TvFocusUtils.dpToPx(context, Dimens.BUTTON_HEIGHT)

            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            onClick?.let { setOnClickListener { it() } }

            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    /**
     * Create a small compact button.
     */
    fun createSmallButton(
        context: Context,
        text: String,
        isPrimary: Boolean = true,
        colors: UIColors = UIColors.fromContext(context),
        onClick: (() -> Unit)? = null
    ): MaterialButton {
        val button = if (isPrimary) {
            createPrimaryButton(context, text, colors, onClick)
        } else {
            createSecondaryButton(context, text, colors, onClick)
        }

        return button.apply {
            textSize = Dimens.TEXT_SIZE_SMALL
            minHeight = TvFocusUtils.dpToPx(context, Dimens.BUTTON_HEIGHT_SMALL)
            setPadding(
                TvFocusUtils.dpToPx(context, Dimens.PADDING_MEDIUM),
                0,
                TvFocusUtils.dpToPx(context, Dimens.PADDING_MEDIUM),
                0
            )
        }
    }

    /**
     * Create a text-only button (no background).
     */
    fun createTextButton(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context),
        onClick: (() -> Unit)? = null
    ): MaterialButton {
        return MaterialButton(context).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_BUTTON
            setTextColor(colors.primary)

            // Make it look like a text button (no background)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.primary, 40))

            insetTop = 0
            insetBottom = 0
            minHeight = TvFocusUtils.dpToPx(context, Dimens.BUTTON_HEIGHT)

            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD

            onClick?.let { setOnClickListener { it() } }

            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    /**
     * Create a danger/destructive action button.
     */
    fun createDangerButton(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context),
        onClick: (() -> Unit)? = null
    ): MaterialButton {
        return MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_BUTTON
            setTextColor(colors.error)
            strokeColor = ColorStateList.valueOf(colors.error)
            strokeWidth = TvFocusUtils.dpToPx(context, Dimens.STROKE_WIDTH)

            cornerRadius = TvFocusUtils.dpToPx(context, Dimens.CORNER_RADIUS_SMALL)
            insetTop = 0
            insetBottom = 0
            minHeight = TvFocusUtils.dpToPx(context, Dimens.BUTTON_HEIGHT)

            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD

            onClick?.let { setOnClickListener { it() } }

            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    // ==================== CARD COMPONENTS ====================

    /**
     * Create a Cloudstream-styled card.
     */
    fun createCard(
        context: Context,
        colors: UIColors = UIColors.fromContext(context),
        cornerRadius: Int = Dimens.CORNER_RADIUS_MEDIUM,
        elevation: Float = 0f
    ): MaterialCardView {
        return MaterialCardView(context).apply {
            setCardBackgroundColor(colors.card)
            radius = TvFocusUtils.dpToPx(context, cornerRadius).toFloat()
            cardElevation = elevation
            strokeWidth = 0

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Create a clickable card with ripple effect.
     */
    fun createClickableCard(
        context: Context,
        colors: UIColors = UIColors.fromContext(context),
        onClick: (() -> Unit)? = null
    ): MaterialCardView {
        return createCard(context, colors).apply {
            isClickable = true
            isFocusable = true

            // Add ripple via foreground
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            foreground = androidx.core.content.ContextCompat.getDrawable(context, typedValue.resourceId)

            onClick?.let { setOnClickListener { it() } }

            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    // ==================== TEXT COMPONENTS ====================

    /**
     * Create a header text view.
     */
    fun createHeaderText(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context)
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_HEADER
            setTextColor(colors.text)
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    /**
     * Create a title text view.
     */
    fun createTitleText(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context)
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_TITLE
            setTextColor(colors.text)
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    /**
     * Create a body text view.
     */
    fun createBodyText(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context)
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_BODY
            setTextColor(colors.text)
        }
    }

    /**
     * Create a secondary/caption text view.
     */
    fun createCaptionText(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context)
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_SMALL
            setTextColor(colors.textGray)
        }
    }

    /**
     * Create a dialog title (20sp bold).
     */
    fun createDialogTitle(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context)
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_LARGE
            setTextColor(colors.text)
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    /**
     * Create a clickable text link.
     */
    fun createTextLink(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context),
        onClick: (() -> Unit)? = null
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_BODY
            setTextColor(colors.primary)
            isClickable = true
            isFocusable = true

            setPadding(
                TvFocusUtils.dpToPx(context, Dimens.PADDING_SMALL),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_SMALL),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_SMALL),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_SMALL)
            )

            onClick?.let { setOnClickListener { it() } }

            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    /**
     * Create a compact outlined button (for reorder, edit actions).
     */
    fun createCompactOutlinedButton(
        context: Context,
        text: String,
        colors: UIColors = UIColors.fromContext(context),
        height: Int = 32,
        onClick: (() -> Unit)? = null
    ): MaterialButton {
        return MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_SMALL
            setTextColor(colors.primary)
            strokeColor = ColorStateList.valueOf(colors.primary)
            strokeWidth = TvFocusUtils.dpToPx(context, Dimens.STROKE_WIDTH)

            minimumWidth = 0
            minimumHeight = 0
            minWidth = 0
            minHeight = TvFocusUtils.dpToPx(context, height)
            insetTop = 0
            insetBottom = 0

            setPadding(
                TvFocusUtils.dpToPx(context, Dimens.PADDING_MEDIUM),
                0,
                TvFocusUtils.dpToPx(context, Dimens.PADDING_MEDIUM),
                0
            )

            isAllCaps = false

            onClick?.let { setOnClickListener { it() } }

            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    /**
     * Set a compact outlined button to filled state (for active reorder mode).
     */
    fun setCompactButtonFilled(button: MaterialButton, colors: UIColors) {
        button.backgroundTintList = ColorStateList.valueOf(colors.primary)
        button.setTextColor(colors.onPrimary)
        button.strokeWidth = 0
    }

    /**
     * Set a compact outlined button back to outlined state.
     */
    fun setCompactButtonOutlined(button: MaterialButton, colors: UIColors) {
        button.backgroundTintList = ColorStateList.valueOf(0)
        button.setTextColor(colors.primary)
        button.strokeColor = ColorStateList.valueOf(colors.primary)
        button.strokeWidth = TvFocusUtils.dpToPx(button.context, Dimens.STROKE_WIDTH)
    }

    // ==================== SWITCH COMPONENT ====================

    /**
     * Create a Cloudstream-themed switch toggle.
     *
     * @param context The context
     * @param isChecked Initial checked state
     * @param colors UI colors (resolved from theme)
     * @param onChanged Callback when switch state changes
     */
    fun createSwitch(
        context: Context,
        isChecked: Boolean = false,
        colors: UIColors = UIColors.fromContext(context),
        onChanged: ((Boolean) -> Unit)? = null
    ): SwitchMaterial {
        return SwitchMaterial(context).apply {
            this.isChecked = isChecked

            // Theme the switch track and thumb
            trackTintList = createSwitchTrackColorStateList(colors)
            thumbTintList = createSwitchThumbColorStateList(colors)

            onChanged?.let {
                setOnCheckedChangeListener { _, checked -> it(checked) }
            }

            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    private fun createSwitchTrackColorStateList(colors: UIColors): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val colorValues = intArrayOf(
            ColorUtils.setAlphaComponent(colors.primary, 128),  // Checked: 50% Primary
            ColorUtils.setAlphaComponent(colors.textGray, 80)   // Unchecked: Subtle gray
        )
        return ColorStateList(states, colorValues)
    }

    private fun createSwitchThumbColorStateList(colors: UIColors): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val colorValues = intArrayOf(
            colors.primary,     // Checked: Primary
            colors.textGray     // Unchecked: Gray
        )
        return ColorStateList(states, colorValues)
    }

    // ==================== BADGE / TAG COMPONENTS ====================

    /**
     * Create a small badge/tag view.
     */
    fun createBadge(
        context: Context,
        text: String,
        backgroundColor: Int = Colors.PRIMARY,
        textColor: Int = Colors.ON_PRIMARY
    ): TextView {
        return TextView(context).apply {
            this.text = text
            this.textSize = Dimens.TEXT_SIZE_BADGE
            this.setTextColor(textColor)
            this.typeface = Typeface.DEFAULT_BOLD
            this.gravity = Gravity.CENTER

            setPadding(
                TvFocusUtils.dpToPx(context, 6),
                TvFocusUtils.dpToPx(context, 2),
                TvFocusUtils.dpToPx(context, 6),
                TvFocusUtils.dpToPx(context, 2)
            )

            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = TvFocusUtils.dpToPx(context, Dimens.CORNER_RADIUS_SMALL).toFloat()
            }

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Create an error badge.
     */
    fun createErrorBadge(context: Context, text: String): TextView {
        return createBadge(context, text, Colors.ERROR, Colors.ON_PRIMARY)
    }

    /**
     * Create a success badge.
     */
    fun createSuccessBadge(context: Context, text: String): TextView {
        return createBadge(context, text, Colors.SUCCESS, Colors.ON_PRIMARY)
    }

    // ==================== DIVIDER COMPONENT ====================

    /**
     * Create a horizontal divider line.
     */
    fun createDivider(
        context: Context,
        colors: UIColors = UIColors.fromContext(context),
        marginVertical: Int = Dimens.PADDING_MEDIUM
    ): View {
        return View(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(colors.textGray, 50))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = TvFocusUtils.dpToPx(context, marginVertical)
                bottomMargin = TvFocusUtils.dpToPx(context, marginVertical)
            }
        }
    }

    // ==================== PILL TOGGLE GROUP ====================

    /**
     * Create a pill-style toggle group (like tabs but pill-shaped).
     */
    fun createPillToggleGroup(
        context: Context,
        options: List<String>,
        selectedIndex: Int = 0,
        colors: UIColors = UIColors.fromContext(context),
        onSelectionChanged: ((Int) -> Unit)? = null
    ): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            // Background shape
            background = GradientDrawable().apply {
                setColor(colors.card)
                cornerRadius = TvFocusUtils.dpToPx(context, Dimens.CORNER_RADIUS_PILL).toFloat()
            }

            setPadding(
                TvFocusUtils.dpToPx(context, 4),
                TvFocusUtils.dpToPx(context, 4),
                TvFocusUtils.dpToPx(context, 4),
                TvFocusUtils.dpToPx(context, 4)
            )
        }

        var currentSelection = selectedIndex

        options.forEachIndexed { index, option ->
            val pillView = createPillOption(context, option, index == selectedIndex, colors)

            pillView.setOnClickListener {
                if (index != currentSelection) {
                    // Update old selection
                    (container.getChildAt(currentSelection) as? TextView)?.let {
                        updatePillSelection(it, false, colors)
                    }
                    // Update new selection
                    updatePillSelection(pillView, true, colors)
                    currentSelection = index
                    onSelectionChanged?.invoke(index)
                }
            }

            container.addView(pillView)
        }

        return container
    }

    private fun createPillOption(
        context: Context,
        text: String,
        isSelected: Boolean,
        colors: UIColors
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = Dimens.TEXT_SIZE_BODY
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isFocusable = true
            isClickable = true

            setPadding(
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 8),
                TvFocusUtils.dpToPx(context, 16),
                TvFocusUtils.dpToPx(context, 8)
            )

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )

            updatePillSelection(this, isSelected, colors)

            if (TvFocusUtils.isTvMode(context)) {
                TvFocusUtils.makeFocusable(this)
            }
        }
    }

    private fun updatePillSelection(view: TextView, isSelected: Boolean, colors: UIColors) {
        if (isSelected) {
            view.setTextColor(colors.onPrimary)
            view.background = GradientDrawable().apply {
                setColor(colors.primary)
                cornerRadius = TvFocusUtils.dpToPx(view.context, Dimens.CORNER_RADIUS_PILL).toFloat()
            }
        } else {
            view.setTextColor(colors.textGray)
            view.background = null
        }
    }

    // ==================== STATUS INDICATOR ====================

    /**
     * Create a status indicator dot.
     */
    fun createStatusIndicator(
        context: Context,
        status: Status
    ): View {
        val color = when (status) {
            Status.SUCCESS -> Colors.SUCCESS
            Status.ERROR -> Colors.ERROR
            Status.WARNING -> Colors.WARNING
            Status.NEUTRAL -> Colors.TEXT_GRAY
        }

        return View(context).apply {
            val size = TvFocusUtils.dpToPx(context, 8)
            layoutParams = ViewGroup.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
    }

    enum class Status {
        SUCCESS, ERROR, WARNING, NEUTRAL
    }

    // ==================== SEARCH BAR ====================

    /**
     * Create a search-style input container.
     * Uses the dedicated search background color from the Cloudstream theme.
     */
    fun createSearchContainer(context: Context): FrameLayout {
        return FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Colors.SEARCH)
                cornerRadius = TvFocusUtils.dpToPx(context, Dimens.CORNER_RADIUS_MEDIUM).toFloat()
            }

            setPadding(
                TvFocusUtils.dpToPx(context, Dimens.PADDING_MEDIUM),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_SMALL),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_MEDIUM),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_SMALL)
            )

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    // ==================== LOADING INDICATOR ====================

    /**
     * Create a simple text loading indicator.
     */
    fun createLoadingText(
        context: Context,
        text: String = "Loading...",
        colors: UIColors = UIColors.fromContext(context)
    ): TextView {
        return createCaptionText(context, text, colors).apply {
            gravity = Gravity.CENTER
            setPadding(
                TvFocusUtils.dpToPx(context, Dimens.PADDING_LARGE),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_XLARGE),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_LARGE),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_XLARGE)
            )
        }
    }

    /**
     * Create an empty state view.
     */
    fun createEmptyState(
        context: Context,
        message: String,
        colors: UIColors = UIColors.fromContext(context)
    ): TextView {
        return createBodyText(context, message, colors).apply {
            setTextColor(colors.textGray)
            gravity = Gravity.CENTER
            setPadding(
                TvFocusUtils.dpToPx(context, Dimens.PADDING_LARGE),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_XLARGE),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_LARGE),
                TvFocusUtils.dpToPx(context, Dimens.PADDING_XLARGE)
            )
        }
    }
}
