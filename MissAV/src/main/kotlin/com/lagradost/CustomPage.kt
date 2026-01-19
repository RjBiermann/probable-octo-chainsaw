package com.lagradost

import android.content.Context
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
        private const val PREFS_NAME = "missav_plugin_prefs"
        private const val KEY_CUSTOM_PAGES = "custom_pages"

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

        /**
         * Load custom pages from SharedPreferences.
         */
        fun loadFromPrefs(context: Context): List<CustomPage> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_CUSTOM_PAGES, "[]") ?: "[]"
            return listFromJson(json)
        }

        /**
         * Save custom pages to SharedPreferences.
         * @return true if save succeeded, false otherwise
         */
        fun saveToPrefs(context: Context, pages: List<CustomPage>): Boolean {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val success = prefs.edit().putString(KEY_CUSTOM_PAGES, listToJson(pages)).commit()
                if (!success) {
                    Log.e(TAG, "Failed to save custom pages to SharedPreferences")
                }
                success
            } catch (e: Exception) {
                Log.e(TAG, "Exception while saving custom pages", e)
                false
            }
        }
    }
}
