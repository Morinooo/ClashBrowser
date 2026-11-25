package com.jomo.clashbrowser

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

/** 下载任务状态。 */
enum class DlState { RUNNING, DONE, FAILED }

/** 下载任务快照（进度经 StateFlow 推送给 UI）。 */
data class DlTask(
    val id: String,
    val url: String,
    val name: String,
    val mime: String,
    val total: Long,
    val done: Long,
    val state: DlState,
    val uri: String,
    val error: String,
    val time: Long
)

/**
 * 下载管理器：接管 WebView 非图片下载，流式写入并报告进度；
 * API29+ 存入系统 Download/ClashBrowser（MediaStore，免权限），
 * API26-28 存入应用外部私有 Download 目录（免权限），经 FileProvider 打开。
 * 任务元数据持久化，失败可重试；上次退出时中断的任务标记为失败。
 */
object DownloadStore {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tasks = MutableStateFlow<List<DlTask>>(emptyList())

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs.getString("tasks", "[]")) }.getOrDefault(JSONArray())
        val list = mutableListOf<DlTask>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            var state = runCatching { DlState.valueOf(o.optString("state")) }.getOrDefault(DlState.FAILED)
            var err = o.optString("error")
            if (state == DlState.RUNNING) {
                state = DlState.FAILED
                err = "上次退出时中断"
            }
            list.add(
                DlTask(
                    o.getString("id"), o.getString("url"), o.getString("name"),
                    o.optString("mime"), o.optLong("total"), o.optLong("done"),
                    state, o.optString("uri"), err, o.optLong("time")
                )
            )
        }
        tasks.value = list
    }

    private fun persist() {
        val arr = JSONArray()
        tasks.value.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id).put("url", t.url).put("name", t.name)
                    .put("mime", t.mime).put("total", t.total).put("done", t.done)
                    .put("state", t.state.name).put("uri", t.uri)
                    .put("error", t.error).put("time", t.time)
            )
        }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }

    private fun update(id: String, f: (DlTask) -> DlTask) {
        tasks.value = tasks.value.map { if (it.id == id) f(it) else it }
        persist()
    }

    /** WebView DownloadListener 入口：登记任务并开始流式下载。 */
    fun enqueue(url: String, ua: String?, disposition: String?, mime: String?, length: Long) {
        val name = cleanName(URLUtil.guessFileName(url, disposition, mime))
        val id = java.util.UUID.randomUUID().toString().substring(0, 8)
        val task = DlTask(
            id, url, name, mime ?: "application/octet-stream",
            length, 0, DlState.RUNNING, "", "", System.currentTimeMillis()
        )
        tasks.value = listOf(task) + tasks.value
        persist()
        start(task, ua)
    }

    /** 失败/中断任务从头重试。 */
    fun retry(id: String) {
        val t = tasks.value.firstOrNull { it.id == id } ?: return
        if (t.state == DlState.RUNNING) return
        update(id) { it.copy(state = DlState.RUNNING, done = 0, error = "", uri = "") }
        start(tasks.value.first { it.id == id }, null)
    }

    /** 删除任务记录与已写入文件（UI 侧需二次确认）。 */
    fun delete(id: String) {
        val t = tasks.value.firstOrNull { it.id == id } ?: return
        runCatching {
            if (t.uri.startsWith("content://")) {
                appContext.contentResolver.delete(Uri.parse(t.uri), null, null)
            } else if (t.uri.isNotEmpty()) {
                File(t.uri).delete()
            }
            Unit
        }
        tasks.value = tasks.value.filter { it.id != id }
        persist()
    }

    /** 用系统应用打开已下载文件；无可用应用时返回 false。 */
    fun open(context: Context, t: DlTask): Boolean {
        if (t.uri.isEmpty()) return false
        val uri = runCatching {
            if (t.uri.startsWith("content://")) Uri.parse(t.uri)
            else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(t.uri))
        }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, t.mime.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun start(task: DlTask, ua: String?) {
        scope.launch {
            var out: OutputStream? = null
            var uriStr = ""
            var file: File? = null
            try {
                val builder = Request.Builder().url(task.url)
                builder.header("User-Agent", ua ?: System.getProperty("http.agent") ?: "ClashBrowser")
                CookieManager.getInstance().getCookie(task.url)?.let { builder.header("Cookie", it) }
                val resp = MediaActions.client().newCall(builder.build()).execute()
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("空响应")
                val total = if (task.total > 0) task.total else body.contentLength()
                val stream: OutputStream = if (Build.VERSION.SDK_INT >= 29) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, task.name)
                        put(MediaStore.Downloads.MIME_TYPE, task.mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClashBrowser")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                        ?: error("无法创建下载文件")
                    uriStr = uri.toString()
                    appContext.contentResolver.openOutputStream(uri) ?: error("无法写入下载文件")
                } else {
                    val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("存储不可用")
                    dir.mkdirs()
                    file = uniqueFile(dir, task.name)
                    uriStr = file.absolutePath
                    file.outputStream()
                }
                out = stream
                update(task.id) { it.copy(total = total, uri = uriStr) }
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastPush = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        stream.write(buf, 0, n)
                        done += n
                        if (done - lastPush >= 256 * 1024) {
                            lastPush = done
                            update(task.id) { it.copy(done = done) }
                        }
                    }
                }
                stream.flush()
                stream.close()
                out = null
                if (Build.VERSION.SDK_INT >= 29) {
                    val cv = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    appContext.contentResolver.update(Uri.parse(uriStr), cv, null, null)
                }
                update(task.id) {
                    it.copy(done = done, total = if (total > 0) total else done, state = DlState.DONE)
                }
            } catch (e: Exception) {
                runCatching { out?.close() }
                runCatching {
                    if (uriStr.startsWith("content://")) {
                        appContext.contentResolver.delete(Uri.parse(uriStr), null, null)
                    }
                }
                runCatching { file?.delete() }
                update(task.id) {
                    it.copy(state = DlState.FAILED, error = e.message ?: "下载失败", uri = "")
                }
            }
        }
    }

    private fun cleanName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download.bin" }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    /** 进度/体积展示用。 */
    fun fmtSize(b: Long): String = when {
        b >= 1024 * 1024 * 1024 -> String.format("%.2f GB", b / 1024.0 / 1024 / 1024)
        b >= 1024 * 1024 -> String.format("%.1f MB", b / 1024.0 / 1024)
        b >= 1024 -> String.format("%.0f KB", b / 1024.0)
        else -> "$b B"
    }
}