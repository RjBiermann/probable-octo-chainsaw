package com.lagradost

import com.lagradost.common.ValidationResult
import org.junit.Assert.*
import org.junit.Test

class FreePornVideosUrlValidatorTest {

    // Category URLs
    @Test
    fun `category URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/categories/milf")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/categories/milf/", (result as ValidationResult.Valid).path)
        assertEquals("Category: Milf", result.label)
    }

    @Test
    fun `category URL with trailing slash is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/categories/milf/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/categories/milf/", (result as ValidationResult.Valid).path)
    }

    @Test
    fun `category URL with hyphenated name generates proper label`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/categories/jav-uncensored")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Category: Jav Uncensored", (result as ValidationResult.Valid).label)
    }

    @Test
    fun `category URL without www is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://freepornvideos.xxx/categories/anal")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/categories/anal/", (result as ValidationResult.Valid).path)
    }

    // Network URLs
    @Test
    fun `network URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/networks/brazzers-com")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/networks/brazzers-com/", (result as ValidationResult.Valid).path)
        assertEquals("Network: Brazzers Com", result.label)
    }

    @Test
    fun `network URL with trailing slash is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/networks/mylf-com/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/networks/mylf-com/", (result as ValidationResult.Valid).path)
    }

    // Site URLs
    @Test
    fun `site URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/sites/divine-bitches")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/sites/divine-bitches/", (result as ValidationResult.Valid).path)
        assertEquals("Site: Divine Bitches", result.label)
    }

    // Latest updates
    @Test
    fun `latest updates URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/latest-updates")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/latest-updates/", (result as ValidationResult.Valid).path)
        assertEquals("Latest Updates", result.label)
    }

    @Test
    fun `latest updates URL with trailing slash is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/latest-updates/")
        assertTrue(result is ValidationResult.Valid)
    }

    // Most popular
    @Test
    fun `most popular bare URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/most-popular/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/most-popular/", (result as ValidationResult.Valid).path)
        assertEquals("Most Popular", result.label)
    }

    @Test
    fun `most popular week URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/most-popular/week")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/most-popular/week/", (result as ValidationResult.Valid).path)
        assertEquals("Most Popular: Week", result.label)
    }

    // Search
    @Test
    fun `search URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/search/blonde")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/search/blonde/", (result as ValidationResult.Valid).path)
        assertEquals("Search: Blonde", result.label)
    }

    @Test
    fun `search URL with hyphenated query is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/search/big-tits")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Search: Big Tits", (result as ValidationResult.Valid).label)
    }

    // Model URLs
    @Test
    fun `model URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/models/mick-blue")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/models/mick-blue/", (result as ValidationResult.Valid).path)
        assertEquals("Model: Mick Blue", result.label)
    }

    @Test
    fun `model URL with trailing slash is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/models/mick-blue/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/models/mick-blue/", (result as ValidationResult.Valid).path)
    }

    @Test
    fun `model URL with dot in slug is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/models/boris-b.")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/models/boris-b./", (result as ValidationResult.Valid).path)
    }

    @Test
    fun `model URL with parentheses in slug is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/models/louise-lee-(eu)")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/models/louise-lee-(eu)/", (result as ValidationResult.Valid).path)
    }

    // Top Rated
    @Test
    fun `top rated URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/top-rated")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/top-rated/", (result as ValidationResult.Valid).path)
        assertEquals("Top Rated", result.label)
    }

    @Test
    fun `top rated URL with trailing slash is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/top-rated/")
        assertTrue(result is ValidationResult.Valid)
    }

    // Invalid URLs
    @Test
    fun `wrong domain returns InvalidDomain`() {
        val result = FreePornVideosUrlValidator.validate("https://pornhub.com/categories/milf")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `video page URL returns InvalidPath`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/videos/12345/some-video/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `root URL returns InvalidPath`() {
        val result = FreePornVideosUrlValidator.validate("https://www.freepornvideos.xxx/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `empty string returns InvalidPath`() {
        val result = FreePornVideosUrlValidator.validate("")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `blank string returns InvalidPath`() {
        val result = FreePornVideosUrlValidator.validate("   ")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `invalid URL returns InvalidPath`() {
        val result = FreePornVideosUrlValidator.validate("not a url")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Edge cases
    @Test
    fun `http URL is valid`() {
        val result = FreePornVideosUrlValidator.validate("http://www.freepornvideos.xxx/categories/milf")
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `uppercase domain is valid`() {
        val result = FreePornVideosUrlValidator.validate("https://WWW.FREEPORNVIDEOS.XXX/categories/milf")
        assertTrue(result is ValidationResult.Valid)
    }
}
