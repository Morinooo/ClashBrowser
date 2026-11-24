package com.jomo.clashbrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** 首页图标条目：key 为收藏夹 URL，其余为用户自定义覆盖值（null 表示用默认）。 */
data class HomePin(val key: String, val name: String?, val letter: String?, val link: String?)

/** 主页圆形图标解析后的展示数据。 */
data class HomeEntry(val key: String, val name: String, val letter: String, val link: String)

/** 首页图标存储：用户从收藏夹中挑选显示在首页的网页（有序，上限 8 个），支持自定义字母/名称/网址。 */
object HomeStore {

    private lateinit var prefs: SharedPreferences
    const val MAX_PINS = 8

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext
                .getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        }
    }

    fun pins(): List<HomePin> = runCatching {
        val arr = JSONArray(prefs.getString("home_pins", "[]"))
        (0 until arr.length()).map { i ->
            when (val e = arr.get(i)) {
                is JSONObject -> HomePin(
                    e.optString("u"),
                    e.optString("n").takeIf { it.isNotBlank() },
                    e.optString("l").takeIf { it.isNotBlank() },
                    e.optString("k").takeIf { it.isNotBlank() }
                )
                else -> HomePin(e.toString(), null, null, null)
            }
        }
    }.getOrDefault(emptyList())

    fun addPin(url: String) {
        val arr = rawArr()
        if ((0 until arr.length()).none { keyOf(arr.get(it)) == url }) {
            arr.put(JSONObject().put("u", url))
            saveArr(trim(arr))
        }
    }

    fun removePin(url: String) {
        val arr = rawArr()
        val out = JSONArray()
        (0 until arr.length()).forEach { i ->
            if (keyOf(arr.get(i)) != url) out.put(arr.get(i))
        }
        saveArr(out)
    }

    /** 保存自定义覆盖值；传 null 表示恢复默认。 */
    fun updatePin(key: String, name: String?, letter: String?, link: String?) {
        val arr = rawArr()
        val out = JSONArray()
        (0 until arr.length()).forEach { i ->
            val e = arr.get(i)
            if (keyOf(e) == key) {
                val o = if (e is JSONObject) e else JSONObject().put("u", key)
                if (name != null) o.put("n", name) else o.remove("n")
                if (letter != null) o.put("l", letter) else o.remove("l")
                if (link != null) o.put("k", link) else o.remove("k")
                out.put(o)
            } else out.put(e)
        }
        saveArr(out)
    }

    private fun keyOf(e: Any): String =
        if (e is JSONObject) e.optString("u") else e.toString()

    private fun rawArr(): JSONArray =
        runCatching { JSONArray(prefs.getString("home_pins", "[]")) }.getOrDefault(JSONArray())

    private fun trim(arr: JSONArray): JSONArray {
        val out = JSONArray()
        (0 until minOf(arr.length(), MAX_PINS)).forEach { out.put(arr.get(it)) }
        return out
    }

    private fun saveArr(arr: JSONArray) {
        prefs.edit().putString("home_pins", arr.toString()).apply()
    }
}