package com.lagradost

import com.lagradost.common.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Porn36UrlValidatorTest {

    @Test
    fun `valid category URL`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/categories/blonde/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/categories/blonde/", (result as ValidationResult.Valid).path)
        assertEquals("Blonde", result.label)
    }

    @Test
    fun `valid category URL without trailing slash`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/categories/blonde")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/categories/blonde/", (result as ValidationResult.Valid).path)
    }

    @Test
    fun `valid network URL`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/networks/mylf-com/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/networks/mylf-com/", (result as ValidationResult.Valid).path)
        assertEquals("Mylf Com", result.label)
    }

    @Test
    fun `valid model URL`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/models/abella-danger/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/models/abella-danger/", (result as ValidationResult.Valid).path)
        assertEquals("Abella Danger", result.label)
    }

    @Test
    fun `valid main section URL`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/top-rated/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/top-rated/", (result as ValidationResult.Valid).path)
        assertEquals("Top Rated", result.label)
    }

    @Test
    fun `invalid domain`() {
        val result = Porn36UrlValidator.validate("https://www.example.com/categories/blonde/")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `invalid path`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/upload/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `domain without www`() {
        val result = Porn36UrlValidator.validate("https://porn36.com/categories/blonde/")
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `rejects excessively long URL`() {
        val longUrl = "https://www.porn36.com/categories/" + "a".repeat(2100)
        val result = Porn36UrlValidator.validate(longUrl)
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Site URLs
    @Test
    fun `valid site URL`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/sites/divine-bitches/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/sites/divine-bitches/", (result as ValidationResult.Valid).path)
        assertEquals("Divine Bitches", result.label)
    }

    @Test
    fun `valid site URL without trailing slash`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/sites/divine-bitches")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/sites/divine-bitches/", (result as ValidationResult.Valid).path)
    }

    // Search URLs
    @Test
    fun `valid search URL`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/search/blonde")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/search/blonde/relevance/", (result as ValidationResult.Valid).path)
        assertEquals("Search: Blonde", result.label)
    }

    @Test
    fun `valid search URL with relevance`() {
        val result = Porn36UrlValidator.validate("https://www.porn36.com/search/big-tits/relevance/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/search/big-tits/relevance/", (result as ValidationResult.Valid).path)
        assertEquals("Search: Big Tits", result.label)
    }
}
