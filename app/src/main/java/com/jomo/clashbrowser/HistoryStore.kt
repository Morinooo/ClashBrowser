package com.jomo.clashbrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val url: String, val title: String, val time: Long)

/** 浏览记录存储（SharedPreferences JSON，上限 500 条）+ 无痕模式开关。 */
object HistoryStore {

    private lateinit var prefs: SharedPreferences
    private const val MAX_ENTRIES = 500

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
    }

    fun isIncognito(): Boolean = prefs.getBoolean("incognito", false)
    fun setIncognito(v: Boolean) = prefs.edit().putBoolean("incognito", v).apply()

    fun record(url: String, title: String) {
        if (isIncognito()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val list = list().toMutableList()
        if (list.firstOrNull()?.url == url) return
        list.removeAll { it.url == url }
        list.add(0, HistoryEntry(url, title.ifBlank { url }, System.currentTimeMillis()))
        save(list.take(MAX_ENTRIES))
    }

    fun list(): List<HistoryEntry> {
        val arr = JSONArray(prefs.getString("history", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryEntry(o.getString("u"), o.optString("t"), o.optLong("ts"))
        }
    }

    fun remove(entry: HistoryEntry) {
        save(list().filterNot { it.url == entry.url && it.time == entry.time })
    }

    fun clear() = prefs.edit().putString("history", "[]").apply()

    private fun save(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("u", it.url).put("t", it.title).put("ts", it.time))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }
}