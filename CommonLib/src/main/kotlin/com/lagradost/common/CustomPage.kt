package com.lagradost.common

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class CustomPage(
    val path: String,
    val label: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path)
        put("label", label)
    }

    companion object {
        private const val TAG = "CustomPage"

        fun fromJson(json: JSONObject): CustomPage? = try {
            val path = json.getString("path")
            val label = json.getString("label")
            if (path.isBlank() || label.isBlank()) null else CustomPage(path, label)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse custom page entry", e)
            null
        }

        fun listToJson(pages: List<CustomPage>): String {
            val array = JSONArray()
            pages.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(json: String): List<CustomPage> {
            if (json.isBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { index ->
                    try {
                        fromJson(array.getJSONObject(index))
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping corrupted custom page at index $index", e)
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse custom pages JSON", e)
                emptyList()
            }
        }
    }
}
