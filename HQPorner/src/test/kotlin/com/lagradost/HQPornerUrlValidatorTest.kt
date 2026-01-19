package com.lagradost

import org.junit.Assert.*
import org.junit.Test

class HQPornerUrlValidatorTest {

    // Category URLs
    @Test
    fun `category URL is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/category/milf")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/category/milf", (result as ValidationResult.Valid).path)
        assertEquals("Milf", result.label)
    }

    @Test
    fun `category URL with trailing slash is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/category/big-tits/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/category/big-tits", (result as ValidationResult.Valid).path)
        assertEquals("Big Tits", result.label)
    }

    @Test
    fun `category URL with hyphenated name generates proper label`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/category/old-and-young")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Old And Young", (result as ValidationResult.Valid).label)
    }

    // Actress URLs
    @Test
    fun `actress URL is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/actress/malena-morgan")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/actress/malena-morgan", (result as ValidationResult.Valid).path)
        assertEquals("Malena Morgan", result.label)
    }

    @Test
    fun `actress URL with trailing slash is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/actress/asa-akira/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/actress/asa-akira", (result as ValidationResult.Valid).path)
        assertEquals("Asa Akira", result.label)
    }

    // Studio URLs
    @Test
    fun `studio URL is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/studio/free-brazzers-videos")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/studio/free-brazzers-videos", (result as ValidationResult.Valid).path)
        assertEquals("Free Brazzers Videos", result.label)
    }

    @Test
    fun `studio URL with trailing slash is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/studio/nubile-films/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/studio/nubile-films", (result as ValidationResult.Valid).path)
        assertEquals("Nubile Films", result.label)
    }

    // Top URLs
    @Test
    fun `top URL is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/top")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/top", (result as ValidationResult.Valid).path)
        assertEquals("Top Videos", result.label)
    }

    @Test
    fun `top URL with trailing slash is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/top/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/top", (result as ValidationResult.Valid).path)
        assertEquals("Top Videos", result.label)
    }

    @Test
    fun `top month URL is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/top/month")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/top/month", (result as ValidationResult.Valid).path)
        assertEquals("Top Month", result.label)
    }

    @Test
    fun `top week URL is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/top/week")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/top/week", (result as ValidationResult.Valid).path)
        assertEquals("Top Week", result.label)
    }

    // HD Porn page
    @Test
    fun `hdporn URL is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/hdporn")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/hdporn", (result as ValidationResult.Valid).path)
        assertEquals("New HD Videos", result.label)
    }

    @Test
    fun `hdporn URL with trailing slash is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/hdporn/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/hdporn", (result as ValidationResult.Valid).path)
        assertEquals("New HD Videos", result.label)
    }

    // Search URLs
    @Test
    fun `search URL is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/?q=blonde")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/?q=blonde", (result as ValidationResult.Valid).path)
        assertEquals("Search: blonde", result.label)
    }

    @Test
    fun `search URL with encoded spaces is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/?q=big%20tits")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/?q=big tits", (result as ValidationResult.Valid).path)
        assertEquals("Search: big tits", result.label)
    }

    @Test
    fun `search URL with additional params preserves only query`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/?q=teen&p=2")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/?q=teen", (result as ValidationResult.Valid).path)
        assertEquals("Search: teen", result.label)
    }

    // Invalid URLs
    @Test
    fun `wrong domain returns InvalidDomain`() {
        val result = HQPornerUrlValidator.validate("https://pornhub.com/category/milf")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `video page URL returns InvalidPath`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/hdporn/124657-instead_of_iron.html")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `root URL without query returns InvalidPath`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `random path returns InvalidPath`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/random-porn")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `invalid URL returns InvalidPath`() {
        val result = HQPornerUrlValidator.validate("not a url")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `empty string returns InvalidPath`() {
        val result = HQPornerUrlValidator.validate("")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Edge cases - URL encoding
    @Test
    fun `category URL with URL-encoded characters decodes properly`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/category/asian%20japanese")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/category/asian%20japanese", (result as ValidationResult.Valid).path)
        assertEquals("Asian Japanese", result.label)
    }

    @Test
    fun `actress URL with URL-encoded characters decodes properly`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/actress/m%C3%BCller")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Müller", (result as ValidationResult.Valid).label)
    }

    // Edge cases - www subdomain
    @Test
    fun `www subdomain returns InvalidDomain`() {
        // www.hqporner.com is intentionally not accepted - users should use hqporner.com
        val result = HQPornerUrlValidator.validate("https://www.hqporner.com/category/milf")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    // Edge cases - empty search query
    @Test
    fun `search URL with empty query returns Valid with empty search`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/?q=")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/?q=", (result as ValidationResult.Valid).path)
        assertEquals("Search: ", result.label)
    }

    // Edge cases - HTTP scheme
    @Test
    fun `http URL without TLS is valid`() {
        val result = HQPornerUrlValidator.validate("http://hqporner.com/category/milf")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/category/milf", (result as ValidationResult.Valid).path)
    }

    // Edge cases - pagination paths
    @Test
    fun `category URL with page number returns InvalidPath`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/category/milf/2")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Edge cases - invalid top period
    @Test
    fun `top URL with invalid period returns InvalidPath`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/top/year")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Edge cases - special characters in search
    @Test
    fun `search URL with plus-encoded space is valid`() {
        val result = HQPornerUrlValidator.validate("https://hqporner.com/?q=big+tits")
        assertTrue(result is ValidationResult.Valid)
        // Plus sign is decoded to space by URLDecoder
        assertEquals("Search: big tits", (result as ValidationResult.Valid).label)
    }
}
