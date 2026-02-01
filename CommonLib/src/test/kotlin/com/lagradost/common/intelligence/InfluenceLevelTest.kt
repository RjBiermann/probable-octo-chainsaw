package com.lagradost.common.intelligence

import org.junit.Test
import kotlin.test.assertEquals

class InfluenceLevelTest {

    @Test
    fun `top 20 percent items are Strong`() {
        val items = (1..10).map { TagAffinity("tag$it", it.toFloat(), it, System.currentTimeMillis()) }
        val levels = InfluenceLevel.assign(items) { it.score }
        assertEquals(InfluenceLevel.STRONG, levels[items[9]]) // highest score
        assertEquals(InfluenceLevel.STRONG, levels[items[8]])
    }

    @Test
    fun `bottom 40 percent items are Weak`() {
        val items = (1..10).map { TagAffinity("tag$it", it.toFloat(), it, System.currentTimeMillis()) }
        val levels = InfluenceLevel.assign(items) { it.score }
        assertEquals(InfluenceLevel.WEAK, levels[items[0]]) // lowest score
        assertEquals(InfluenceLevel.WEAK, levels[items[1]])
        assertEquals(InfluenceLevel.WEAK, levels[items[2]])
        assertEquals(InfluenceLevel.WEAK, levels[items[3]])
    }

    @Test
    fun `middle items are Moderate`() {
        val items = (1..10).map { TagAffinity("tag$it", it.toFloat(), it, System.currentTimeMillis()) }
        val levels = InfluenceLevel.assign(items) { it.score }
        assertEquals(InfluenceLevel.MODERATE, levels[items[4]])
        assertEquals(InfluenceLevel.MODERATE, levels[items[7]])
    }

    @Test
    fun `single item is Strong`() {
        val items = listOf(TagAffinity("tag1", 1f, 1, System.currentTimeMillis()))
        val levels = InfluenceLevel.assign(items) { it.score }
        assertEquals(InfluenceLevel.STRONG, levels[items[0]])
    }

    @Test
    fun `empty list returns empty map`() {
        val levels = InfluenceLevel.assign(emptyList<TagAffinity>()) { it.score }
        assertEquals(emptyMap(), levels)
    }

    @Test
    fun `label returns correct strings`() {
        assertEquals("Strong", InfluenceLevel.STRONG.label)
        assertEquals("Moderate", InfluenceLevel.MODERATE.label)
        assertEquals("Weak", InfluenceLevel.WEAK.label)
    }
}
