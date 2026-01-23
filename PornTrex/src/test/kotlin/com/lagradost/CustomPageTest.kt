package com.lagradost

import com.lagradost.common.CustomPage
import org.junit.Assert.*
import org.junit.Test

class CustomPageTest {

    @Test
    fun `toJson creates valid JSON object`() {
        val page = CustomPage("/categories/amateur/", "Category: Amateur")
        val json = page.toJson()
        assertEquals("/categories/amateur/", json.getString("path"))
        assertEquals("Category: Amateur", json.getString("label"))
    }

    @Test
    fun `fromJson parses JSON object correctly`() {
        val page = CustomPage("/tags/blonde/", "Tag: Blonde")
        val json = page.toJson()
        val parsed = CustomPage.fromJson(json)
        assertNotNull(parsed)
        assertEquals(page.path, parsed!!.path)
        assertEquals(page.label, parsed.label)
    }

    @Test
    fun `round trip serialization preserves data`() {
        val original = CustomPage("/models/jane-doe/", "Model: Jane Doe")
        val json = original.toJson()
        val restored = CustomPage.fromJson(json)
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `listToJson creates valid JSON array string`() {
        val pages = listOf(
            CustomPage("/categories/amateur/", "Amateur"),
            CustomPage("/tags/blonde/", "Blonde")
        )
        val jsonString = CustomPage.listToJson(pages)
        assertTrue(jsonString.startsWith("["))
        assertTrue(jsonString.endsWith("]"))
        assertTrue(jsonString.contains("amateur"))
        assertTrue(jsonString.contains("blonde"))
    }

    @Test
    fun `listFromJson parses JSON array string correctly`() {
        val pages = listOf(
            CustomPage("/categories/amateur/", "Amateur"),
            CustomPage("/tags/blonde/", "Blonde")
        )
        val jsonString = CustomPage.listToJson(pages)
        val restored = CustomPage.listFromJson(jsonString)
        assertEquals(2, restored.size)
        assertEquals(pages[0], restored[0])
        assertEquals(pages[1], restored[1])
    }

    @Test
    fun `listFromJson with empty list returns empty list`() {
        val jsonString = CustomPage.listToJson(emptyList())
        val restored = CustomPage.listFromJson(jsonString)
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `listFromJson with blank string returns empty list`() {
        val restored = CustomPage.listFromJson("")
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `listFromJson with whitespace only returns empty list`() {
        val restored = CustomPage.listFromJson("   ")
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `listFromJson with empty array string returns empty list`() {
        val restored = CustomPage.listFromJson("[]")
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `listFromJson with malformed JSON returns empty list`() {
        val result = CustomPage.listFromJson("not valid json")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fromJson with missing path returns null`() {
        val json = org.json.JSONObject().apply {
            put("label", "Test")
        }
        val result = CustomPage.fromJson(json)
        assertNull(result)
    }

    @Test
    fun `fromJson with missing label returns null`() {
        val json = org.json.JSONObject().apply {
            put("path", "/test/")
        }
        val result = CustomPage.fromJson(json)
        assertNull(result)
    }

    @Test
    fun `data class equality works correctly`() {
        val page1 = CustomPage("/categories/amateur/", "Amateur")
        val page2 = CustomPage("/categories/amateur/", "Amateur")
        val page3 = CustomPage("/categories/amateur/", "Different Label")
        assertEquals(page1, page2)
        assertNotEquals(page1, page3)
    }

    @Test
    fun `data class hashCode is consistent`() {
        val page1 = CustomPage("/categories/amateur/", "Amateur")
        val page2 = CustomPage("/categories/amateur/", "Amateur")
        assertEquals(page1.hashCode(), page2.hashCode())
    }

    @Test
    fun `special characters in path are preserved`() {
        val page = CustomPage("/search/?query=hot%20yoga", "Search: hot yoga")
        val json = page.toJson()
        val restored = CustomPage.fromJson(json)
        assertNotNull(restored)
        assertEquals("/search/?query=hot%20yoga", restored!!.path)
    }

    @Test
    fun `unicode characters in label are preserved`() {
        val page = CustomPage("/tags/test/", "Test \u2764 Unicode")
        val jsonString = CustomPage.listToJson(listOf(page))
        val restored = CustomPage.listFromJson(jsonString)
        assertEquals("Test \u2764 Unicode", restored[0].label)
    }

    @Test
    fun `listFromJson with partially corrupted data preserves valid entries`() {
        // Simulate JSON with one valid and one invalid entry (missing label)
        val jsonString = """[{"path":"/categories/amateur/","label":"Amateur"},{"path":"/tags/broken/"},{"path":"/models/jane/","label":"Jane"}]"""
        val restored = CustomPage.listFromJson(jsonString)
        assertEquals(2, restored.size)
        assertEquals("/categories/amateur/", restored[0].path)
        assertEquals("/models/jane/", restored[1].path)
    }

    @Test
    fun `listFromJson with all corrupted entries returns empty list`() {
        val jsonString = """[{"path":"/broken1/"},{"path":"/broken2/"}]"""
        val restored = CustomPage.listFromJson(jsonString)
        assertTrue(restored.isEmpty())
    }
}
