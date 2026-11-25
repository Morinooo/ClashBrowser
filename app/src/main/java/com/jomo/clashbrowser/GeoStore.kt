package com.jomo.clashbrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 网页定位权限的按来源决定（允许/拒绝），JSON 持久化。
 * 工具箱-更多-定位权限记录可查看/单条删除/清空（删除均二次确认）。
 */
object GeoStore {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("geo_prefs", Context.MODE_PRIVATE)
    }

    private fun map(): MutableMap<String, Boolean> {
        val o = JSONObject(prefs.getString("decisions", "{}") ?: "{}")
        val m = mutableMapOf<String, Boolean>()
        o.keys().forEach { k -> m[k] = o.optBoolean(k) }
        return m
    }

    private fun save(m: Map<String, Boolean>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("decisions", o.toString()).apply()
    }

    /** 已记住的决定；null 表示该来源尚未决定（需弹窗询问）。 */
    fun decision(origin: String): Boolean? = map()[origin]

    fun remember(origin: String, allow: Boolean) = save(map().apply { put(origin, allow) })

    fun forget(origin: String) = save(map().apply { remove(origin) })

    fun clear() = prefs.edit().putString("decisions", "{}").apply()

    fun list(): List<Pair<String, Boolean>> = map().toList().sortedBy { it.first }
}