package com.lagradost

import com.lagradost.common.DialogUtils
import com.lagradost.common.TvFocusUtils

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/**
 * Dialog for managing feed groups.
 * Allows creating, editing, deleting groups and adding feeds to them.
 * Supports drag-and-drop (touch) and tap-to-reorder (TV) for group ordering.
 */
class FeedGroupManagerDialog(
    private val initialGroups: List<FeedGroup>,
    private val allFeeds: List<FeedItem>,
    private val availableFeeds: List<AvailableFeed>,
    private val showPluginNames: Boolean,
    private val onGroupsChanged: (List<FeedGroup>) -> Unit,
    private val onFeedsUpdated: (List<FeedItem>) -> Unit
) : DialogFragment() {

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }
    private val groups = mutableListOf<FeedGroup>()
    private var currentFeeds = mutableListOf<FeedItem>()

    // Theme colors (resolved in onCreateDialog)
    private lateinit var colors: DialogUtils.ThemeColors
    private val textColor get() = colors.textColor
    private val grayTextColor get() = colors.grayTextColor
    private val backgroundColor get() = colors.backgroundColor
    private val cardColor get() = colors.cardColor
    private val primaryColor get() = colors.primaryColor

    private lateinit var mainContainer: LinearLayout
    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var groupAdapter: GroupListAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var emptyStateText: TextView
    private lateinit var subtitleText: TextView

    // Reorder mode state (TV)
    private var isReorderMode = false
    private var selectedReorderPosition = -1
    private var reorderModeButton: MaterialButton? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = DialogUtils.resolveThemeColors(context)

        // Load existing groups and feeds
        groups.clear()
        groups.addAll(initialGroups)
        currentFeeds.clear()
        currentFeeds.addAll(allFeeds)

        val contentView = createDialogView(context)
        return DialogUtils.createTvOrBottomSheetDialogSimple(context, isTvMode, theme, contentView)
    }

    private fun createDialogView(context: Context): View {
        val scrollView = NestedScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(backgroundColor)
        }

        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        // Title row with Reorder button (TV mode)
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        titleRow.addView(TextView(context).apply {
            text = "Manage Groups"
            textSize = 20f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Reorder button (TV mode only)
        if (isTvMode) {
            reorderModeButton = MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "Reorder"
                textSize = 12f
                minimumWidth = 0
                minimumHeight = 0
                minWidth = 0
                minHeight = dp(36)
                insetTop = 0
                insetBottom = 0
                setPadding(dp(12), 0, dp(12), 0)
                strokeColor = ColorStateList.valueOf(primaryColor)
                setTextColor(primaryColor)
                setOnClickListener { toggleReorderMode(context) }
                TvFocusUtils.makeFocusable(this)
            }
            titleRow.addView(reorderModeButton)
        }

        mainContainer.addView(titleRow)

        // Subtitle
        subtitleText = TextView(context).apply {
            text = getSubtitleText()
            textSize = 13f
            setTextColor(grayTextColor)
            setPadding(0, dp(4), 0, dp(16))
        }
        mainContainer.addView(subtitleText)

        // Empty state
        emptyStateText = TextView(context).apply {
            text = "No groups yet.\nCreate one below to get started."
            textSize = 14f
            setTextColor(grayTextColor)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
            visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
        }
        mainContainer.addView(emptyStateText)

        // Groups RecyclerView
        setupGroupsRecyclerView(context)
        mainContainer.addView(groupsRecyclerView)

        // Create Group button
        mainContainer.addView(MaterialButton(context).apply {
            text = "Create New Group"
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setOnClickListener { showEditGroupDialog(context, null) }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        // Close button
        mainContainer.addView(MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Close"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
            strokeColor = ColorStateList.valueOf(grayTextColor)
            setTextColor(grayTextColor)
            setOnClickListener {
                // Save groups before closing
                onGroupsChanged(groups)
                dismiss()
            }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        scrollView.addView(mainContainer)

        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(mainContainer)
        }

        return scrollView
    }

    private fun setupGroupsRecyclerView(context: Context) {
        groupAdapter = GroupListAdapter(
            context = context,
            isTvMode = isTvMode,
            textColor = textColor,
            grayTextColor = grayTextColor,
            primaryColor = primaryColor,
            cardColor = cardColor,
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            },
            onReorderTap = { position ->
                onGroupTappedForReorder(position)
            },
            onAddFeeds = { group ->
                showAddFeedsDialog(group)
            },
            onEdit = { group ->
                showEditGroupDialog(context, group)
            },
            onDelete = { group ->
                showDeleteConfirmation(context, group)
            },
            getFeedCount = { groupId ->
                FeedAssignmentService.getFeedsInGroup(currentFeeds, groupId).size
            }
        )

        groupsRecyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            adapter = groupAdapter
            isNestedScrollingEnabled = false
            visibility = if (groups.isEmpty()) View.GONE else View.VISIBLE
        }

        // Setup drag-and-drop (touch mode)
        val touchHelper = GroupItemTouchHelper(
            adapter = groupAdapter,
            onDragComplete = {
                syncGroupsFromAdapter()
            }
        )
        itemTouchHelper = ItemTouchHelper(touchHelper)
        itemTouchHelper.attachToRecyclerView(groupsRecyclerView)

        groupAdapter.submitList(groups)
    }

    private fun getSubtitleText(): String {
        return when {
            isReorderMode && selectedReorderPosition >= 0 ->
                "Tap destination to move group"
            isReorderMode ->
                "Tap a group to select, then tap destination"
            isTvMode ->
                "Tap Reorder to change group order"
            else ->
                "Drag ≡ to reorder groups"
        }
    }

    private fun toggleReorderMode(context: Context) {
        isReorderMode = !isReorderMode
        selectedReorderPosition = -1

        reorderModeButton?.apply {
            if (isReorderMode) {
                text = "Done"
                backgroundTintList = ColorStateList.valueOf(primaryColor)
                setTextColor(0xFFFFFFFF.toInt())
                strokeWidth = 0
            } else {
                text = "Reorder"
                backgroundTintList = ColorStateList.valueOf(0)
                setTextColor(primaryColor)
                strokeColor = ColorStateList.valueOf(primaryColor)
                strokeWidth = dp(1)
            }
        }

        subtitleText.text = getSubtitleText()
        groupAdapter.setReorderMode(isReorderMode, -1)
    }

    private fun onGroupTappedForReorder(position: Int) {
        if (!isReorderMode) return

        if (selectedReorderPosition < 0) {
            // First tap - select the group
            selectedReorderPosition = position
            subtitleText.text = getSubtitleText()
            groupAdapter.setReorderMode(true, position)
            restoreFocusToPosition(position)
        } else if (selectedReorderPosition == position) {
            // Tapped same item - deselect
            selectedReorderPosition = -1
            subtitleText.text = getSubtitleText()
            groupAdapter.setReorderMode(true, -1)
            restoreFocusToPosition(position)
        } else {
            // Second tap - move the group
            groupAdapter.moveItem(selectedReorderPosition, position)
            syncGroupsFromAdapter()

            // Reset selection
            selectedReorderPosition = -1
            subtitleText.text = getSubtitleText()
            groupAdapter.setReorderMode(true, -1)
            restoreFocusToPosition(position)
        }
    }

    private fun restoreFocusToPosition(position: Int) {
        groupsRecyclerView.post {
            val viewHolder = groupsRecyclerView.findViewHolderForAdapterPosition(position)
            viewHolder?.itemView?.requestFocus()
        }
    }

    private fun syncGroupsFromAdapter() {
        groups.clear()
        groups.addAll(groupAdapter.getGroups())
        updatePriorities()
        onGroupsChanged(groups)
    }

    private fun updatePriorities() {
        // Higher index = lower priority (first group has highest priority)
        groups.forEachIndexed { index, group ->
            groups[index] = group.copy(priority = groups.size - index)
        }
    }

    private fun showEditGroupDialog(context: Context, existingGroup: FeedGroup?) {
        val dialog = FeedGroupEditorDialog(
            existingGroup = existingGroup,
            onSave = { updatedGroup ->
                if (existingGroup != null) {
                    // Update existing
                    val index = groups.indexOfFirst { it.id == existingGroup.id }
                    if (index >= 0) {
                        groups[index] = updatedGroup
                    }
                } else {
                    // Add new at top (highest priority)
                    groups.add(0, updatedGroup)
                    updatePriorities()
                }
                onGroupsChanged(groups)
                refreshGroupsList()
            }
        )
        dialog.show(parentFragmentManager, "FeedGroupEditorDialog")
    }

    private fun showDeleteConfirmation(context: Context, group: FeedGroup) {
        // Capture immutable values at dialog creation time to avoid stale references
        val groupId = group.id
        val groupName = group.name
        val feedsInGroup = FeedAssignmentService.getFeedsInGroup(currentFeeds, groupId).size

        val message = if (feedsInGroup > 0) {
            "Delete \"$groupName\"?\n\n$feedsInGroup feeds assigned to this group will become ungrouped."
        } else {
            "Delete \"$groupName\"?"
        }

        AlertDialog.Builder(context)
            .setTitle("Delete Group")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ ->
                // First remove the group (use captured ID to avoid stale reference)
                val index = groups.indexOfFirst { it.id == groupId }
                if (index >= 0) {
                    groups.removeAt(index)
                    updatePriorities()
                    onGroupsChanged(groups)
                }

                // Then ungroup feeds
                if (feedsInGroup > 0) {
                    currentFeeds = FeedAssignmentService.clearGroupAssignments(currentFeeds, groupId).toMutableList()
                    onFeedsUpdated(currentFeeds)
                }

                refreshGroupsList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddFeedsDialog(group: FeedGroup) {
        // Save current groups first
        onGroupsChanged(groups)

        val unassigned = FeedAssignmentService.getUnassignedFeeds(currentFeeds)

        // Filter available feeds to exclude ones already in the feed list
        val addedKeys = currentFeeds.map { it.key() }.toSet()
        val unaddedAvailable = availableFeeds.filter { it.key() !in addedKeys }

        val dialog = AddFeedsToGroupDialog(
            feedGroup = group,
            unassignedFeeds = unassigned,
            availableFeeds = unaddedAvailable,
            showPluginNames = showPluginNames,
            onFeedsSelected = { existingFeeds, newFeeds ->
                // Assign existing unassigned feeds to the group
                if (existingFeeds.isNotEmpty()) {
                    currentFeeds = FeedAssignmentService.assignFeedsToGroup(
                        currentFeeds, existingFeeds, group.id
                    ).toMutableList()
                }

                // Add new feeds and assign them to the group
                if (newFeeds.isNotEmpty()) {
                    val newFeedItems = newFeeds.map { available ->
                        FeedItem(
                            pluginName = available.pluginName,
                            sectionName = available.sectionName,
                            sectionData = available.sectionData,
                            groupId = group.id
                        )
                    }
                    currentFeeds.addAll(newFeedItems)
                }

                onFeedsUpdated(currentFeeds)
                refreshGroupsList()
            }
        )
        dialog.show(parentFragmentManager, "AddFeedsToGroupDialog")
    }

    private fun refreshGroupsList() {
        groupAdapter.submitList(groups)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = groups.isEmpty()
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        groupsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(requireContext(), dp)
}
