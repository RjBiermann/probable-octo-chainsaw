package com.lagradost.common

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurationUtilsTest {

    // Colon format tests (MM:SS)
    @Test
    fun `parseDuration parses MM SS format`() {
        assertEquals(10, DurationUtils.parseDuration("10:25"))
    }

    @Test
    fun `parseDuration parses single digit minutes`() {
        assertEquals(5, DurationUtils.parseDuration("5:30"))
    }

    @Test
    fun `parseDuration returns 1 for zero minutes with seconds`() {
        assertEquals(1, DurationUtils.parseDuration("0:45"))
    }

    @Test
    fun `parseDuration returns 0 for zero duration`() {
        assertEquals(0, DurationUtils.parseDuration("0:00"))
    }

    // Colon format tests (HH:MM:SS)
    @Test
    fun `parseDuration parses HH MM SS format`() {
        assertEquals(90, DurationUtils.parseDuration("1:30:00"))
    }

    @Test
    fun `parseDuration parses HH MM SS with seconds`() {
        assertEquals(90, DurationUtils.parseDuration("1:30:45"))
    }

    @Test
    fun `parseDuration parses long duration`() {
        assertEquals(150, DurationUtils.parseDuration("2:30:00"))
    }

    @Test
    fun `parseDuration returns 1 for HH MM SS with only seconds`() {
        assertEquals(1, DurationUtils.parseDuration("0:00:30"))
    }

    // Suffix format tests
    @Test
    fun `parseDuration parses hours only`() {
        assertEquals(60, DurationUtils.parseDuration("1h"))
    }

    @Test
    fun `parseDuration parses minutes only with m`() {
        assertEquals(25, DurationUtils.parseDuration("25m"))
    }

    @Test
    fun `parseDuration parses minutes only with min`() {
        assertEquals(25, DurationUtils.parseDuration("25min"))
    }

    @Test
    fun `parseDuration parses hours and minutes`() {
        assertEquals(90, DurationUtils.parseDuration("1h 30m"))
    }

    @Test
    fun `parseDuration parses full suffix format`() {
        assertEquals(90, DurationUtils.parseDuration("1h 30m 45s"))
    }

    @Test
    fun `parseDuration parses minutes and seconds`() {
        assertEquals(25, DurationUtils.parseDuration("25m 54s"))
    }

    @Test
    fun `parseDuration returns 1 for seconds only`() {
        assertEquals(1, DurationUtils.parseDuration("45s"))
    }

    @Test
    fun `parseDuration handles non-breaking space`() {
        assertEquals(90, DurationUtils.parseDuration("1h\u00A030m"))
    }

    // Edge cases
    @Test
    fun `parseDuration returns null for empty string`() {
        assertNull(DurationUtils.parseDuration(""))
    }

    @Test
    fun `parseDuration returns null for whitespace only`() {
        assertNull(DurationUtils.parseDuration("   "))
    }

    @Test
    fun `parseDuration returns null for invalid format`() {
        assertNull(DurationUtils.parseDuration("invalid"))
    }

    @Test
    fun `parseDuration returns null for single number`() {
        assertNull(DurationUtils.parseDuration("123"))
    }

    @Test
    fun `parseDuration handles leading and trailing whitespace`() {
        assertEquals(10, DurationUtils.parseDuration("  10:25  "))
    }

    @Test
    fun `parseDuration handles whitespace in colon format`() {
        assertEquals(10, DurationUtils.parseDuration("10 : 25"))
    }

    // Suffix format should not match pm/ms
    @Test
    fun `parseDuration does not match pm as minutes`() {
        assertNull(DurationUtils.parseDuration("5pm"))
    }

    @Test
    fun `parseDuration does not match ms as seconds`() {
        assertNull(DurationUtils.parseDuration("500ms"))
    }

    // Multiple colons edge case
    @Test
    fun `parseDuration returns null for too many colons`() {
        assertNull(DurationUtils.parseDuration("1:2:3:4"))
    }

    @Test
    fun `parseDuration returns null for single colon segment`() {
        assertNull(DurationUtils.parseDuration("10"))
    }
}
