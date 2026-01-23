package com.lagradost

import com.lagradost.common.TvFocusUtils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying and reordering the user's feed list.
 * Supports drag-and-drop (touch) and pick-and-tap reorder mode (TV).
 */
class FeedListAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val textColor: Int,
    private val grayTextColor: Int,
    private val primaryColor: Int,
    private val showPluginNames: Boolean,
    private val onRemove: (position: Int) -> Unit,
    private val onStartDrag: (viewHolder: RecyclerView.ViewHolder) -> Unit,
    private val onReorderTap: (position: Int) -> Unit = {}
) : RecyclerView.Adapter<FeedListAdapter.FeedViewHolder>() {

    private val feeds = mutableListOf<FeedItem>()
    private var reorderModeEnabled = false
    private var selectedPosition = -1

    fun setReorderMode(enabled: Boolean, selected: Int) {
        reorderModeEnabled = enabled
        selectedPosition = selected
        notifyDataSetChanged()
    }

    fun submitList(newFeeds: List<FeedItem>) {
        feeds.clear()
        feeds.addAll(newFeeds)
        notifyDataSetChanged()
    }

    fun getFeeds(): List<FeedItem> = feeds.toList()

    fun moveItem(from: Int, to: Int) {
        if (from < 0 || from >= feeds.size || to < 0 || to >= feeds.size) return
        val item = feeds.removeAt(from)
        feeds.add(to, item)
        notifyItemMoved(from, to)
    }

    fun removeItem(position: Int) {
        if (position < 0 || position >= feeds.size) return
        feeds.removeAt(position)
        notifyItemRemoved(position)
    }

    override fun getItemCount(): Int = feeds.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Drag text indicator for touch mode only
        val dragText: TextView? = if (!isTvMode) {
            TextView(context).apply {
                text = "≡"
                textSize = 20f
                setTextColor(grayTextColor)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(12) }
            }.also { row.addView(it) }
        } else null

        // Feed name
        val nameText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            textSize = 15f
            setTextColor(textColor)
        }
        row.addView(nameText)

        // Delete button with close icon (bundled in plugin resources)
        val deleteButton = ImageButton(context).apply {
            val closeIconId = context.resources.getIdentifier(
                "ic_baseline_close_24", "drawable", context.packageName
            )
            if (closeIconId != 0) {
                val drawable = ContextCompat.getDrawable(context, closeIconId)
                drawable?.setTint(grayTextColor)
                setImageDrawable(drawable)
            }
            contentDescription = "Remove"
            setBackgroundColor(0)  // Transparent background
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        row.addView(deleteButton)
        if (isTvMode) {
            TvFocusUtils.makeFocusable(deleteButton)
        }

        val holder = FeedViewHolder(row, dragText, nameText, deleteButton)

        // Setup drag handle touch listener
        dragText?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }

        return holder
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val feed = feeds[position]
        val isSelected = reorderModeEnabled && position == selectedPosition

        // Display name
        holder.nameText.text = if (showPluginNames) {
            "[${feed.pluginName}] ${feed.sectionName}"
        } else {
            feed.sectionName
        }

        // Visual feedback for reorder mode
        val row = holder.itemView as LinearLayout
        if (reorderModeEnabled) {
            if (isSelected) {
                // Selected item - highlight with primary color
                row.setBackgroundColor((primaryColor and 0x00FFFFFF) or 0x30000000)  // 20% opacity
                holder.nameText.setTextColor(primaryColor)
            } else {
                // Available target
                row.setBackgroundColor(0)
                holder.nameText.setTextColor(textColor)
            }

            // Make entire row clickable in reorder mode
            row.isClickable = true
            row.isFocusable = true
            TvFocusUtils.makeFocusable(row, 8)  // Add focus border for TV navigation
            row.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onReorderTap(pos)
                }
            }

            // Hide other controls in reorder mode
            holder.dragHandle?.visibility = View.GONE
            holder.deleteButton.visibility = View.GONE
        } else {
            // Normal mode
            row.setBackgroundColor(0)
            holder.nameText.setTextColor(textColor)
            row.isClickable = false
            row.isFocusable = false
            row.setOnClickListener(null)

            // Show controls
            holder.dragHandle?.visibility = View.VISIBLE
            holder.deleteButton.visibility = View.VISIBLE
        }

        // Delete button
        holder.deleteButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onRemove(pos)
            }
        }
    }

    private fun dp(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    class FeedViewHolder(
        itemView: View,
        val dragHandle: TextView?,
        val nameText: TextView,
        val deleteButton: ImageButton
    ) : RecyclerView.ViewHolder(itemView)
}

/**
 * ItemTouchHelper callback for drag-and-drop reordering.
 */
class FeedItemTouchHelper(
    private val adapter: FeedListAdapter,
    private val onDragComplete: () -> Unit
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = false // Use drag handle instead

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        onDragComplete()
    }
}
