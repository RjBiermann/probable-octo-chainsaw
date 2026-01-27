package com.lagradost.common

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Base class for custom pages settings dialog fragments.
 * Provides all common UI and functionality for managing custom homepage sections.
 *
 * Subclasses need to override:
 * - Site-specific configuration (domain, examples, error messages)
 * - URL validation logic
 * - Repository for storage operations
 *
 * Example implementation using Repository pattern:
 * ```kotlin
 * class MyPluginSettingsFragment : BaseCustomPagesSettingsFragment() {
 *     override val siteDomain = "example.com"
 *     override val siteExamples = "Examples:\n• Categories: example.com/categories/foo"
 *     override val invalidPathMessage = "Invalid URL (use category or tag pages)"
 *     override val logTag = "MyPluginSettings"
 *
 *     override val repository = GlobalStorageCustomPagesRepository(
 *         storageKey = MyPlugin.STORAGE_KEY,
 *         legacyPrefsName = MyPlugin.LEGACY_PREFS_NAME,
 *         tag = logTag
 *     )
 *
 *     override fun validateUrl(url: String) = MyUrlValidator.validate(url)
 * }
 * ```
 */
abstract class BaseCustomPagesSettingsFragment : DialogFragment() {

    // ===== Abstract members - must be overridden by subclasses =====

    /** Domain name for URL hints and error messages (e.g., "hqporner.com") */
    protected abstract val siteDomain: String

    /** Examples text shown when user taps "Show examples" */
    protected abstract val siteExamples: String

    /** Error message shown when URL path is invalid */
    protected abstract val invalidPathMessage: String

    /** Tag used for logging */
    protected abstract val logTag: String

    /** Repository for storage operations. Override this to use the Repository pattern. */
    protected open val repository: CustomPagesRepository? = null

    /** Validate a URL and return the result */
    protected abstract fun validateUrl(url: String): ValidationResult

    /**
     * Load custom pages from storage.
     * Default implementation delegates to repository if provided.
     * Override this if not using repository pattern.
     */
    protected open fun loadPages(): List<CustomPage> {
        if (repository == null) {
            Log.w(logTag, "loadPages: repository is null - override loadPages() or provide repository")
        }
        return repository?.load() ?: emptyList()
    }

    /**
     * Save custom pages to storage.
     * Default implementation delegates to repository if provided.
     * Override this if not using repository pattern.
     * @return true on success, false on failure
     */
    protected open fun savePages(pages: List<CustomPage>): Boolean {
        if (repository == null) {
            Log.w(logTag, "savePages: repository is null - override savePages() or provide repository")
        }
        return repository?.save(pages) ?: false
    }

    // ===== Instance state =====

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

    // Theme colors resolved at runtime using CloudstreamUI
    private lateinit var colors: CloudstreamUI.UIColors
    private val textColor get() = colors.text
    private val grayTextColor get() = colors.textGray
    private val backgroundColor get() = colors.background
    private val cardColor get() = colors.card
    private val primaryColor get() = colors.primary
    private val onPrimaryColor get() = colors.onPrimary

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = CloudstreamUI.UIColors.fromContext(context)
        currentPages = loadPages().toMutableList()

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
        val title = CloudstreamUI.createDialogTitle(context, "Custom Homepage Sections", colors).apply {
            setPadding(0, 0, 0, dp(context, 16))
        }

