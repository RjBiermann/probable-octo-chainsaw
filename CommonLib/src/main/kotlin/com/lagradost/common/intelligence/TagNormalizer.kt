package com.lagradost.common.intelligence

/**
 * Stateless singleton that normalizes raw tags to canonical names with type classification.
 *
 * Synonym map and type map are built at class-load time from hardcoded data
 * validated against real category pages from 7+ sites in the plugin collection.
 *
 * Unknown tags get type [TagType.OTHER] and keep their original text.
 */
object TagNormalizer {

    // canonical -> type
    private val typeMap: Map<String, TagType>

    // synonym -> canonical
    private val synonymMap: Map<String, String>

    init {
        val genres = listOf(
            "amateur", "anal", "blowjob", "creampie", "threesome", "pov",
            "gangbang", "deepthroat", "cumshot", "double penetration",
            "ass to mouth", "group sex", "role play", "doggystyle",
            "cum in mouth", "foot fetish", "sex toys", "rough sex",
            "step fantasy", "massage", "solo", "big tits", "big ass",
            "big cock", "ass licking", "face sitting", "titty fuck",
            "pussy licking", "female orgasm", "hairy", "strap-on",
            "uniform", "schoolgirl", "webcam", "bbc", "celebrity",
            "transgender", "interracial", "bondage", "lesbian", "gay", "solo male",
            "handjob", "facial", "squirting",
            "compilation", "casting", "public", "vintage",
            // Added from tag audit (3+ sites)
            "fetish", "masturbation", "outdoor", "fingering", "fisting",
            "hentai", "lingerie", "hardcore", "cuckold", "gaping",
            "riding", "gloryhole", "bukkake",
            // Added from tag audit (2+ sites)
            "babe", "bdsm", "stockings", "old and young",
            "office", "wife", "cosplay", "femdom", "voyeur",
            "striptease", "spanking", "cowgirl", "reverse cowgirl",
            "missionary"
        )

        val bodyTypes = listOf(
            "milf", "teen", "mature", "bbw", "petite",
            "ebony", "latina", "japanese", "asian", "korean",
            "indian", "arab", "redhead", "blonde", "brunette",
            // Added from tag audit (3+ sites)
            "russian", "small tits", "skinny", "german",
            // Added from tag audit (2+ sites)
            "czech", "hungarian", "granny", "pregnant"
        )

        val otherTags = listOf("hd", "vr")

        val synonymGroups = mapOf(
            // Genre synonyms
            "amateur" to listOf("homemade", "home-made", "self-shot", "selfmade"),
            "anal" to listOf("ass-fuck", "butt fuck"),
            "blowjob" to listOf("bj", "oral", "fellatio"),
            "threesome" to listOf("3some", "trio", "ffm", "mmf"),
            "pov" to listOf("point-of-view", "first-person"),
            "creampie" to listOf("cream-pie", "internal"),
            "gangbang" to listOf("gang bang"),
            "deepthroat" to listOf("deep throat"),
            "cumshot" to listOf("cumshots"),
            "double penetration" to listOf("double penetration (dp)", "dp"),
            "ass to mouth" to listOf("ass-to-mouth", "ass to mouth (atm)"),
            "group sex" to listOf("orgy", "foursome", "sex party"),
            "role play" to listOf("roleplay", "babysitter", "fantasy"),
            "doggystyle" to listOf("doggy style"),
            "cum in mouth" to listOf("cum swallowing", "swallow", "cum-swap", "cum swap", "cum swapping"),
            "foot fetish" to listOf("feet", "footjob"),
            "sex toys" to listOf("toys", "vibrator", "dildo"),
            "rough sex" to listOf("brutal sex"),
            "step fantasy" to listOf("stepmom", "stepbrother", "stepdad", "stepdaughter", "stepsister", "stepson", "family taboo"),
            "massage" to listOf("sex massage"),
            "solo" to listOf("solo female"),
            "big tits" to listOf("big boobs", "busty", "big natural tits"),
            "big ass" to listOf("pawg", "bubble butt"),
            "big cock" to listOf("big dick", "bwc", "bwc (big white cock)"),
            "ass licking" to listOf("ass eating", "rimjob", "rimming"),
            "face sitting" to listOf("facesitting"),
            "titty fuck" to listOf("titfuck"),
            "pussy licking" to listOf("cunnilingus"),
            "female orgasm" to listOf("orgasm"),
            "hairy" to listOf("hairy pussy", "hairy bush", "bush"),
            "strap-on" to listOf("strap on"),
            "uniform" to listOf("uniforms"),
            "schoolgirl" to listOf("school girl", "school (18+)", "college (18+)", "college girl", "college"),
            "webcam" to listOf("cam"),
            "bbc" to listOf("bbc (big black cock)"),
            "celebrity" to listOf("celebrities"),
            "transgender" to listOf("shemale", "transsexual", "ladyboy", "tgirl", "t-girl", "futanari", "newhalf", "femboy", "sissy", "crossdresser", "crossdressing", "trap", "ts", "dickgirl", "hermaphrodite", "shemales", "tranny", "trans", "transexual", "she-male", "chicks with dicks", "pre-op", "post-op", "mtf", "ftm"),
            "squirting" to listOf("squirt"),
            "masturbation" to listOf("jerking off"),
            "outdoor" to listOf("beach", "pool"),
            "lingerie" to listOf("fishnet", "stockings and lingerie"),
            "riding" to listOf("cowgirl position"),
            "bukkake" to listOf("bukake"),
            "bdsm" to listOf("domination", "maledom", "slave", "restraints"),
            "femdom" to listOf("lesdom"),
            "voyeur" to listOf("spycam", "peeping"),
            "striptease" to listOf("strip", "undressing"),
            "gay" to listOf("m2m", "male on male", "gay porn", "yaoi", "bears", "twink", "daddy gay", "bisexual", "frottage", "bara", "bareback", "jock", "hunks", "muscle men"),
            "lesbian" to listOf("girl on girl", "scissoring", "tribbing", "yuri", "dyke", "lez", "lesbians"),
            "cosplay" to listOf("costume"),
            "old and young" to listOf("old/young", "age gap"),

            // Body type synonyms
            "milf" to listOf("cougar"),
            "teen" to listOf("18+", "young", "barely legal", "barely-legal", "18-25", "18 years old", "19 years old", "teen porn"),
            "skinny" to listOf("thin", "super skinny", "slim"),
            "small tits" to listOf("flat chest", "tiny tits"),
            "granny" to listOf("gilf", "grandma", "60+", "50+"),
            "russian" to listOf("russian girl"),
            "german" to listOf("deutsch"),
            "czech" to listOf("czech girl", "czech massage"),
            "hungarian" to listOf("hungarian girl"),
            "pregnant" to listOf("preggo"),
            "redhead" to listOf("red head"),
            "latina" to listOf("latin", "hispanic"),
            "japanese" to listOf("jav", "japan"),

            // Other synonyms
            "hd" to listOf("high-definition", "720p", "1080p", "hd porn", "4k porn", "4k quality", "1080p porn", "4k", "60fps", "60 fps porn", "1080p porn hd"),
            "vr" to listOf("virtual reality", "virtual reality (vr)")
        )

        // Build type map
        val types = mutableMapOf<String, TagType>()
        genres.forEach { types[it] = TagType.GENRE }
        bodyTypes.forEach { types[it] = TagType.BODY_TYPE }
        otherTags.forEach { types[it] = TagType.OTHER }
        typeMap = types

        // Build synonym map
        val syns = mutableMapOf<String, String>()
        for ((canonical, synonyms) in synonymGroups) {
            for (synonym in synonyms) {
                syns[synonym.lowercase()] = canonical
            }
        }
        synonymMap = syns
    }

    /**
     * Normalize a single raw tag to its canonical form with type.
     */
    fun normalize(raw: String): NormalizedTag {
        val key = raw.lowercase().trim()
        val canonical = synonymMap[key] ?: key
        val type = typeMap[canonical] ?: TagType.OTHER
        return NormalizedTag(canonical, type)
    }

    /**
     * Normalize a list of tags and actors, deduplicating by canonical name.
     * Actors are always classified as [TagType.PERFORMER].
     */
    fun normalizeAll(tags: List<String>, actors: List<String> = emptyList()): List<NormalizedTag> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<NormalizedTag>()

        for (tag in tags) {
            val normalized = normalize(tag)
            if (seen.add(normalized.canonical)) {
                result.add(normalized)
            }
        }

        for (actor in actors) {
            val canonical = actor.lowercase().trim()
            if (canonical.isNotBlank() && seen.add(canonical)) {
                result.add(NormalizedTag(canonical, TagType.PERFORMER))
            }
        }

        return result
    }
}
