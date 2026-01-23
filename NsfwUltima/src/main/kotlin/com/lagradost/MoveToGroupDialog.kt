package com.lagradost

import com.lagradost.common.DialogUtils
import com.lagradost.common.TvFocusUtils

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Dialog for moving a feed to a different group or creating a new group.
 */
class MoveToGroupDialog(
    private val feedItem: FeedItem,
    private val existingGroups: List<FeedGroup>,
    private val currentGroupId: String?,
    private val onMoveToGroup: (FeedItem, String?) -> Unit,  // null = ungroup
    private val onCreateGroup: (String) -> FeedGroup  // Returns newly created group
) : DialogFragment() {

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    // Theme colors (resolved in onCreateDialog)
    private lateinit var colors: DialogUtils.ThemeColors
    private val textColor get() = colors.textColor
    private val grayTextColor get() = colors.grayTextColor
    private val backgroundColor get() = colors.backgroundColor
    private val primaryColor get() = colors.primaryColor

    private lateinit var mainContainer: LinearLayout

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        colors = DialogUtils.resolveThemeColors(context)
        val contentView = createDialogView(context)
        return DialogUtils.createTvOrBottomSheetDialogSimple(context, isTvMode, theme, contentView)
    }

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

        // Title
        mainContainer.addView(TextView(context).apply {
            text = "Move Feed to Group"
            textSize = 18f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        // Feed info
        mainContainer.addView(TextView(context).apply {
            text = "[${feedItem.pluginName}] ${feedItem.sectionName}"
            textSize = 14f
            setTextColor(grayTextColor)
            setPadding(0, 0, 0, dp(16))
        })

        // Ungroup option (if currently in a group)
        if (currentGroupId != null) {
            mainContainer.addView(createOptionButton(
                context,
                "Remove from group",
                isSelected = false,
                isUngroup = true
            ) {
                onMoveToGroup(feedItem, null)
                dismiss()
            })
        }

        // Existing groups
        if (existingGroups.isNotEmpty()) {
            mainContainer.addView(TextView(context).apply {
                text = "Move to existing group:"
                textSize = 14f
                setTextColor(grayTextColor)
                setPadding(0, dp(16), 0, dp(8))
            })

            existingGroups.forEach { group ->
                val isCurrentGroup = group.id == currentGroupId
                mainContainer.addView(createOptionButton(
                    context,
                    group.name,
                    isSelected = isCurrentGroup,
                    isUngroup = false
                ) {
                    if (!isCurrentGroup) {
                        onMoveToGroup(feedItem, group.id)
                    }
                    dismiss()
                })
            }
        }

        // Create new group section
        mainContainer.addView(TextView(context).apply {
            text = "Or create a new group:"
            textSize = 14f
            setTextColor(grayTextColor)
            setPadding(0, dp(20), 0, dp(8))
        })

        // New group name input
        val inputLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "Group name"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        val nameInput = TextInputEditText(context).apply {
            setTextColor(textColor)
        }
        inputLayout.addView(nameInput)

        if (isTvMode) {
            TvFocusUtils.makeFocusableTextInput(inputLayout, primaryColor)
        } else {
            inputLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(primaryColor))
        }

        mainContainer.addView(inputLayout)

        // Create button
        mainContainer.addView(MaterialButton(context).apply {
            text = "Create & Move"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setOnClickListener {
                val groupName = nameInput.text?.toString()?.trim()
                if (!groupName.isNullOrBlank()) {
                    val newGroup = onCreateGroup(groupName)
                    onMoveToGroup(feedItem, newGroup.id)
                    dismiss()
                } else {
                    inputLayout.error = "Enter a group name"
                }
            }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        // Cancel button
        mainContainer.addView(MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Cancel"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            strokeColor = ColorStateList.valueOf(grayTextColor)
            setTextColor(grayTextColor)
            setOnClickListener { dismiss() }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        })

        scrollView.addView(mainContainer)

        if (isTvMode) {
            TvFocusUtils.enableFocusLoop(mainContainer)
        }

        return scrollView
    }

    private fun createOptionButton(
        context: Context,
        text: String,
        isSelected: Boolean,
        isUngroup: Boolean,
        onClick: () -> Unit
    ): View {
        return MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            this.text = if (isSelected) "✓ $text (current)" else text
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }

            if (isSelected) {
                strokeColor = ColorStateList.valueOf(primaryColor)
                setTextColor(primaryColor)
                isEnabled = false
            } else if (isUngroup) {
                strokeColor = ColorStateList.valueOf(grayTextColor)
                setTextColor(grayTextColor)
            } else {
                strokeColor = ColorStateList.valueOf(textColor)
                setTextColor(textColor)
            }

            setOnClickListener { onClick() }
            if (isTvMode) TvFocusUtils.makeFocusable(this)
        }
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(requireContext(), dp)
}
