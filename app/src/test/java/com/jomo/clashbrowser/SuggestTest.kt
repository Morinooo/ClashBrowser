package com.jomo.clashbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suggest 纯函数单测：端点构造、四引擎解析、本地匹配。 */
class SuggestTest {
    @Test
    fun urlEncodesQueryPerEngine() {
        assertTrue(Suggest.url("google", "a b").contains("q=a+b"))
        assertTrue(Suggest.url("bing", "a b").contains("query=a+b"))
        assertTrue(Suggest.url("baidu", "a b").contains("wd=a+b"))
        assertTrue(Suggest.url("duckduckgo", "a b").contains("q=a+b"))
        assertTrue(Suggest.url("google", "中").contains("%E4%B8%AD"))
    }

    @Test
    fun parseOpenSearchShape() {
        val body = """["clash",[ "clash meta","clash verge","clash"]]"""
        assertEquals(listOf("clash meta", "clash verge", "clash"), Suggest.parse("google", body))
        assertEquals(listOf("clash meta", "clash verge", "clash"), Suggest.parse("bing", body))
    }

    @Test
    fun parseBaiduSugrec() {
        val body = """{"q":"cl","g":[{"q":"clash","t":"st"},{"q":"clion","t":"st"}],"s":["clashx"]}"""
        assertEquals(listOf("clash", "clion", "clashx"), Suggest.parse("baidu", body))
    }

    @Test
    fun parseDuckListAndBadBody() {
        assertEquals(listOf("a", "b"), Suggest.parse("duckduckgo", """["a","b","a"]"""))
        assertTrue(Suggest.parse("google", "not json").isEmpty())
        assertTrue(Suggest.parse("baidu", "{}").isEmpty())
    }

    @Test
    fun localMatchCaseInsensitiveAndDedup() {
        val h = listOf(
            HistoryEntry("https://a.example/x", "Clash Docs", 1),
            HistoryEntry("https://b.example/y", "other", 2)
        )
        val b = listOf(Bookmark("https://a.example/x", "Clash Docs", 1), Bookmark("https://c.example/z", "CLASH wiki", 2))
        val items = Suggest.local("clash", h, b)
        assertEquals(2, items.size)
        assertEquals(Suggest.Kind.HISTORY, items[0].kind)
        assertEquals(Suggest.Kind.BOOKMARK, items[1].kind)
        assertTrue(Suggest.local("  ", h, b).isEmpty())
    }
}