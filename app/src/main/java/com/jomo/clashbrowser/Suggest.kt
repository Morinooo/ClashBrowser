package com.jomo.clashbrowser

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 地址栏搜索建议：各引擎 suggest 端点的 URL 构造与响应解析（纯函数，可单测），
 * 以及本地历史/收藏夹的关键词匹配。网络请求走 ProxyManager.httpGet（IO 线程）。
 */
object Suggest {
    enum class Kind { HISTORY, BOOKMARK, SEARCH }

    data class Item(val text: String, val url: String, val kind: Kind)

    /** 引擎 suggest 端点（与地址栏检索引擎偏好一致）。 */
    fun url(engine: String, q: String): String {
        val e = URLEncoder.encode(q, "UTF-8")
        return when (engine) {
            "bing" -> "https://api.bing.com/osjson.aspx?query=$e"
            "baidu" -> "https://www.baidu.com/sugrec?prod=pc&wd=$e"
            "duckduckgo" -> "https://duckduckgo.com/ac/?type=list&q=$e"
            else -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$e"
        }
    }

    /** 解析 suggest 响应为建议词列表（去空白/去重）；格式异常返回空列表。 */
    fun parse(engine: String, body: String): List<String> = runCatching {
        val raw = when (engine) {
            "baidu" -> {
                val o = JSONObject(body)
                val out = mutableListOf<String>()
                o.optJSONArray("g")?.let { a ->
                    for (i in 0 until a.length()) out += a.optJSONObject(i)?.optString("q").orEmpty()
                }
                o.optJSONArray("s")?.let { a ->
                    for (i in 0 until a.length()) out += a.optString(i)
                }
                out
            }
            "duckduckgo" -> {
                val a = JSONArray(body)
                (0 until a.length()).map { a.optString(it) }
            }
            else -> {
                // OpenSearch JSON：[query, [suggestions...]]
                val a = JSONArray(body)
                val arr = a.optJSONArray(1) ?: JSONArray()
                (0 until arr.length()).map { arr.optString(it) }
            }
        }
        raw.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }.getOrDefault(emptyList())

    /** 本地匹配：标题或 URL 含关键词（忽略大小写），历史/收藏各取 limit 条后按 URL 去重。 */
    fun local(
        q: String,
        history: List<HistoryEntry>,
        bookmarks: List<Bookmark>,
        limit: Int = 3
    ): List<Item> {
        val k = q.trim().lowercase()
        if (k.isBlank()) return emptyList()
        fun hit(title: String, url: String) =
            title.lowercase().contains(k) || url.lowercase().contains(k)
        val h = history.filter { hit(it.title, it.url) }.take(limit)
            .map { Item(it.title.ifBlank { it.url }, it.url, Kind.HISTORY) }
        val b = bookmarks.filter { hit(it.title, it.url) }.take(limit)
            .map { Item(it.title.ifBlank { it.url }, it.url, Kind.BOOKMARK) }
        return (h + b).distinctBy { it.url }
    }
}