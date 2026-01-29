package com.lagradost

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

// === DoodStream family ===

open class DoodStream : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://myvidplay.com"
    override val requiresReferer = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"

    override suspend fun getUrl(
        url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("doply.net", "myvidplay.com").replace("vide0.net", "myvidplay.com")
        val response = app.get(embedUrl, referer = mainUrl, headers = mapOf("User-Agent" to userAgent)).text

        val md5Match = Regex("/pass_md5/([^/]*)/([^/']*)").find(response) ?: return
        val expiry = md5Match.groupValues[1]
        val token = md5Match.groupValues[2]

        val md5Response = app.get(
            "$mainUrl${md5Match.value}",
            referer = embedUrl,
            headers = mapOf("User-Agent" to userAgent)
        ).text.trim()

        val directLink = if (token.isNotEmpty() && expiry.isNotEmpty()) {
            "$md5Response?token=$token&expiry=${expiry}000"
        } else {
            md5Response
        }

        callback(newExtractorLink(name, name, directLink, INFER_TYPE) {
            this.referer = "https://myvidplay.com"
            this.quality = Qualities.Unknown.value
            this.headers = mapOf("User-Agent" to userAgent)
        })
    }
}

class DoodDoply : DoodStream() {
    override var mainUrl = "https://doply.net"
    override var name = "DoodDoply"
}

class DoodVideo : DoodStream() {
    override var mainUrl = "https://vide0.net"
    override var name = "DoodVideo"
}

class D000d : DoodStream() {
    override var mainUrl = "https://d000d.com"
    override var name = "D000d"
}

class Ds2Play : DoodStream() {
    override var mainUrl = "https://ds2play.com"
    override var name = "Ds2Play"
}

// === Vidhidepro family ===

open class Vidhidepro : ExtractorApi() {
    override val name = "Vidhidepro"
    override val mainUrl = "https://vidhidepro.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val script = app.get(url).document
            .selectFirst("script:containsData(sources)")?.data() ?: return null

        val link = Regex("""sources:.\[.file:"(.*)".*""").find(script)?.groupValues?.get(1)
            ?: return null

        if (!link.contains("m3u8")) return null

        return listOf(newExtractorLink(name, name, link, ExtractorLinkType.M3U8) {
            this.referer = referer ?: "$mainUrl/"
            this.quality = Qualities.Unknown.value
        })
    }
}

class Javlion : Vidhidepro() {
    override var mainUrl = "https://javlion.xyz"
    override val name = "Javlion"
}

class VidhideVIP : Vidhidepro() {
    override var mainUrl = "https://vidhidevip.com"
    override val name = "VidhideVIP"
}

// === Streamwish family ===

open class Streamwish : ExtractorApi() {
    override var name = "Streamwish"
    override var mainUrl = "https://streamwish.to"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url)
        if (response.code != 200) return null

        val script = response.document
            .selectFirst("script:containsData(sources)")?.data() ?: return null

        val link = Regex("""file:"(.*?)"""").find(script)?.groupValues?.get(1) ?: return null

        return listOf(newExtractorLink(name, name, link, INFER_TYPE) {
            this.referer = referer ?: ""
            this.quality = Qualities.Unknown.value
        })
    }
}

class Streamhihi : Streamwish() {
    override var mainUrl = "https://streamhihi.com"
    override var name = "Streamhihi"
}

class Javsw : Streamwish() {
    override var mainUrl = "https://javsw.me"
    override var name = "Javsw"
}

// === Standalone extractors ===

class Javclan : ExtractorApi() {
    override var name = "Javclan"
    override var mainUrl = "https://javclan.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, referer = referer)
        if (response.code != 200) return null

        val document = response.document

        // Try plain sources first
        val plainScript = document.selectFirst("script:containsData(sources)")?.data()
        val plainLink = plainScript?.let { Regex("""file:"(.*?)"""").find(it)?.groupValues?.get(1) }
        if (plainLink != null) {
            return listOf(newExtractorLink(name, name, plainLink, INFER_TYPE) {
                this.referer = referer ?: ""
                this.quality = Qualities.Unknown.value
            })
        }

        // Try packed JS (p,a,c,k,e,d)
        val packed = document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
            ?: return null
        val unpacked = JsUnpacker(packed).unpack() ?: return null
        val link = Regex("""file:"(.*?)"""").find(unpacked)?.groupValues?.get(1) ?: return null

        return listOf(newExtractorLink(name, name, link, INFER_TYPE) {
            this.referer = referer ?: ""
            this.quality = Qualities.Unknown.value
        })
    }
}

class Javggvideo : ExtractorApi() {
    override val name = "Javgg Video"
    override val mainUrl = "https://javggvideo.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url).text
        val link = response.substringAfter("var urlPlay = '").substringBefore("';")
        if (link == response) return null

        return listOf(newExtractorLink(name, name, link, INFER_TYPE) {
            this.referer = referer ?: "$mainUrl/"
            this.quality = Qualities.Unknown.value
        })
    }
}

// === VOE family (mirrors used by JavGuru) ===

class Javlesbians : com.lagradost.cloudstream3.extractors.Voe() {
    override val mainUrl = "https://javlesbians.com"
    override val name = "Javlesbians"
}

class Lauradaydo : com.lagradost.cloudstream3.extractors.Voe() {
    override val mainUrl = "https://lauradaydo.com"
    override val name = "Lauradaydo"
}

// === Streamtape family (mirrors used by JavGuru) ===

class Advertape : com.lagradost.cloudstream3.extractors.StreamTape() {
    override var mainUrl = "https://advertape.net"
    override var name = "Advertape"
}

class Maxstream : ExtractorApi() {
    override var name = "Maxstream"
    override var mainUrl = "https://maxstream.org"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val packed = app.get(url).document
            .selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data() ?: return null

        val unpacked = JsUnpacker(packed).unpack() ?: return null
        val link = Regex("""file:"(.*?)"""").find(unpacked)?.groupValues?.get(1) ?: return null

        return listOf(newExtractorLink(name, name, link, INFER_TYPE) {
            this.referer = referer ?: ""
            this.quality = Qualities.Unknown.value
        })
    }
}
