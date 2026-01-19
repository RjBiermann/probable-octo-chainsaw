package com.lagradost

import android.content.Context
import android.content.res.ColorStateList
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/**
 * RecyclerView adapter for displaying and managing custom pages list.
 * Supports both TV mode (Up/Down buttons) and touch mode (drag handle).
 */
class CustomPagesAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val textColor: Int,
    private val grayTextColor: Int,
    private val primaryColor: Int,
    private val onRemove: (Int) -> Unit,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<CustomPagesAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "CustomPagesAdapter"
    }

    init {
        setHasStableIds(true)
    }

    private var items = mutableListOf<CustomPage>()
    private var filteredItems = mutableListOf<CustomPage>()
    private var filterQuery = ""

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
        filteredItems.clear()
        if (filterQuery.isBlank()) {
            filteredItems.addAll(items)
        } else {
            filteredItems.addAll(items.filter {
                it.label.contains(filterQuery, ignoreCase = true)
            })
        }
        notifyDataSetChanged()
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
            )
            setPadding(0, dp(4), 0, dp(4))
        }

        // Drag handle (touch mode only)
        val dragHandle: TextView? = if (!isTvMode) {
            TextView(context).apply {
                text = "\u2630" // Unicode hamburger menu
                textSize = 20f
                setTextColor(grayTextColor)
                setPadding(dp(4), dp(8), dp(12), dp(8))
                gravity = Gravity.CENTER
            }.also { row.addView(it) }
        } else null

        // Label
        val label = TextView(context).apply {
            setTextColor(textColor)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(if (isTvMode) dp(4) else 0, 0, 0, 0)
        }
        row.addView(label)

        // Up button (TV mode only)
        val upButton: MaterialButton? = if (isTvMode) {
            createArrowButton("\u2191").also { btn ->
                row.addView(btn)
                // Setup focus handling once at creation time
                TvFocusUtils.makeFocusable(btn)
            } // Unicode up arrow
        } else null

        // Down button (TV mode only)
        val downButton: MaterialButton? = if (isTvMode) {
            createArrowButton("\u2193").also { btn ->
                row.addView(btn)
                // Setup focus handling once at creation time
                TvFocusUtils.makeFocusable(btn)
            } // Unicode down arrow
        } else null

        // Remove button
        val removeButton = MaterialButton(context).apply {
            text = "Remove"
            textSize = 12f
            minimumHeight = 0
            minHeight = dp(36)
            setTextColor(primaryColor)
            backgroundTintList = ColorStateList.valueOf(0)
            rippleColor = ColorStateList.valueOf(primaryColor and 0x33FFFFFF)
            elevation = 0f
        }
        row.addView(removeButton)
        // Setup focus handling once at creation time (TV mode)
        if (isTvMode) {
            TvFocusUtils.makeFocusable(removeButton)
        }

        return ViewHolder(row, dragHandle, label, upButton, downButton, removeButton)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = filteredItems[position]
        val sourceIndex = getSourceIndex(position)
        val isFirst = sourceIndex == 0
        val isLast = sourceIndex == items.size - 1

        holder.label.text = page.label

        // Setup drag handle - dim when filtered (reordering disabled)
        holder.dragHandle?.apply {
            alpha = if (isFiltered()) 0.3f else 1.0f
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN && !isFiltered()) {
                    onStartDrag(holder)
                }
                false
            }
        }

        // Setup Up/Down buttons (TV mode)
        holder.upButton?.apply {
            isEnabled = !isFirst && !isFiltered()
            isFocusable = isEnabled  // Prevent focus on disabled buttons for TV navigation
            setTextColor(if (isEnabled) primaryColor else grayTextColor)
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onMoveUp(pos)
            }
        }

        holder.downButton?.apply {
            isEnabled = !isLast && !isFiltered()
            isFocusable = isEnabled  // Prevent focus on disabled buttons for TV navigation
            setTextColor(if (isEnabled) primaryColor else grayTextColor)
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onMoveDown(pos)
            }
        }

        // Setup Remove button
        holder.removeButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onRemove(pos)
        }
    }

    private fun createArrowButton(text: String): MaterialButton {
        return MaterialButton(context).apply {
            this.text = text
            textSize = 16f
            minimumWidth = dp(44)
            minimumHeight = 0
            minHeight = dp(36)
            backgroundTintList = ColorStateList.valueOf(0)
        }
    }

    private fun dp(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    class ViewHolder(
        itemView: View,
        val dragHandle: TextView?,
        val label: TextView,
        val upButton: MaterialButton?,
        val downButton: MaterialButton?,
        val removeButton: MaterialButton
    ) : RecyclerView.ViewHolder(itemView)
}
