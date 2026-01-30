package com.lagradost.common

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.core.widget.NestedScrollView

/**
 * NestedScrollView subclass that catches [IllegalArgumentException] during layout.
 *
 * On Android TV, when a RecyclerView inside a NestedScrollView recycles a focused view
 * during a layout pass, [NestedScrollView.onSizeChanged] calls `isWithinDeltaOfScreen`
 * → `offsetDescendantRectToMyCoords` on a view that is no longer a descendant,
 * causing "parameter must be a descendant of this view". This is a known AndroidX bug.
 *
 * Use this instead of [NestedScrollView] in any dialog or fragment that contains a
 * RecyclerView and may run on TV (D-pad focus navigation).
 */
class SafeNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        try {
            super.onSizeChanged(w, h, oldw, oldh)
        } catch (e: IllegalArgumentException) {
            // Swallow "parameter must be a descendant of this view" from
            // offsetDescendantRectToMyCoords when RecyclerView recycles focused views.
            Log.w("SafeNestedScrollView", "onSizeChanged: caught descendant exception", e)
        }
    }
}
