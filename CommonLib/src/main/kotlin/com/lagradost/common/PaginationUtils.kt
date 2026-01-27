package com.lagradost.common

import org.jsoup.nodes.Document

/**
 * Pagination strategy for building paginated URLs.
 * Abstracts the different pagination patterns used across providers.
 *
 * Usage:
 * ```kotlin
 * val strategy = PaginationStrategy.QueryParam("page")
 * val url = strategy.buildUrl("https://example.com/videos", 2)
 * // Result: "https://example.com/videos?page=2"
 *
 * val pathStrategy = PaginationStrategy.PathSegment("/page/%d/")
 * val url2 = pathStrategy.buildUrl("https://example.com/videos", 3)
 * // Result: "https://example.com/videos/page/3/"
 * ```
 */
sealed class PaginationStrategy {

    /**
     * Build a paginated URL for the given page number.
     * @param baseUrl The base URL without pagination
     * @param page The page number (1-indexed)
     * @return The URL with pagination applied
     */
    abstract fun buildUrl(baseUrl: String, page: Int): String

    /**
     * Query parameter pagination (e.g., ?page=2, ?p=2, ?from=10)
     * @param paramName The query parameter name
     * @param startPage The starting page number (default: 1)
     * @param pageMultiplier For offset-based pagination (e.g., 10 items per page means page 2 = from=10)
     */
    data class QueryParam(
        val paramName: String,
        val startPage: Int = 1,
        val pageMultiplier: Int = 1
    ) : PaginationStrategy() {
        override fun buildUrl(baseUrl: String, page: Int): String {
            val pageValue = (page - 1 + startPage) * pageMultiplier
            return if (baseUrl.contains("?")) {
                "$baseUrl&$paramName=$pageValue"
            } else {
                "$baseUrl?$paramName=$pageValue"
            }
        }
    }

    /**
     * Path segment pagination (e.g., /page/2/, /2)
     * @param format The format string with %d placeholder for page number
     * @param startPage The starting page number (default: 1)
     */
    data class PathSegment(
        val format: String,
        val startPage: Int = 1
    ) : PaginationStrategy() {
        override fun buildUrl(baseUrl: String, page: Int): String {
            val pageValue = page - 1 + startPage
            val suffix = format.replace("%d", pageValue.toString())
            val cleanBase = baseUrl.trimEnd('/')
            return if (suffix.startsWith("/")) {
                "$cleanBase$suffix"
            } else {
                "$cleanBase/$suffix"
            }
        }
    }

    /**
     * No pagination - always returns the base URL.
     * Useful for single-page sections.
     */
    object None : PaginationStrategy() {
        override fun buildUrl(baseUrl: String, page: Int): String = baseUrl
    }
}

/**
 * Utilities for detecting if there are more pages available.
 */
object PaginationDetector {

    /**
     * Check if a "next" pagination link exists in the document.
     * More reliable than assuming hasNext based on item count.
     *
     * @param doc The parsed HTML document
     * @param selectors CSS selectors to check for next page links
     * @return true if a next page link was found
     */
    fun hasNextPage(
        doc: Document,
        vararg selectors: String = arrayOf(
            "a.next",
            "a[rel=next]",
            ".pagination a:contains(>)",
            ".pagination a:contains(»)",
            ".pagination a:contains(Next)",
            ".pagination .next a",
            "a[title=Next]"
        )
    ): Boolean {
        return selectors.any { selector ->
            try {
                doc.select(selector).isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Check if the current page is the last page based on page number elements.
     *
     * @param doc The parsed HTML document
     * @param currentPage The current page number
     * @param selector CSS selector for page number elements
     * @return true if there are more pages after the current page
     */
    fun hasMorePages(
        doc: Document,
        currentPage: Int,
        selector: String = ".pagination a, .pagination span"
    ): Boolean {
        return try {
            val pageNumbers = doc.select(selector)
                .mapNotNull { it.text().toIntOrNull() }
                .maxOrNull() ?: currentPage
            pageNumbers > currentPage
        } catch (e: Exception) {
            false
        }
    }
}
