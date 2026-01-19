package com.lagradost

import org.junit.Assert.*
import org.junit.Test

class PornTrexUrlValidatorTest {

    @Test
    fun `valid category URL returns Valid with correct path and label`() {
        val result = PornTrexUrlValidator.validate("https://www.porntrex.com/categories/amateur/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/categories/amateur/", valid.path)
        assertEquals("Category: Amateur", valid.label)
    }

    @Test
    fun `valid category URL without trailing slash`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/categories/big-tits")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/categories/big-tits/", valid.path)
        assertEquals("Category: Big Tits", valid.label)
    }

    @Test
    fun `valid category URL with pagination stripped`() {
        val result = PornTrexUrlValidator.validate("https://www.porntrex.com/categories/amateur/5/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/categories/amateur/", valid.path)
    }

    @Test
    fun `valid tag URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/tags/blonde/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/tags/blonde/", valid.path)
        assertEquals("Tag: Blonde", valid.label)
    }

    @Test
    fun `valid model URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://www.porntrex.com/models/jane-doe/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/models/jane-doe/", valid.path)
        assertEquals("Model: Jane Doe", valid.label)
    }

    @Test
    fun `valid channel URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/channels/brazzers/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/channels/brazzers/", valid.path)
        assertEquals("Channel: Brazzers", valid.label)
    }

    @Test
    fun `valid search URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://www.porntrex.com/search/?query=fitness")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/search/?query=fitness", valid.path)
        assertEquals("Search: fitness", valid.label)
    }

    @Test
    fun `valid special page URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/top-rated/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/top-rated/", valid.path)
        assertEquals("Top Rated", valid.label)
    }

    @Test
    fun `most viewed special page URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/most-popular/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/most-popular/", valid.path)
        assertEquals("Most Viewed", valid.label)
    }

    @Test
    fun `latest updates special page URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/latest-updates/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/latest-updates/", valid.path)
        assertEquals("Latest", valid.label)
    }

    @Test
    fun `invalid domain returns InvalidDomain`() {
        val result = PornTrexUrlValidator.validate("https://example.com/categories/amateur/")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `wrong domain returns InvalidDomain`() {
        val result = PornTrexUrlValidator.validate("https://neporn.com/categories/amateur/")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `video URL returns InvalidPath`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/video/12345/some-video/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `homepage URL returns InvalidPath`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `empty string returns InvalidPath`() {
        val result = PornTrexUrlValidator.validate("")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `malformed URL returns InvalidPath or InvalidDomain`() {
        val result = PornTrexUrlValidator.validate("not a url")
        assertTrue(result is ValidationResult.InvalidDomain || result is ValidationResult.InvalidPath)
    }

    @Test
    fun `URL with www prefix works`() {
        val result = PornTrexUrlValidator.validate("https://www.porntrex.com/categories/anal/")
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `URL without www prefix works`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/categories/anal/")
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `hyphenated category converts to title case`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/categories/big-ass/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("Category: Big Ass", valid.label)
    }

    // URL-encoded search query tests
    @Test
    fun `search URL with encoded spaces decodes correctly`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/search/?query=hot%20yoga")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        // Path preserves encoded form, label is decoded
        assertTrue(valid.path.contains("query="))
        assertEquals("Search: hot yoga", valid.label)
    }

    @Test
    fun `search URL with plus-encoded spaces`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/search/?query=big+tits")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        // URLDecoder decodes + to space in application/x-www-form-urlencoded
        assertTrue(valid.path.contains("query="))
        assertEquals("Search: big tits", valid.label)
    }

    // Pagination stripping tests for tags, models, channels
    @Test
    fun `tag URL with pagination stripped`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/tags/blonde/3/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/tags/blonde/", valid.path)
        assertEquals("Tag: Blonde", valid.label)
    }

    @Test
    fun `model URL with pagination stripped`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/models/jane-doe/2/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/models/jane-doe/", valid.path)
        assertEquals("Model: Jane Doe", valid.label)
    }

    @Test
    fun `channel URL with pagination stripped`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/channels/brazzers/4/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/channels/brazzers/", valid.path)
        assertEquals("Channel: Brazzers", valid.label)
    }

    // Remaining special pages
    @Test
    fun `longest special page URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/longest/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/longest/", valid.path)
        assertEquals("Longest", valid.label)
    }

    @Test
    fun `newest special page URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/newest/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/newest/", valid.path)
        assertEquals("Newest", valid.label)
    }

    // Special pages without trailing slash
    @Test
    fun `special page URL without trailing slash works`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/top-rated")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/top-rated/", valid.path)
        assertEquals("Top Rated", valid.label)
    }

    // Underscore in slug conversion
    @Test
    fun `underscore in category slug converts to title case`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/categories/step_sister/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("Category: Step Sister", valid.label)
    }

    // HTTP protocol
    @Test
    fun `HTTP protocol URL works`() {
        val result = PornTrexUrlValidator.validate("http://porntrex.com/categories/amateur/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/categories/amateur/", valid.path)
    }

    // Empty search query
    @Test
    fun `search URL with empty query returns InvalidPath`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/search/?query=")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `search URL without query param returns InvalidPath`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/search/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Domain security tests (strict validation)
    @Test
    fun `spoofed domain with porntrex in subdomain returns InvalidDomain`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com.evil.com/categories/amateur/")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `spoofed domain with fake prefix returns InvalidDomain`() {
        val result = PornTrexUrlValidator.validate("https://fakeporntrex.com/categories/amateur/")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `URL with no host returns InvalidDomain`() {
        val result = PornTrexUrlValidator.validate("file:///categories/amateur/")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    // Malformed URL - specific assertion
    @Test
    fun `malformed URL returns InvalidPath`() {
        val result = PornTrexUrlValidator.validate("not a url at all")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Path-based search URL format tests
    @Test
    fun `path-based search URL returns Valid`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/search/metart/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/search/metart/", valid.path)
        assertEquals("Search: metart", valid.label)
    }

    @Test
    fun `path-based search URL without trailing slash`() {
        val result = PornTrexUrlValidator.validate("https://www.porntrex.com/search/metart")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/search/metart/", valid.path)
        assertEquals("Search: metart", valid.label)
    }

    @Test
    fun `path-based search URL with hyphenated term`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/search/big-tits/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals("/search/big-tits/", valid.path)
        assertEquals("Search: big-tits", valid.label)
    }

    @Test
    fun `path-based search URL with encoded spaces`() {
        val result = PornTrexUrlValidator.validate("https://porntrex.com/search/hot%20teen/")
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertTrue(valid.path.contains("search/"))
        assertEquals("Search: hot teen", valid.label)
    }
}
