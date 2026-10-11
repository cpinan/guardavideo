package com.carlospinan.guardavideo.app.media

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val LIMIT = 20

/**
 * Dailymotion through its public API (fast, no key, the recommended default); YouTube through
 * yt-dlp's `ytsearch` (flat: only the list, nothing is downloaded).
 */
@Singleton
class FullWebSearch @Inject constructor(
    private val http: HttpClient,
    private val ytDlp: YtDlpSiteDownloader,
) : WebSearch {
    override val sites = listOf(DAILYMOTION, YOUTUBE)

    override suspend fun search(site: SearchSite, query: String): List<SearchHit> = when (site) {
        DAILYMOTION -> dailymotion(query)
        YOUTUBE -> youtube(query)
        else -> emptyList()
    }

    private suspend fun dailymotion(query: String): List<SearchHit> {
        val body = http.get("https://api.dailymotion.com/videos") {
            parameter("search", query)
            parameter("fields", "id,title,thumbnail_240_url,duration,owner.screenname")
            parameter("limit", LIMIT)
            parameter("sort", "relevance")
        }.bodyAsText()
        return parseDailymotion(body)
    }

    private suspend fun youtube(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        ytDlp.ensureReady()
        val request = YoutubeDLRequest("ytsearch$LIMIT:$query").apply {
            addOption("--flat-playlist")
            addOption("-J")
            addOption("--no-warnings")
        }
        parseYtDlpSearch(YoutubeDL.getInstance().execute(request).out)
    }

    companion object {
        val DAILYMOTION = SearchSite("dailymotion", "Dailymotion", recommended = true)
        val YOUTUBE = SearchSite("youtube", "YouTube")
    }
}

private val json = Json { ignoreUnknownKeys = true }

internal fun parseDailymotion(body: String): List<SearchHit> =
    json.parseToJsonElement(body).jsonObject["list"]?.jsonArray.orEmpty().mapNotNull { el ->
        val o = el.jsonObject
        val id = o.str("id") ?: return@mapNotNull null
        SearchHit(
            site = FullWebSearch.DAILYMOTION,
            title = o.str("title") ?: return@mapNotNull null,
            url = "https://www.dailymotion.com/video/$id",
            thumbnailUrl = o.str("thumbnail_240_url")?.replace("http://", "https://"),
            durationSec = o["duration"]?.jsonPrimitive?.doubleOrNull?.toLong(),
            author = o.str("owner.screenname"),
        )
    }

internal fun parseYtDlpSearch(out: String): List<SearchHit> {
    // yt-dlp may print warnings before the JSON object.
    val start = out.indexOf('{')
    if (start < 0) return emptyList()
    return json.parseToJsonElement(out.substring(start)).jsonObject["entries"]?.jsonArray.orEmpty().mapNotNull { el ->
        val o = el.jsonObject
        val id = o.str("id") ?: return@mapNotNull null
        SearchHit(
            site = FullWebSearch.YOUTUBE,
            title = o.str("title") ?: return@mapNotNull null,
            url = o.str("url")?.takeIf { it.startsWith("http") } ?: "https://www.youtube.com/watch?v=$id",
            thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
            durationSec = o["duration"]?.jsonPrimitive?.doubleOrNull?.toLong(),
            author = o.str("channel") ?: o.str("uploader"),
        )
    }
}

private fun JsonObject.str(key: String): String? =
    this[key]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
