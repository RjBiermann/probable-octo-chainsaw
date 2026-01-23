package com.lagradost

import com.lagradost.common.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FullpornerUrlValidatorTest {

    @Test
    fun `valid category URL returns Valid result`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/category/milf")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/milf", result.path)
        assertEquals("Milf", result.label)
    }

    @Test
    fun `valid category URL with hyphen returns correct label`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/category/big-tits")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/big-tits", result.path)
        assertEquals("Big Tits", result.label)
    }

    @Test
    fun `valid pornstar URL returns Valid result`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/pornstar/riley-reid")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/pornstar/riley-reid", result.path)
        assertEquals("Riley Reid", result.label)
    }

    @Test
    fun `valid search URL returns Valid result`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/search?q=blonde")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/search?q=blonde", result.path)
        assertEquals("Search: blonde", result.label)
    }

    @Test
    fun `valid home URL returns Valid result`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/home")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/home", result.path)
        assertEquals("Featured Videos", result.label)
    }

    @Test
    fun `www subdomain is accepted`() {
        val result = FullpornerUrlValidator.validate("https://www.fullporner.com/category/anal")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/anal", result.path)
    }

    @Test
    fun `trailing slash is handled`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/category/teen/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/teen", result.path)
    }

    @Test
    fun `invalid domain returns InvalidDomain`() {
        val result = FullpornerUrlValidator.validate("https://example.com/category/milf")
        assertIs<ValidationResult.InvalidDomain>(result)
    }

    @Test
    fun `invalid path returns InvalidPath`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/watch/12345")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `empty string returns InvalidPath`() {
        val result = FullpornerUrlValidator.validate("")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `malformed URL returns InvalidPath`() {
        val result = FullpornerUrlValidator.validate("not a valid url")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `root path returns InvalidPath`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `valid channel URL returns Valid result`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/channel/w4b")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/channel/w4b", result.path)
        assertEquals("W4b", result.label)
    }

    @Test
    fun `valid channel URL with hyphen returns correct label`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/channel/brazzers-exxtra")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/channel/brazzers-exxtra", result.path)
        assertEquals("Brazzers Exxtra", result.label)
    }

    @Test
    fun `channel URL with trailing slash is handled`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/channel/mylf/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/channel/mylf", result.path)
    }

    // URL encoding tests
    @Test
    fun `search URL with encoded spaces is valid`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/search?q=big%20tits")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/search?q=big+tits", result.path) // Re-encoded with + for spaces
        assertEquals("Search: big tits", result.label)
    }

    @Test
    fun `search URL with plus-encoded space is valid`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/search?q=big+tits")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("Search: big tits", result.label)
    }

    @Test
    fun `search URL with additional params preserves only query`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/search?q=teen&p=2")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/search?q=teen", result.path)
        assertEquals("Search: teen", result.label)
    }

    @Test
    fun `search URL with special characters is properly encoded`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/search?q=foo%26bar")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/search?q=foo%26bar", result.path) // & is re-encoded
        assertEquals("Search: foo&bar", result.label)
    }

    @Test
    fun `http URL without TLS is valid`() {
        val result = FullpornerUrlValidator.validate("http://fullporner.com/category/milf")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/milf", result.path)
    }

    @Test
    fun `category URL with page number returns InvalidPath`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/category/milf/2")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `whitespace-only string returns InvalidPath`() {
        val result = FullpornerUrlValidator.validate("   ")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `search URL without q parameter returns InvalidPath`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/search?other=value")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `search URL with empty q parameter returns InvalidPath`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/search?q=")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `search URL with whitespace-only q parameter returns InvalidPath`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/search?q=%20%20")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    // URL-encoded slug tests
    @Test
    fun `category URL with URL-encoded characters decodes properly`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/category/big%20ass")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/big%20ass", result.path)
        assertEquals("Big Ass", result.label)
    }

    @Test
    fun `pornstar URL with URL-encoded characters decodes properly`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/pornstar/eva%20elfie")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/pornstar/eva%20elfie", result.path)
        assertEquals("Eva Elfie", result.label)
    }

    @Test
    fun `channel URL with URL-encoded characters decodes properly`() {
        val result = FullpornerUrlValidator.validate("https://fullporner.com/channel/reality%20kings")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/channel/reality%20kings", result.path)
        assertEquals("Reality Kings", result.label)
    }
}
