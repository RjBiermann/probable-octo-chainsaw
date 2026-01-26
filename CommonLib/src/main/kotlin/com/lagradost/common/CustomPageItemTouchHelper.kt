package com.lagradost.common

import android.os.Build
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * ItemTouchHelper.Callback for drag-and-drop reordering of custom pages.
 * Only allows vertical dragging via explicit drag handle (not long-press).
 */
class CustomPageItemTouchHelper(
    private val adapter: CustomPagesAdapter,
    private val onMoved: () -> Unit
) : ItemTouchHelper.Callback() {

    // Default elevation for drag feedback
    private var defaultElevation: Float? = null

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // Only allow dragging if not filtered (reordering filtered list is confusing)
        if (adapter.isFiltered()) {
            return makeMovementFlags(0, 0)
        }
        // Allow up/down drag, no swipe
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition

        if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
            return false
        }

        adapter.moveItem(fromPosition, toPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Swiping disabled
    }

    override fun isLongPressDragEnabled(): Boolean {
        // Disable long-press drag - we use explicit drag handle
        return false
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        viewHolder?.itemView?.apply {
            when (actionState) {
                ItemTouchHelper.ACTION_STATE_DRAG -> {
                    // Dragging started - add visual feedback
                    defaultElevation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        elevation
                    } else 0f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        elevation = 8f
                    }
                    alpha = 0.9f
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        // Reset visual feedback
        viewHolder.itemView.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = defaultElevation ?: 0f
            }
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }
        defaultElevation = null
        super.clearView(recyclerView, viewHolder)
        // Called when drag is complete
        onMoved()
    }
}
