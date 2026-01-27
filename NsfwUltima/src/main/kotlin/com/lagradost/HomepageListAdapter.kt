package com.lagradost

import com.lagradost.common.TvFocusUtils

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying homepages (sorted alphabetically).
 */
class HomepageListAdapter(
    private val context: Context,
    private val isTvMode: Boolean,
    private val textColor: Int,
    private val grayTextColor: Int,
    private val primaryColor: Int,
    private val cardColor: Int,
    private val onHomepageClick: (Homepage) -> Unit,
    private val getFeedCount: (homepageId: String) -> Int
) : RecyclerView.Adapter<HomepageListAdapter.HomepageViewHolder>() {

    private val homepages = mutableListOf<Homepage>()

    init {
        setHasStableIds(true)
    }

    /**
     * Submit a new list of homepages.
     * @param forceContentRefresh If true, forces rebind of all items using notifyDataSetChanged().
     *        Use this when feed counts (from external data source) may have changed.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newHomepages: List<Homepage>, forceContentRefresh: Boolean = false) {
        if (forceContentRefresh) {
            // When external data (feed counts) changes, DiffUtil can't detect it because
            // Homepage objects themselves haven't changed. Use notifyDataSetChanged() directly.
            homepages.clear()
            homepages.addAll(newHomepages)
            notifyDataSetChanged()
        } else {
            val diffCallback = HomepageDiffCallback(homepages.toList(), newHomepages)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            homepages.clear()
            homepages.addAll(newHomepages)
            diffResult.dispatchUpdatesTo(this)
        }
    }

    override fun getItemCount(): Int = homepages.size

    override fun getItemId(position: Int): Long = homepages[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomepageViewHolder {
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
            setPadding(dp(12), dp(14), dp(12), dp(14))
            // Add ripple feedback
            isClickable = true
            val rippleAttr = android.R.attr.selectableItemBackground
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(rippleAttr, typedValue, true)
            foreground = androidx.core.content.ContextCompat.getDrawable(context, typedValue.resourceId)
        }

        // Content container
        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(context).apply {
            textSize = 16f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        contentContainer.addView(nameText)

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

        // Arrow indicator
        val arrowText = TextView(context).apply {
            text = ">"
            textSize = 18f
            setTextColor(grayTextColor)
            contentDescription = "Edit"
        }
        card.addView(arrowText)

        if (isTvMode) {
            TvFocusUtils.makeFocusable(card)
        }

        return HomepageViewHolder(card, nameText, countText)
    }

    override fun onBindViewHolder(holder: HomepageViewHolder, position: Int) {
        val homepage = homepages[position]
        val feedCount = getFeedCount(homepage.id)

        holder.itemView.tag = homepage.id
        holder.nameText.text = homepage.name
        holder.countText.text = "$feedCount feeds"
        holder.countText.setTextColor(if (feedCount > 0) primaryColor else grayTextColor)
        holder.itemView.contentDescription = "${homepage.name}, $feedCount feeds. Tap to edit."

        val card = holder.itemView as LinearLayout
        card.setOnClickListener {
            onHomepageClick(homepage)
        }
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(context, dp)

    private class HomepageDiffCallback(
        private val oldList: List<Homepage>,
        private val newList: List<Homepage>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) =
            oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int) =
            oldList[oldPos] == newList[newPos]
    }

    class HomepageViewHolder(
        itemView: View,
        val nameText: TextView,
        val countText: TextView
    ) : RecyclerView.ViewHolder(itemView)
}
