package com.lagradost.common

import android.app.UiModeManager
import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout

/**
 * Utilities for TV/D-pad navigation support.
 * Provides focus handling, visual feedback, and navigation looping.
 */
object TvFocusUtils {

    private const val TAG = "TvFocusUtils"

    // Tags for storing state on views (using hashCode for int-based tag keys)
    private val RV_FOCUS_STATE_TAG = "tv_focus_utils_rv_state".hashCode()
    private val ITEM_KEY_LISTENER_TAG = "tv_focus_item_key_listener".hashCode()

    /**
     * Internal state for RecyclerView focus loop management.
     * @property childAttachListener The listener registered on the RecyclerView to track item view lifecycle
     * @property nonRvBoundaryListeners Map of boundary views to their key listeners for cleanup on re-setup
     */
    private data class RecyclerViewFocusState(
        val childAttachListener: RecyclerView.OnChildAttachStateChangeListener,
        val nonRvBoundaryListeners: MutableMap<View, View.OnKeyListener> = mutableMapOf()
    )

    /**
     * State for key listener attached to an item view.
     * Stores both the listener and the actual view it was attached to,
     * ensuring cleanup removes the listener from the correct view even if
     * the view hierarchy changed (e.g., switching between normal and reorder mode).
     */
    private data class ItemKeyListenerState(
        val listener: View.OnKeyListener,
        val attachedView: View
    )

    // Layout values from Cloudstream's app settings
    private const val LAYOUT_AUTO = -1
    private const val LAYOUT_PHONE = 0
    private const val LAYOUT_TV = 1
    private const val LAYOUT_EMULATOR = 2

    /**
     * Detect if the app should use TV layout based on Cloudstream's layout setting.
     * Reads from the app's SharedPreferences using hardcoded key "app_layout_key"
     * (must match Cloudstream's R.string.app_layout_key value).
     *
     * Layout values: -1 (Auto), 0 (Phone), 1 (TV), 2 (Emulator)
     * - TV and Emulator layouts return true (both use D-pad navigation)
     * - Phone layout returns false
     * - Auto mode falls back to device detection
     */
    fun isTvMode(context: Context): Boolean {
        val layoutSetting = try {
            val prefs = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
            )
            prefs.getInt("app_layout_key", LAYOUT_AUTO)
        } catch (e: Exception) {
            // Fall back to auto-detection on any error (corrupted prefs, security exception, etc.)
            Log.w(TAG, "Failed to read layout setting, using auto-detection", e)
            LAYOUT_AUTO
        }

