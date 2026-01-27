package com.lagradost

import com.lagradost.common.CloudstreamUI
import com.lagradost.common.TvFocusUtils

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for reordering feeds in the ReorderFeedsDialog.
 *
 * Touch mode: drag ≡ handle to reorder
 * TV mode: tap to select, tap destination to move
 */
class ReorderAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val colors: CloudstreamUI.UIColors,
    private val showPluginNames: Boolean,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onTap: (Int) -> Unit
) : RecyclerView.Adapter<ReorderAdapter.ViewHolder>() {

    private val items = mutableListOf<AvailableFeed>()
    private var selectedPos = -1

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<AvailableFeed>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        val oldPos = selectedPos
        selectedPos = position
        // Only update the specific items that changed, preserving focus
        if (oldPos >= 0 && oldPos < items.size) {
            notifyItemChanged(oldPos, PAYLOAD_SELECTION)
        }
        if (position >= 0 && position < items.size) {
            notifyItemChanged(position, PAYLOAD_SELECTION)
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            // Partial update - only update selection visuals, preserving focus
            val isSelected = position == selectedPos
            val row = holder.itemView as LinearLayout
            if (isSelected) {
                row.setBackgroundColor((colors.primary and 0x00FFFFFF) or 0x30000000)
                holder.nameText.setTextColor(colors.primary)
            } else {
                row.setBackgroundColor(0)
                holder.nameText.setTextColor(colors.text)
            }
        } else {
            // Full bind
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Drag handle (touch mode only)
        val dragHandle: TextView? = if (!isTvMode) {
            TextView(context).apply {
                text = "≡"
                textSize = 20f
                setTextColor(colors.textGray)
                contentDescription = "Drag to reorder"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(12) }
            }.also { row.addView(it) }
        } else null

        val numberText = TextView(context).apply {
            textSize = 14f
            setTextColor(colors.textGray)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        }
        row.addView(numberText)

        val nameText = TextView(context).apply {
            textSize = 14f
            setTextColor(colors.text)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(nameText)

        return ViewHolder(row, dragHandle, numberText, nameText)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feed = items[position]
        val isSelected = position == selectedPos

        holder.numberText.text = "${position + 1}."
        holder.nameText.text = if (showPluginNames) {
            "[${feed.pluginName}] ${feed.sectionName}"
        } else {
            feed.sectionName
        }

        val row = holder.itemView as LinearLayout

        // Visual selection state
        if (isSelected) {
            row.setBackgroundColor((colors.primary and 0x00FFFFFF) or 0x30000000)
            holder.nameText.setTextColor(colors.primary)
        } else {
            row.setBackgroundColor(0)
            holder.nameText.setTextColor(colors.text)
        }

        // Interaction setup
        if (isTvMode) {
            // TV: tap to select/move
            row.isClickable = true
            row.isFocusable = true
            TvFocusUtils.makeFocusable(row, 8)
            row.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onTap(pos)
            }
        } else {
            // Touch: drag handle only
            holder.dragHandle?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
            row.isFocusable = false
            row.isClickable = false
        }
    }

    private fun dp(dp: Int) = TvFocusUtils.dpToPx(context, dp)

    class ViewHolder(
        itemView: View,
        val dragHandle: TextView?,
        val numberText: TextView,
        val nameText: TextView
    ) : RecyclerView.ViewHolder(itemView)
}
