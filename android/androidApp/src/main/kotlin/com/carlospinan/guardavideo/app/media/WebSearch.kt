package com.carlospinan.guardavideo.app.media

/** A site the in-app search can query. [recommended] ones are on by default. */
data class SearchSite(val id: String, val label: String, val recommended: Boolean = false)

data class SearchHit(
    val site: SearchSite,
    val title: String,
    /** Page link: what the download form would get. */
    val url: String,
    val thumbnailUrl: String? = null,
    val durationSec: Long? = null,
    val author: String? = null,
)

/** Searches video sites without leaving the app. Only the full flavor has one (see [SiteSupport]). */
interface WebSearch {
    val sites: List<SearchSite>

    /** Throws on network/tool errors; the caller shows them per site. */
    suspend fun search(site: SearchSite, query: String): List<SearchHit>
}
