package com.lagradost

import com.lagradost.common.TvFocusUtils

import android.annotation.SuppressLint
import android.content.Context
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
 * Adapter for displaying grouped feeds with collapsible sections.
 * Supports drag-and-drop reordering (touch) and tap-to-reorder (TV).
 */
class GroupedFeedListAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val textColor: Int,
    private val grayTextColor: Int,
    private val primaryColor: Int,
    private val showPluginNames: Boolean,
    private val onGroupToggle: (groupId: String) -> Unit,
    private val onFeedRemove: (feedKey: String) -> Unit,
    private val onFeedLongPress: (feedKey: String) -> Unit,
    private val onStartDrag: (viewHolder: RecyclerView.ViewHolder) -> Unit = {},
    private val onReorderTap: (position: Int) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_FEED = 1
    }

    private val items = mutableListOf<GroupedFeedItem>()

    // Reorder mode state (TV mode)
    private var reorderModeEnabled = false
    private var selectedPosition = -1

    fun setReorderMode(enabled: Boolean, selected: Int) {
        reorderModeEnabled = enabled
        selectedPosition = selected
        notifyDataSetChanged()
    }

    fun isReorderModeEnabled(): Boolean = reorderModeEnabled

    fun submitList(newItems: List<GroupedFeedItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is GroupedFeedItem.Header -> VIEW_TYPE_HEADER
            is GroupedFeedItem.Feed -> VIEW_TYPE_FEED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> createHeaderViewHolder()
            else -> createFeedViewHolder()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is GroupedFeedItem.Header -> bindHeader(holder as HeaderViewHolder, item)
            is GroupedFeedItem.Feed -> bindFeed(holder as FeedViewHolder, item, position)
        }
    }

    fun getItems(): List<GroupedFeedItem> = items.toList()

    fun getItemAt(position: Int): GroupedFeedItem? = items.getOrNull(position)

    fun moveItem(from: Int, to: Int) {
        if (from < 0 || from >= items.size || to < 0 || to >= items.size) return
        // Only allow moving feed items, not headers
        if (items[from] !is GroupedFeedItem.Feed || items[to] !is GroupedFeedItem.Feed) return

        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    fun isFeedPosition(position: Int): Boolean {
        return items.getOrNull(position) is GroupedFeedItem.Feed
    }

    private fun createHeaderViewHolder(): HeaderViewHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(12), dp(16), dp(12), dp(8))
            isClickable = true
            isFocusable = true
        }

        val expandIcon = TextView(context).apply {
            text = "▼"
            textSize = 12f
            setTextColor(primaryColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        }
        row.addView(expandIcon)

        val nameText = TextView(context).apply {
            textSize = 15f
            setTextColor(primaryColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        row.addView(nameText)

        val countBadge = TextView(context).apply {
            textSize = 12f
            setTextColor(grayTextColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(countBadge)

        if (isTvMode) {
            TvFocusUtils.makeFocusable(row)
        }

        return HeaderViewHolder(row, expandIcon, nameText, countBadge)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFeedViewHolder(): FeedViewHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
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
            }.also { row.addView(it) }
        } else null

        val nameText = TextView(context).apply {
            textSize = 15f
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        row.addView(nameText)

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
            setBackgroundColor(0)
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

        val holder = FeedViewHolder(row, dragHandle, nameText, deleteButton)

        // Setup drag handle touch listener
        dragHandle?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }

        return holder
    }

    private fun bindHeader(holder: HeaderViewHolder, item: GroupedFeedItem.Header) {
        val group = item.group

        holder.expandIcon.text = if (item.isExpanded) "▼" else "▶"
        holder.nameText.text = group.name
        holder.countBadge.text = "(${item.feedCount})"

        // All groups are user-created
        holder.nameText.setTextColor(primaryColor)

        holder.itemView.setOnClickListener {
            onGroupToggle(group.id)
        }
    }

    private fun bindFeed(holder: FeedViewHolder, item: GroupedFeedItem.Feed, position: Int) {
        val feed = item.item
        val isInGroup = item.groupId != null
        val isSelected = reorderModeEnabled && position == selectedPosition

        // Indent feeds that are in a group
        val leftPadding = if (isInGroup) dp(36) else dp(12)
        holder.itemView.setPadding(leftPadding, dp(10), dp(12), dp(10))

        // Display name
        holder.nameText.text = if (showPluginNames) {
            "[${feed.pluginName}] ${feed.sectionName}"
        } else {
            feed.sectionName
        }

        val row = holder.itemView as LinearLayout

        // Visual feedback for reorder mode (TV)
        if (reorderModeEnabled) {
            if (isSelected) {
                // Selected item - highlight with primary color
                row.setBackgroundColor((primaryColor and 0x00FFFFFF) or 0x30000000)
                holder.nameText.setTextColor(primaryColor)
            } else {
                // Available target
                row.setBackgroundColor(0)
                holder.nameText.setTextColor(textColor)
            }

            // Make entire row clickable in reorder mode
            row.isClickable = true
            row.isFocusable = true
            TvFocusUtils.makeFocusable(row, 8)
            row.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onReorderTap(pos)
                }
            }

            // Hide controls in reorder mode
            holder.dragHandle?.visibility = View.GONE
            holder.deleteButton.visibility = View.GONE
        } else {
            // Normal mode
            row.setBackgroundColor(0)
            holder.nameText.setTextColor(textColor)

            // Delete button
            holder.deleteButton.setOnClickListener {
                onFeedRemove(feed.key())
            }
            holder.deleteButton.visibility = View.VISIBLE
            holder.dragHandle?.visibility = View.VISIBLE

            // Long press for move-to-group
            holder.itemView.setOnLongClickListener {
                onFeedLongPress(feed.key())
                true
            }

            // For TV mode, make the row focusable for long-press context
            if (isTvMode) {
                holder.itemView.isFocusable = true
                holder.itemView.isClickable = false
                holder.itemView.setOnClickListener(null)
                TvFocusUtils.makeFocusable(holder.itemView)
            } else {
                holder.itemView.isFocusable = false
                holder.itemView.isClickable = false
                holder.itemView.setOnClickListener(null)
            }
        }
    }

    private fun dp(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    class HeaderViewHolder(
        itemView: View,
        val expandIcon: TextView,
        val nameText: TextView,
        val countBadge: TextView
    ) : RecyclerView.ViewHolder(itemView)

    class FeedViewHolder(
        itemView: View,
        val dragHandle: TextView?,
        val nameText: TextView,
        val deleteButton: ImageButton
    ) : RecyclerView.ViewHolder(itemView)
}

/**
 * ItemTouchHelper callback for drag-and-drop reordering of feeds in grouped view.
 * Only allows dragging feed items, not group headers.
 */
class GroupedFeedItemTouchHelper(
    private val adapter: GroupedFeedListAdapter,
    private val onDragComplete: () -> Unit
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = false // Use drag handle instead

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // Only allow dragging feed items
        if (!adapter.isFeedPosition(viewHolder.bindingAdapterPosition)) {
            return makeMovementFlags(0, 0)
        }
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // Only allow dropping onto feed items, not headers
        return adapter.isFeedPosition(target.bindingAdapterPosition)
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
