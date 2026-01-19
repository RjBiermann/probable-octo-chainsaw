package com.lagradost

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MissAVSettingsFragment : DialogFragment() {

    companion object {
        private val EXAMPLES = """
            Examples of pages you can add:
            • Categories: missav.ws/uncensored or /censored
            • Genres: missav.ws/genres/amateur
            • Actresses: missav.ws/actresses/yua-mikami
            • Makers: missav.ws/makers/s1-no-1-style
            • Tags: missav.ws/tags/creampie
            • Search: missav.ws/search/keyword
        """.trimIndent()
    }

    private var currentPages = mutableListOf<CustomPage>()
    private lateinit var mainContainer: LinearLayout
    private lateinit var sectionsRecyclerView: RecyclerView
    private lateinit var adapter: CustomPagesAdapter
    private lateinit var emptyStateText: TextView
    private var itemTouchHelper: ItemTouchHelper? = null

    // TV mode detection - evaluated once per fragment lifecycle
    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    // Theme colors resolved at runtime
    private var textColor: Int = 0
    private var grayTextColor: Int = 0
    private var backgroundColor: Int = 0
    private var cardColor: Int = 0
    private var primaryColor: Int = 0

    private fun resolveThemeColors(context: Context) {
        val tv = TypedValue()
        val theme = context.theme

        // Try CloudStream's custom attributes first, fall back to standard Android
        textColor = resolveAttr(theme, tv, "textColor", android.R.attr.textColorPrimary, context)
        grayTextColor = resolveAttr(theme, tv, "grayTextColor", android.R.attr.textColorSecondary, context)
        backgroundColor = resolveAttr(theme, tv, "primaryBlackBackground", android.R.attr.colorBackground, context)
        cardColor = resolveAttr(theme, tv, "boxItemBackground", android.R.attr.colorBackgroundFloating, context)
        primaryColor = resolveAttr(theme, tv, "colorPrimary", android.R.attr.colorPrimary, context)
    }

    private fun resolveAttr(theme: android.content.res.Resources.Theme, tv: TypedValue, customAttr: String, fallbackAttr: Int, context: Context): Int {
        val customId = context.resources.getIdentifier(customAttr, "attr", context.packageName)
        return if (customId != 0 && theme.resolveAttribute(customId, tv, true)) {
            tv.data
        } else if (theme.resolveAttribute(fallbackAttr, tv, true)) {
            tv.data
        } else {
            Log.w("MissAVSettings", "Failed to resolve theme attribute: $customAttr (fallback: $fallbackAttr)")
            0
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        resolveThemeColors(context)
        currentPages = CustomPage.loadFromPrefs(context).toMutableList()

        val contentView = createSettingsView(context)

        return if (isTvMode) {
            // TV: Use AlertDialog for better D-pad navigation
            AlertDialog.Builder(context, theme)
                .setView(contentView)
                .create().apply {
                    window?.apply {
                        // Make dialog wider on TV for better visibility
                        setLayout(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.WRAP_CONTENT
                        )
                    }
                }
        } else {
            // Phone: Use BottomSheetDialog
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
    ): View? {
        // Return null since we're using onCreateDialog
        return null
    }

    override fun onStart() {
        super.onStart()
        // Request initial focus on TV after dialog is shown
        if (isTvMode) {
            dialog?.window?.decorView?.post {
                TvFocusUtils.requestInitialFocus(mainContainer)
            }
        }
    }

    private fun createSettingsView(context: Context): View {
        val scrollView = NestedScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(backgroundColor)
            // Allow children to receive focus for D-pad navigation
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24))
            // Allow focus traversal within container
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        // Title
        val title = TextView(context).apply {
            text = "Custom Homepage Sections"
            textSize = 20f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(context, 16))
        }

        // Input Card
        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 16) }
            setCardBackgroundColor(cardColor)
            radius = dp(context, 12).toFloat()
            cardElevation = 0f
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
        }

        // URL Input
        val urlInputLayout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "Paste URL from missav.ws"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val urlInput = TextInputEditText(context).apply {
            setTextColor(textColor)
        }
        urlInputLayout.addView(urlInput)

        // Apply TV focus handling to URL input (uses Material's native stroke color)
        if (isTvMode) {
            TvFocusUtils.makeFocusableTextInput(urlInputLayout, primaryColor)
        } else {
            urlInputLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(primaryColor))
        }

        // Status Text
        val statusText = TextView(context).apply {
            setTextColor(grayTextColor)
            textSize = 14f
            setPadding(0, dp(context, 8), 0, dp(context, 8))
        }

        // Label Input (hidden by default)
        val labelInputLayout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "Label (auto-detected)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            visibility = View.GONE
        }
        val labelInput = TextInputEditText(context).apply {
            setTextColor(textColor)
        }
        labelInputLayout.addView(labelInput)

        // Apply TV focus handling to label input (uses Material's native stroke color)
        if (isTvMode) {
            TvFocusUtils.makeFocusableTextInput(labelInputLayout, primaryColor)
        } else {
            labelInputLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(primaryColor))
        }

        // Add Button
        val addButton = MaterialButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 12) }
            text = "Add Section"
            isEnabled = false
            backgroundTintList = ColorStateList.valueOf(primaryColor)
        }

        // Apply TV focus handling to add button
        if (isTvMode) {
            TvFocusUtils.makeFocusable(addButton)
        }

        cardContent.addView(urlInputLayout)
        cardContent.addView(statusText)
        cardContent.addView(labelInputLayout)
        cardContent.addView(addButton)
        card.addView(cardContent)

        // Examples Section
        val examplesToggle = TextView(context).apply {
            text = "Show examples"
            setTextColor(primaryColor)
            textSize = 14f
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
        }

        // Apply TV focus handling to examples toggle
        if (isTvMode) {
            TvFocusUtils.makeFocusable(examplesToggle)
        }

        val examplesText = TextView(context).apply {
            text = EXAMPLES
            setTextColor(grayTextColor)
            textSize = 13f
            visibility = View.GONE
            setPadding(dp(context, 8), 0, 0, dp(context, 16))
        }

        examplesToggle.setOnClickListener {
            if (examplesText.visibility == View.GONE) {
                examplesText.visibility = View.VISIBLE
                examplesToggle.text = "Hide examples"
            } else {
                examplesText.visibility = View.GONE
                examplesToggle.text = "Show examples"
            }
        }

        // Current Sections Header
        val sectionsHeader = TextView(context).apply {
            text = "Current Sections"
            textSize = 16f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(context, 16), 0, dp(context, 8))
        }

        // Search/Filter Input
        val filterInputLayout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 8)
            }
            hint = "Filter sections..."
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        }
        val filterInput = TextInputEditText(context).apply {
            setTextColor(textColor)
        }
        filterInputLayout.addView(filterInput)

        // Apply TV focus handling to filter input (uses Material's native stroke color)
        if (isTvMode) {
            TvFocusUtils.makeFocusableTextInput(filterInputLayout, primaryColor)
        } else {
            filterInputLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(primaryColor))
        }

        // Empty state text
        emptyStateText = TextView(context).apply {
            text = "(none added)"
            setTextColor(grayTextColor)
            setPadding(0, dp(context, 8), 0, dp(context, 8))
            visibility = View.GONE
        }

        // Initialize adapter
        adapter = CustomPagesAdapter(
            context = context,
            isTvMode = isTvMode,
            textColor = textColor,
            grayTextColor = grayTextColor,
            primaryColor = primaryColor,
            onRemove = { position ->
                val sourceIndex = adapter.getSourceIndex(position)
                if (sourceIndex >= 0) {
                    currentPages.removeAt(sourceIndex)
                    CustomPage.saveToPrefs(context, currentPages)
                    adapter.submitList(currentPages)
                    updateEmptyState()
                    if (isTvMode) {
                        TvFocusUtils.enableFocusLoop(mainContainer)
                    }
                }
            },
            onMoveUp = { position ->
                val sourceIndex = adapter.getSourceIndex(position)
                if (sourceIndex > 0) {
                    java.util.Collections.swap(currentPages, sourceIndex, sourceIndex - 1)
                    CustomPage.saveToPrefs(context, currentPages)
                    adapter.submitList(currentPages)
                    if (isTvMode) {
                        TvFocusUtils.enableFocusLoop(mainContainer)
                    }
                }
            },
            onMoveDown = { position ->
                val sourceIndex = adapter.getSourceIndex(position)
                if (sourceIndex >= 0 && sourceIndex < currentPages.size - 1) {
                    java.util.Collections.swap(currentPages, sourceIndex, sourceIndex + 1)
                    CustomPage.saveToPrefs(context, currentPages)
                    adapter.submitList(currentPages)
                    if (isTvMode) {
                        TvFocusUtils.enableFocusLoop(mainContainer)
                    }
                }
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            }
        )

        // Sections RecyclerView
        sectionsRecyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@MissAVSettingsFragment.adapter
            isNestedScrollingEnabled = false // Works better inside NestedScrollView
        }

        // Setup ItemTouchHelper for drag-and-drop (touch mode only)
        if (!isTvMode) {
            val touchHelperCallback = CustomPageItemTouchHelper(adapter) {
                // Called when drag completes - persist the new order
                val newItems = adapter.getItems()
                // Guard against accidental data loss from empty adapter
                if (newItems.isNotEmpty() || currentPages.isEmpty()) {
                    currentPages.clear()
                    currentPages.addAll(newItems)
                    CustomPage.saveToPrefs(context, currentPages)
                }
            }
            itemTouchHelper = ItemTouchHelper(touchHelperCallback)
            itemTouchHelper?.attachToRecyclerView(sectionsRecyclerView)
        }

        // Close Button
        val closeButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 24) }
            text = "Close"
            strokeColor = ColorStateList.valueOf(grayTextColor)
        }

        // Apply TV focus handling to close button
        if (isTvMode) {
            TvFocusUtils.makeFocusable(closeButton)
        }

        // Wire up URL validation
        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                if (text.isBlank()) {
                    statusText.text = ""
                    labelInputLayout.visibility = View.GONE
                    addButton.isEnabled = false
                    return
                }
                when (val result = MissAVUrlValidator.validate(text)) {
                    is ValidationResult.Valid -> {
                        statusText.text = "Detected: ${result.label}"
                        labelInput.setText(result.label)
                        labelInputLayout.visibility = View.VISIBLE
                        addButton.isEnabled = true
                    }
                    is ValidationResult.InvalidDomain -> {
                        statusText.text = "URL must be from missav.ws"
                        labelInputLayout.visibility = View.GONE
                        addButton.isEnabled = false
                    }
                    is ValidationResult.InvalidPath -> {
                        statusText.text = "Invalid URL (use categories, genres, actresses, makers, or tags)"
                        labelInputLayout.visibility = View.GONE
                        addButton.isEnabled = false
                    }
                }
            }
        })

        // Wire up filter input
        filterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString() ?: "")
                updateEmptyState()
            }
        })

        // Wire up add button
        addButton.setOnClickListener {
            val result = MissAVUrlValidator.validate(urlInput.text.toString())
            if (result is ValidationResult.Valid) {
                val label = labelInput.text.toString().ifBlank { result.label }
                val newPage = CustomPage(result.path, label)
                if (currentPages.none { it.path == newPage.path }) {
                    currentPages.add(newPage)
                    val saved = CustomPage.saveToPrefs(context, currentPages)
                    urlInput.text?.clear()
                    labelInput.text?.clear()
                    labelInputLayout.visibility = View.GONE
                    statusText.text = if (saved) "Added! Restart app to see changes." else "Failed to save. Please try again."
                    addButton.isEnabled = false
                    adapter.submitList(currentPages)
                    updateEmptyState()
                    // Re-enable focus loop after list changes on TV
                    if (isTvMode) {
                        TvFocusUtils.enableFocusLoop(mainContainer)
                    }
                } else {
                    statusText.text = "This section already exists"
                }
            }
        }

        // Wire up close button
        closeButton.setOnClickListener { dismiss() }

        // Build view hierarchy
        mainContainer.addView(title)
        mainContainer.addView(card)
        mainContainer.addView(examplesToggle)
        mainContainer.addView(examplesText)
        mainContainer.addView(sectionsHeader)
        mainContainer.addView(filterInputLayout)
        mainContainer.addView(emptyStateText)
        mainContainer.addView(sectionsRecyclerView)
        mainContainer.addView(closeButton)
        scrollView.addView(mainContainer)

        // Initial list render
        adapter.submitList(currentPages)
        updateEmptyState()

        // Enable focus loop on TV (wraps from last to first focusable element)
        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(mainContainer)
        }

        return scrollView
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        sectionsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyStateText.text = if (adapter.isFiltered()) "(no matches)" else "(none added)"
    }

    private fun dp(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
