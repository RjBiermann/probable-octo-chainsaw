package com.lagradost.common

import com.lagradost.common.StringUtils.capitalizeWords
import com.lagradost.common.StringUtils.slugToLabel
import org.junit.Test
import kotlin.test.assertEquals

class StringUtilsTest {

    // capitalizeWords tests
    @Test
    fun `capitalizeWords capitalizes single word`() {
        assertEquals("Hello", "hello".capitalizeWords())
    }

    @Test
    fun `capitalizeWords capitalizes multiple words`() {
        assertEquals("Hello World", "hello world".capitalizeWords())
    }

    @Test
    fun `capitalizeWords preserves already capitalized words`() {
        assertEquals("Hello World", "Hello World".capitalizeWords())
    }

    @Test
    fun `capitalizeWords handles mixed case`() {
        // capitalizeWords only capitalizes first char, preserves rest of each word
        assertEquals("HELLO WORLD", "hELLO wORLD".capitalizeWords())
    }

    @Test
    fun `capitalizeWords handles empty string`() {
        assertEquals("", "".capitalizeWords())
    }

    @Test
    fun `capitalizeWords handles single character`() {
        assertEquals("A", "a".capitalizeWords())
    }

    @Test
    fun `capitalizeWords handles multiple spaces`() {
        assertEquals("Hello  World", "hello  world".capitalizeWords())
    }

    // slugToLabel tests
    @Test
    fun `slugToLabel converts simple slug`() {
        assertEquals("Amateur", "amateur".slugToLabel())
    }

    @Test
    fun `slugToLabel converts hyphenated slug`() {
        assertEquals("Big Boobs", "big-boobs".slugToLabel())
    }

    @Test
    fun `slugToLabel converts multiple hyphens`() {
        assertEquals("Hot Asian Teen", "hot-asian-teen".slugToLabel())
    }

    @Test
    fun `slugToLabel handles empty string`() {
        assertEquals("", "".slugToLabel())
    }

    @Test
    fun `slugToLabel handles single hyphen`() {
        // Single hyphen becomes single space
        assertEquals(" ", "-".slugToLabel())
    }

    @Test
    fun `slugToLabel preserves numbers`() {
        assertEquals("Top 10", "top-10".slugToLabel())
    }

    @Test
    fun `slugToLabel with urlDecode decodes percent encoding`() {
        assertEquals("Hot Yoga", "hot%20yoga".slugToLabel(urlDecode = true))
    }

    @Test
    fun `slugToLabel with urlDecode decodes plus signs`() {
        assertEquals("Hot Yoga", "hot+yoga".slugToLabel(urlDecode = true))
    }

    @Test
    fun `slugToLabel with urlDecode handles special characters`() {
        assertEquals("Jane & John", "jane%20%26%20john".slugToLabel(urlDecode = true))
    }

    @Test
    fun `slugToLabel without urlDecode preserves percent encoding`() {
        assertEquals("Hot%20yoga", "hot%20yoga".slugToLabel(urlDecode = false))
    }

    @Test
    fun `slugToLabel with urlDecode handles invalid encoding gracefully`() {
        assertEquals("Invalid%zzencoding", "invalid%zzencoding".slugToLabel(urlDecode = true))
    }

    @Test
    fun `slugToLabel combines hyphen replacement and url decoding`() {
        assertEquals("Hot Asian Teen", "hot-asian%20teen".slugToLabel(urlDecode = true))
    }
}
