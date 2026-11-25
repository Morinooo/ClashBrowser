package com.jomo.clashbrowser

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** subscription-userinfo 解析容错（v1.15.0 加固）。 */
class UserinfoTest {

    @Test
    fun standardHeader() {
        assertArrayEquals(
            longArrayOf(1L, 2L, 3L, 4L),
            ProxyManager.parseUserinfo("upload=1; download=2; total=3; expire=4")
        )
    }

    @Test
    fun caseSpacesAndQuotes() {
        assertArrayEquals(
            longArrayOf(10L, 20L, 30L, 1700000000L),
            ProxyManager.parseUserinfo(" Upload = \"10\" ; DOWNLOAD=20 ; Total=30 ; Expire=1700000000 ")
        )
    }

    @Test
    fun millisecondExpireNormalized() {
        val r = ProxyManager.parseUserinfo("upload=0; download=0; total=0; expire=1700000000000")
        assertArrayEquals(longArrayOf(0L, 0L, 0L, 1700000000L), r)
    }

    @Test
    fun missingFieldsAndGarbage() {
        assertArrayEquals(
            longArrayOf(5L, 0L, 0L, 0L),
            ProxyManager.parseUserinfo("upload=5; foo=bar; expire=abc")
        )
        assertNull(ProxyManager.parseUserinfo(null))
        assertNull(ProxyManager.parseUserinfo("   "))
        assertNull(ProxyManager.parseUserinfo("nonsense"))
    }

    @Test
    fun negativeClamped() {
        assertArrayEquals(
            longArrayOf(0L, 0L, 0L, 0L),
            ProxyManager.parseUserinfo("upload=-1; download=-2; total=-3; expire=-4")
        )
    }
}