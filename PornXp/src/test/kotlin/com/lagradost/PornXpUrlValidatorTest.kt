package com.lagradost

import com.lagradost.common.ValidationResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PornXpUrlValidatorTest {

    @Test
    fun `validates best page`() {
        val result = PornXpUrlValidator.validate("https://pornxp.ph/best/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/best/", result.path)
        assertEquals("Best", result.label)
    }

    @Test
    fun `validates hd page`() {
        val result = PornXpUrlValidator.validate("https://pornxp.ph/hd/")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/hd/", result.path)
        assertEquals("Hd", result.label)
    }

    @Test
    fun `validates tag with simple name`() {
        val result = PornXpUrlValidator.validate("https://pornxp.ph/tags/NubileFilms")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tags/NubileFilms", result.path)
        assertEquals("NubileFilms", result.label)
    }

    @Test
    fun `validates tag with URL-encoded name`() {
        val result = PornXpUrlValidator.validate("https://pornxp.ph/tags/Adriana%20Chechik")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tags/Adriana%20Chechik", result.path)
        assertEquals("Adriana Chechik", result.label)
    }

    @Test
    fun `strips pagination params`() {
        val result = PornXpUrlValidator.validate("https://pornxp.ph/tags/NubileFilms?page=2")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tags/NubileFilms", result.path)
    }

    @Test
    fun `strips sort params`() {
        val result = PornXpUrlValidator.validate("https://pornxp.ph/tags/NubileFilms?sort=new")
        assertIs<ValidationResult.Valid>(result)
        assertEquals("/tags/NubileFilms", result.path)
    }

    @Test
    fun `rejects invalid domain`() {
        val result = PornXpUrlValidator.validate("https://example.com/tags/Test")
        assertIs<ValidationResult.InvalidDomain>(result)
    }

    @Test
    fun `rejects video pages`() {
        val result = PornXpUrlValidator.validate("https://pornxp.ph/videos/12345")
        assertIs<ValidationResult.InvalidPath>(result)
    }
}
