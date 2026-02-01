package com.lagradost.common.intelligence

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagNormalizerTest {

    @Test
    fun `normalize resolves synonym to canonical name`() {
        val result = TagNormalizer.normalize("homemade")
        assertEquals("amateur", result.canonical)
        assertEquals(TagType.GENRE, result.type)
    }

    @Test
    fun `normalize preserves canonical name`() {
        val result = TagNormalizer.normalize("amateur")
        assertEquals("amateur", result.canonical)
        assertEquals(TagType.GENRE, result.type)
    }

    @Test
    fun `normalize classifies body type correctly`() {
        val result = TagNormalizer.normalize("milf")
        assertEquals("milf", result.canonical)
        assertEquals(TagType.BODY_TYPE, result.type)
    }

    @Test
    fun `normalize resolves body type synonym`() {
        val result = TagNormalizer.normalize("cougar")
        assertEquals("milf", result.canonical)
        assertEquals(TagType.BODY_TYPE, result.type)
    }

    @Test
    fun `normalize is case insensitive`() {
        val result = TagNormalizer.normalize("AMATEUR")
        assertEquals("amateur", result.canonical)
    }

    @Test
    fun `normalize trims whitespace`() {
        val result = TagNormalizer.normalize("  amateur  ")
        assertEquals("amateur", result.canonical)
    }

    @Test
    fun `normalize returns OTHER for unknown tags`() {
        val result = TagNormalizer.normalize("some-obscure-niche")
        assertEquals("some-obscure-niche", result.canonical)
        assertEquals(TagType.OTHER, result.type)
    }

    @Test
    fun `normalizeAll classifies actors as PERFORMER`() {
        val results = TagNormalizer.normalizeAll(
            tags = listOf("amateur", "hd"),
            actors = listOf("Riley Reid")
        )
        val performer = results.find { it.type == TagType.PERFORMER }
        assertEquals("riley reid", performer?.canonical)
    }

    @Test
    fun `normalizeAll deduplicates tags`() {
        val results = TagNormalizer.normalizeAll(
            tags = listOf("amateur", "homemade", "amateur"),
            actors = emptyList()
        )
        val amateurCount = results.count { it.canonical == "amateur" }
        assertEquals(1, amateurCount)
    }

    @Test
    fun `normalizeAll handles empty inputs`() {
        val results = TagNormalizer.normalizeAll(emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `NormalizedTag prefixed format`() {
        val tag = NormalizedTag("amateur", TagType.GENRE)
        assertEquals("g:amateur", tag.prefixed())
    }

    @Test
    fun `NormalizedTag fromPrefixed roundtrip`() {
        val original = NormalizedTag("milf", TagType.BODY_TYPE)
        val parsed = NormalizedTag.fromPrefixed(original.prefixed())
        assertEquals(original, parsed)
    }

    @Test
    fun `synonym groups from audit - big tits variants`() {
        listOf("big boobs", "busty", "big natural tits").forEach { synonym ->
            val result = TagNormalizer.normalize(synonym)
            assertEquals("big tits", result.canonical, "Expected '$synonym' to map to 'big tits'")
        }
    }

    @Test
    fun `synonym groups from audit - teen variants`() {
        listOf("18+", "young", "barely legal", "18-25").forEach { synonym ->
            val result = TagNormalizer.normalize(synonym)
            assertEquals("teen", result.canonical, "Expected '$synonym' to map to 'teen'")
        }
    }

    @Test
    fun `asian and japanese are distinct`() {
        val asian = TagNormalizer.normalize("asian")
        val japanese = TagNormalizer.normalize("japanese")
        assertTrue(asian.canonical != japanese.canonical, "asian and japanese should be distinct")
    }

    @Test
    fun `step fantasy maps to genre`() {
        val result = TagNormalizer.normalize("step fantasy")
        assertEquals(TagType.GENRE, result.type)
    }

    @Test
    fun `hd synonym groups`() {
        listOf("hd porn", "4k porn", "1080p porn", "4k", "60fps").forEach { synonym ->
            val result = TagNormalizer.normalize(synonym)
            assertEquals("hd", result.canonical, "Expected '$synonym' to map to 'hd'")
            assertEquals(TagType.OTHER, result.type)
        }
    }

    // --- Tag audit additions ---

    @Test
    fun `new genres from audit are classified correctly`() {
        val expectedGenres = listOf(
            "fetish", "masturbation", "outdoor", "fingering", "fisting",
            "hentai", "lingerie", "hardcore", "cuckold", "gaping",
            "riding", "gloryhole", "bukkake", "babe", "bdsm",
            "stockings", "old and young", "office", "wife",
            "cosplay", "femdom", "voyeur", "striptease", "spanking",
            "cowgirl", "reverse cowgirl", "missionary"
        )
        expectedGenres.forEach { tag ->
            val result = TagNormalizer.normalize(tag)
            assertEquals(TagType.GENRE, result.type, "Expected '$tag' to be GENRE")
            assertEquals(tag, result.canonical, "Expected '$tag' to be its own canonical")
        }
    }

    @Test
    fun `new body types from audit are classified correctly`() {
        val expectedBodyTypes = listOf(
            "russian", "small tits", "skinny", "german",
            "czech", "hungarian", "granny", "pregnant"
        )
        expectedBodyTypes.forEach { tag ->
            val result = TagNormalizer.normalize(tag)
            assertEquals(TagType.BODY_TYPE, result.type, "Expected '$tag' to be BODY_TYPE")
            assertEquals(tag, result.canonical, "Expected '$tag' to be its own canonical")
        }
    }

    @Test
    fun `new synonym mappings from audit`() {
        // squirt -> squirting
        assertEquals("squirting", TagNormalizer.normalize("squirt").canonical)
        // college -> schoolgirl
        assertEquals("schoolgirl", TagNormalizer.normalize("college").canonical)
        // babysitter -> role play
        assertEquals("role play", TagNormalizer.normalize("babysitter").canonical)
        // fantasy -> role play
        assertEquals("role play", TagNormalizer.normalize("fantasy").canonical)
        // cum-swap -> cum in mouth
        assertEquals("cum in mouth", TagNormalizer.normalize("cum-swap").canonical)
        // gilf -> granny
        assertEquals("granny", TagNormalizer.normalize("gilf").canonical)
        // beach -> outdoor
        assertEquals("outdoor", TagNormalizer.normalize("beach").canonical)
        // bukake -> bukkake
        assertEquals("bukkake", TagNormalizer.normalize("bukake").canonical)
        // domination -> bdsm
        assertEquals("bdsm", TagNormalizer.normalize("domination").canonical)
        // spycam -> voyeur
        assertEquals("voyeur", TagNormalizer.normalize("spycam").canonical)
        // undressing -> striptease
        assertEquals("striptease", TagNormalizer.normalize("undressing").canonical)
        // deutsch -> german
        assertEquals("german", TagNormalizer.normalize("deutsch").canonical)
        // preggo -> pregnant
        assertEquals("pregnant", TagNormalizer.normalize("preggo").canonical)
    }

    // --- Content orientation tags ---

    @Test
    fun `solo male is classified as genre`() {
        val result = TagNormalizer.normalize("solo male")
        assertEquals("solo male", result.canonical)
        assertEquals(TagType.GENRE, result.type)
    }

    @Test
    fun `gay is classified as genre`() {
        val result = TagNormalizer.normalize("gay")
        assertEquals("gay", result.canonical)
        assertEquals(TagType.GENRE, result.type)
    }

    @Test
    fun `gay synonym mappings`() {
        listOf("m2m", "male on male", "gay porn", "yaoi", "bears", "twink", "daddy gay",
            "bisexual", "frottage", "bara", "bareback", "jock", "hunks", "muscle men").forEach { synonym ->
            val result = TagNormalizer.normalize(synonym)
            assertEquals("gay", result.canonical, "Expected '$synonym' to map to 'gay'")
        }
    }

    @Test
    fun `lesbian expanded synonyms`() {
        listOf("girl on girl", "scissoring", "tribbing", "yuri", "dyke", "lez", "lesbians").forEach { synonym ->
            val result = TagNormalizer.normalize(synonym)
            assertEquals("lesbian", result.canonical, "Expected '$synonym' to map to 'lesbian'")
        }
    }

    @Test
    fun `transgender expanded synonyms`() {
        listOf("ladyboy", "tgirl", "t-girl", "futanari", "newhalf",
            "femboy", "sissy", "crossdresser", "crossdressing", "trap", "ts",
            "dickgirl", "hermaphrodite", "shemales", "tranny", "trans",
            "transexual", "she-male", "chicks with dicks", "pre-op", "post-op",
            "mtf", "ftm").forEach { synonym ->
            val result = TagNormalizer.normalize(synonym)
            assertEquals("transgender", result.canonical, "Expected '$synonym' to map to 'transgender'")
        }
    }
}
