package com.rjbiermann

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast

class NSFWFiltersFragment(
    val plugin: TestPlugin, private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private fun getString(name: String): String? {
        val id = plugin.resources!!.getIdentifier(
            name, "string", recloudstream.BuildConfig.LIBRARY_PACKAGE_NAME
        )
        return plugin.resources!!.getString(id)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = plugin.resources!!.getIdentifier(
            name, "id", recloudstream.BuildConfig.LIBRARY_PACKAGE_NAME
        )
        return this.findViewById(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val id = plugin.resources!!.getIdentifier(
            "fragment_nsfw_filters", "layout", recloudstream.BuildConfig.LIBRARY_PACKAGE_NAME
        )
        Log.d("NSFWFilters", id.toString())
        Log.d("NSFWFilters", recloudstream.BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = plugin.resources!!.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // All the views
        val hdOnlySwitch = view.findView<Switch>("hdOnly")
        val durationTextView = view.findView<TextView>("duration")
        val minDurationEditText = view.findView<EditText>("minDuration")
        val maxDurationEditText = view.findView<EditText>("maxDuration")
        val saveButton = view.findView<Button>("saveButton")
        val addSearchHomeTextView = view.findView<TextView>("addSearchHome")
        val searchHomeEditText = view.findView<EditText>("searchHomeEdit")
        val searchHomeSortTextView = view.findView<TextView>("searchSort")
        val addSearchHomeSortEditText = view.findView<EditText>("addSearchSort")
        val clearButton = view.findView<Button>("clearButton")
        val searchSortHintTextView = view.findView<TextView>("searchSortHint")
        val disclaimerTextView = view.findView<TextView>("disclaimerText")

        // Set strings
        hdOnlySwitch.text = getString("hd_only")
        durationTextView.text = getString("duration")
        minDurationEditText.hint = getString("min_duration")
        maxDurationEditText.hint = getString("max_duration")
        saveButton.text = getString("save_button")
        addSearchHomeTextView.text = getString("add_search_home")
        searchHomeEditText.hint = getString("add_search_home_hint")
        searchHomeSortTextView.text = getString("search_sorting")
        addSearchHomeSortEditText.hint = getString("search_sorting_hint")
        clearButton.text = getString("clear_button")
        searchSortHintTextView.text = getString("search_sorting_all")
        disclaimerTextView.text = getString("disclaimer")

        try {
            // Initialize from preference
            if (sharedPref.getInt(MIN_DURATION, 0) != 0) {
                minDurationEditText.setText(sharedPref.getInt(MIN_DURATION, 0).toString())
            }
            if (sharedPref.getInt(MAX_DURATION, 0) != 0) {
                maxDurationEditText.setText(sharedPref.getInt(MAX_DURATION, 0).toString())
            }
            searchHomeEditText.setText(
                sharedPref.getString(HOME_SEARCH_STRING, "")
            )
            hdOnlySwitch.isChecked = sharedPref.getBoolean(HD_ONLY, false)
            addSearchHomeSortEditText.setText(
                sharedPref.getString(HOME_SEARCH_SORT, "")
            )
        } catch (_: Exception) {
            clearFilters()
        }

        // Handle save_button button
        saveButton.setOnClickListener {
            sharedPref.edit().apply {
                putBoolean(HD_ONLY, hdOnlySwitch.isChecked)
                putInt(MIN_DURATION, minDurationEditText.text.toString().trim().toIntOrNull() ?: 0)
                putInt(MAX_DURATION, maxDurationEditText.text.toString().trim().toIntOrNull() ?: 0)
                putString(
                    HOME_SEARCH_STRING, searchHomeEditText.text.toString()
                )
                putString(
                    HOME_SEARCH_SORT, addSearchHomeSortEditText.text.toString()
                )
                commit()
            }
            showToast(getString("saved_toast"))
            dismiss()
        }

        // Handle clear_button
        clearButton.setOnClickListener {
            clearFilters()
        }
    }

    private fun clearFilters() {
        sharedPref.edit().apply {
            clear()
            commit()
        }
        showToast(getString("cleared_toast"))
        dismiss()
    }
}
