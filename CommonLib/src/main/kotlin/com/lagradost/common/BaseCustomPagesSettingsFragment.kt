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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Base class for custom pages settings dialog fragments.
 * Provides all common UI and functionality for managing custom homepage sections
 * using reactive ViewModel state management.
 *
 * Subclasses need to override:
 * - Site-specific configuration (domain, examples, error messages)
 * - URL validation function
 * - ViewModel instance
 *
 * Example:
 * ```kotlin
 * class MyPluginSettingsFragment : BaseCustomPagesSettingsFragment() {
 *     override val siteDomain = "example.com"
 *     override val siteExamples = "Examples:\n• Categories: example.com/categories/foo"
 *     override val invalidPathMessage = "Invalid URL (use category or tag pages)"
 *     override val logTag = "MyPluginSettings"
 *
 *     override val validator: (String) -> ValidationResult = MyUrlValidator::validate
 *
 *     override val viewModel = CustomPagesViewModelFactory.create(
 *         repository = MyPlugin.createRepository("MyPluginVM"),
 *         validator = validator,
 *         logTag = "MyPluginVM"
 *     )
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

    /** ViewModel for reactive state management. */
    protected abstract val viewModel: BaseCustomPagesViewModel

    /** URL validation function (plugin-specific). Used for real-time input validation and passed to the factory. */
    protected abstract val validator: (String) -> ValidationResult

    private lateinit var mainContainer: LinearLayout
    private lateinit var sectionsRecyclerView: RecyclerView
    private lateinit var adapter: CustomPagesAdapter
    private lateinit var emptyStateText: TextView
    private var itemTouchHelper: ItemTouchHelper? = null
    private lateinit var progressBar: ProgressBar
    private var stateCollectionJob: Job? = null

    // Tap-and-reorder state (TV mode only)
    private var reorderModeButton: MaterialButton? = null
    private var reorderSubtitle: TextView? = null
    private var isReorderMode = false
    private var selectedReorderPosition: Int? = null

    // TV mode detection - evaluated once per fragment lifecycle
    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    // Theme colors resolved at runtime using CloudstreamUI
    private lateinit var colors: CloudstreamUI.UIColors
    private val textColor get() = colors.text
    private val grayTextColor get() = colors.textGray
    private val backgroundColor get() = colors.background
    private val primaryColor get() = colors.primary

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = CloudstreamUI.UIColors.fromContext(context)

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

        // Observe ViewModel state changes. Manually cancelled in onStop() to approximate STARTED lifecycle scoping.
        // Cancel previous job to avoid duplicate collectors since onStart() can be called multiple times.
        stateCollectionJob?.cancel()
        stateCollectionJob = lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (!isAdded) return@collect
                updateUIFromState(state)
            }
        }

        // Request initial focus on TV after dialog is shown
        if (isTvMode) {
            dialog?.window?.decorView?.post {
                if (isAdded && ::mainContainer.isInitialized && mainContainer.isAttachedToWindow) {
                    TvFocusUtils.requestInitialFocus(mainContainer)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stateCollectionJob?.cancel()
        stateCollectionJob = null
    }

    private fun createSettingsView(context: Context): View {
        val scrollView = SafeNestedScrollView(context).apply {
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
                viewModel.toggleReorderMode()
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
                if (!isAdded) return@CustomPagesAdapter
                viewModel.deletePage(position)
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
                if (!isAdded) return@CustomPageItemTouchHelper

                val newItems = adapter.getItems()
                // Guard against accidental data loss from empty adapter
                if (newItems.isNotEmpty() || viewModel.uiState.value.pages.isEmpty()) {
                    // Re-check lifecycle before storage operation
                    if (!isAdded) {
                        Log.w(logTag, "Fragment detached before saveOrder(), aborting drag persist")
                        return@CustomPageItemTouchHelper
                    }
                    viewModel.saveOrder(newItems)
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
                when (val result = validator(text)) {
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
                val query = s?.toString() ?: ""
                viewModel.filterPages(query)
            }
        })

        // Wire up add button
        addButton.setOnClickListener {
            val url = urlInput.text.toString()
            val label = labelInput.text.toString()

            // Only submit if local validation passes (avoids unnecessary async call)
            val result = validator(url)
            if (result is ValidationResult.Valid) {
                viewModel.addPage(url, label)
                // Optimistically clear inputs — Snackbar will show success/error from ViewModel
                urlInput.text?.clear()
                labelInput.text?.clear()
                labelInputLayout.visibility = View.GONE
                statusText.text = ""
                addButton.isEnabled = false
            }
        }

        // Build view hierarchy
        // Loading indicator (hidden by default, shown during async operations)
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(primaryColor)
            visibility = View.GONE
        }

        mainContainer.addView(title)
        mainContainer.addView(progressBar)
        mainContainer.addView(card)
        mainContainer.addView(examplesToggle)
        mainContainer.addView(examplesText)
        mainContainer.addView(sectionsHeaderRow)
        mainContainer.addView(reorderSubtitle)
        mainContainer.addView(filterInputLayout)
        mainContainer.addView(emptyStateText)
        mainContainer.addView(sectionsRecyclerView)

        // Reset All Data button
        val resetButton = CloudstreamUI.createDangerButton(context, "Reset All Data", colors) {
            showResetConfirmation()
        }.apply {
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 16)
            }
        }
        mainContainer.addView(resetButton)

        scrollView.addView(mainContainer)

        // Initial list render (ViewModel will push state via StateFlow)
        adapter.submitList(emptyList())
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
            isReorderMode && selectedReorderPosition != null -> "Tap destination to move section"
            isReorderMode -> "Tap a section to select, then tap destination"
            isTvMode -> "Tap Reorder to arrange sections"
            else -> "Drag ≡ to reorder"
        }
    }

    private fun onPageTappedForReorder(position: Int) {
        if (!isReorderMode || !isAdded) return

        val currentState = viewModel.uiState.value
        val currentSelected = currentState.selectedReorderPosition

        if (currentSelected == null) {
            viewModel.selectForReorder(position)
            restoreFocusToPosition(position)
        } else if (currentSelected == position) {
            viewModel.selectForReorder(null)
            restoreFocusToPosition(position)
        } else {
            viewModel.reorderPages(currentSelected, position)
            restoreFocusToPosition(position)
        }
    }

    private fun restoreFocusToPosition(position: Int) {
        sectionsRecyclerView.post {
            if (!isAdded) {
                Log.d(logTag, "restoreFocusToPosition: fragment not added, skipping")
                return@post
            }
            val viewHolder = sectionsRecyclerView.findViewHolderForAdapterPosition(position)
            if (viewHolder == null) {
                Log.d(logTag, "restoreFocusToPosition: no ViewHolder at position $position")
                return@post
            }
            if (!viewHolder.itemView.requestFocus()) {
                Log.d(logTag, "restoreFocusToPosition: requestFocus() failed for position $position")
            }
        }
    }

    /**
     * Show confirmation dialog before resetting all data.
     */
    private fun showResetConfirmation() {
        if (!isAdded) return

        AlertDialog.Builder(requireContext())
            .setTitle("Reset All Data")
            .setMessage("This will permanently delete all your custom sections. This cannot be undone. Are you sure?")
            .setPositiveButton("Reset") { _, _ ->
                if (!isAdded) return@setPositiveButton
                viewModel.clearAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Update UI based on ViewModel state.
     * Called when ViewModel StateFlow emits new state.
     */
    private fun updateUIFromState(state: BaseCustomPagesViewModel.SettingsUiState) {
        // Show/hide loading indicator
        if (::progressBar.isInitialized) {
            progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        }

        // Update adapter with filtered pages
        if (::adapter.isInitialized) {
            adapter.submitList(state.filteredPages)
            updateEmptyState()
        }

        // Handle reorder mode (TV only)
        if (isTvMode && ::adapter.isInitialized) {
            val wasReorderMode = isReorderMode
            isReorderMode = state.isReorderMode
            selectedReorderPosition = state.selectedReorderPosition

            // Update reorder button
            reorderModeButton?.let { button ->
                if (state.isReorderMode) {
                    button.text = "Done"
                    CloudstreamUI.setCompactButtonFilled(button, colors)
                } else {
                    button.text = "Reorder"
                    CloudstreamUI.setCompactButtonOutlined(button, colors)
                }
            }

            // Update subtitle
            reorderSubtitle?.text = getReorderSubtitleText()

            // Update adapter mode
            adapter.setReorderMode(state.isReorderMode, state.selectedReorderPosition ?: -1)

            // Re-enable focus loop if reorder mode changed
            if (wasReorderMode != state.isReorderMode && ::sectionsRecyclerView.isInitialized) {
                TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, sectionsRecyclerView)
            }
        }

        // Handle save status (one-time events)
        when (val status = state.saveStatus) {
            is BaseCustomPagesViewModel.SaveStatus.Success -> {
                showSnackbar(status.message)
                viewModel.clearSaveStatus()
            }
            is BaseCustomPagesViewModel.SaveStatus.Deleted -> {
                showSnackbar(status.message, actionLabel = "Undo") {
                    viewModel.undoDelete(status.deletedPage, status.sourceIndex)
                }
                viewModel.clearSaveStatus()
            }
            is BaseCustomPagesViewModel.SaveStatus.Error -> {
                showSnackbar(status.message, isError = true)
                viewModel.clearSaveStatus()
            }
            BaseCustomPagesViewModel.SaveStatus.Idle -> {
                // No action needed
            }
        }
    }

    /**
     * Show a Snackbar anchored to the dialog content. Falls back to Toast on TV.
     * On TV, undo actions are not available (Toast cannot carry actions) — a confirmation
     * dialog should be used for destructive operations where undo is critical.
     */
    private fun showSnackbar(
        message: String,
        isError: Boolean = false,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        if (!isAdded) return

        // On TV, use Toast (Snackbar doesn't work well with D-pad).
        // Undo actions are lost — this is a known limitation.
        if (isTvMode) {
            val tvMessage = if (actionLabel != null) "$message (${actionLabel} not available on TV)" else message
            Toast.makeText(requireContext(), tvMessage, if (isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            return
        }

        val anchorView = dialog?.window?.decorView?.findViewById<View>(android.R.id.content)
            ?: mainContainer
        val snackbar = Snackbar.make(anchorView, message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(if (isError) colors.error else colors.card)
        snackbar.setTextColor(colors.text)
        if (actionLabel != null && onAction != null) {
            snackbar.setActionTextColor(colors.primary)
            snackbar.setAction(actionLabel) { onAction() }
        }
        snackbar.show()
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
        // Clear ViewModel coroutines since we're not using ViewModelProvider
        viewModel.onCleared()
    }
}
