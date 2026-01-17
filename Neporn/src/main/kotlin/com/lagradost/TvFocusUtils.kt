package com.lagradost

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children

/**
 * Utilities for TV/D-pad navigation support.
 * Provides focus handling, visual feedback, and navigation looping.
 */
object TvFocusUtils {

    /**
     * Detect if the app is running on a TV device using UiModeManager.
     * Also checks common streaming device model names.
     */
    fun isTvMode(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        // Also check device model for common streaming devices
        val model = android.os.Build.MODEL.lowercase()
        return model.contains("aft") || // Amazon Fire TV
               model.contains("firestick") ||
               model.contains("fire tv") ||
               model.contains("chromecast") ||
               model.contains("shield") // NVIDIA Shield
    }

    /**
     * Make a view focusable and apply focus effects (scale + border).
     * Uses Cloudstream's primaryColor for the focus border.
     *
     * @param view The view to make focusable
     * @param primaryColor The theme's primary color for the focus border
     * @param scaleFactor Scale factor when focused (default 1.05x)
     */
    fun makeFocusable(
        view: View,
        primaryColor: Int,
        scaleFactor: Float = 1.05f
    ) {
        view.isFocusable = true
        view.isFocusableInTouchMode = false

        // Store original background to restore on unfocus
        val originalBackground = view.background
        val focusBorder = createFocusBorder(view.context, primaryColor)

        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Scale up and apply border
                v.animate()
                    .scaleX(scaleFactor)
                    .scaleY(scaleFactor)
                    .setDuration(150)
                    .start()
                v.background = focusBorder
            } else {
                // Reset scale and restore background
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
                v.background = originalBackground
            }
        }
    }

    /**
     * Create a rounded border drawable for focus indication.
     * Matches Material Design card radius.
     */
    private fun createFocusBorder(context: Context, primaryColor: Int): GradientDrawable {
        val strokeWidth = dpToPx(context, 3)
        val cornerRadius = dpToPx(context, 8).toFloat()

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(strokeWidth, primaryColor)
            this.cornerRadius = cornerRadius
            // Transparent fill to preserve original appearance
            setColor(0x00000000)
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

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
