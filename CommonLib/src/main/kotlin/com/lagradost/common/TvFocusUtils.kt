package com.lagradost.common

import android.app.UiModeManager
import android.content.Context
import android.util.Log
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.google.android.material.textfield.TextInputLayout

/**
 * Utilities for TV/D-pad navigation support.
 * Provides focus handling, visual feedback, and navigation looping.
 */
object TvFocusUtils {

    private const val TAG = "TvFocusUtils"

    // Layout values from Cloudstream's app settings
    private const val LAYOUT_AUTO = -1
    private const val LAYOUT_PHONE = 0
    private const val LAYOUT_TV = 1
    private const val LAYOUT_EMULATOR = 2

    /**
     * Detect if the app should use TV layout based on Cloudstream's layout setting.
     * Reads from the app's SharedPreferences (app_layout_key).
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
     * Matches Cloudstream's isAutoTv() logic in Globals.kt.
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
        // Create a ColorStateList that shows white when focused, default color otherwise
        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf() // default state
        )
        val colors = intArrayOf(Color.WHITE, defaultColor)
        val colorStateList = ColorStateList(states, colors)

        layout.setBoxStrokeColorStateList(colorStateList)
        // Increase stroke width when focused for better visibility
        layout.boxStrokeWidthFocused = dpToPx(layout.context, 3)
    }

    /**
     * Create a 2dp white border drawable for focus indication.
     * Matches Cloudstream's outline.xml pattern - stroke only, no fill.
     */
    private fun createFocusBorder(context: Context, cornerRadiusDp: Int): GradientDrawable {
        val strokeWidth = dpToPx(context, 2)
        val cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(strokeWidth, Color.WHITE)
            this.cornerRadius = cornerRadius
            // No setColor() call - stroke only, no fill (matches outline.xml)
        }
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

        // Down from last wraps to first
        last.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                event.action == KeyEvent.ACTION_DOWN
            ) {
                first.requestFocus()
                true
            } else {
                false
            }
        }

        // Up from first wraps to last
        first.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                event.action == KeyEvent.ACTION_DOWN
            ) {
                last.requestFocus()
                true
            } else {
                false
            }
        }
    }

    /**
     * Recursively collect all focusable views in the hierarchy.
     * Returns views in document order (top to bottom).
     */
    private fun ViewGroup.collectFocusableViews(): List<View> {
        val result = mutableListOf<View>()
        for (child in children) {
            if (child.isFocusable && child.visibility == View.VISIBLE) {
                result.add(child)
            }
            if (child is ViewGroup) {
                result.addAll(child.collectFocusableViews())
            }
        }
        return result
    }

    /**
     * Request focus on the first focusable child in the container.
     * Useful for setting initial focus when the dialog opens.
     */
    fun requestInitialFocus(container: ViewGroup) {
        val focusableViews = container.collectFocusableViews()
        focusableViews.firstOrNull()?.requestFocus()
    }

    fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
