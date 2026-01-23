package com.lagradost

import com.lagradost.common.ValidationResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NepornUrlValidatorTest {

    @Test
    fun `validates category URL and extracts label`() {
        val result = NepornUrlValidator.validate("https://neporn.com/categories/amateur/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/categories/amateur/", result.path)
        assertEquals("Amateur", result.label)
    }

    @Test
    fun `validates category URL with hyphens`() {
        val result = NepornUrlValidator.validate("https://neporn.com/categories/big-boobs/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/categories/big-boobs/", result.path)
        assertEquals("Big Boobs", result.label)
    }

    @Test
    fun `validates tag URL`() {
        val result = NepornUrlValidator.validate("https://neporn.com/tags/hd-porn/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tags/hd-porn/", result.path)
        assertEquals("Hd Porn", result.label)
    }

    @Test
    fun `validates special pages`() {
        val result = NepornUrlValidator.validate("https://neporn.com/top-rated/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/top-rated/", result.path)
        assertEquals("Top Rated", result.label)
    }

    @Test
    fun `strips pagination params`() {
        val result = NepornUrlValidator.validate("https://neporn.com/categories/amateur/?from=3")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/categories/amateur/", result.path)
    }

    @Test
    fun `rejects invalid domain`() {
        val result = NepornUrlValidator.validate("https://example.com/categories/amateur/")
        assertIs<ValidationResult.InvalidDomain>(result)
    }

    @Test
    fun `rejects invalid path`() {
        val result = NepornUrlValidator.validate("https://neporn.com/video/12345/")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `handles URL without trailing slash`() {
        val result = NepornUrlValidator.validate("https://neporn.com/categories/amateur")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/categories/amateur/", result.path)
    }

    // Model URL tests
    @Test
    fun `validates model URL and extracts label`() {
        val result = NepornUrlValidator.validate("https://neporn.com/models/jane-doe/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/models/jane-doe/", result.path)
        assertEquals("Jane Doe", result.label)
    }

    @Test
    fun `validates model URL without trailing slash`() {
        val result = NepornUrlValidator.validate("https://neporn.com/models/john-smith")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/models/john-smith/", result.path)
        assertEquals("John Smith", result.label)
    }

    // Search URL tests
    @Test
    fun `validates search path URL`() {
        val result = NepornUrlValidator.validate("https://neporn.com/search/massage/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/search/massage/", result.path)
        assertEquals("Search: Massage", result.label)
    }

    @Test
    fun `validates search query URL`() {
        val result = NepornUrlValidator.validate("https://neporn.com/search/?q=fitness")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/search/?q=fitness", result.path)
        assertEquals("Search: fitness", result.label)
    }

    @Test
    fun `validates search query URL with encoded spaces`() {
        val result = NepornUrlValidator.validate("https://neporn.com/search/?q=hot%20yoga")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("Search: hot yoga", result.label)
    }

    // Pagination stripping tests
    @Test
    fun `strips pagination from category URL`() {
        val result = NepornUrlValidator.validate("https://neporn.com/categories/hd-porn/2/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/categories/hd-porn/", result.path)
        assertEquals("Hd Porn", result.label)
    }

    @Test
    fun `strips pagination from tag URL`() {
        val result = NepornUrlValidator.validate("https://neporn.com/tags/european/5/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tags/european/", result.path)
    }

    @Test
    fun `strips pagination from model URL`() {
        val result = NepornUrlValidator.validate("https://neporn.com/models/jane-doe/3/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/models/jane-doe/", result.path)
    }

    @Test
    fun `rejects models list pagination as invalid`() {
        // /models/2/ is pagination of models list, not a specific model
        val result = NepornUrlValidator.validate("https://neporn.com/models/2/")
        assertIs<ValidationResult.InvalidPath>(result)
    }
}
