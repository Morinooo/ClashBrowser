package com.jomo.clashbrowser

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * 无痕隔离守卫：记录无痕标签访问过的 host；
 * 退出无痕（开关关闭或最后一个无痕标签关闭）时过期这些 host 的 Cookie
 * 并清空 WebView 缓存，使无痕会话不留登录态与缓存痕迹。
 * 不触碰普通标签访问站点的 Cookie。
 */
object IncognitoGuard {

    private val hosts = mutableSetOf<String>()

    fun recordHost(url: String) {
        val h = runCatching { Uri.parse(url).host }.getOrNull()
        if (!h.isNullOrBlank()) synchronized(hosts) { hosts.add(h.lowercase()) }
    }

    /** 清除无痕期间访问站点的 Cookie 与全局缓存；webView 用于 clearCache。 */
    fun cleanup(webView: WebView?) {
        val snapshot = synchronized(hosts) { hosts.toSet() }
        if (snapshot.isEmpty()) {
            webView?.clearCache(true)
            return
        }
        val cm = CookieManager.getInstance()
        snapshot.forEach { h ->
            listOf("https://$h/", "http://$h/").forEach base@{ base ->
                val cookie = cm.getCookie(base) ?: return@base
                cookie.split(";").forEach { kv ->
                    val name = kv.trim().substringBefore('=')
                    if (name.isNotEmpty()) {
                        cm.setCookie(base, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                        cm.setCookie(
                            base,
                            "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=$h"
                        )
                    }
                }
            }
        }
        cm.flush()
        webView?.clearCache(true)
        synchronized(hosts) { hosts.clear() }
    }
}