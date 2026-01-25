package com.lagradost

import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.common.CustomPage
import com.lagradost.common.CustomPageItemTouchHelper
import com.lagradost.common.CustomPagesAdapter
import com.lagradost.common.TvFocusUtils
import com.lagradost.common.ValidationResult
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class HQPornerSettingsFragment : DialogFragment() {

    companion object {
        private val EXAMPLES = """
            Examples of pages you can add:
            • Categories: hqporner.com/category/milf
            • Actresses: hqporner.com/actress/malena-morgan
            • Studios: hqporner.com/studio/free-brazzers-videos
            • Top Videos: hqporner.com/top or /top/month or /top/week
            • Search: hqporner.com/?q=blonde
        """.trimIndent()
    }

    private var currentPages = mutableListOf<CustomPage>()
    private lateinit var mainContainer: LinearLayout
    private lateinit var sectionsRecyclerView: RecyclerView
    private lateinit var adapter: CustomPagesAdapter
    private lateinit var emptyStateText: TextView
    private var itemTouchHelper: ItemTouchHelper? = null

    // Tap-and-reorder state (TV mode only)
    private var reorderModeButton: MaterialButton? = null
    private var reorderSubtitle: TextView? = null
    private var isReorderMode = false
    private var selectedReorderPosition: Int = -1

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
            0
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        resolveThemeColors(context)
        currentPages = loadCustomPages(context).toMutableList()

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
                if (isAdded && ::mainContainer.isInitialized && mainContainer.isAttachedToWindow) {
                    TvFocusUtils.requestInitialFocus(mainContainer)
                }
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
            hint = "Paste URL from hqporner.com"
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

        // Current Sections Header with Reorder button
        val sectionsHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(context, 16), 0, dp(context, 8))
        }

        sectionsHeaderRow.addView(TextView(context).apply {
            text = "Current Sections"
            textSize = 16f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Reorder button (TV mode only)
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
            sectionsHeaderRow.addView(reorderModeButton)
        }

        // Subtitle - changes based on reorder mode
        reorderSubtitle = TextView(context).apply {
            text = getReorderSubtitleText()
            textSize = 13f
            setTextColor(grayTextColor)
            setPadding(0, 0, 0, dp(context, 8))
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
                    val removed = currentPages.removeAt(sourceIndex)
                    if (!saveCustomPages(context, currentPages)) {
                        currentPages.add(sourceIndex, removed)
                        Toast.makeText(context, "Failed to save changes", Toast.LENGTH_SHORT).show()
                        return@CustomPagesAdapter
                    }
                    adapter.submitList(currentPages)
                    updateEmptyState()
                    // Reset reorder mode when list changes
                    if (isTvMode && isReorderMode) {
                        isReorderMode = false
                        selectedReorderPosition = -1
                        adapter.setReorderMode(false, -1)
                        reorderModeButton?.apply {
                            text = "Reorder"
                            backgroundTintList = ColorStateList.valueOf(0)
                            setTextColor(primaryColor)
                            strokeColor = ColorStateList.valueOf(primaryColor)
                            strokeWidth = dp(context, 1)
                        }
                        reorderSubtitle?.text = getReorderSubtitleText()
                    }
                    if (isTvMode) {
                        TvFocusUtils.enableFocusLoop(mainContainer)
                    }
                }
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            },
            onReorderTap = { position ->
                onPageTappedForReorder(position)
            }
        )

        // Sections RecyclerView
        sectionsRecyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@HQPornerSettingsFragment.adapter
            isNestedScrollingEnabled = false // Works better inside NestedScrollView
        }

        // Setup ItemTouchHelper for drag-and-drop (touch mode only)
        if (!isTvMode) {
            val touchHelperCallback = CustomPageItemTouchHelper(adapter) {
                // Called when drag completes - persist the new order
                val newItems = adapter.getItems()
                // Guard against accidental data loss from empty adapter
                if (newItems.isNotEmpty() || currentPages.isEmpty()) {
                    val oldItems = currentPages.toList()
                    currentPages.clear()
                    currentPages.addAll(newItems)
                    if (!saveCustomPages(context, currentPages)) {
                        currentPages.clear()
                        currentPages.addAll(oldItems)
                        adapter.submitList(currentPages)
                        Toast.makeText(context, "Failed to save changes", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            itemTouchHelper = ItemTouchHelper(touchHelperCallback)
            itemTouchHelper?.attachToRecyclerView(sectionsRecyclerView)
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
                when (val result = HQPornerUrlValidator.validate(text)) {
                    is ValidationResult.Valid -> {
                        statusText.text = "Detected: ${result.label}"
                        labelInput.setText(result.label)
                        labelInputLayout.visibility = View.VISIBLE
                        addButton.isEnabled = true
                    }
                    is ValidationResult.InvalidDomain -> {
                        statusText.text = "URL must be from hqporner.com"
                        labelInputLayout.visibility = View.GONE
                        addButton.isEnabled = false
                    }
                    is ValidationResult.InvalidPath -> {
                        statusText.text = "Invalid URL (use category, actress, studio, or top pages)"
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
            val result = HQPornerUrlValidator.validate(urlInput.text.toString())
            if (result is ValidationResult.Valid) {
                val label = labelInput.text.toString().ifBlank { result.label }
                val newPage = CustomPage(result.path, label)
                if (currentPages.none { it.path == newPage.path }) {
                    currentPages.add(newPage)
                    if (!saveCustomPages(context, currentPages)) {
                        currentPages.removeAt(currentPages.size - 1)
                        statusText.text = "Failed to save. Please try again."
                        return@setOnClickListener
                    }
                    urlInput.text?.clear()
                    labelInput.text?.clear()
                    labelInputLayout.visibility = View.GONE
                    statusText.text = "Added! Restart app to see changes."
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

        // Build view hierarchy
        mainContainer.addView(title)
        mainContainer.addView(card)
        mainContainer.addView(examplesToggle)
        mainContainer.addView(examplesText)
        mainContainer.addView(sectionsHeaderRow)
        mainContainer.addView(reorderSubtitle)
        mainContainer.addView(filterInputLayout)
        mainContainer.addView(emptyStateText)
        mainContainer.addView(sectionsRecyclerView)
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

    private fun loadCustomPages(@Suppress("UNUSED_PARAMETER") context: Context): List<CustomPage> {
        return try {
            val json = getKey<String>(HQPornerPlugin.STORAGE_KEY) ?: return emptyList()
            CustomPage.listFromJson(json)
        } catch (e: Exception) {
            Log.e("HQPornerSettings", "Failed to load custom pages (${e.javaClass.simpleName})", e)
            emptyList()
        }
    }

    private fun saveCustomPages(@Suppress("UNUSED_PARAMETER") context: Context, pages: List<CustomPage>): Boolean {
        return try {
            val json = CustomPage.listToJson(pages)
            setKey(HQPornerPlugin.STORAGE_KEY, json)
            // Verify write succeeded
            getKey<String>(HQPornerPlugin.STORAGE_KEY) == json
        } catch (e: Exception) {
            Log.e("HQPornerSettings", "Failed to save custom pages (${e.javaClass.simpleName})", e)
            false
        }
    }

    private fun getReorderSubtitleText(): String {
        return when {
            isReorderMode && selectedReorderPosition >= 0 -> "Tap destination to move section"
            isReorderMode -> "Tap a section to select, then tap destination"
            isTvMode -> "Tap Reorder to arrange sections"
            else -> "Drag ≡ to reorder"
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

        reorderSubtitle?.text = getReorderSubtitleText()
        adapter.setReorderMode(isReorderMode, -1)
    }

    private fun onPageTappedForReorder(position: Int) {
        if (!isReorderMode) return

        val context = requireContext()

        if (selectedReorderPosition < 0) {
            // First tap - select the section
            selectedReorderPosition = position
            reorderSubtitle?.text = getReorderSubtitleText()
            adapter.setReorderMode(true, position)
            // Restore focus to the tapped item after adapter refresh
            restoreFocusToPosition(position)
        } else if (selectedReorderPosition == position) {
            // Tapped same item - deselect
            selectedReorderPosition = -1
            reorderSubtitle?.text = getReorderSubtitleText()
            adapter.setReorderMode(true, -1)
            // Restore focus to the tapped item after adapter refresh
            restoreFocusToPosition(position)
        } else {
            // Second tap - move the section
            val sourceIdx = adapter.getSourceIndex(selectedReorderPosition)
            val destIdx = adapter.getSourceIndex(position)

            if (sourceIdx >= 0 && destIdx >= 0) {
                val movedItem = currentPages.removeAt(sourceIdx)
                currentPages.add(destIdx, movedItem)
                if (!saveCustomPages(context, currentPages)) {
                    currentPages.removeAt(destIdx)
                    currentPages.add(sourceIdx, movedItem)
                    Toast.makeText(context, "Failed to save changes", Toast.LENGTH_SHORT).show()
                }
                adapter.submitList(currentPages)
            }

            // Reset selection
            selectedReorderPosition = -1
            reorderSubtitle?.text = getReorderSubtitleText()
            adapter.setReorderMode(true, -1)
            // Restore focus to the target position after move
            restoreFocusToPosition(position)

            if (isTvMode) {
                TvFocusUtils.enableFocusLoop(mainContainer)
            }
        }
    }

    private fun restoreFocusToPosition(position: Int) {
        sectionsRecyclerView.post {
            val viewHolder = sectionsRecyclerView.findViewHolderForAdapterPosition(position)
            viewHolder?.itemView?.requestFocus()
        }
    }

    private fun dp(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
