package com.lagradost

import org.junit.Assert.*
import org.junit.Test

class PornHitsUrlValidatorTest {

    // Category URLs (ct parameter)
    @Test
    fun `category URL is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?s=l&ct=milf")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l&ct=milf", (result as ValidationResult.Valid).path)
        assertEquals("Category: Milf", result.label)
    }

    @Test
    fun `category URL with hyphenated name generates proper label`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?ct=big-tits")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Category: Big Tits", (result as ValidationResult.Valid).label)
    }

    @Test
    fun `category URL without www is valid`() {
        val result = PornHitsUrlValidator.validate("https://pornhits.com/videos.php?ct=anal")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l&ct=anal", (result as ValidationResult.Valid).path)
    }

    // Pornstar URLs (ps parameter)
    @Test
    fun `pornstar URL is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?s=l&ps=mia-khalifa")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l&ps=mia-khalifa", (result as ValidationResult.Valid).path)
        assertEquals("Pornstar: Mia Khalifa", result.label)
    }

    @Test
    fun `pornstar URL with underscore generates proper label`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?ps=asa_akira")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Pornstar: Asa Akira", (result as ValidationResult.Valid).label)
    }

    // Site/Sponsor URLs (spon parameter)
    @Test
    fun `sponsor URL is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?s=l&spon=brazzers")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l&spon=brazzers", (result as ValidationResult.Valid).path)
        assertEquals("Site: Brazzers", result.label)
    }

    @Test
    fun `sponsor URL with hyphenated name generates proper label`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?spon=nubile-films")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Site: Nubile Films", (result as ValidationResult.Valid).label)
    }

    // Network URLs (csg parameter)
    @Test
    fun `network URL is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?s=l&csg=metart")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l&csg=metart", (result as ValidationResult.Valid).path)
        assertEquals("Network: Metart", result.label)
    }

    @Test
    fun `network URL without sort param is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?csg=metart-network")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l&csg=metart-network", (result as ValidationResult.Valid).path)
        assertEquals("Network: Metart Network", result.label)
    }

    @Test
    fun `network URL with page param is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?p=1&s=l&csg=metart")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l&csg=metart", (result as ValidationResult.Valid).path)
        assertEquals("Network: Metart", result.label)
    }

    // Search URLs (q parameter)
    @Test
    fun `search URL is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?q=blonde")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?q=blonde", (result as ValidationResult.Valid).path)
        assertEquals("Search: blonde", result.label)
    }

    @Test
    fun `search URL with encoded spaces is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?q=big%20tits")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?q=big%20tits", (result as ValidationResult.Valid).path)
        assertEquals("Search: big tits", result.label)
    }

    // Sort parameter URLs
    @Test
    fun `latest sort URL is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?s=l")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l", (result as ValidationResult.Valid).path)
        assertEquals("Latest Videos", result.label)
    }

    @Test
    fun `top rated sort URL is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?s=bm")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=bm", (result as ValidationResult.Valid).path)
        assertEquals("Top Rated", result.label)
    }

    @Test
    fun `most viewed sort URL is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?s=pm")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=pm", (result as ValidationResult.Valid).path)
        assertEquals("Most Viewed", result.label)
    }

    // Full porn page
    @Test
    fun `full porn URL is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/full-porn")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/full-porn/", (result as ValidationResult.Valid).path)
        assertEquals("Full Porn", result.label)
    }

    @Test
    fun `full porn URL with trailing slash is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/full-porn/")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/full-porn/", (result as ValidationResult.Valid).path)
        assertEquals("Full Porn", result.label)
    }

    // Invalid URLs
    @Test
    fun `wrong domain returns InvalidDomain`() {
        val result = PornHitsUrlValidator.validate("https://pornhub.com/videos.php?ct=milf")
        assertTrue(result is ValidationResult.InvalidDomain)
    }

    @Test
    fun `video page URL returns InvalidPath`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/video/123456-some-video")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `root URL without query returns InvalidPath`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `random path returns InvalidPath`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/random-page")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `invalid URL returns InvalidPath`() {
        val result = PornHitsUrlValidator.validate("not a url")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `empty string returns InvalidPath`() {
        val result = PornHitsUrlValidator.validate("")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    @Test
    fun `blank string returns InvalidPath`() {
        val result = PornHitsUrlValidator.validate("   ")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Edge cases - URL encoding
    @Test
    fun `category URL with URL-encoded characters decodes properly`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?ct=big%20ass")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Category: Big Ass", (result as ValidationResult.Valid).label)
    }

    // Edge cases - HTTP scheme
    @Test
    fun `http URL without TLS is valid`() {
        val result = PornHitsUrlValidator.validate("http://www.pornhits.com/videos.php?ct=milf")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l&ct=milf", (result as ValidationResult.Valid).path)
    }

    // Edge cases - videos.php with invalid sort
    @Test
    fun `videos php URL with invalid sort returns InvalidPath`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?s=invalid")
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Edge cases - trailing slash on videos.php
    @Test
    fun `videos php URL with trailing slash is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php/?s=l")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("/videos.php?s=l", (result as ValidationResult.Valid).path)
    }

    // Edge cases - multiple parameters priority
    @Test
    fun `URL with ct parameter takes priority`() {
        // If multiple filter params exist, ct (category) takes priority based on implementation
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?ct=milf&ps=mia-khalifa")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Category: Milf", (result as ValidationResult.Valid).label)
    }

    // Edge cases - special characters in names
    @Test
    fun `pornstar name with special characters decodes properly`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?ps=m%C3%BCller")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Pornstar: Müller", (result as ValidationResult.Valid).label)
    }

    // Edge cases - empty parameter values
    @Test
    fun `category URL with empty value still validates`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?ct=")
        assertTrue(result is ValidationResult.Valid)
        // Empty category produces "Category: " label
        assertEquals("Category: ", (result as ValidationResult.Valid).label)
    }

    // Edge cases - plus-encoded spaces in search
    @Test
    fun `search URL with plus-encoded spaces is valid`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?q=big+tits")
        assertTrue(result is ValidationResult.Valid)
        // URLDecoder converts + to space
        assertEquals("Search: big tits", (result as ValidationResult.Valid).label)
    }

    // Edge cases - parameter without value is ignored
    @Test
    fun `query parameter without value is ignored`() {
        val result = PornHitsUrlValidator.validate("https://www.pornhits.com/videos.php?ct")
        // ct without = is not recognized, so falls through to InvalidPath
        assertTrue(result is ValidationResult.InvalidPath)
    }

    // Edge cases - uppercase domain (domain comparison is case-insensitive)
    @Test
    fun `domain with uppercase letters is valid`() {
        val result = PornHitsUrlValidator.validate("https://WWW.PORNHITS.COM/videos.php?ct=milf")
        assertTrue(result is ValidationResult.Valid)
        assertEquals("Category: Milf", (result as ValidationResult.Valid).label)
    }
}
