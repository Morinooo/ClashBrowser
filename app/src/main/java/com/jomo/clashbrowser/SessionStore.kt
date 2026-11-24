package com.jomo.clashbrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 会话快照（v1.4.3）：持续记录未关闭标签的 URL/标题。
 * 正常退出（返回键退出 / 最近任务划掉）会标记 clean_exit；
 * 进程被系统直接杀死时没有任何回调，标记保持 false，
 * 下次冷启动即弹窗询问是否恢复上次意外关闭的页面。
 */
object SessionStore {
    private lateinit var prefs: SharedPreferences

    // 进程内首次创建 Activity 才检查，避免旋转/系统深浅色切换等
    // 配置变更导致的 Activity 重建被误判为"意外关闭"
    private var firstCreateInProcess = true

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext
                .getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        }
    }

    /** 冷启动时调用一次：判定是否需要弹恢复弹窗，并把运行标记置为"未正常退出"。 */
    fun shouldPromptRestore(isHome: (String) -> Boolean): Boolean {
        if (!firstCreateInProcess) return false
        firstCreateInProcess = false
        val clean = prefs.getBoolean("clean_exit", true)
        prefs.edit().putBoolean("clean_exit", false).apply()
        return isRestoreEnabled() && !clean &&
            loadTabs().any { it.first.isNotBlank() && !isHome(it.first) }
    }

    /** 用户开关：意外退出时是否询问恢复上次页面。 */
    fun isRestoreEnabled(): Boolean = prefs.getBoolean("restore_enabled", true)

    fun setRestoreEnabled(v: Boolean) {
        prefs.edit().putBoolean("restore_enabled", v).apply()
    }

    fun markCleanExit() {
        prefs.edit().putBoolean("clean_exit", true).apply()
    }

    fun saveTabs(tabs: List<Pair<String, String>>) {
        val arr = JSONArray()
        tabs.forEach { (url, title) ->
            if (url.isNotBlank()) arr.put(JSONObject().put("u", url).put("t", title))
        }
        prefs.edit().putString("tabs", arr.toString()).apply()
    }

    fun loadTabs(): List<Pair<String, String>> {
        val raw = prefs.getString("tabs", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                o.optString("u") to o.optString("t")
            }.filter { it.first.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun clearTabs() {
        prefs.edit().remove("tabs").apply()
    }
}