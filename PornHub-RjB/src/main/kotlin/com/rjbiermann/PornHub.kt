package com.rjbiermann

import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.nodes.Element

class PornHub(val sharedPref: SharedPreferences) : MainAPI() {
    private val globalTvType = TvType.NSFW
    override var mainUrl = "https://www.pornhub.com"
    override var name = "PornHub-RjB"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val cookies = mapOf(Pair("hasVisited", "1"), Pair("accessAgeDisclaimerPH", "1"))

    val categoriesMap: List<Pair<String, String>> = runBlocking {
        getAllCategories()
    }

    val nsfwFilters = getNSFWFilters(sharedPref)

    var categoriesFound: MutableList<String> = mutableListOf()

    val categoriesHome = getAllCategoriesHome()

    val homeSearch = getAllHomeSearch()

    override val mainPage = mainPageOf(
        "$mainUrl/video" to "Recently Featured ",
        "$mainUrl/video?o=mv" to "This Week's Most Viewed",
        "$mainUrl/video?o=tr&t=w" to "This Week's Top Rated",
        *homeSearch,
        *categoriesHome
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val categoryData = request.data
            val categoryName = request.name

            val httpUrl = buildURL(categoryData.toHttpUrl(), page, null)
            Log.d("pornhub", httpUrl.toString())

            val soup = app.get(httpUrl.toString(), cookies = cookies).document

            if (soup.select("div.noResultsWrapper div#noResultBigText").isNotEmpty() || soup.select(
                    "div.sectionWrapper div.noVideosNotice"
                ).isNotEmpty()
            ) {
                return newHomePageResponse(emptyList<HomePageList>(), hasNext = false)
            }

            val home = soup.select("div.sectionWrapper div.wrap")
                .filter { wrap -> !wrap.classNames().contains("videoPremiumBlock") }.mapNotNull {
                    val title = it.selectFirst("span.title a")?.text() ?: ""
                    val link =
                        fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    val img = fetchImgUrl(it.selectFirst("img"))
                    newMovieSearchResponse(
                        name = title,
                        url = link,
                        type = globalTvType,

                        ) {
                        posterUrl = img
                    }
                }
            home.distinctBy { item -> item.url }
            if (home.isNotEmpty()) {
                return newHomePageResponse(
                    list = HomePageList(
                        name = categoryName, list = home, isHorizontalImages = true
                    ), hasNext = true
                )
            } else {
                return newHomePageResponse(emptyList<HomePageList>(), hasNext = false)
            }
        } catch (e: Exception) {
            return newHomePageResponse(emptyList<HomePageList>(), hasNext = false)
        }
        return newHomePageResponse(emptyList<HomePageList>(), hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = mutableListOf<SearchResponse>()
        for (i in 1..5) {
            val url = "$mainUrl/video/search"
            val urlForCategory = if(query.contains("+")) getUrlPairForCategory(query) else null
            var httpUrl = if (urlForCategory != null) {
                setSort(buildURL(urlForCategory.first.toHttpUrl(), i, null))
            } else {
                setSort(buildURL(url.toHttpUrl(), i, query))
            }
            Log.d("pornhub", httpUrl.toString())
            val document = app.get(httpUrl.toString(), cookies = cookies).document
            if (document.select("div.noResultsWrapper div#noResultBigText")
                    .isNotEmpty() || document.select("div.sectionWrapper div.noVideosNotice")
                    .isNotEmpty()
            ) {
                break
            }
            val result = document.select("div.sectionWrapper div.wrap")
                .filter { wrap -> !wrap.classNames().contains("videoPremiumBlock") }.mapNotNull {
                    val title = it.selectFirst("span.title a")?.text() ?: return@mapNotNull null
                    val link =
                        fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    val image = fetchImgUrl(it.selectFirst("img"))

                    newMovieSearchResponse(
                        name = title, url = link, type = globalTvType
                    ) {
                        posterUrl = image
                    }
                }
            result.distinctBy { item -> item.url }
            if (result.isNotEmpty()) {
                response.addAll(result)
            } else {
                break
            }
        }
        return response.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val soup = app.get(url, cookies = cookies).document
        val title = soup.selectFirst(".title span")?.text() ?: ""
        val poster: String? = soup.selectFirst("div.video-wrapper .mainPlayerDiv img")?.attr("src")
            ?: soup.selectFirst("head meta[property=og:image]")?.attr("content")
        val tags = soup.select("div.categoriesWrapper a")
            .map { it?.text()?.trim().toString().replace(", ", "") }

        val recommendations = soup.select("ul#recommendedVideos li.pcVideoListItem").map {
            val rTitle = it.selectFirst("div.phimage a")?.attr("title") ?: ""
            val rUrl = fixUrl(it.selectFirst("div.phimage a")?.attr("href").toString())
            val rPoster = fixUrl(
                it.selectFirst("div.phimage img.js-videoThumb")?.attr("src").toString()
            )
            newMovieSearchResponse(
                name = rTitle, url = rUrl,
            ) {
                posterUrl = rPoster
            }
        }

        val actors =
            soup.select("div.video-wrapper div.video-info-row.userRow div.userInfo div.usernameWrap a")
                .map { it.text() }

        val relatedVideo = soup.select("ul#relatedVideosCenter li.pcVideoListItem").map {
            val rTitle = it.selectFirst("div.phimage a")?.attr("title") ?: ""
            val rUrl = fixUrl(it.selectFirst("div.phimage a")?.attr("href").toString())
            val rPoster = fixUrl(
                it.selectFirst("div.phimage img.js-videoThumb")?.attr("src").toString()
            )
            newMovieSearchResponse(
                name = rTitle, url = rUrl,
            ) {
                posterUrl = rPoster
            }
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = title
            this.tags = tags
            addActors(actors)
            this.recommendations = recommendations + relatedVideo
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val request = app.get(
            url = data, cookies = cookies
        )
        val document = request.document
        val mediaDefinitions = JSONObject(
            document.selectXpath("//script[contains(text(),'flashvars')]").first()?.data()
                ?.substringAfter("=")?.substringBefore(";") ?: "{}"
        ).getJSONArray("mediaDefinitions")

        for (i in 0 until mediaDefinitions.length()) {
            if (mediaDefinitions.getJSONObject(i).optString("quality") != null) {
                val quality = mediaDefinitions.getJSONObject(i).getString("quality")
                val videoUrl = mediaDefinitions.getJSONObject(i).getString("videoUrl")
                val extlinkList = mutableListOf<ExtractorLink>()
                M3u8Helper().m3u8Generation(
                    M3u8Helper.M3u8Stream(
                        videoUrl
                    ), true
                ).amap { stream ->
                    extlinkList.add(
                        newExtractorLink(
                            source = name, name = name, url = stream.streamUrl
                        ) {
                            this.referer = mainUrl
                            this.quality = Regex("(\\d+)").find(quality ?: "")?.groupValues?.get(1)
                                .let { getQualityFromName(it) }
                        })
                }
                extlinkList.forEach(callback)
            }
        }

        return true
    }

    private fun fetchImgUrl(imgsrc: Element?): String? {
        return try {
            imgsrc?.attr("src") ?: imgsrc?.attr("data-src") ?: imgsrc?.attr("data-mediabook")
            ?: imgsrc?.attr("alt") ?: imgsrc?.attr("data-mediumthumb")
            ?: imgsrc?.attr("data-thumb_url")
        } catch (_: Exception) {
            null
        }
    }

    private fun setSort(httpUrl: HttpUrl): HttpUrl {
        val nsfwFilters = getNSFWFilters(sharedPref)
        val sorting = nsfwFilters.homeSearchSort
        val builder = httpUrl.newBuilder()
        when {
            sorting.contains("daily") -> {
                builder.setQueryParameter("t", "t")
            }

            sorting.contains("weekly") -> {
                builder.setQueryParameter("t", "w")
            }

            sorting.contains("monthly") -> {
                builder.setQueryParameter("t", "m")
            }

            sorting.contains("yearly") -> {
                builder.setQueryParameter("t", "y")
            }

            sorting.contains("all") -> {
                builder.setQueryParameter("o", "a")
            }
        }

        when {
            sorting.contains("recent") -> {
                builder.setQueryParameter("o", "mr")
            }

            sorting.contains("viewed") -> {
                builder.setQueryParameter("o", "mv")
            }

            sorting.contains("rated") -> {
                builder.setQueryParameter("o", "tr")
            }

            sorting.contains("longest") -> {
                builder.setQueryParameter("o", "tl")
            }
        }
        return builder.build()
    }

    private fun buildURL(httpUrl: HttpUrl, page: Int?, query: String?): HttpUrl {
        val nsfwFilters = getNSFWFilters(sharedPref)
        val builder = httpUrl.newBuilder()
        if (nsfwFilters.hdOnly) {
            builder.setQueryParameter("hd", "1")
        }
        if (nsfwFilters.minDuration > 9 && nsfwFilters.minDuration < 40) {
            builder.setQueryParameter("min_duration", "${nsfwFilters.minDuration / 10 * 10}")
        }
        if (nsfwFilters.maxDuration > 9 && nsfwFilters.maxDuration < 40) {
            builder.setQueryParameter("max_duration", "${nsfwFilters.maxDuration / 10 * 10}")
        }
        if (page != null && page > 1) {
            builder.setQueryParameter("page", "$page")
        }
        if (query != null) {
            builder.setQueryParameter("search", query.replace(" ", "+"))
        }
        return builder.build()
    }


    private fun getUrlPairForCategory(search: String): Pair<String, String>? {
        val categories = Pair(search.split("+")[0], search.split("+")[1])
        val categoryFirst = categoriesMap.firstOrNull {
            FuzzySearch.partialRatio(
                it.second, categories.first
            ) >= 80
        }
        val categorySecond = categoriesMap.firstOrNull {
            FuzzySearch.partialRatio(
                it.second, categories.second
            ) >= 80
        }
        return when {
            categoryFirst != null && categorySecond == null -> {
                categoriesFound.add(search)
                getUrlPairForSingleCategoryAndSearch(
                    categories.second, categoryFirst.second, categoryFirst.first
                )
            }

            categoryFirst == null && categorySecond != null -> {
                categoriesFound.add(search)
                getUrlPairForSingleCategoryAndSearch(
                    categories.first, categorySecond.second, categorySecond.first
                )
            }

            categoryFirst != null && categorySecond != null -> {
                categoriesFound.add(search)
                getUrlPairForTwoCategories(categoryFirst, categorySecond)
            }

            else -> {
                null
            }
        }
    }

    private fun getUrlPairForTwoCategories(
        categoryFirst: Pair<String, String>, categorySecond: Pair<String, String>
    ): Pair<String, String> {
        val categoryFirstSlug =
            categoryFirst.second.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
                .replace(" ", "-")
        val categorySecondSlug =
            categorySecond.second.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
                .replace(" ", "-")

        val url =
            "$mainUrl/video/incategories/${categoryFirstSlug}/${categorySecondSlug}".toHttpUrl()

        return setSort(
            url
        ).toString() to categoryFirst.second.capitalize() + " + " + categorySecond.second.capitalize() + " Category"
    }

    private fun getUrlPairForSingleCategoryAndSearch(
        search: String, category: String, categoryId: String
    ): Pair<String, String> {
        val url = "$mainUrl/video/search".toHttpUrl().newBuilder()
        url.setQueryParameter("search", search.replace(" ", "+"))
        url.setQueryParameter("filter_category", categoryId)

        return setSort(
            url.toString().toHttpUrl()
        ).toString() to search.capitalize() + " + " + category.capitalize() + " Category"
    }

    private fun getUrlPairForSingleCategory(search: String): Pair<String, String> {
        val category = categoriesMap.first {
            FuzzySearch.partialRatio(
                it.second, search
            ) >= 80
        }
        val url = "$mainUrl/video".toHttpUrl().newBuilder()
        val categoryUrl = url.setQueryParameter("c", category.first).build().toString()
        categoriesFound.add(search)
        return setSort(
            categoryUrl.toString().toHttpUrl()
        ).toString() to category.second.capitalize() + " Category"
    }

    private fun getAllCategoriesHome(): Array<Pair<String, String>> {
        return nsfwFilters.homeSearch.split(",").filter { search -> search.isNotBlank() }
            .map { search ->
                if (!search.contains("+")) {
                    getUrlPairForSingleCategory(search)
                } else {
                    getUrlPairForCategory(search)
                }
            }.mapNotNull { it }.toTypedArray()
    }

    private suspend fun getAllCategories(): List<Pair<String, String>> {
        val soup = app.get("$mainUrl/categories", cookies = cookies).document

        return soup.select("ul.categoriesListSection li.catPic").mapNotNull { category ->
            val categoryName =
                category.selectFirst("span.categoryTitleWrapper a strong")?.text() ?: ""
            val categoryId = category.attr("data-category").toString()
            categoryId to categoryName
        }.filter { it.second.isNotEmpty() || it.first.isNotEmpty() }
    }

    private fun getAllHomeSearch(): Array<Pair<String, String>> =
        nsfwFilters.homeSearch.split(",").filter { search -> search.isNotBlank() }
            .filter { !categoriesFound.contains(it) }.map { search ->
                val url = "$mainUrl/video/search".toHttpUrl().newBuilder()
                url.setQueryParameter("search", search.replace(" ", "+"))
                setSort(url.toString().toHttpUrl()).toString() to search.capitalize()
            }.toTypedArray()
}
