package com.jomo.clashbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 地址栏输入解析：空输入回主页、URL 补 scheme、关键词走搜索。 */
class AddressTest {

    @Test
    fun emptyGoesHome() {
        assertEquals("about:home", toLoadUrl("  "))
    }

    @Test
    fun keepsHttpUrl() {
        assertEquals("https://a.com/x", toLoadUrl("https://a.com/x"))
    }

    @Test
    fun addsSchemeToBareDomain() {
        assertEquals("https://example.com", toLoadUrl("example.com"))
    }

    @Test
    fun searchesKeywords() {
        val out = toLoadUrl("hello world")
        assertTrue(out.startsWith("https://"))
        assertTrue(out.contains("search?q="))
    }
}