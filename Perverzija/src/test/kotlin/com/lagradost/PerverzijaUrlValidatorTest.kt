package com.lagradost

import org.junit.Assert.*
import org.junit.Test

class PerverzijaUrlValidatorTest {

    // --- Studio URLs ---

    @Test
    fun `valid studio URL returns Valid with correct path and label`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/studio/brazzers/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/studio/brazzers/", valid.path)
        assertEquals("Brazzers", valid.label)
    }

    @Test
    fun `valid studio URL without trailing slash`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/studio/realitykings")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/studio/realitykings/", valid.path)
        assertEquals("Realitykings", valid.label)
    }

    @Test
    fun `valid studio URL with hyphenated name`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/studio/naughty-america/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/studio/naughty-america/", valid.path)
        assertEquals("Naughty America", valid.label)
    }

    @Test
    fun `valid studio URL with pagination stripped`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/studio/brazzers/page/2/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/studio/brazzers/", valid.path)
    }

    @Test
    fun `valid studio URL with numeric pagination stripped`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/studio/brazzers/5/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/studio/brazzers/", valid.path)
    }

    // --- Sub-studio URLs ---

    @Test
    fun `valid sub-studio URL returns Valid with child label`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/studio/vxn/blacked/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/studio/vxn/blacked/", valid.path)
        assertEquals("Blacked", valid.label)
    }

    @Test
    fun `valid sub-studio URL with hyphenated child name`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/studio/vxn/blacked-raw/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/studio/vxn/blacked-raw/", valid.path)
        assertEquals("Blacked Raw", valid.label)
    }

    // --- Tag URLs ---

    @Test
    fun `valid tag URL returns Valid`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/tag/anal/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/tag/anal/", valid.path)
        assertEquals("Anal", valid.label)
    }

    @Test
    fun `valid tag URL with hyphenated name`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/tag/big-tits/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/tag/big-tits/", valid.path)
        assertEquals("Big Tits", valid.label)
    }

    @Test
    fun `valid tag URL with pagination stripped`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/tag/anal/page/3/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/tag/anal/", valid.path)
    }

    // --- Stars URLs ---

    @Test
    fun `valid stars URL returns Valid`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/stars/angela-white/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/stars/angela-white/", valid.path)
        assertEquals("Angela White", valid.label)
    }

    @Test
    fun `valid stars URL without trailing slash`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/stars/riley-reid")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/stars/riley-reid/", valid.path)
        assertEquals("Riley Reid", valid.label)
    }

    // --- Search URLs ---

    @Test
    fun `valid search URL returns Valid`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/?s=fitness")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/?s=fitness&orderby=date", valid.path)
        assertEquals("Search: fitness", valid.label)
    }

    @Test
    fun `valid search URL with encoded spaces`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/?s=big+tits")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/?s=big+tits&orderby=date", valid.path)
        assertEquals("Search: big tits", valid.label)
    }

    @Test
    fun `valid search URL with percent encoded spaces`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/?s=big%20tits")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        // Note: URI class decodes %20 to space in the query parameter
        assertEquals("/?s=big tits&orderby=date", valid.path)
        assertEquals("Search: big tits", valid.label)
    }

    // --- Special Pages ---

    @Test
    fun `valid featured scenes URL returns Valid`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/featured-scenes/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/featured-scenes/", valid.path)
        assertEquals("Featured Scenes", valid.label)
    }

    @Test
    fun `valid featured scenes URL with pagination`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/featured-scenes/page/2/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/featured-scenes/", valid.path)
    }

    // --- Domain Validation ---

    @Test
    fun `wrong domain returns InvalidDomain`() {
        val result = PerverzijaUrlValidator.validate("https://example.com/studio/brazzers/")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `similar domain without tube prefix returns InvalidDomain`() {
        val result = PerverzijaUrlValidator.validate("https://perverzija.com/studio/brazzers/")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `URL with www prefix is accepted`() {
        val result = PerverzijaUrlValidator.validate("https://www.tube.perverzija.com/studio/brazzers/")
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `spoofed domain returns InvalidDomain`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com.evil.com/studio/brazzers/")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    // --- Invalid Paths ---

    @Test
    fun `homepage without search returns InvalidPath`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `video page URL returns InvalidPath`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/video/some-video-title/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `random path returns InvalidPath`() {
        val result = PerverzijaUrlValidator.validate("https://tube.perverzija.com/random/path/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // --- Edge Cases ---

    @Test
    fun `empty string returns InvalidPath`() {
        val result = PerverzijaUrlValidator.validate("")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `blank string returns InvalidPath`() {
        val result = PerverzijaUrlValidator.validate("   ")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `malformed URL returns InvalidPath`() {
        val result = PerverzijaUrlValidator.validate("not a valid url")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `URL without host returns InvalidPath`() {
        val result = PerverzijaUrlValidator.validate("file:///studio/brazzers/")
        assertTrue(result is ValidationResult.InvalidPath)
    }
}
