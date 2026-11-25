package com.jomo.clashbrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 广告/追踪拦截规则：黑名单命中、白名单放行、媒体切片放行。 */
class AdBlockerTest {

    @Test
    fun blocksAdDomain() {
        assertTrue(AdBlocker.shouldBlock("https://pos.baidu.com/x.js"))
    }

    @Test
    fun blocksAdSubdomain() {
        assertTrue(AdBlocker.shouldBlock("https://a.b.cnzz.com/z.js"))
    }

    @Test
    fun allowsDrmLicenseProvider() {
        assertFalse(AdBlocker.shouldBlock("https://license.widevine.com/x"))
    }

    @Test
    fun allowsMediaCdn() {
        assertFalse(AdBlocker.shouldBlock("https://d123.cloudfront.net/a.js"))
    }

    @Test
    fun allowsMediaSegmentsEvenOnAdHost() {
        assertFalse(AdBlocker.shouldBlock("https://pos.baidu.com/seg.m3u8"))
    }

    @Test
    fun allowsOrdinarySite() {
        assertFalse(AdBlocker.shouldBlock("https://example.com/page"))
    }
}