        return when (layoutSetting) {
            LAYOUT_TV, LAYOUT_EMULATOR -> true
            LAYOUT_PHONE -> false
            else -> isAutoTvDevice(context)
        }
    }

    /**
     * Auto-detect if the device is a TV using UiModeManager and device model.
     * Based on Cloudstream's isAutoTv() logic in Globals.kt, with additional null-safety.
     */
    private fun isAutoTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        // Check device model for common streaming devices (matches Cloudstream's detection)
        // AFT check is case-sensitive to match Cloudstream's Globals.kt behavior
        val originalModel = Build.MODEL ?: return false
        val modelLower = originalModel.lowercase()
        return originalModel.contains("AFT") ||
               modelLower.contains("firestick") ||
               modelLower.contains("fire tv") ||
               modelLower.contains("chromecast")
    }

    /**
     * Make a view focusable and apply a white focus border.
     * Matches Cloudstream's TV focus style (2dp white border, no scale).
     * Uses OnFocusChangeListener for explicit control over border visibility.
     *
     * @param view The view to make focusable
     * @param cornerRadiusDp Corner radius for the focus border (default 8dp)
     */
    fun makeFocusable(view: View, cornerRadiusDp: Int = 8) {
        view.isFocusable = true
        view.isFocusableInTouchMode = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23+: Use foreground with explicit visibility control
            // Create new drawable each time to avoid shared state issues
            view.setOnFocusChangeListener { v, hasFocus ->
                v.foreground = if (hasFocus) createFocusBorder(v.context, cornerRadiusDp) else null
            }
        } else {
            // API 21-22: Swap background on focus change
            val originalBackground = view.background
            view.setOnFocusChangeListener { v, hasFocus ->
                v.background = if (hasFocus) createFocusBorder(v.context, cornerRadiusDp) else originalBackground
            }
        }
    }

    /**
     * Apply TV focus styling to a TextInputLayout.
     * Uses Material's native box stroke color for focus indication.
     * This avoids conflicts with the floating label/hint.
     *
     * @param layout The TextInputLayout to make focusable
     * @param defaultColor The default stroke color when unfocused
     */
    fun makeFocusableTextInput(layout: TextInputLayout, defaultColor: Int) {
        val focusColor = resolveFocusColor(layout.context)
        // Create a ColorStateList that shows focus color when focused, default color otherwise
        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf() // default state
        )
        val colors = intArrayOf(focusColor, defaultColor)
        val colorStateList = ColorStateList(states, colors)

        layout.setBoxStrokeColorStateList(colorStateList)
        // Increase stroke width when focused for better visibility
        layout.boxStrokeWidthFocused = dpToPx(layout.context, 3)
    }

    /**
     * Create a 2dp border drawable for focus indication.
     * Uses a theme-aware focus color that works on both light and dark themes.
     */
    private fun createFocusBorder(context: Context, cornerRadiusDp: Int): GradientDrawable {
        val strokeWidth = dpToPx(context, 2)
        val cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
        val focusColor = resolveFocusColor(context)

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(strokeWidth, focusColor)
            this.cornerRadius = cornerRadius
            // No setColor() call - stroke only, no fill
        }
    }

    /**
     * Resolve the focus indicator color from the theme.
     * Falls back to white for dark themes, primary color for light themes.
     */
    private fun resolveFocusColor(context: Context): Int {
        val tv = TypedValue()
        val theme = context.theme

        // Try to get colorControlHighlight first (theme-appropriate highlight)
        if (theme.resolveAttribute(android.R.attr.colorControlHighlight, tv, true)) {
            // colorControlHighlight is often semi-transparent, make it opaque for border
            val color = tv.data
            val alpha = Color.alpha(color)
            if (alpha < 255) {
                // Make opaque but use the same RGB values
                return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))
            }
            return color
        }

        // Try to get colorPrimary as fallback
        if (theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)) {
            return tv.data
        }

        // Final fallback: white for dark backgrounds, accent blue for light
        return if (isDarkTheme(context)) Color.WHITE else Color.parseColor("#1976D2")
    }

    /**
     * Detect if the current theme is a dark theme.
     */
    private fun isDarkTheme(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Enable focus looping: when at the last focusable element,
     * pressing DOWN wraps to the first element, and vice versa.
     *
     * @param container The root container to collect focusable views from
     * @param firstFocusable Optional explicit first view for focus loop
     * @param lastFocusable Optional explicit last view for focus loop
     */
    fun enableFocusLoop(
        container: ViewGroup,
        firstFocusable: View? = null,
        lastFocusable: View? = null
    ) {
        val focusableViews = container.collectFocusableViews()
        if (focusableViews.size < 2) return

        val first = firstFocusable ?: focusableViews.first()
        val last = lastFocusable ?: focusableViews.last()

        last.setOnKeyListener(createKeyListener(KeyEvent.KEYCODE_DPAD_DOWN) {
            if (!first.requestFocus()) {
                Log.d(TAG, "Focus loop: failed to focus first element")
            }
        })
        first.setOnKeyListener(createKeyListener(KeyEvent.KEYCODE_DPAD_UP) {
            if (!last.requestFocus()) {
                Log.d(TAG, "Focus loop: failed to focus last element")
            }
        })
    }

    /**
     * Create a key listener that runs an action when the specified key is pressed.
     */
    private fun createKeyListener(keyCode: Int, action: () -> Unit): View.OnKeyListener {
        return View.OnKeyListener { _, code, event ->
            if (code == keyCode && event.action == KeyEvent.ACTION_DOWN) {
                action()
                true
            } else {
                false
            }
        }
    }

    /**
     * Enable focus looping for a container that includes a RecyclerView.
     *
     * RecyclerViews virtualize their items - only visible items have views.
     * This method handles that by attaching key listeners to child views
     * as they're attached/detached. When at the first/last item:
     * - DOWN from last item → next element after RV, or loop to first
     * - UP from first item → previous element before RV, or loop to last
     *
     * Works with both normal mode (focusable child = button/card) and reorder mode
     * (focusable child = entire row) by detecting the actual focused view at runtime.
     *
     * @param container The root container to collect focusable views from
     * @param recyclerView The RecyclerView to handle specially
     */
    fun enableFocusLoopWithRecyclerView(
        container: ViewGroup,
        recyclerView: RecyclerView
    ) {
        // Clean up any existing state
        cleanupRecyclerViewFocusLoop(recyclerView)

        // Collect focusable views excluding RecyclerView children
        val nonRvFocusables = container.collectFocusableViews(exclude = recyclerView)

        if (nonRvFocusables.isEmpty()) {
            // Only RecyclerView items in the container - set up simple RV-only loop
            setupRecyclerViewOnlyLoop(recyclerView)
            return
        }

        val firstNonRv = nonRvFocusables.first()
        val lastNonRv = nonRvFocusables.last()

        // Find the position of RecyclerView in the focusable order
        // Elements before RV in document order, elements after RV
        val (beforeRv, afterRv) = partitionAroundRecyclerView(container, recyclerView, nonRvFocusables)

        val lastBeforeRv = beforeRv.lastOrNull()
        val firstAfterRv = afterRv.firstOrNull()

        // Define focus targets for boundary navigation
        val onDownFromLast = { focusIfAttached(firstAfterRv ?: firstNonRv) }
        val onUpFromFirst = { focusIfAttached(lastBeforeRv ?: lastNonRv) }

        // Create state storage
        val focusState = RecyclerViewFocusState(
            childAttachListener = createChildAttachListener(recyclerView, onDownFromLast, onUpFromFirst)
        )

        // Attach listener to RecyclerView
        recyclerView.addOnChildAttachStateChangeListener(focusState.childAttachListener)

        // Set up key listeners on already-attached children
        // (OnChildAttachStateChangeListener only fires for NEW attachments)
        forEachChild(recyclerView) { child ->
            setupKeyListenerOnItem(child, recyclerView, onDownFromLast, onUpFromFirst)
        }

        // Set up non-RV boundary elements for full page loop
        setupNonRvBoundaryLoops(
            firstNonRv,
            lastNonRv,
            lastBeforeRv,
            firstAfterRv,
            recyclerView,
            focusState
        )

        // Store state for cleanup
        recyclerView.setTag(RV_FOCUS_STATE_TAG, focusState)
    }

    /**
     * Set up key listener on a single RecyclerView item view.
     * Called both for newly-attached views and already-attached views when re-enabling focus loop.
     *
     * @param view The item view to set up
     * @param recyclerView The RecyclerView containing this item
     * @param onDownFromLast Called when pressing DOWN from the last item (null for RV-only loop)
     * @param onUpFromFirst Called when pressing UP from the first item (null for RV-only loop)
     */
    private fun setupKeyListenerOnItem(
        view: View,
        recyclerView: RecyclerView,
        onDownFromLast: (() -> Unit)?,
        onUpFromFirst: (() -> Unit)?
    ) {
        // Clean up any existing key listener first
        cleanupKeyListenerOnItem(view)

        val keyListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false

            val positionInfo = getItemPositionInfo(recyclerView, view) ?: return@OnKeyListener false

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (positionInfo.isLast) {
                        onDownFromLast?.invoke() ?: scrollToPositionAndFocus(recyclerView, 0)
                        return@OnKeyListener true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (positionInfo.isFirst) {
                        onUpFromFirst?.invoke() ?: scrollToPositionAndFocus(recyclerView, positionInfo.lastIndex)
                        return@OnKeyListener true
                    }
                }
            }
            false
        }

        // Find the actually focusable child within this item view
        val focusableChild = findFocusableChildInItemView(view)
        if (focusableChild == null) {
            Log.d(TAG, "Focus loop: no focusable child in item view at position ${findAdapterPositionOfItemView(recyclerView, view)}")
            return
        }
        focusableChild.setOnKeyListener(keyListener)

        // Store listener state (including attached view) for proper cleanup
        view.setTag(ITEM_KEY_LISTENER_TAG, ItemKeyListenerState(keyListener, focusableChild))
    }

    /**
     * Position information for an item in a RecyclerView.
     */
    private data class ItemPositionInfo(
        val position: Int,
        val itemCount: Int
    ) {
        val isFirst: Boolean get() = position == 0
        val isLast: Boolean get() = position == itemCount - 1
        val lastIndex: Int get() = itemCount - 1
    }

    /**
     * Get position info for an item view, or null if position cannot be determined.
     */
    private fun getItemPositionInfo(recyclerView: RecyclerView, view: View): ItemPositionInfo? {
        val position = findAdapterPositionOfItemView(recyclerView, view)
        if (position < 0) {
            Log.d(TAG, "Focus loop: position lookup failed (position=$position), view may be recycling")
            return null
        }

        val itemCount = recyclerView.adapter?.itemCount ?: 0
        if (itemCount == 0) {
            Log.d(TAG, "Focus loop: adapter is null or itemCount is 0 during key event")
            return null
        }

        return ItemPositionInfo(position, itemCount)
    }

    /**
     * Scroll to a position and focus the item once it's attached.
     */
    private fun scrollToPositionAndFocus(
        recyclerView: RecyclerView,
        position: Int,
        fallbackView: View? = null
    ) {
        recyclerView.scrollToPosition(position)
        recyclerView.post {
            if (!recyclerView.isAttachedToWindow) {
                Log.d(TAG, "Focus loop: RecyclerView detached before focus could be set")
                return@post
            }

            val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
            val focusTarget = viewHolder?.itemView?.let { findFocusableChildInItemView(it) }
                ?: run {
                    Log.d(TAG, "Focus loop: no focusable view at position $position, using fallback")
                    fallbackView ?: findAnyFocusableInRecyclerView(recyclerView)
                }

            if (focusTarget == null) {
                Log.w(TAG, "Focus loop: no focusable target found at position $position")
            } else if (!focusTarget.requestFocus()) {
                Log.d(TAG, "Focus loop: requestFocus() returned false for position $position")
            }
        }
    }

    /**
     * Clean up key listener from a single item view.
     * Uses the stored ItemKeyListenerState to remove the listener from the exact view
     * it was attached to, even if the view hierarchy has changed.
     */
    private fun cleanupKeyListenerOnItem(view: View) {
        val state = view.getTag(ITEM_KEY_LISTENER_TAG) as? ItemKeyListenerState ?: return
        state.attachedView.setOnKeyListener(null)
        view.setTag(ITEM_KEY_LISTENER_TAG, null)
    }

    /**
     * Create the OnChildAttachStateChangeListener that sets up key listeners on items.
     */
    private fun createChildAttachListener(
        recyclerView: RecyclerView,
        onDownFromLast: (() -> Unit)?,
        onUpFromFirst: (() -> Unit)?
    ): RecyclerView.OnChildAttachStateChangeListener {
        return object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                setupKeyListenerOnItem(view, recyclerView, onDownFromLast, onUpFromFirst)
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                cleanupKeyListenerOnItem(view)
            }
        }
    }

    /**
     * Set up focus loops for non-RecyclerView boundary elements.
     * Creates full page loops: DOWN from last element -> first element, UP from first -> last.
     *
     * @param recyclerView Needed for looping into RV when no elements exist on one side
     */
    private fun setupNonRvBoundaryLoops(
        firstNonRv: View,
        lastNonRv: View,
        lastBeforeRv: View?,
        firstAfterRv: View?,
        recyclerView: RecyclerView,
        focusState: RecyclerViewFocusState
    ) {
        // DOWN from lastNonRv: loop to first element on page (full page loop)
        if (firstAfterRv != null) {
            // lastNonRv is after RV, so DOWN loops back to firstNonRv
            attachBoundaryKeyListener(
                view = lastNonRv,
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                target = firstNonRv,
                focusState = focusState
            )
        }
        // Note: if firstAfterRv == null, lastNonRv is before RV, and DOWN will naturally enter RV

        // UP from firstNonRv: loop to last element on page (full page loop)
        if (firstAfterRv != null) {
            // There are elements after RV, so lastNonRv is the true last element
            attachBoundaryKeyListener(
                view = firstNonRv,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                target = lastNonRv,
                focusState = focusState
            )
        } else if (lastBeforeRv != null) {
            // No elements after RV - the last element on page is inside RV
            // UP from firstNonRv should scroll RV to last item and focus it
            attachBoundaryKeyListenerToRv(
                view = firstNonRv,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                recyclerView = recyclerView,
                toLastItem = true,
                focusState = focusState
            )
        }
        // Note: if lastBeforeRv == null, firstNonRv is after RV, and UP will naturally enter RV
    }

    /**
     * Attach a key listener that scrolls to an RV item and focuses it.
     * Used when looping from non-RV elements into the RecyclerView.
     *
     * @param toLastItem If true, scrolls to last item; if false, scrolls to first item
     */
    private fun attachBoundaryKeyListenerToRv(
        view: View,
        keyCode: Int,
        recyclerView: RecyclerView,
        toLastItem: Boolean,
        focusState: RecyclerViewFocusState
    ) {
        val listener = View.OnKeyListener { _, code, event ->
            if (code == keyCode && event.action == KeyEvent.ACTION_DOWN) {
                val itemCount = recyclerView.adapter?.itemCount ?: 0
                if (itemCount > 0) {
                    val targetPosition = if (toLastItem) itemCount - 1 else 0
                    scrollToPositionAndFocus(recyclerView, targetPosition)
                }
                true
            } else {
                false
            }
        }
        view.setOnKeyListener(listener)
        focusState.nonRvBoundaryListeners[view] = listener
    }

    /**
     * Attach a key listener for boundary navigation that focuses the target view on the specified key.
     */
    private fun attachBoundaryKeyListener(
        view: View,
        keyCode: Int,
        target: View,
        focusState: RecyclerViewFocusState
    ) {
        val listener = createKeyListener(keyCode) { focusIfAttached(target) }
        view.setOnKeyListener(listener)
        focusState.nonRvBoundaryListeners[view] = listener
    }

    /**
     * Request focus on a view if it is attached to the window.
     */
    private fun focusIfAttached(view: View) {
        if (!view.isAttachedToWindow) {
            Log.d(TAG, "Focus loop: target view detached, cannot focus")
            return
        }
        if (!view.requestFocus()) {
            Log.d(TAG, "Focus loop: requestFocus() returned false despite view being attached")
        }
    }

    /**
     * Set up focus loop for a RecyclerView when it's the only focusable content.
     * DOWN from last item loops to first, UP from first loops to last.
     * Note: Caller must clean up existing state before calling this method.
     */
    private fun setupRecyclerViewOnlyLoop(recyclerView: RecyclerView) {
        // Use null callbacks to trigger RV-only loop behavior (scroll within RV)
        val childAttachListener = createChildAttachListener(recyclerView, null, null)

        recyclerView.addOnChildAttachStateChangeListener(childAttachListener)

        // Set up key listeners on already-attached children
        forEachChild(recyclerView) { child ->
            setupKeyListenerOnItem(child, recyclerView, null, null)
        }

        recyclerView.setTag(RV_FOCUS_STATE_TAG, RecyclerViewFocusState(childAttachListener))
    }

    /**
     * Iterate over all currently-attached children of a RecyclerView.
     */
    private inline fun forEachChild(recyclerView: RecyclerView, action: (View) -> Unit) {
        for (i in 0 until recyclerView.childCount) {
            action(recyclerView.getChildAt(i))
        }
    }

    /**
     * Find the adapter position of an item view.
     */
    private fun findAdapterPositionOfItemView(recyclerView: RecyclerView, itemView: View): Int {
        val holder = recyclerView.findContainingViewHolder(itemView)
        return holder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
    }

    /**
     * Find the actually focusable child within an item view.
     * Handles both normal mode (button/card is focusable) and reorder mode (entire row is focusable).
     */
    private fun findFocusableChildInItemView(itemView: View): View? {
        // Check if the itemView itself is focusable (reorder mode)
        if (itemView.isFocusable && itemView.visibility == View.VISIBLE) {
            return itemView
        }

        // Search children for focusable view (normal mode)
        if (itemView is ViewGroup) {
            for (child in itemView.children) {
                if (child.isFocusable && child.visibility == View.VISIBLE) {
                    return child
                }
                // Recurse if needed
                if (child is ViewGroup) {
                    val focusable = findFocusableChildInItemView(child)
                    if (focusable != null) return focusable
                }
            }
        }

        return null
    }

    /**
     * Find any focusable view in the RecyclerView's visible items.
     * Used as a fallback when the target position's view has no focusable child.
     */
    private fun findAnyFocusableInRecyclerView(recyclerView: RecyclerView): View? {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val focusable = findFocusableChildInItemView(child)
            if (focusable != null) return focusable
        }
        return null
    }

    /**
     * Clean up focus loop state from a RecyclerView.
     * Call this before setting up new focus loops to prevent memory leaks.
     */
    private fun cleanupRecyclerViewFocusLoop(recyclerView: RecyclerView) {
        (recyclerView.getTag(RV_FOCUS_STATE_TAG) as? RecyclerViewFocusState)?.let { state ->
            recyclerView.removeOnChildAttachStateChangeListener(state.childAttachListener)
            state.nonRvBoundaryListeners.keys.forEach { it.setOnKeyListener(null) }
            recyclerView.setTag(RV_FOCUS_STATE_TAG, null)
        }

        // Clean up key listeners on all currently-attached children
        forEachChild(recyclerView) { cleanupKeyListenerOnItem(it) }
    }

    /**
     * Partition focusable views into those before and after the RecyclerView.
     * Uses document order (view hierarchy traversal order).
     */
    private fun partitionAroundRecyclerView(
        container: ViewGroup,
        recyclerView: RecyclerView,
        focusables: List<View>
    ): Pair<List<View>, List<View>> {
        val before = mutableListOf<View>()
        val after = mutableListOf<View>()
        var foundRv = false

        fun traverse(viewGroup: ViewGroup) {
            for (child in viewGroup.children) {
                if (child == recyclerView) {
                    foundRv = true
                    continue
                }
                if (child in focusables) {
                    if (foundRv) after.add(child) else before.add(child)
                }
                if (child is ViewGroup && child !is RecyclerView) {
                    traverse(child)
                }
            }
        }

        traverse(container)
        return before to after
    }

    /**
     * Recursively collect all focusable views in the hierarchy.
     * Returns views in document order (top to bottom).
     *
     * @param exclude Optional view to skip (and its children if it's a ViewGroup)
     */
    private fun ViewGroup.collectFocusableViews(exclude: View? = null): List<View> {
        val result = mutableListOf<View>()
        for (child in children) {
            if (child == exclude) continue

            if (child.isFocusable && child.visibility == View.VISIBLE) {
                result.add(child)
            }
            if (child is ViewGroup) {
                result.addAll(child.collectFocusableViews(exclude))
            }
        }
        return result
    }

    /**
     * Request focus on the first focusable child in the container.
     * Useful for setting initial focus when the dialog opens.
     * @return true if focus was successfully set, false otherwise
     */
    fun requestInitialFocus(container: ViewGroup): Boolean {
        val focusableViews = container.collectFocusableViews()
        val firstFocusable = focusableViews.firstOrNull()
        if (firstFocusable == null) {
            Log.d(TAG, "requestInitialFocus: no focusable views found in container")
            return false
        }
        val focused = firstFocusable.requestFocus()
        if (!focused) {
            Log.d(TAG, "requestInitialFocus: requestFocus() returned false")
        }
        return focused
    }

    fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
