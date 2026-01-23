package com.lagradost

import com.lagradost.common.TvFocusUtils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/**
 * Adapter for displaying groups with drag-and-drop (touch) and tap-to-reorder (TV).
 */
class GroupListAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val textColor: Int,
    private val grayTextColor: Int,
    private val primaryColor: Int,
    private val cardColor: Int,
    private val onStartDrag: (viewHolder: RecyclerView.ViewHolder) -> Unit,
    private val onReorderTap: (position: Int) -> Unit,
    private val onAddFeeds: (group: FeedGroup) -> Unit,
    private val onEdit: (group: FeedGroup) -> Unit,
    private val onDelete: (group: FeedGroup) -> Unit,
    private val getFeedCount: (groupId: String) -> Int
) : RecyclerView.Adapter<GroupListAdapter.GroupViewHolder>() {

    private val groups = mutableListOf<FeedGroup>()

    // Reorder mode state (TV mode)
    private var reorderModeEnabled = false
    private var selectedPosition = -1

    fun setReorderMode(enabled: Boolean, selected: Int) {
        reorderModeEnabled = enabled
        selectedPosition = selected
        notifyDataSetChanged()
    }

    fun isReorderModeEnabled(): Boolean = reorderModeEnabled

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newGroups: List<FeedGroup>) {
        groups.clear()
        groups.addAll(newGroups)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = groups.size

    fun getGroups(): List<FeedGroup> = groups.toList()

    fun getGroupAt(position: Int): FeedGroup? = groups.getOrNull(position)

    fun moveItem(from: Int, to: Int) {
        if (from < 0 || from >= groups.size || to < 0 || to >= groups.size) return
        val item = groups.removeAt(from)
        groups.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        return createGroupViewHolder()
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        bindGroup(holder, group, position)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createGroupViewHolder(): GroupViewHolder {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            setBackgroundColor(cardColor)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Drag handle (touch mode only)
        val dragHandle: TextView? = if (!isTvMode) {
            TextView(context).apply {
                text = "≡"
                textSize = 24f
                setTextColor(grayTextColor)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(12) }
            }.also { card.addView(it) }
        } else null

        // Content container
        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Group name
        val nameText = TextView(context).apply {
            textSize = 16f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        contentContainer.addView(nameText)

        // Feed count
        val countText = TextView(context).apply {
            textSize = 12f
            setTextColor(grayTextColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        }
        contentContainer.addView(countText)

        card.addView(contentContainer)

        // Action buttons container
        val actionsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val addFeedsButton = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Add"
            textSize = 11f
            minimumWidth = 0
            minimumHeight = 0
            minWidth = 0
            minHeight = dp(32)
            insetTop = 0
            insetBottom = 0
            setPadding(dp(8), 0, dp(8), 0)
            strokeColor = ColorStateList.valueOf(primaryColor)
            setTextColor(primaryColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(4) }
        }
        actionsContainer.addView(addFeedsButton)

        val editButton = createSmallButton("Edit", primaryColor)
        actionsContainer.addView(editButton)

        val deleteButton = createSmallButton("Delete", grayTextColor)
        actionsContainer.addView(deleteButton)

        card.addView(actionsContainer)

        val holder = GroupViewHolder(
            card, dragHandle, nameText, countText,
            actionsContainer, addFeedsButton, editButton, deleteButton
        )

        // Setup drag handle touch listener
        dragHandle?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }

        if (isTvMode) {
            TvFocusUtils.makeFocusable(card)
        }

        return holder
    }

    private fun createSmallButton(text: String, color: Int): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(color)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindGroup(holder: GroupViewHolder, group: FeedGroup, position: Int) {
        val isSelected = reorderModeEnabled && position == selectedPosition
        val feedCount = getFeedCount(group.id)

        // Tag for focus restoration
        holder.itemView.tag = group.id

        holder.nameText.text = group.name
        holder.countText.text = "$feedCount feeds"
        holder.countText.setTextColor(if (feedCount > 0) primaryColor else grayTextColor)

        val card = holder.itemView as LinearLayout

        if (reorderModeEnabled) {
            // Reorder mode styling
            if (isSelected) {
                card.setBackgroundColor((primaryColor and 0x00FFFFFF) or 0x30000000)
                holder.nameText.setTextColor(primaryColor)
            } else {
                card.setBackgroundColor(cardColor)
                holder.nameText.setTextColor(textColor)
            }

            // Hide actions, make card clickable for reorder
            holder.actionsContainer.visibility = View.GONE
            holder.dragHandle?.visibility = View.GONE

            card.isClickable = true
            card.isFocusable = true
            if (isTvMode) TvFocusUtils.makeFocusable(card, 8)
            card.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onReorderTap(pos)
                }
            }
        } else {
            // Normal mode
            card.setBackgroundColor(cardColor)
            holder.nameText.setTextColor(textColor)

            holder.actionsContainer.visibility = View.VISIBLE
            holder.dragHandle?.visibility = View.VISIBLE

            holder.addFeedsButton.setOnClickListener { onAddFeeds(group) }
            holder.editButton.setOnClickListener { onEdit(group) }
            holder.deleteButton.setOnClickListener { onDelete(group) }

            if (isTvMode) {
                TvFocusUtils.makeFocusable(holder.addFeedsButton)
                TvFocusUtils.makeFocusable(holder.editButton)
                TvFocusUtils.makeFocusable(holder.deleteButton)
                holder.itemView.isFocusable = false
                holder.itemView.isClickable = false
                holder.itemView.setOnClickListener(null)
            } else {
                holder.itemView.isFocusable = false
                holder.itemView.isClickable = false
                holder.itemView.setOnClickListener(null)
            }
        }
    }

    private fun dp(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    class GroupViewHolder(
        itemView: View,
        val dragHandle: TextView?,
        val nameText: TextView,
        val countText: TextView,
        val actionsContainer: LinearLayout,
        val addFeedsButton: MaterialButton,
        val editButton: TextView,
        val deleteButton: TextView
    ) : RecyclerView.ViewHolder(itemView)
}

/**
 * ItemTouchHelper callback for drag-and-drop reordering of groups.
 */
class GroupItemTouchHelper(
    private val adapter: GroupListAdapter,
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
