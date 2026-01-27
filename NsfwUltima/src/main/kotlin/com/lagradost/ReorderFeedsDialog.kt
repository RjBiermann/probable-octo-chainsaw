package com.lagradost

import com.lagradost.common.CloudstreamUI
import com.lagradost.common.DialogUtils
import com.lagradost.common.TvFocusUtils

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Dialog for reordering selected feeds.
 *
 * Touch mode: drag ≡ handle to reorder
 * TV mode: tap to select, tap destination to move
 * Changes are saved automatically on dismiss.
 */
class ReorderFeedsDialog(
    private val feeds: List<AvailableFeed>,
    private val showPluginNames: Boolean,
    private val onReorder: (List<AvailableFeed>) -> Unit
) : DialogFragment() {

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }
    private lateinit var colors: CloudstreamUI.UIColors
    private lateinit var adapter: ReorderAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var mainContainer: LinearLayout
    private lateinit var subtitle: TextView
    private var itemTouchHelper: ItemTouchHelper? = null

    // Working copy
    private val workingFeeds = feeds.toMutableList()

    // Selection state (TV mode)
    private var selectedPosition = -1

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = CloudstreamUI.UIColors.fromContext(context)

        val contentView = createDialogView(context)
        return DialogUtils.createTvOrBottomSheetDialog(context, isTvMode, theme, contentView, 0.9)
    }

    override fun onStart() {
        super.onStart()
        if (isTvMode) {
            dialog?.window?.decorView?.post {
                if (isAdded && ::recyclerView.isInitialized && recyclerView.isAttachedToWindow) {
                    // Focus first RV item directly
                    recyclerView.post {
                        if (!isAdded || !recyclerView.isAttachedToWindow) return@post
                        recyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        if (isAdded) {
            onReorder(workingFeeds.toList())
        }
        super.onDismiss(dialog)
    }

    private fun createDialogView(context: android.content.Context): android.view.View {
        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(colors.background)
        }

        // Title
        mainContainer.addView(CloudstreamUI.createHeaderText(context, "Reorder Feeds", colors).apply {
            setPadding(0, 0, 0, dp(4))
        })

        // Subtitle
        val subtitleText = if (isTvMode) "Tap to select, tap destination to move" else "Drag ≡ to reorder"
        subtitle = CloudstreamUI.createCaptionText(context, subtitleText, colors).apply {
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        }
        mainContainer.addView(subtitle)

        // RecyclerView
        adapter = ReorderAdapter(
            context = context,
            isTvMode = isTvMode,
            colors = colors,
            showPluginNames = showPluginNames,
            onStartDrag = { holder -> itemTouchHelper?.startDrag(holder) },
            onTap = { position -> onItemTapped(position) }
        )
        adapter.submitList(workingFeeds)

        recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            adapter = this@ReorderFeedsDialog.adapter
            isNestedScrollingEnabled = false
        }

        // Touch helper for drag (non-TV)
        if (!isTvMode) {
            val touchHelper = object : ItemTouchHelper.Callback() {
                override fun isLongPressDragEnabled() = false
                override fun isItemViewSwipeEnabled() = false
                override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

                override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                    val fromPos = from.bindingAdapterPosition
                    val toPos = to.bindingAdapterPosition
                    if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                        val item = workingFeeds.removeAt(fromPos)
                        workingFeeds.add(toPos, item)
                        adapter.notifyItemMoved(fromPos, toPos)
                    }
                    return true
                }

                override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
            }
            itemTouchHelper = ItemTouchHelper(touchHelper)
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        }

        mainContainer.addView(recyclerView)

        return mainContainer
    }

    private fun onItemTapped(position: Int) {
        if (!isTvMode) return

        if (selectedPosition < 0) {
            // First tap - select item
            selectedPosition = position
            subtitle.text = "Tap destination to move"
            adapter.setSelectedPosition(position)
        } else if (selectedPosition == position) {
            // Tap same item - deselect
            selectedPosition = -1
            subtitle.text = "Tap to select, tap destination to move"
            adapter.setSelectedPosition(-1)
        } else {
            // Tap different item - move selected to this position
            val item = workingFeeds.removeAt(selectedPosition)
            workingFeeds.add(position, item)
            adapter.submitList(workingFeeds)

            selectedPosition = -1
            subtitle.text = "Tap to select, tap destination to move"
            adapter.setSelectedPosition(-1)

            // Restore focus to moved item's new position
            restoreFocusToPosition(position)
        }
    }

    private fun restoreFocusToPosition(position: Int) {
        recyclerView.post {
            if (!isAdded || !recyclerView.isAttachedToWindow) return@post
            recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        if (::recyclerView.isInitialized) {
            recyclerView.adapter = null
        }
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(requireContext(), dp)
}
