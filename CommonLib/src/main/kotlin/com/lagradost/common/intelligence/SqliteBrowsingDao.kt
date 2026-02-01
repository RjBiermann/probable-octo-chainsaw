package com.lagradost.common.intelligence

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * SQLite-backed DAO for browsing intelligence.
 * Thread-safe via SQLiteDatabase's internal locking.
 */
class SqliteBrowsingDao(private val dbHelper: BrowsingDbHelper) : BrowsingDao {

    companion object {
        private const val TAG = "SqliteBrowsingDao"
    }

    override fun insertView(entry: ViewHistoryEntry) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("url", entry.url)
                put("title", entry.title)
                put("thumbnail", entry.thumbnail)
                put("duration", entry.duration)
                put("tags", "," + entry.tags.map { it.replace(",", "") }.joinToString(",") + ",")
                put("tag_types", entry.tagTypes)
                put("source_plugin", entry.sourcePlugin)
                put("timestamp", entry.timestamp)
            }
            db.insertWithOnConflict("view_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert view: ${e.message}", e)
        }
    }

    override fun insertSearch(query: String, clickedUrl: String?, sourcePlugin: String, timestamp: Long) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("query", query)
                put("clicked_url", clickedUrl)
                put("source_plugin", sourcePlugin)
                put("timestamp", timestamp)
            }
            db.insert("search_history", null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert search: ${e.message}", e)
        }
    }

    override fun insertOrUpdateTagSource(tag: String, pluginName: String, categoryUrl: String, lastSeen: Long) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("tag", tag.lowercase())
                put("plugin_name", pluginName)
                put("category_url", categoryUrl)
                put("last_seen", lastSeen)
            }
            db.insertWithOnConflict("tag_sources", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upsert tag source: ${e.message}", e)
        }
    }

    override fun getRecentViews(limit: Int): List<ViewHistoryEntry> {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT url, title, thumbnail, duration, tags, source_plugin, timestamp, tag_types FROM view_history ORDER BY timestamp DESC LIMIT ?",
                arrayOf(limit.toString())
            )
            val results = mutableListOf<ViewHistoryEntry>()
            cursor.use {
                while (it.moveToNext()) {
                    results.add(ViewHistoryEntry(
                        url = it.getString(0),
                        title = it.getString(1),
                        thumbnail = it.getString(2),
                        duration = if (it.isNull(3)) null else it.getInt(3),
                        tags = it.getString(4)?.trim(',')?.split(",")?.filter { tag -> tag.isNotBlank() } ?: emptyList(),
                        sourcePlugin = it.getString(5),
                        timestamp = it.getLong(6),
                        tagTypes = it.getString(7)
                    ))
                }
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent views: ${e.message}", e)
            emptyList()
        }
    }

    override fun getSearchSuggestions(partial: String, limit: Int): List<String> {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                """SELECT query, COUNT(*) as cnt FROM search_history
                   WHERE query LIKE ? ESCAPE '\' GROUP BY LOWER(query)
                   ORDER BY cnt DESC, MAX(timestamp) DESC LIMIT ?""",
                arrayOf("%${partial.replace("%", "\\%").replace("_", "\\_")}%", limit.toString())
            )
            val results = mutableListOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    results.add(it.getString(0))
                }
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get search suggestions: ${e.message}", e)
            emptyList()
        }
    }

    override fun getTagViewCounts(): Map<String, Pair<Int, Long>> {
        return try {
            val db = dbHelper.readableDatabase
            val cutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000) // 90 days
            val cursor = db.rawQuery("SELECT tags, timestamp FROM view_history WHERE timestamp > ?", arrayOf(cutoff.toString()))
            val result = mutableMapOf<String, Pair<Int, Long>>()
            cursor.use {
                while (it.moveToNext()) {
                    val tagsRaw = it.getString(0) ?: continue
                    // Tags stored as ",tag1,tag2," — strip leading/trailing commas before splitting
                    val tags = tagsRaw.trim(',').split(",").filter { tag -> tag.isNotBlank() }
                    val timestamp = it.getLong(1)
                    for (tag in tags) {
                        val key = tag.lowercase().trim()
                        val existing = result[key]
                        val count = (existing?.first ?: 0) + 1
                        val lastSeen = maxOf(existing?.second ?: 0L, timestamp)
                        result[key] = Pair(count, lastSeen)
                    }
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tag view counts: ${e.message}", e)
            emptyMap()
        }
    }

    override fun getTagViewCounts(tagType: TagType): Map<String, Pair<Int, Long>> {
        return try {
            val db = dbHelper.readableDatabase
            val cutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
            val cursor = db.rawQuery(
                "SELECT tag_types, timestamp FROM view_history WHERE timestamp > ? AND tag_types IS NOT NULL",
                arrayOf(cutoff.toString())
            )
            val result = mutableMapOf<String, Pair<Int, Long>>()
            cursor.use {
                while (it.moveToNext()) {
                    val tagTypesRaw = it.getString(0) ?: continue
                    val timestamp = it.getLong(1)
                    val prefixed = tagTypesRaw.trim(',').split(",").filter { t -> t.isNotBlank() }
                    for (p in prefixed) {
                        val nt = NormalizedTag.fromPrefixed(p) ?: continue
                        if (nt.type != tagType) continue
                        val key = nt.canonical
                        val existing = result[key]
                        val count = (existing?.first ?: 0) + 1
                        val lastSeen = maxOf(existing?.second ?: 0L, timestamp)
                        result[key] = Pair(count, lastSeen)
                    }
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get typed tag view counts: ${e.message}", e)
            emptyMap()
        }
    }

    override fun getViewsForTag(tag: String, limit: Int): List<ViewHistoryEntry> {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                """SELECT url, title, thumbnail, duration, tags, source_plugin, timestamp, tag_types
                   FROM view_history WHERE LOWER(tags) LIKE ? ESCAPE '\'
                   ORDER BY timestamp DESC LIMIT ?""",
                arrayOf("%,${tag.lowercase().replace("%", "\\%").replace("_", "\\_")},%", limit.toString())
            )
            val results = mutableListOf<ViewHistoryEntry>()
            cursor.use {
                while (it.moveToNext()) {
                    results.add(ViewHistoryEntry(
                        url = it.getString(0),
                        title = it.getString(1),
                        thumbnail = it.getString(2),
                        duration = if (it.isNull(3)) null else it.getInt(3),
                        tags = it.getString(4)?.trim(',')?.split(",")?.filter { t -> t.isNotBlank() } ?: emptyList(),
                        sourcePlugin = it.getString(5),
                        timestamp = it.getLong(6),
                        tagTypes = it.getString(7)
                    ))
                }
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get views for tag: ${e.message}", e)
            emptyList()
        }
    }

    override fun getTagSources(tag: String): List<TagSource> {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT tag, plugin_name, category_url, last_seen FROM tag_sources WHERE LOWER(tag) = ?",
                arrayOf(tag.lowercase())
            )
            val results = mutableListOf<TagSource>()
            cursor.use {
                while (it.moveToNext()) {
                    results.add(TagSource(it.getString(0), it.getString(1), it.getString(2), it.getLong(3)))
                }
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tag sources: ${e.message}", e)
            emptyList()
        }
    }

    override fun clearAll(): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            db.delete("view_history", null, null)
            db.delete("search_history", null, null)
            db.delete("tag_sources", null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all: ${e.message}", e)
            false
        }
    }

    override fun pruneOlderThan(timestampMs: Long) {
        try {
            val db = dbHelper.writableDatabase
            db.delete("view_history", "timestamp < ?", arrayOf(timestampMs.toString()))
            db.delete("search_history", "timestamp < ?", arrayOf(timestampMs.toString()))
            db.delete("tag_sources", "last_seen < ?", arrayOf(timestampMs.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune: ${e.message}", e)
        }
    }

    override fun getDatabaseSize(): Long {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT page_count * page_size FROM pragma_page_count(), pragma_page_size()", null)
            cursor.use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get database size: ${e.message}", e)
            0L
        }
    }

    override fun deleteViewHistoryEntry(url: String) {
        try {
            dbHelper.writableDatabase.delete("view_history", "url = ?", arrayOf(url))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete view history entry: ${e.message}", e)
        }
    }

    override fun deleteViewsByTag(tag: String) {
        try {
            val escaped = tag.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            dbHelper.writableDatabase.delete("view_history", "tags LIKE ? ESCAPE '\\'", arrayOf("%,$escaped,%"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete views by tag: ${e.message}", e)
        }
    }

    override fun deleteViewsByPerformer(performer: String) {
        try {
            val escaped = performer.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            dbHelper.writableDatabase.delete("view_history", "tag_types LIKE ? ESCAPE '\\'", arrayOf("%,p:$escaped,%"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete views by performer: ${e.message}", e)
        }
    }

    override fun clearViewHistory() {
        try {
            dbHelper.writableDatabase.delete("view_history", null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear view history: ${e.message}", e)
        }
    }

    override fun clearTagSources() {
        try {
            dbHelper.writableDatabase.delete("tag_sources", null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear tag sources: ${e.message}", e)
        }
    }

    override fun stripPerformerTags() {
        try {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val cursor = db.rawQuery("SELECT rowid, tag_types FROM view_history WHERE tag_types LIKE '%,p:%'", null)
                cursor.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val tagTypes = it.getString(1) ?: continue
                        val cleaned = tagTypes.split(",")
                            .filter { part -> part.isNotBlank() && !part.startsWith("p:") }
                            .let { parts -> if (parts.isEmpty()) null else ",${parts.joinToString(",")},"}
                        val values = ContentValues().apply { put("tag_types", cleaned) }
                        db.update("view_history", values, "rowid = ?", arrayOf(id.toString()))
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to strip performer tags: ${e.message}", e)
        }
    }

    override fun close() {
        try {
            dbHelper.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close database: ${e.message}", e)
        }
    }
}
