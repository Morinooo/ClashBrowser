package com.jomo.clashbrowser

import kotlinx.coroutines.flow.MutableStateFlow

/** 嗅探到的媒体资源条目。 */
data class SniffItem(val url: String, val kind: String, val time: Long)

/**
 * 资源嗅探：在 shouldInterceptRequest 中按扩展名识别媒体/流媒体请求，
 * 按标签去重留存（上限 200 条），供工具箱「资源嗅探」页复制/下载/打开。
 * 不读响应体、不落盘，仅记录 URL 元数据。
 */
object SnifferStore {

    private val map = HashMap<String, LinkedHashMap<String, SniffItem>>()

    /** 列表版本号：UI collectAsState 触发重组。 */
    val version = MutableStateFlow(0L)

    private val MEDIA_EXT = Regex(
        "\\.(m3u8|mpd|mp4|m4s|ts|flv|webm|mkv|mp3|m4a|aac|ogg|wav)(\\?|$)",
        RegexOption.IGNORE_CASE
    )
    private val AUDIO_EXT = Regex("\\.(mp3|m4a|aac|ogg|wav)(\\?|$)", RegexOption.IGNORE_CASE)

    @Synchronized
    fun record(tabId: String, url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        if (!MEDIA_EXT.containsMatchIn(url)) return
        val kind = when {
            url.substringBefore('?').endsWith(".m3u8", true) ||
                url.substringBefore('?').endsWith(".mpd", true) -> "流媒体"
            AUDIO_EXT.containsMatchIn(url) -> "音频"
            else -> "视频"
        }
        val m = map.getOrPut(tabId) { LinkedHashMap() }
        if (m.containsKey(url)) return
        m[url] = SniffItem(url, kind, System.currentTimeMillis())
        while (m.size > 200) m.remove(m.keys.first())
        version.value++
    }

    @Synchronized
    fun list(tabId: String): List<SniffItem> =
        map[tabId]?.values?.toList()?.reversed() ?: emptyList()

    @Synchronized
    fun clear(tabId: String) {
        map.remove(tabId)
        version.value++
    }

    /** 标签关闭时静默释放，不触发 UI 刷新。 */
    @Synchronized
    fun forget(tabId: String) {
        map.remove(tabId)
    }
}