        // Input Card
        val card = CloudstreamUI.createCard(context, colors, CloudstreamUI.Dimens.CORNER_RADIUS_LARGE).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(context, 16)
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
        }

        // URL Input
        val urlInputLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "Paste URL from $siteDomain"
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
        val labelInputLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
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
        val addButton = CloudstreamUI.createPrimaryButton(context, "Add Section", colors).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 12) }
            isEnabled = false
        }

        cardContent.addView(urlInputLayout)
        cardContent.addView(statusText)
        cardContent.addView(labelInputLayout)
        cardContent.addView(addButton)
        card.addView(cardContent)

        // Examples Section
        val examplesText = CloudstreamUI.createCaptionText(context, siteExamples, colors).apply {
            textSize = 13f
            visibility = View.GONE
            setPadding(dp(context, 8), 0, 0, dp(context, 16))
        }

        val examplesToggle = CloudstreamUI.createTextLink(context, "Show examples", colors)
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

        sectionsHeaderRow.addView(CloudstreamUI.createTitleText(context, "Current Sections", colors).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Reorder button (TV mode only)
        if (isTvMode) {
            reorderModeButton = CloudstreamUI.createCompactOutlinedButton(context, "Reorder", colors) {
                toggleReorderMode(context)
            }
            sectionsHeaderRow.addView(reorderModeButton)
        }

        // Subtitle - changes based on reorder mode
        reorderSubtitle = CloudstreamUI.createCaptionText(context, getReorderSubtitleText(), colors).apply {
            textSize = 13f
            setPadding(0, 0, 0, dp(context, 8))
        }

        // Search/Filter Input
        val filterInputLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
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
        emptyStateText = CloudstreamUI.createCaptionText(context, "(none added)", colors).apply {
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
                    val pageToDelete = currentPages[sourceIndex]
                    DialogUtils.showDeleteConfirmation(
                        context = context,
                        itemName = pageToDelete.label,
                        itemType = "page"
                    ) {
                        if (!isAdded) return@showDeleteConfirmation
                        val removed = currentPages.removeAt(sourceIndex)
                        if (!savePages(currentPages)) {
                            currentPages.add(sourceIndex, removed)
                            Toast.makeText(context, "Failed to save changes", Toast.LENGTH_SHORT).show()
                            return@showDeleteConfirmation
                        }
                        adapter.submitList(currentPages)
                        updateEmptyState()
                        // Reset reorder mode when list changes
                        if (isTvMode && isReorderMode) {
                            isReorderMode = false
                            selectedReorderPosition = -1
                            adapter.setReorderMode(false, -1)
                            reorderModeButton?.let { button ->
                                button.text = "Reorder"
                                CloudstreamUI.setCompactButtonOutlined(button, colors)
                            }
                            reorderSubtitle?.text = getReorderSubtitleText()
                        }
                        if (isTvMode) {
                            TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, sectionsRecyclerView)
                        }
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
            this.adapter = this@BaseCustomPagesSettingsFragment.adapter
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
                    if (!savePages(currentPages)) {
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
                when (val result = validateUrl(text)) {
                    is ValidationResult.Valid -> {
                        statusText.text = "Detected: ${result.label}"
                        labelInput.setText(result.label)
                        labelInputLayout.visibility = View.VISIBLE
                        addButton.isEnabled = true
                    }
                    is ValidationResult.InvalidDomain -> {
                        statusText.text = "URL must be from $siteDomain"
                        labelInputLayout.visibility = View.GONE
                        addButton.isEnabled = false
                    }
                    is ValidationResult.InvalidPath -> {
                        statusText.text = invalidPathMessage
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
            val result = validateUrl(urlInput.text.toString())
            if (result is ValidationResult.Valid) {
                val label = labelInput.text.toString().ifBlank { result.label }
                val newPage = CustomPage(result.path, label)
                if (currentPages.none { it.path == newPage.path }) {
                    currentPages.add(newPage)
                    if (!savePages(currentPages)) {
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
                        TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, sectionsRecyclerView)
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
            TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, sectionsRecyclerView)
        }

        return scrollView
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        sectionsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyStateText.text = if (adapter.isFiltered()) "(no matches)" else "(none added)"
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

        reorderModeButton?.let { button ->
            if (isReorderMode) {
                button.text = "Done"
                CloudstreamUI.setCompactButtonFilled(button, colors)
            } else {
                button.text = "Reorder"
                CloudstreamUI.setCompactButtonOutlined(button, colors)
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
                if (!savePages(currentPages)) {
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
                TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, sectionsRecyclerView)
            }
        }
    }

    private fun restoreFocusToPosition(position: Int) {
        sectionsRecyclerView.post {
            if (!isAdded) {
                android.util.Log.d(logTag, "restoreFocusToPosition: fragment not added, skipping")
                return@post
            }
            val viewHolder = sectionsRecyclerView.findViewHolderForAdapterPosition(position)
            if (viewHolder == null) {
                android.util.Log.d(logTag, "restoreFocusToPosition: no ViewHolder at position $position")
                return@post
            }
            if (!viewHolder.itemView.requestFocus()) {
                android.util.Log.d(logTag, "restoreFocusToPosition: requestFocus() failed for position $position")
            }
        }
    }

    private fun dp(context: Context, dp: Int): Int = TvFocusUtils.dpToPx(context, dp)

    override fun onDestroyView() {
        super.onDestroyView()
        // Detach ItemTouchHelper to prevent memory leaks
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        // Clear RecyclerView adapter to prevent memory leaks
        if (::sectionsRecyclerView.isInitialized) {
            sectionsRecyclerView.adapter = null
        }
    }
}
