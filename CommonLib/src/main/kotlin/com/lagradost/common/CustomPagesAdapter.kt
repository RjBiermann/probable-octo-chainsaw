package com.lagradost.common

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for displaying and managing custom pages list.
 * Supports both TV mode (tap-and-reorder) and touch mode (drag handle).
 */
class CustomPagesAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val textColor: Int,
    private val grayTextColor: Int,
    private val primaryColor: Int,
    private val onRemove: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onReorderTap: (Int) -> Unit = {}
) : RecyclerView.Adapter<CustomPagesAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "CustomPagesAdapter"
    }

    /**
     * DiffUtil callback for calculating differences between CustomPage lists.
     */
    private class PagesDiffCallback(
        private val oldList: List<CustomPage>,
        private val newList: List<CustomPage>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            // Items are the same if they have the same path (unique identifier)
            return oldList[oldItemPosition].path == newList[newItemPosition].path
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            // Contents are the same if both path and label match
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old.path == new.path && old.label == new.label
        }
    }

    init {
        setHasStableIds(true)
    }

    private var items = mutableListOf<CustomPage>()
    private var filteredItems = mutableListOf<CustomPage>()
    private var filterQuery = ""

    // Tap-and-reorder state (TV mode only)
    private var reorderModeEnabled = false
    private var selectedPosition = -1

    /**
     * Toggle reorder mode for TV users. In reorder mode:
     * - Entire row becomes clickable for tap-to-select, tap-to-move
     * - Drag handle and remove button are hidden
     * - Selected item is highlighted with primary color
     */
    fun setReorderMode(enabled: Boolean, selected: Int) {
        val wasReorderMode = reorderModeEnabled
        val oldSelected = selectedPosition
        reorderModeEnabled = enabled
        selectedPosition = selected

        // Only notify changes for affected items to avoid full rebind
        when {
            wasReorderMode != enabled -> {
                // Mode changed - rebind all items
                notifyItemRangeChanged(0, filteredItems.size)
            }
            oldSelected != selected -> {
                // Just selection changed - only update affected items
                if (oldSelected in 0 until filteredItems.size) {
                    notifyItemChanged(oldSelected)
                }
                if (selected in 0 until filteredItems.size) {
                    notifyItemChanged(selected)
                }
            }
        }
    }

    /**
     * Submit a new list of items. Reapplies the current filter if one is active.
     */
    fun submitList(newItems: List<CustomPage>) {
        items.clear()
        items.addAll(newItems)
        applyFilter()
    }

    /**
     * Filter items by label (case-insensitive).
     */
    fun filter(query: String) {
        filterQuery = query.trim()
        applyFilter()
    }

    private fun applyFilter() {
        val oldFiltered = filteredItems.toList()

        filteredItems.clear()
        if (filterQuery.isBlank()) {
            filteredItems.addAll(items)
        } else {
            filteredItems.addAll(items.filter {
                it.label.contains(filterQuery, ignoreCase = true)
            })
        }

        // Use DiffUtil for efficient updates
        val diffResult = DiffUtil.calculateDiff(PagesDiffCallback(oldFiltered, filteredItems))
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Move an item during drag operation.
     * Updates both source list and filtered view to maintain consistency.
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || toPosition < 0 ||
            fromPosition >= filteredItems.size || toPosition >= filteredItems.size) {
            Log.w(TAG, "Invalid move positions: from=$fromPosition, to=$toPosition, size=${filteredItems.size}")
            return
        }

        // Get actual items
        val movedItem = filteredItems[fromPosition]
        val targetItem = filteredItems[toPosition]

        // Find their positions in the source list
        val fromSourceIndex = items.indexOf(movedItem)
        val toSourceIndex = items.indexOf(targetItem)

        if (fromSourceIndex >= 0 && toSourceIndex >= 0) {
            // Move in source list - adjust target index after removal
            items.removeAt(fromSourceIndex)
            val adjustedTargetIndex = if (fromSourceIndex < toSourceIndex) {
                toSourceIndex - 1
            } else {
                toSourceIndex
            }
            items.add(adjustedTargetIndex, movedItem)

            // Move in filtered list
            filteredItems.removeAt(fromPosition)
            filteredItems.add(toPosition, movedItem)

            notifyItemMoved(fromPosition, toPosition)
        } else {
            Log.e(TAG, "Data inconsistency: item not found in source list. fromSourceIndex=$fromSourceIndex, toSourceIndex=$toSourceIndex")
        }
    }

    /**
     * Get the current items (source of truth for persistence).
     */
    fun getItems(): List<CustomPage> = items.toList()

    /**
     * Get the original index in the source list for a filtered position.
     */
    fun getSourceIndex(filteredPosition: Int): Int {
        if (filteredPosition < 0 || filteredPosition >= filteredItems.size) return -1
        return items.indexOf(filteredItems[filteredPosition])
    }

    /**
     * Check if the current view is showing filtered results.
     */
    fun isFiltered(): Boolean = filterQuery.isNotBlank()

    override fun getItemCount(): Int = filteredItems.size

    override fun getItemId(position: Int): Long {
        return if (position >= 0 && position < filteredItems.size) {
            filteredItems[position].path.hashCode().toLong()
        } else {
            Log.w(TAG, "getItemId called with invalid position: $position, size: ${filteredItems.size}")
            RecyclerView.NO_ID
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Drag handle (touch mode only)
        val dragHandle: TextView? = if (!isTvMode) {
            TextView(context).apply {
                text = "≡"
                textSize = 20f
                setTextColor(grayTextColor)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(12) }
                contentDescription = "Drag to reorder"
            }.also { row.addView(it) }
        } else null

        // Label
        val label = TextView(context).apply {
            setTextColor(textColor)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        // Remove button with close icon (bundled in plugin resources)
        val removeButton = ImageButton(context).apply {
            val closeIconId = context.resources.getIdentifier(
                "ic_baseline_close_24", "drawable", context.packageName
            )
            if (closeIconId != 0) {
                val drawable = ContextCompat.getDrawable(context, closeIconId)
                drawable?.setTint(grayTextColor)
                setImageDrawable(drawable)
            }
            setBackgroundColor(0)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Remove"
        }
        row.addView(removeButton)
        // Setup focus handling once at creation time (TV mode)
        if (isTvMode) {
            TvFocusUtils.makeFocusable(removeButton)
        }

        return ViewHolder(row, dragHandle, label, removeButton)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = filteredItems[position]
        val isSelected = reorderModeEnabled && position == selectedPosition

        // Tag for focus restoration after list mutations
        holder.itemView.tag = page.path

        holder.label.text = page.label

        // Update content descriptions with item name
        holder.removeButton.contentDescription = "Remove ${page.label}"
        holder.dragHandle?.contentDescription = "Drag to reorder ${page.label}"

        // Get the row for background manipulation
        val row = holder.itemView as LinearLayout

        if (reorderModeEnabled && isTvMode) {
            // Reorder mode: entire row becomes clickable target
            if (isSelected) {
                // Highlight selected item with 20% opacity primary color
                row.setBackgroundColor((primaryColor and 0x00FFFFFF) or 0x30000000)
                holder.label.setTextColor(primaryColor)
            } else {
                // Available target
                row.setBackgroundColor(0)
                holder.label.setTextColor(textColor)
            }

            // Make row clickable and focusable
            row.isClickable = true
            row.isFocusable = true
            TvFocusUtils.makeFocusable(row, 8)
            row.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onReorderTap(pos)
                }
            }

            // Hide all controls during reorder mode
            holder.dragHandle?.visibility = View.GONE
            holder.removeButton.visibility = View.GONE
        } else {
            // Normal mode
            row.setBackgroundColor(0)
            holder.label.setTextColor(textColor)
            row.isClickable = false
            row.isFocusable = false
            row.setOnClickListener(null)

            // Setup drag handle - dim when filtered (reordering disabled)
            holder.dragHandle?.apply {
                visibility = View.VISIBLE
                alpha = if (isFiltered()) 0.3f else 1.0f
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN && !isFiltered()) {
                        onStartDrag(holder)
                    }
                    false
                }
            }

            // Setup Remove button
            holder.removeButton.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onRemove(pos)
                }
            }
        }
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(context, dp)

    class ViewHolder(
        itemView: View,
        val dragHandle: TextView?,
        val label: TextView,
        val removeButton: ImageButton
    ) : RecyclerView.ViewHolder(itemView)
}
