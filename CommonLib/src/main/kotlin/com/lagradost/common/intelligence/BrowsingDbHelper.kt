package com.lagradost.common.intelligence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite database helper for browsing intelligence data.
 * Stores view history, search history, and tag-to-plugin source mappings.
 *
 * All data is local-only (never leaves the device).
 */
class BrowsingDbHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "browsing_intelligence.db"
        const val DATABASE_VERSION = 3
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS view_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                thumbnail TEXT,
                duration INTEGER,
                tags TEXT,
                tag_types TEXT,
                source_plugin TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_view_history_timestamp ON view_history(timestamp DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_view_history_plugin ON view_history(source_plugin)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS search_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL,
                clicked_url TEXT,
                source_plugin TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_search_history_query ON search_history(query)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tag_sources (
                tag TEXT NOT NULL,
                plugin_name TEXT NOT NULL,
                category_url TEXT NOT NULL,
                last_seen INTEGER NOT NULL,
                PRIMARY KEY (tag, plugin_name)
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Guard against retry after partial migration: check if column already exists
            if (!hasColumn(db, "view_history", "tag_types")) {
                db.execSQL("ALTER TABLE view_history ADD COLUMN tag_types TEXT")
            }
            migrateTagTypes(db)
        }
        if (oldVersion < 3) {
            // Deduplicate view_history by keeping the latest entry per URL
            db.execSQL("""
                DELETE FROM view_history WHERE id NOT IN (
                    SELECT MAX(id) FROM view_history GROUP BY url
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_view_history_url ON view_history(url)")
        }
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info($table)", null)
        cursor.use {
            val nameIndex = it.getColumnIndex("name")
            while (it.moveToNext()) {
                if (it.getString(nameIndex).equals(column, ignoreCase = true)) return true
            }
        }
        return false
    }

    private fun migrateTagTypes(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            val cursor = db.rawQuery(
                "SELECT id, tags FROM view_history WHERE tag_types IS NULL AND tags IS NOT NULL",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val tagsRaw = it.getString(1) ?: continue
                    val tags = tagsRaw.trim(',').split(",").filter { t -> t.isNotBlank() }
                    val normalized = TagNormalizer.normalizeAll(tags)
                    if (normalized.isEmpty()) continue
                    val tagTypes = "," + normalized.joinToString(",") { n -> n.prefixed() } + ","
                    val canonicalTags = "," + normalized.map { n -> n.canonical }.joinToString(",") + ","
                    val values = android.content.ContentValues().apply {
                        put("tag_types", tagTypes)
                        put("tags", canonicalTags)
                    }
                    db.update("view_history", values, "id = ?", arrayOf(id.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
