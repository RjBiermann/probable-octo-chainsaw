package com.lagradost

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
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
        private const val PREFS_NAME = "pornhits_plugin_prefs"
        private const val KEY_CUSTOM_PAGES = "custom_pages"

        fun fromJson(json: JSONObject): CustomPage? = try {
            val path = json.getString("path")
            val label = json.getString("label")
            if (path.isBlank() || label.isBlank()) null else CustomPage(path, label)
        } catch (e: JSONException) {
            Log.w(TAG, "Failed to parse CustomPage from JSON", e)
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
                    } catch (e: JSONException) {
                        Log.w(TAG, "Failed to parse custom page at index $index", e)
                        null
                    }
                }
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse custom pages JSON array", e)
                emptyList()
            }
        }

        fun load(context: Context): List<CustomPage> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_CUSTOM_PAGES, "[]") ?: "[]"
            return listFromJson(json)
        }

        fun save(context: Context, pages: List<CustomPage>): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.edit()
                .putString(KEY_CUSTOM_PAGES, listToJson(pages))
                .commit()
        }
    }
}
