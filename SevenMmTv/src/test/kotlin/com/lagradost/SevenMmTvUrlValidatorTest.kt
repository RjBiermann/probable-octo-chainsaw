package com.lagradost

import com.lagradost.common.ValidationResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SevenMmTvUrlValidatorTest {

    @Test
    fun `validates censored list page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/censored_list/all/1.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/censored_list/all/", result.path)
        assertEquals("Censored", result.label)
    }

    @Test
    fun `validates uncensored list page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/uncensored_list/all/1.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/uncensored_list/all/", result.path)
        assertEquals("Uncensored", result.label)
    }

    @Test
    fun `validates category page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/censored_category/5/Amateur/1.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/censored_category/5/Amateur/", result.path)
        assertEquals("Censored - Amateur", result.label)
    }

    @Test
    fun `validates maker page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/censored_makersr/5638/Some-Studio/1.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/censored_makersr/5638/Some-Studio/", result.path)
        assertEquals("Some Studio", result.label)
    }

    @Test
    fun `validates performer page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/censored_avperformer/14/amateur/1.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/censored_avperformer/14/amateur/", result.path)
        assertEquals("Amateur", result.label)
    }

    @Test
    fun `validates issuer page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/censored_issuer/5/MOODYZ%20DIVA/1.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/censored_issuer/5/MOODYZ%20DIVA/", result.path)
        assertEquals("MOODYZ DIVA", result.label)
    }

    @Test
    fun `validates director page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/censored_director/3546/Hiromichi%20Hose/1.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/censored_director/3546/Hiromichi%20Hose/", result.path)
        assertEquals("Hiromichi Hose", result.label)
    }

    @Test
    fun `validates reducing-mosaic list page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/reducing-mosaic_list/all/1.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/reducing-mosaic_list/all/", result.path)
        assertEquals("Reducing Mosaic", result.label)
    }

    @Test
    fun `rejects invalid domain`() {
        val result = SevenMmTvUrlValidator.validate("https://example.com/en/censored_list/all/1.html")
        assertIs<ValidationResult.InvalidDomain>(result)
    }

    @Test
    fun `rejects video content pages`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/censored_content/197902/KRS-303.html")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    @Test
    fun `rejects invalid paths`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/random/path")
        assertIs<ValidationResult.InvalidPath>(result)
    }

    // Random pages
    @Test
    fun `validates random page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/censored_random/all/index.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/censored_random/all/", result.path)
        assertEquals("Censored Random", result.label)
    }

    @Test
    fun `validates uncensored random page`() {
        val result = SevenMmTvUrlValidator.validate("https://7mmtv.sx/en/uncensored_random/all/index.html")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/en/uncensored_random/all/", result.path)
        assertEquals("Uncensored Random", result.label)
    }
}
