package com.lagradost

import com.lagradost.common.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JavGuruUrlValidatorTest {

    // === Valid Category URLs ===

    @Test
    fun `validates category URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/category/jav-uncensored")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/jav-uncensored", result.path)
        assertEquals("Category: Jav Uncensored", result.label)
    }

    @Test
    fun `validates category URL with trailing slash`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/category/amateur/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/amateur", result.path)
        assertEquals("Category: Amateur", result.label)
    }

    // === Valid Tag URLs ===

    @Test
    fun `validates tag URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/tag/big-tits")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tag/big-tits", result.path)
        assertEquals("Tag: Big Tits", result.label)
    }

    @Test
    fun `validates tag URL with trailing slash`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/tag/married-woman/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tag/married-woman", result.path)
        assertEquals("Tag: Married Woman", result.label)
    }

    // === Valid Maker URLs ===

    @Test
    fun `validates maker URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/maker/sod-create")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/maker/sod-create", result.path)
        assertEquals("Studio: Sod Create", result.label)
    }

    @Test
    fun `validates maker URL with trailing slash`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/maker/s1-no-1-style/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/maker/s1-no-1-style", result.path)
        assertEquals("Studio: S1 No 1 Style", result.label)
    }

    // === Valid Studio URLs ===

    @Test
    fun `validates studio URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/studio/start")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/studio/start", result.path)
        assertEquals("Label: Start", result.label)
    }

    @Test
    fun `validates studio URL with trailing slash`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/studio/moodyz/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/studio/moodyz", result.path)
        assertEquals("Label: Moodyz", result.label)
    }

    // === Valid Actor URLs ===

    @Test
    fun `validates actor URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/actor/%e6%9d%be%e6%9c%ac%e3%82%b1%e3%83%b3")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/actor/%e6%9d%be%e6%9c%ac%e3%82%b1%e3%83%b3", result.path)
    }

    @Test
    fun `validates actor URL with english name`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/actor/ken-matsumoto/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/actor/ken-matsumoto", result.path)
        assertEquals("Actor: Ken Matsumoto", result.label)
    }

    // === Valid Actress URLs ===

    @Test
    fun `validates actress URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/actress/kamiki-rei")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/actress/kamiki-rei", result.path)
        assertEquals("Actress: Kamiki Rei", result.label)
    }

    @Test
    fun `validates actress URL with trailing slash`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/actress/yua-mikami/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/actress/yua-mikami", result.path)
        assertEquals("Actress: Yua Mikami", result.label)
    }

    // === Valid Series URLs ===

    @Test
    fun `validates series URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/series/first-impression")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/series/first-impression", result.path)
        assertEquals("Series: First Impression", result.label)
    }

    @Test
    fun `validates series URL with trailing slash`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/series/hunter-black/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/series/hunter-black", result.path)
        assertEquals("Series: Hunter Black", result.label)
    }

    @Test
    fun `validates series URL with encoded Japanese name`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/series/%e3%83%a9%e3%82%b0%e3%82%b8%e3%83%a5tv/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/series/%e3%83%a9%e3%82%b0%e3%82%b8%e3%83%a5tv", result.path)
    }

    // === Valid Listing URLs ===

    @Test
    fun `validates most-watched-rank URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/most-watched-rank")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/most-watched-rank", result.path)
        assertEquals("Most Watched Rank", result.label)
    }

    // === URL with www prefix ===

    @Test
    fun `validates URL with www prefix`() {
        val result = JavGuruUrlValidator.validate("https://www.jav.guru/category/idol")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/idol", result.path)
    }

    // === Invalid Domain ===

    @Test
    fun `rejects URL from different domain`() {
        val result = JavGuruUrlValidator.validate("https://example.com/category/amateur")
        assertIs<ValidationResult.InvalidDomain>(result)
    }

    @Test
    fun `rejects URL from similar but wrong domain`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru.com/category/amateur")
        assertIs<ValidationResult.InvalidDomain>(result)
    }

    // === Invalid Path ===

    @Test
    fun `rejects homepage URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects video page URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/some-video-title")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects blank URL`() {
        val result = JavGuruUrlValidator.validate("")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects malformed URL`() {
        val result = JavGuruUrlValidator.validate("not a valid url")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    // === Edge Cases ===

    @Test
    fun `rejects whitespace-only URL`() {
        val result = JavGuruUrlValidator.validate("   ")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `validates uppercase domain`() {
        val result = JavGuruUrlValidator.validate("https://JAV.GURU/category/idol")
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `validates HTTP URL`() {
        val result = JavGuruUrlValidator.validate("http://jav.guru/tag/hardcore")
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `ignores query parameters`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/category/amateur?page=2")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/category/amateur", result.path)
    }

    @Test
    fun `ignores URL fragment`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/tag/hardcore#section")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tag/hardcore", result.path)
    }

    @Test
    fun `rejects URL without host`() {
        val result = JavGuruUrlValidator.validate("file:///category/amateur")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `validates encoded tag URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/tag/black-actor")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tag/black-actor", result.path)
        assertEquals("Tag: Black Actor", result.label)
    }

    // === Search URLs ===

    @Test
    fun `validates search URL`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/?s=creampie")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/?s=creampie", result.path)
        assertEquals("Search: Creampie", result.label)
    }

    @Test
    fun `validates search URL with hyphenated query`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/?s=big-tits")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/?s=big-tits", result.path)
        assertEquals("Search: Big Tits", result.label)
    }

    @Test
    fun `validates search URL with encoded query`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/?s=%e6%9d%be%e6%9c%ac")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/?s=%e6%9d%be%e6%9c%ac", result.path)
    }

    @Test
    fun `rejects search URL with empty query`() {
        val result = JavGuruUrlValidator.validate("https://jav.guru/?s=")
        assertIs<ValidationResult.InvalidPath>(result)
    }
}
