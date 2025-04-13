package com.rjbiermann

import android.content.SharedPreferences

const val HD_ONLY = "hdOnly"
const val MIN_DURATION = "minDuration"
const val MAX_DURATION = "maxDuration"
const val HOME_SEARCH_SET = "homeSearchSet"
const val HOME_SEARCH_SORT = "homeSearchSort"

data class NSFWFilters(
    val hdOnly: Boolean,
    val minDuration: Int,
    val maxDuration: Int,
    val homeSearch: List<String>?,
    val homeSearchSort: List<String>?
)

fun getNSFWFilters(sharedPref: SharedPreferences): NSFWFilters {
    return NSFWFilters(
        sharedPref.getBoolean(HD_ONLY,false),
        sharedPref.getInt(MIN_DURATION,0),
        sharedPref.getInt(MAX_DURATION,0),
        sharedPref.getStringSet(HOME_SEARCH_SET,emptySet<String>())?.toList(),
        sharedPref.getStringSet(HOME_SEARCH_SORT,emptySet<String>())?.toList()
    )
}