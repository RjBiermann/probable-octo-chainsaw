package com.rjbiermann

import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast

class NSFWFiltersFragment(
    val plugin: TestPlugin, private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {
    private val res = plugin.resources ?: throw Exception("Unable to read resources")
    private lateinit var hdOnlySwitch: Switch
    private lateinit var durationTextView: TextView
    private lateinit var minDurationEditText: EditText
    private lateinit var maxDurationEditText: EditText
    private lateinit var addSearchHomeTextView: TextView
    private lateinit var searchHomeEditText: EditText
    private lateinit var searchHomeSortTextView: TextView
    private lateinit var addSearchHomeSortEditText: EditText
    private lateinit var searchSortHintTextView: TextView
    private lateinit var disclaimerTextView: TextView

    private var cleared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private fun getString(name: String): String? {
        val id = res.getIdentifier(
            name, "string", BuildConfig.LIBRARY_PACKAGE_NAME
        )
        return res.getString(id)
    }

    private fun getDrawable(name: String): Drawable? {
        val id =
            plugin.resources!!.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return ResourcesCompat.getDrawable(plugin.resources!!, id, null)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(
            name, "id", BuildConfig.LIBRARY_PACKAGE_NAME
        )
        return this.findViewById(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val id = res.getIdentifier(
            "fragment_nsfw_filters", "layout", BuildConfig.LIBRARY_PACKAGE_NAME
        )
        Log.d("NSFWFilters", id.toString())
        Log.d("NSFWFilters", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // All the views
        hdOnlySwitch = view.findView<Switch>("hdOnly")
        durationTextView = view.findView<TextView>("duration")
        minDurationEditText = view.findView<EditText>("minDuration")
        maxDurationEditText = view.findView<EditText>("maxDuration")
        addSearchHomeTextView = view.findView<TextView>("addSearchHome")
        searchHomeEditText = view.findView<EditText>("searchHomeEdit")
        searchHomeSortTextView = view.findView<TextView>("searchSort")
        addSearchHomeSortEditText = view.findView<EditText>("addSearchSort")
        searchSortHintTextView = view.findView<TextView>("searchSortHint")
        disclaimerTextView = view.findView<TextView>("disclaimerText")

        // Set strings
        hdOnlySwitch.text = getString("hd_only")
        durationTextView.text = getString("duration")
        minDurationEditText.hint = getString("min_duration")
        maxDurationEditText.hint = getString("max_duration")
        addSearchHomeTextView.text = getString("add_search_home")
        searchHomeEditText.hint = getString("add_search_home_hint")
        searchHomeSortTextView.text = getString("search_sorting")
        addSearchHomeSortEditText.hint = getString("search_sorting_hint")
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

        // Handle clear_button
//        clearButton.setOnClickListener {
//            clearFilters()
//        }
    }

    private fun clearFilters() {
        sharedPref.edit().apply {
            clear()
            commit()
        }
        showToast(getString("cleared_toast"))
        cleared = true
        dismiss()
    }

    override fun onDestroy() {
        if (!cleared) {
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
        }
        cleared = false
        super.onDestroy()
    }
}
