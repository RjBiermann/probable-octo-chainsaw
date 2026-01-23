package com.lagradost.common

import android.app.Dialog
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Shared dialog utilities for plugin dialogs.
 * Provides theme color resolution and TV/touch dialog creation.
 */
object DialogUtils {

    /**
     * Resolved theme colors for dialog styling.
     */
    data class ThemeColors(
        val textColor: Int,
        val grayTextColor: Int,
        val backgroundColor: Int,
        val cardColor: Int,
        val primaryColor: Int
    )

    /**
     * Resolve Cloudstream theme colors with Android fallbacks.
     * Tries custom Cloudstream attributes first, then falls back to standard Android attributes.
     */
    fun resolveThemeColors(context: Context): ThemeColors {
        val tv = TypedValue()
        val theme = context.theme

        return ThemeColors(
            textColor = resolveAttr(theme, tv, "textColor", android.R.attr.textColorPrimary, context),
            grayTextColor = resolveAttr(theme, tv, "grayTextColor", android.R.attr.textColorSecondary, context),
            backgroundColor = resolveAttr(theme, tv, "primaryBlackBackground", android.R.attr.colorBackground, context),
            cardColor = resolveAttr(theme, tv, "boxItemBackground", android.R.attr.colorBackgroundFloating, context),
            primaryColor = resolveAttr(theme, tv, "colorPrimary", android.R.attr.colorPrimary, context)
        )
    }

    /**
     * Resolve a single theme attribute with fallback.
     * @param customAttr Cloudstream custom attribute name (e.g., "textColor")
     * @param fallbackAttr Android fallback attribute (e.g., android.R.attr.textColorPrimary)
     */
    fun resolveAttr(
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

    /**
     * Create a dialog appropriate for the current mode.
     * - TV mode: AlertDialog (better D-pad navigation)
     * - Touch mode: BottomSheetDialog (better touch UX)
     *
     * @param context The context
     * @param isTvMode Whether TV mode is active
     * @param dialogTheme The dialog theme resource
     * @param contentView The content view to display
     * @param expandedHeight Optional height ratio for BottomSheetDialog (0.0-1.0, default 0.9)
     */
    fun createTvOrBottomSheetDialog(
        context: Context,
        isTvMode: Boolean,
        dialogTheme: Int,
        contentView: View,
        expandedHeight: Double = 0.9
    ): Dialog {
        return if (isTvMode) {
            AlertDialog.Builder(context, dialogTheme)
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
            BottomSheetDialog(context, dialogTheme).apply {
                setContentView(contentView)
                behavior.apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                    peekHeight = (context.resources.displayMetrics.heightPixels * expandedHeight).toInt()
                }
            }
        }
    }

    /**
     * Create a TV/BottomSheet dialog without custom peek height.
     * BottomSheetDialog uses default peek height behavior.
     */
    fun createTvOrBottomSheetDialogSimple(
        context: Context,
        isTvMode: Boolean,
        dialogTheme: Int,
        contentView: View
    ): Dialog {
        return if (isTvMode) {
            AlertDialog.Builder(context, dialogTheme)
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
            BottomSheetDialog(context, dialogTheme).apply {
                setContentView(contentView)
                behavior.apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
        }
    }
}
