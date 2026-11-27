package com.jomo.clashbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.16.1 安全加固：订阅掩码、日志脱敏、端口绑定与随机 secret、导入大小兜底。 */
class SecurityTest {

    @Test
    fun maskUrlHidesQueryAndFragment() {
        assertEquals(
            "https://sub.example.com/link/abc?***",
            ProxyManager.maskUrl("https://sub.example.com/link/abc?token=SECRET123&flag=1")
        )
        assertEquals(
            "https://sub.example.com/link/abc",
            ProxyManager.maskUrl("https://sub.example.com/link/abc#frag")
        )
        assertEquals(
            "https://sub.example.com/link/abc?***",
            ProxyManager.maskUrl("https://sub.example.com/link/abc?token=x#frag")
        )
        assertEquals(
            "https://sub.example.com/link/abc",
            ProxyManager.maskUrl("https://sub.example.com/link/abc")
        )
    }

    @Test
    fun maskTextMasksUrlsInsideMessages() {
        val msg = "failed to fetch https://sub.example.com/s?token=SECRET123 : timeout"
        val masked = ProxyManager.maskText(msg)
        assertTrue(!masked.contains("SECRET123"))
        assertTrue(masked.contains("https://sub.example.com/s?***"))
    }

    @Test
    fun basicsBindLoopbackAndRandomSecret() {
        val out = ProxyManager.ensureBasics("proxies: []\n")
        assertTrue(out.contains("bind-address: 127.0.0.1"))
        assertTrue(out.contains("allow-lan: false"))
        assertTrue(out.contains("external-controller: 127.0.0.1:19090"))
        assertTrue(Regex("(?m)^secret: \"[0-9a-f]{32}\"$").containsMatchIn(out))
    }

    @Test
    fun rejectsOversizedYaml() {
        val big = "proxies: []\n# " + "x".repeat(2_100_000)
        assertNotNull(ProxyManager.validateYaml(big))
    }

    @Test
    fun rejectsOversizedBookmarkHtml() {
        val big = "<DL>" + "<A HREF=\"https://a.com\">a</A>".repeat(200_000)
        var threw = false
        try {
            BookmarkStore.importHtml(big)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}