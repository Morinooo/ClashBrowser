package com.jomo.clashbrowser

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.URI

/**
 * 轻量广告/追踪拦截：HashSet 内存检索，按域名后缀逐级匹配。
 * 在 WebViewClient.shouldInterceptRequest 中命中后返回空响应。
 */
object AdBlocker {

    private lateinit var prefs: android.content.SharedPreferences
    private var customHosts: MutableSet<String> = mutableSetOf()

    /** 用户"标记广告"的自定义域名（持久化）。 */
    fun init(context: android.content.Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("adblock_prefs", android.content.Context.MODE_PRIVATE)
        customHosts = prefs.getStringSet("custom_hosts", emptySet())!!.toMutableSet()
    }

    fun markHost(host: String) {
        val h = host.lowercase().trim()
        if (h.isEmpty() || !::prefs.isInitialized) return
        customHosts.add(h)
        prefs.edit().putStringSet("custom_hosts", customHosts.toSet()).apply()
    }

    private val blockedDomains: HashSet<String> = hashSetOf(
        // Google 广告与统计
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "adservice.google.com",
        // 国际广告网络
        "adnxs.com", "adsrvr.org", "taboola.com", "outbrain.com", "criteo.com",
        "scorecardresearch.com", "quantserve.com", "moatads.com", "amazon-adsystem.com",
        "zedo.com", "advertising.com", "mgid.com", "revcontent.com",
        "popads.net", "propellerads.com", "adcash.com", "exoclick.com",
        "juicyads.com", "trafficjunky.com", "hilltopads.net",
        "ads.yahoo.com", "ads-twitter.com", "analytics.twitter.com",
        "connect.facebook.net", "hotjar.com", "clarity.ms",
        // 国内广告与统计
        "pos.baidu.com", "cpro.baidu.com", "hm.baidu.com", "cb.baidu.com",
        "eclick.baidu.com", "entry.baidu.com", "cnzz.com", "umeng.com",
        "umengcloud.com", "irs01.com", "growingio.com", "sensorsdata.cn",
        "tanx.com", "alimama.com", "mmstat.com", "ad.qq.com", "e.qq.com",
        "gdt.qq.com", "beacon.qq.com", "pingfore.qq.com", "pgdt.gtimg.cn",
        "adsmind.gdtimg.com", "sax.sina.com.cn", "ads.sina.com.cn",
        "ads.tiktok.com", "log.snssdk.com", "pangolin.snssdk.com"
    )

    fun shouldBlock(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        // 媒体流/切片一律放行，避免广告拦截误伤在线视频
        val path = uri.path?.lowercase() ?: ""
        if (MEDIA_EXT.any { path.endsWith(it) }) return false
        val host = uri.host?.lowercase() ?: return false
        // DRM 许可证服务商与主流媒资 CDN 白名单，杜绝误杀鉴权/分片请求
        if (ALLOW_DOMAINS.any { host == it || host.endsWith(".$it") }) return false
        // 用户标记的广告域名
        if (customHosts.any { host == it || host.endsWith(".$it") }) return true
        var h = host
        while (h.contains('.')) {
            if (blockedDomains.contains(h)) return true
            h = h.substringAfter('.')
        }
        return false
    }

    private val ALLOW_DOMAINS = listOf(
        // DRM 许可证服务商
        "widevine.com", "castlabs.com", "buydrm.com", "expressplay.com",
        "irdeto.com", "verimatrix.com", "nagra.com",
        // 主流媒资 CDN
        "cloudfront.net", "akamaized.net", "akamaihd.net", "fastly.net",
        "edgecastcdn.net", "llnwd.net", "cachefly.net", "cdn77.org"
    )

    private val MEDIA_EXT = listOf(
        ".m3u8", ".mpd", ".ts", ".m4s", ".mp4", ".m4a", ".webm", ".ogg", ".aac", ".flv"
    )

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}