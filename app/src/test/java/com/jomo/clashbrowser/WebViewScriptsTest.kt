package com.jomo.clashbrowser

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：document-start 脚本的来源规则必须是 WebView 认可的格式，
 * 且脚本自身要限定只在 http/https 文档生效。
 *
 * 背景：v1.16.1 曾把 allowedOriginRules 写成 `http://*` / `https://*`，
 * 旧 WebView 不校验所以没暴露；WebView 150（Chromium 新版）严格校验后
 * 抛 `IllegalArgumentException: allowedOriginRules http://* is invalid`，
 * 导致每次创建 WebView 都闪退（v1.16.4 修复）。
 */
class WebViewScriptsTest {

    /** WebView 支持的来源规则：`*`、`scheme://host[:port]`、`scheme://*.host[:port]`。 */
    private val validOriginRule = Regex("""^(\*|https?://(\*\.)?[A-Za-z0-9.-]+(:\d+)?)$""")

    @Test
    fun originRulesMatchWebViewFormat() {
        assertTrue("来源规则集合不能为空", DOCUMENT_START_ORIGINS.isNotEmpty())
        DOCUMENT_START_ORIGINS.forEach { rule ->
            assertTrue("非法的 allowedOriginRules: $rule", validOriginRule.matches(rule))
        }
    }

    @Test
    fun originRulesDoNotReintroduceHttpWildcard() {
        // 这两个写法在 WebView 150+ 会直接抛 IllegalArgumentException
        assertTrue(!DOCUMENT_START_ORIGINS.contains("http://*"))
        assertTrue(!DOCUMENT_START_ORIGINS.contains("https://*"))
    }

    @Test
    fun injectedScriptsScopeThemselvesToHttpPages() {
        // 使用全来源 "*" 后，协议限制改由脚本自己完成（不作用于 file:// 等文档）
        assertTrue(WEBRTC_BLOCK_SCRIPT.contains("location.protocol"))
    }
}
