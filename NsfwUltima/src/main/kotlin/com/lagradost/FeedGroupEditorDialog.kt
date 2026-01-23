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
 * Dialog for creating or editing a feed group.
 * Simple dialog with just a name field.
 */
class FeedGroupEditorDialog(
    private val existingGroup: FeedGroup?,
    private val onSave: (FeedGroup) -> Unit
) : DialogFragment() {

    private val isTvMode by lazy { TvFocusUtils.isTvMode(requireContext()) }

    // Theme colors (resolved in onCreateDialog)
    private lateinit var colors: DialogUtils.ThemeColors
    private val textColor get() = colors.textColor
    private val grayTextColor get() = colors.grayTextColor
    private val backgroundColor get() = colors.backgroundColor
    private val primaryColor get() = colors.primaryColor

    private lateinit var nameInput: TextInputEditText
    private lateinit var nameLayout: TextInputLayout
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
        val title = if (existingGroup != null) "Edit Group" else "Create Group"
        mainContainer.addView(TextView(context).apply {
            text = title
            textSize = 18f
            setTextColor(textColor)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })

        // Group name input
        nameLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
            hint = "Group name"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        nameInput = TextInputEditText(context).apply {
            setTextColor(textColor)
            setText(existingGroup?.name ?: "")
        }
        nameLayout.addView(nameInput)

        if (isTvMode) {
            TvFocusUtils.makeFocusableTextInput(nameLayout, primaryColor)
        } else {
            nameLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(primaryColor))
        }

        mainContainer.addView(nameLayout)

        // Save button
        mainContainer.addView(MaterialButton(context).apply {
            text = if (existingGroup != null) "Save" else "Create"
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setOnClickListener { saveGroup() }
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

    private fun saveGroup() {
        val name = nameInput.text?.toString()?.trim()

        // Validation
        if (name.isNullOrBlank()) {
            nameLayout.error = "Enter a group name"
            return
        }

        val group = if (existingGroup != null) {
            existingGroup.copy(name = name)
        } else {
            FeedGroup.create(name)
        }

        onSave(group)
        dismiss()
    }

    private fun dp(dp: Int): Int = TvFocusUtils.dpToPx(requireContext(), dp)
}
