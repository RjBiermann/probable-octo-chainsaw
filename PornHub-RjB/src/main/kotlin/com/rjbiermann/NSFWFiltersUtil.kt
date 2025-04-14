package com.rjbiermann

import android.content.SharedPreferences

const val HD_ONLY = "hdOnly"
const val MIN_DURATION = "minDuration"
const val MAX_DURATION = "maxDuration"
const val HOME_SEARCH_STRING = "homeSearchString"
const val HOME_SEARCH_SORT = "homeSearchSort"

const val NSFWFiltersKey = "NSFWFilters"

data class NSFWFilters(
    val hdOnly: Boolean,
    val minDuration: Int,
    val maxDuration: Int,
    val homeSearch: String,
    val homeSearchSort: String
)

fun getNSFWFilters(sharedPref: SharedPreferences): NSFWFilters {
    try {
        return NSFWFilters(
            sharedPref.getBoolean(HD_ONLY, false),
            sharedPref.getInt(MIN_DURATION, 0),
            sharedPref.getInt(MAX_DURATION, 0),
            sharedPref.getString(HOME_SEARCH_STRING, "") ?: "",
            sharedPref.getString(HOME_SEARCH_SORT, "") ?: ""
        )
    } catch (_: Exception) {
        sharedPref.edit().apply {
            clear()
            commit()
        }
    }
    return NSFWFilters(
        sharedPref.getBoolean(HD_ONLY, false),
        sharedPref.getInt(MIN_DURATION, 0),
        sharedPref.getInt(MAX_DURATION, 0),
        sharedPref.getString(HOME_SEARCH_STRING, "") ?: "",
        sharedPref.getString(HOME_SEARCH_SORT, "") ?: ""
    )
}