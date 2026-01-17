package com.lagradost

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
        fun fromJson(json: JSONObject): CustomPage = CustomPage(
            path = json.getString("path"),
            label = json.getString("label")
        )

        fun listToJson(pages: List<CustomPage>): String {
            val array = JSONArray()
            pages.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(json: String): List<CustomPage> {
            if (json.isBlank()) return emptyList()
            val array = JSONArray(json)
            return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        }
    }
}
