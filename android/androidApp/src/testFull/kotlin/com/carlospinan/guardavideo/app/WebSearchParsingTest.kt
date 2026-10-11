package com.carlospinan.guardavideo.app

import com.carlospinan.guardavideo.app.media.parseDailymotion
import com.carlospinan.guardavideo.app.media.parseYtDlpSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebSearchParsingTest {
    @Test
    fun dailymotionListBecomesHits() {
        val hits = parseDailymotion(
            """{"page":1,"list":[
              {"id":"x8abc","title":"Gatos","thumbnail_240_url":"http://s1.dmcdn.net/a.jpg","duration":95,"owner.screenname":"Ana"},
              {"id":"x9def","title":"Sin extras"},
              {"title":"Sin id"}]}""",
        )
        assertEquals(2, hits.size)
        assertEquals("https://www.dailymotion.com/video/x8abc", hits[0].url)
        assertEquals("https://s1.dmcdn.net/a.jpg", hits[0].thumbnailUrl)
        assertEquals(95L, hits[0].durationSec)
        assertEquals("Ana", hits[0].author)
        assertNull(hits[1].durationSec)
    }

    @Test
    fun dailymotionWithoutAListIsEmpty() {
        assertEquals(0, parseDailymotion("""{"error":{"message":"x"}}""").size)
    }

    @Test
    fun ytDlpFlatSearchSkipsWarningsAndBuildsLinks() {
        val hits = parseYtDlpSearch(
            """WARNING: something
            {"entries":[{"id":"abc123","title":"Perros","duration":61.0,"channel":"Canal","url":"https://www.youtube.com/watch?v=abc123"},
                        {"id":"zzz","title":"Sin url"}]}""",
        )
        assertEquals(listOf("Perros", "Sin url"), hits.map { it.title })
        assertEquals("https://www.youtube.com/watch?v=zzz", hits[1].url)
        assertEquals(61L, hits[0].durationSec)
        assertEquals(0, parseYtDlpSearch("nothing").size)
    }
}
