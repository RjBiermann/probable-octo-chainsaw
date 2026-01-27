package com.lagradost

import com.lagradost.common.CloudstreamUI
import com.lagradost.common.TvFocusUtils

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.TvType

/**
 * Settings fragment for NSFW Ultima with homepage-centric design.
 * Displays homepages alphabetically sorted with tap-to-edit functionality.
 *
 * Architecture: Uses repository pattern and use cases for clean separation:
 * - HomepageRepository: Abstracts storage access
 * - SaveHomepageUseCase/DeleteHomepageUseCase: Coordinate business logic
 * - FeedAssignmentService: Pure domain logic for feed transformations
 */
class NsfwUltimaSettingsFragment(
    private val plugin: NsfwUltimaPlugin,
    private val repository: HomepageRepository = CloudstreamHomepageRepository()
) : DialogFragment() {

    companion object {
        private const val TAG = "NsfwUltimaSettings"
    }

    // Use cases
    private val saveHomepageUseCase = SaveHomepageUseCase(repository)
    private val deleteHomepageUseCase = DeleteHomepageUseCase(repository)
    private val loadDataUseCase = LoadHomepageDataUseCase(repository)
    private val resetDataUseCase = ResetAllDataUseCase(repository)

    // State
    private var feedList = mutableListOf<FeedItem>()
    private var settings = NsfwUltimaSettings()
    private var homepages = mutableListOf<Homepage>()

    private lateinit var mainContainer: LinearLayout
    private lateinit var homepageAdapter: HomepageListAdapter
    private lateinit var homepageRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    // Theme colors using CloudstreamUI
    private lateinit var colors: CloudstreamUI.UIColors
    private val textColor get() = colors.text
    private val grayTextColor get() = colors.textGray
    private val backgroundColor get() = colors.background
    private val cardColor get() = colors.card
    private val primaryColor get() = colors.primary

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = CloudstreamUI.UIColors.fromContext(context)
        loadData()

        val contentView = createSettingsView(context)

        return if (isTvMode) {
            AlertDialog.Builder(context, theme)
                .setView(contentView)
                .create().apply {
                    window?.apply {
                        setLayout(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.WRAP_CONTENT
                        )
                    }
                }
        } else {
            BottomSheetDialog(context, theme).apply {
                setContentView(contentView)
                behavior.apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = null

    override fun onStart() {
        super.onStart()
        if (isTvMode) {
            dialog?.window?.decorView?.post {
                if (isAdded && ::mainContainer.isInitialized && mainContainer.isAttachedToWindow) {
                    TvFocusUtils.requestInitialFocus(mainContainer)
                }
            }
        }
    }

    private fun loadData() {
        val data = loadDataUseCase.execute()
        settings = data.settings
        feedList = data.feeds.toMutableList()
        homepages = data.homepages.toMutableList()

        Log.d(TAG, "Loaded ${feedList.size} feeds, ${homepages.size} homepages")
    }

    private fun createSettingsView(context: Context): View {
        val scrollView = NestedScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(backgroundColor)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24))
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        // Title
        mainContainer.addView(CloudstreamUI.createDialogTitle(context, "NSFW Ultima Settings", colors).apply {
            setPadding(0, 0, 0, dp(context, 16))
        })

        // Settings card
        mainContainer.addView(createSettingsCard(context))

        // Your Homepages header
        mainContainer.addView(CloudstreamUI.createTitleText(context, "Your Homepages", colors).apply {
            setPadding(0, dp(context, 20), 0, dp(context, 8))
        })

        // Subtitle
        mainContainer.addView(CloudstreamUI.createCaptionText(context, "Tap to edit. Sorted alphabetically. App restart required for changes.", colors).apply {
            textSize = 13f
            setPadding(0, 0, 0, dp(context, 12))
        })

        // Empty state text
        emptyStateText = CloudstreamUI.createEmptyState(context, "No homepages yet.\nCreate one below to get started.", colors).apply {
            visibility = if (homepages.isEmpty()) View.VISIBLE else View.GONE
        }
        mainContainer.addView(emptyStateText)

        // Homepage list RecyclerView
        homepageRecyclerView = createHomepageList(context)
        mainContainer.addView(homepageRecyclerView)

        // Create Homepage button
        mainContainer.addView(CloudstreamUI.createPrimaryButton(context, "Create New Homepage", colors) {
            showHomepageEditor(null)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 16)
            }
        })

        // Reset All Data button
        mainContainer.addView(CloudstreamUI.createSecondaryButton(context, "Reset All Data", colors) {
            showResetConfirmation(context)
        }.apply {
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 12)
            }
        })

        scrollView.addView(mainContainer)

        if (isTvMode) {
            TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, homepageRecyclerView)
        }

        return scrollView
    }

    private fun createSettingsCard(context: Context): com.google.android.material.card.MaterialCardView {
        val card = CloudstreamUI.createCard(context, colors, CloudstreamUI.Dimens.CORNER_RADIUS_LARGE)

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
        }

        // Show plugin names toggle
        cardContent.addView(createToggleRow(
            context,
            title = "Show Plugin Names",
            subtitle = "Prefix feeds with [PluginName]",
            isChecked = settings.showPluginNames
        ) { isChecked ->
            val originalSettings = settings
            settings = settings.copy(showPluginNames = isChecked)
            if (!repository.saveSettings(settings)) {
                Log.e(TAG, "Failed to save settings")
                settings = originalSettings  // Revert in-memory state
                showSaveErrorToast()
                return@createToggleRow
            }
            plugin.refreshAllHomepages()
        })

        card.addView(cardContent)
        return card
    }

    private fun createToggleRow(
        context: Context,
        title: String,
        subtitle: String,
        isChecked: Boolean,
        onChanged: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        textContainer.addView(CloudstreamUI.createBodyText(context, title, colors).apply {
            textSize = 15f
        })

        textContainer.addView(CloudstreamUI.createCaptionText(context, subtitle, colors))

        val toggle = CloudstreamUI.createSwitch(context, isChecked, colors) { checked ->
            onChanged(checked)
        }

        row.addView(textContainer)
        row.addView(toggle)
        return row
    }

    private fun createHomepageList(context: Context): RecyclerView {
        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)
            isNestedScrollingEnabled = false
            visibility = if (homepages.isEmpty()) View.GONE else View.VISIBLE
        }

        homepageAdapter = HomepageListAdapter(
            context = context,
            isTvMode = isTvMode,
            textColor = textColor,
            grayTextColor = grayTextColor,
            primaryColor = primaryColor,
            cardColor = cardColor,
            onHomepageClick = { homepage ->
                showHomepageEditor(homepage)
            },
            getFeedCount = { homepageId ->
                FeedAssignmentService.getFeedsInHomepage(feedList, homepageId).size
            }
        )
        recyclerView.adapter = homepageAdapter

        // Display homepages sorted alphabetically
        homepageAdapter.submitList(homepages.sortedBy { it.name.lowercase() })

        return recyclerView
    }

    private fun showHomepageEditor(existingHomepage: Homepage?) {
        val availableFeeds = getAvailableFeeds()

        val dialog = HomepageEditorDialog(
            existingGroup = existingHomepage,
            currentFeeds = feedList,
            availableFeeds = availableFeeds,
            showPluginNames = settings.showPluginNames,
            onSave = onSave@{ group, _, allFeeds ->
                // Fragment may detach while dialog is open
                if (!isAdded) return@onSave
                val isNew = existingHomepage == null

                // Dialog already computed the final feeds using FeedAssignmentService
                // We just need to update local state and persist via repository

                // Update local state
                if (isNew) {
                    homepages.add(group)
                } else {
                    val index = homepages.indexOfFirst { it.id == group.id }
                    if (index >= 0) {
                        homepages[index] = group
                    }
                }
                feedList.clear()
                feedList.addAll(allFeeds)

                // Persist via repository
                val feedsSaved = repository.saveFeeds(feedList)
                val groupsSaved = repository.saveHomepages(homepages)

                if (!feedsSaved || !groupsSaved) {
                    Log.e(TAG, "Failed to save: feeds=$feedsSaved, groups=$groupsSaved")
                    // Reload to restore consistent state
                    loadData()
                    showSaveErrorToast()
                    refreshUI()
                    return@onSave
                }

                refreshUI()
                plugin.refreshAllHomepages()

                context?.let { ctx ->
                    android.widget.Toast.makeText(
                        ctx,
                        if (isNew) "Homepage created. Restart app to see it." else "Changes saved. Restart app to see updates.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDelete = if (existingHomepage != null) { { homepage ->
                deleteHomepage(homepage)
            } } else null
        )
        dialog.show(parentFragmentManager, "HomepageEditorDialog")
    }

    private fun deleteHomepage(homepage: Homepage) {
        // Fragment may detach while dialog is open
        if (!isAdded) return
        // Use the delete use case for coordinated deletion
        when (val result = deleteHomepageUseCase.execute(homepage.id, homepages, feedList)) {
            is UseCaseResult.Success -> {
                homepages.clear()
                homepages.addAll(result.data.updatedHomepages)
                feedList.clear()
                feedList.addAll(result.data.updatedFeeds)

                refreshUI()
                plugin.refreshAllHomepages()

                context?.let { ctx ->
                    android.widget.Toast.makeText(
                        ctx,
                        "Homepage deleted. Restart app to see changes.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            is UseCaseResult.Failure -> {
                Log.e(TAG, "Delete failed: ${result.error}")
                showSaveErrorToast()
            }
        }
    }

    private fun getAvailableFeeds(): List<AvailableFeed> {
        val feeds = mutableListOf<AvailableFeed>()
        val nsfwProviders = allProviders.filter { api ->
            api.supportedTypes.contains(TvType.NSFW) && !api.name.startsWith("NSFW Ultima")
        }

        nsfwProviders.forEach { api ->
            try {
                api.mainPage.forEach { mainPageData ->
                    feeds.add(AvailableFeed(
                        pluginName = api.name,
                        sectionName = mainPageData.name,
                        sectionData = mainPageData.data
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting feeds from ${api.name}", e)
            }
        }

        return feeds.distinctBy { it.key() }
    }

    private fun refreshUI() {
        // Force content refresh because feed counts may have changed
        homepageAdapter.submitList(homepages.sortedBy { it.name.lowercase() }, forceContentRefresh = true)
        updateEmptyState()
        if (isTvMode) {
            TvFocusUtils.enableFocusLoopWithRecyclerView(mainContainer, homepageRecyclerView)
        }
    }

    private fun updateEmptyState() {
        val isEmpty = homepages.isEmpty()
        emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        homepageRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showSaveErrorToast() {
        context?.let { ctx ->
            android.widget.Toast.makeText(
                ctx,
                "Failed to save changes. Please try again.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showResetConfirmation(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Reset All Data")
            .setMessage("This will remove all your homepages, feeds, and settings. Are you sure?")
            .setPositiveButton("Reset") { _, _ ->
                // Fragment may detach while dialog is open
                if (!isAdded) return@setPositiveButton
                when (val result = resetDataUseCase.execute()) {
                    is UseCaseResult.Success -> {
                        feedList.clear()
                        homepages.clear()
                        settings = NsfwUltimaSettings()

                        homepageAdapter.submitList(emptyList())
                        updateEmptyState()
                        plugin.refreshAllHomepages()

                        Log.d(TAG, "All data reset")
                        android.widget.Toast.makeText(
                            context,
                            "All data reset successfully",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is UseCaseResult.Failure -> {
                        Log.e(TAG, "Failed to reset all data: ${result.error}")
                        android.widget.Toast.makeText(
                            context,
                            "Failed to reset data. Please try again.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(context: Context, dp: Int): Int = TvFocusUtils.dpToPx(context, dp)

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear RecyclerView adapter to prevent memory leaks
        if (::homepageRecyclerView.isInitialized) {
            homepageRecyclerView.adapter = null
        }
    }
}
