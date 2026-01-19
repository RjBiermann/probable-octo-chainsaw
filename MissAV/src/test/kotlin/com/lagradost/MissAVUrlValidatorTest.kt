package com.lagradost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MissAVUrlValidatorTest {

    // === Valid Category URLs ===

    @Test
    fun `validates new category URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm514/en/new")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm514/en/new", result.path)
        assertEquals("New", result.label)
    }

    @Test
    fun `validates today-hot category URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm291/en/today-hot")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm291/en/today-hot", result.path)
        assertEquals("Today Hot", result.label)
    }

    @Test
    fun `validates heyzo URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm1109030/en/heyzo")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm1109030/en/heyzo", result.path)
        assertEquals("Heyzo", result.label)
    }

    @Test
    fun `validates any studio URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm12345/en/some-studio")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm12345/en/some-studio", result.path)
        assertEquals("Some Studio", result.label)
    }

    // === Valid Genre URLs ===

    @Test
    fun `validates genre URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm127/en/genres/Creampie")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm127/en/genres/Creampie", result.path)
        assertEquals("Genre: Creampie", result.label)
    }

    @Test
    fun `validates genre URL with spaces`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm114/en/genres/Big%20Breasts")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm114/en/genres/Big%20Breasts", result.path)
        assertEquals("Genre: Big Breasts", result.label)
    }

    // === Valid Actress URLs ===

    @Test
    fun `validates actress URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm138/en/actresses/JULIA")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm138/en/actresses/JULIA", result.path)
        assertEquals("JULIA", result.label)
    }

    @Test
    fun `validates actress URL with encoded Japanese name`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm248/en/actresses/%E6%B3%A2%E5%A4%9A%E9%87%8E%E7%B5%90%E8%A1%A3")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm248/en/actresses/%E6%B3%A2%E5%A4%9A%E9%87%8E%E7%B5%90%E8%A1%A3", result.path)
        assertEquals("波多野結衣", result.label)
    }

    // === Valid Maker URLs ===

    @Test
    fun `validates maker URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm100/en/makers/S1%20NO.1%20STYLE")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm100/en/makers/S1%20NO.1%20STYLE", result.path)
        assertEquals("Maker: S1 NO.1 STYLE", result.label)
    }

    // === Valid Tag URLs ===

    @Test
    fun `validates tag URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm50/en/tags/creampie")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm50/en/tags/creampie", result.path)
        assertEquals("Tag: Creampie", result.label)
    }

    // === Valid Search URLs ===

    @Test
    fun `validates search URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/search/massage")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/search/massage", result.path)
        assertEquals("Search: Massage", result.label)
    }

    // === Valid URLs without dm prefix ===

    @Test
    fun `validates genre URL without dm prefix`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/genres/Nice%20Boobs")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/genres/Nice%20Boobs", result.path)
        assertEquals("Genre: Nice Boobs", result.label)
    }

    @Test
    fun `validates series URL without dm prefix`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/series/%E5%83%8D%E3%81%8F%E5%A5%B3%E6%80%A7")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/series/%E5%83%8D%E3%81%8F%E5%A5%B3%E6%80%A7", result.path)
        assertEquals("Series: 働く女性", result.label)
    }

    @Test
    fun `validates maker URL without dm prefix`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/makers/1%E3%83%9D%E3%83%B3%E3%83%89")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/makers/1%E3%83%9D%E3%83%B3%E3%83%89", result.path)
        assertEquals("Maker: 1ポンド", result.label)
    }

    @Test
    fun `validates actress URL without dm prefix`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/actresses/Haruka%20Sanada")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/actresses/Haruka%20Sanada", result.path)
        assertEquals("Haruka Sanada", result.label)
    }

    // === URL with www prefix ===

    @Test
    fun `validates URL with www prefix`() {
        val result = MissAVUrlValidator.validate("https://www.missav.ws/dm514/en/new")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm514/en/new", result.path)
    }

    // === Invalid Domain ===

    @Test
    fun `rejects URL from different domain`() {
        val result = MissAVUrlValidator.validate("https://example.com/dm514/en/new")
        assertIs<ValidationResult.InvalidDomain>(result)
    }

    @Test
    fun `rejects URL from similar but wrong domain`() {
        val result = MissAVUrlValidator.validate("https://missav.net/dm514/en/new")
        assertIs<ValidationResult.InvalidDomain>(result)
    }

    // === Invalid Path ===

    @Test
    fun `rejects homepage URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects video page URL`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/ssis-001")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects URL without dm prefix for unknown types`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/unknown/something")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects blank URL`() {
        val result = MissAVUrlValidator.validate("")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects malformed URL`() {
        val result = MissAVUrlValidator.validate("not a valid url")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    // === Edge Cases ===

    @Test
    fun `rejects whitespace-only URL`() {
        val result = MissAVUrlValidator.validate("   ")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `normalizes URL with trailing slash`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm514/en/new/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm514/en/new", result.path)
        assertEquals("New", result.label)
    }

    @Test
    fun `normalizes search URL with trailing slash`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/search/massage/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/search/massage", result.path)
    }

    @Test
    fun `normalizes simple path URL with trailing slash`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/genres/Creampie/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/genres/Creampie", result.path)
    }

    @Test
    fun `validates URL with uppercase domain`() {
        val result = MissAVUrlValidator.validate("https://MISSAV.WS/dm514/en/new")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm514/en/new", result.path)
    }

    @Test
    fun `validates URL with mixed case domain`() {
        val result = MissAVUrlValidator.validate("https://MissAV.WS/dm514/en/new")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm514/en/new", result.path)
    }

    @Test
    fun `validates HTTP URL`() {
        val result = MissAVUrlValidator.validate("http://missav.ws/dm514/en/new")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm514/en/new", result.path)
    }

    @Test
    fun `ignores query parameters`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm514/en/new?page=2")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm514/en/new", result.path)
    }

    @Test
    fun `ignores URL fragment`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm514/en/new#section")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm514/en/new", result.path)
    }

    @Test
    fun `validates tag URL without dm prefix`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/en/tags/amateur")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/tags/amateur", result.path)
        assertEquals("Tag: Amateur", result.label)
    }

    @Test
    fun `validates URL with single digit dm number`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm1/en/new")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm1/en/new", result.path)
    }

    @Test
    fun `validates URL with very large dm number`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm9999999999/en/new")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/dm9999999999/en/new", result.path)
    }

    @Test
    fun `rejects URL with invalid percent encoding`() {
        // %ZZ is invalid percent encoding - URI parser rejects it
        val result = MissAVUrlValidator.validate("https://missav.ws/dm50/en/genres/%ZZinvalid")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects dm URL with non-English locale`() {
        val result = MissAVUrlValidator.validate("https://missav.ws/dm514/fr/new")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects URL without host`() {
        val result = MissAVUrlValidator.validate("file:///dm514/en/new")
        assertIs<ValidationResult.InvalidPath>(result)
    }
}
