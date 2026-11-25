package com.jomo.clashbrowser

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.TimeUnit

enum class CoreState { STOPPED, STARTING, RUNNING, ERROR }

data class ProxyGroup(
    val name: String,
    val type: String,
    val now: String,
    val all: List<String>
)

data class ProxySnapshot(
    val groups: List<ProxyGroup>,
    val delays: Map<String, Int>,
    val types: Map<String, String>
)

/** 订阅配置档案：名称 / 流量 / 到期 / 更新时间 / 是否当前生效。 */
data class ProfileInfo(
    val url: String,
    val name: String,
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long,
    val updated: Long,
    val active: Boolean
)

/**
 * mihomo 内核单例管理器：
 * - 子进程启动 / destroyForcibly 销毁
 * - assets 数据库落盘到 filesDir/mihomo
 * - Clash RESTful API（127.0.0.1:9090）：热重载、策略组查询、节点切换、延迟测速
 * - 订阅拉取 + SnakeYAML 合法性校验 + 多订阅保存
 */
object ProxyManager {

    private const val API = "http://127.0.0.1:9090"
    private const val DELAY_TEST_URL = "http://www.gstatic.com/generate_204"
    private const val MAX_LOG_LINES = 400

    private lateinit var appContext: Context

    lateinit var coreDir: File
        private set
    private val configFile: File get() = File(coreDir, "config.yaml")

    private var process: Process? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(CoreState.STOPPED)
    val state: StateFlow<CoreState> = _state

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs
    private val logBuffer = ArrayDeque<String>()

    var lastError: String? = null
        private set

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // 收紧空闲连接池，减少后台空闲线程与 socket 占用
            .connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
            .build()
    }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ------------------------------------------------------------------ 初始化

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        coreDir = File(appContext.filesDir, "mihomo").apply { mkdirs() }

        val mmdb = File(coreDir, "Country.mmdb")
        if (!mmdb.exists()) {
            appContext.assets.open("Country.mmdb").use { input ->
                mmdb.outputStream().use { input.copyTo(it) }
            }
        }
        val geosite = File(coreDir, "geosite.dat")
        if (!geosite.exists()) {
            runCatching {
                appContext.assets.open("geosite.dat").use { input ->
                    geosite.outputStream().use { input.copyTo(it) }
                }
            }
        }
        if (!configFile.exists()) {
            appContext.assets.open("default_config.yaml").use { input ->
                configFile.outputStream().use { input.copyTo(it) }
            }
        }
        ensureConfigHealthy()
    }

    /**
     * 配置自愈：仅在 config.yaml 丢失/为空时才从备份恢复。
     * 绝不基于 YAML 解析结果覆盖已有配置——避免对订阅内容的误判导致配置被重置。
     */
    private fun ensureConfigHealthy() {
        val bak = File(coreDir, "config.yaml.bak")
        val configOk = configFile.exists() && configFile.length() >= 10
        val bakOk = bak.exists() && bak.length() >= 10
        when {
            configOk -> Unit
            bakOk -> {
                runCatching { bak.copyTo(configFile, overwrite = true) }
                appendLog("[ProxyManager] config.yaml 丢失，已从备份恢复 (${bak.length()} B)")
            }
            else -> runCatching {
                appContext.assets.open("default_config.yaml").use { input ->
                    configFile.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    /** 清理上次会话残留的孤儿内核进程（进程被系统直接杀死时 destroyForcibly 来不及执行）。 */
    private fun killStaleCores() {
        val myPid = android.os.Process.myPid()
        val marker = File(appContext.applicationInfo.nativeLibraryDir, "libmihomo.so").absolutePath
        runCatching {
            File("/proc").listFiles()?.forEach { dir ->
                val pid = dir.name.toIntOrNull() ?: return@forEach
                if (pid == myPid) return@forEach
                val cmd = runCatching { File(dir, "cmdline").readText() }.getOrNull() ?: return@forEach
                if (cmd.contains(marker)) {
                    runCatching { android.os.Process.killProcess(pid) }
                    appendLog("[ProxyManager] 已清理残留内核进程 pid=$pid")
                }
            }
        }
    }

    // ------------------------------------------------------------------ 进程管理

    /** 启动内核并等待 external-controller 就绪。清理残留进程 + 失败自动重试一次。 */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (_state.value == CoreState.RUNNING && pingVersion()) return@withContext true
        shutdown()
        killStaleCores()
        _state.value = CoreState.STARTING
        appendLog("[ProxyManager] 启动内核… 配置: ${configFile.absolutePath} (${configFile.length()} B)")
        val bin = File(appContext.applicationInfo.nativeLibraryDir, "libmihomo.so")
        if (!bin.exists()) {
            lastError = "未找到内核文件: ${bin.absolutePath}"
            _state.value = CoreState.ERROR
            return@withContext false
        }
        runCatching { bin.setExecutable(true) }

        repeat(2) { attempt ->
            try {
                val pb = ProcessBuilder(bin.absolutePath, "-d", coreDir.absolutePath)
                    .directory(coreDir)
                    .redirectErrorStream(true)
                val p = pb.start()
                process = p
                scope.launch { readLogs(p) }

                val deadline = System.currentTimeMillis() + 20_000
                while (System.currentTimeMillis() < deadline) {
                    if (!p.isAlive) {
                        appendLog("[ProxyManager] 内核进程提前退出（第 ${attempt + 1} 次尝试）")
                        break
                    }
                    if (pingVersion()) {
                        lastError = null
                        _state.value = CoreState.RUNNING
                        appendLog("[ProxyManager] 内核 API 已就绪，已加载保存的配置")
                        runCatching { configFile.copyTo(File(coreDir, "config.yaml.bak"), overwrite = true) }
                        return@withContext true
                    }
                    delay(300)
                }
                runCatching { p.destroyForcibly() }
                if (attempt == 0) {
                    appendLog("[ProxyManager] 首次启动失败，清理后 1 秒重试")
                    killStaleCores()
                    delay(1000)
                }
            } catch (e: Exception) {
                appendLog("[ProxyManager] 启动异常: ${e.message}")
                if (attempt == 0) delay(1000)
            }
        }
        lastError = "内核启动失败（已重试）: " +
            synchronized(logBuffer) { logBuffer.takeLast(3).joinToString(" | ") }
        _state.value = CoreState.ERROR
        false
    }

    /** 同步销毁内核进程（onTaskRemoved / onDestroy 中调用）。 */
    fun shutdown() {
        val p = process
        process = null
        if (p != null) {
            runCatching { p.destroyForcibly() }
            runCatching { p.waitFor(2, TimeUnit.SECONDS) }
            appendLog("[ProxyManager] 内核进程已销毁")
        }
        _state.value = CoreState.STOPPED
    }

    private var lastReviveAt = 0L

    private fun readLogs(p: Process) {
        try {
            p.inputStream.bufferedReader().forEachLine { appendLog(it) }
        } catch (_: Exception) {
        }
        val exit = runCatching { p.exitValue() }.getOrNull()
        appendLog("[ProxyManager] 内核输出流结束 exitCode=$exit")
        if (process === p) {
            process = null
            val wasRunning = _state.value == CoreState.RUNNING
            if (wasRunning || _state.value == CoreState.STARTING) {
                _state.value = CoreState.STOPPED
            }
            // 内核在运行中被系统查杀（Android 12+ 幻影进程清理/厂商省电）：
            // 应用仍在前台时自动复活，冷却 5 秒防止循环重启
            if (wasRunning && isProxyEnabled() &&
                System.currentTimeMillis() - lastReviveAt > 5000 &&
                ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                lastReviveAt = System.currentTimeMillis()
                appendLog("[ProxyManager] 检测到内核被系统回收，1.5 秒后自动重启")
                scope.launch {
                    delay(1500)
                    if (_state.value == CoreState.STOPPED && isProxyEnabled()) start()
                }
            }
        }
    }

    private fun appendLog(line: String) {
        synchronized(logBuffer) {
            logBuffer.addLast(line)
            while (logBuffer.size > MAX_LOG_LINES) logBuffer.removeFirst()
            _logs.value = logBuffer.toList()
        }
    }

    // ------------------------------------------------------------------ Clash API

    private fun apiGet(path: String): String? {
        val req = Request.Builder().url(API + path).get().build()
        return runCatching {
            client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
    }

    private fun pingVersion(): Boolean = apiGet("/version") != null

    suspend fun version(): String? = withContext(Dispatchers.IO) {
        apiGet("/version")?.let { runCatching { JSONObject(it).optString("version") }.getOrNull() }
    }

    /** 返回策略组列表 + 各节点最近一次历史延迟 + 节点协议类型。 */
    suspend fun fetchProxies(): ProxySnapshot = withContext(Dispatchers.IO) {
        val raw = apiGet("/proxies")
            ?: return@withContext ProxySnapshot(emptyList(), emptyMap(), emptyMap())
        val groups = mutableListOf<ProxyGroup>()
        val delays = mutableMapOf<String, Int>()
        val types = mutableMapOf<String, String>()
        runCatching {
            val obj = JSONObject(raw).getJSONObject("proxies")
            for (key in obj.keys()) {
                val item = obj.getJSONObject(key)
                val type = item.optString("type")
                types[key] = type
                val history = item.optJSONArray("history")
                if (history != null && history.length() > 0) {
                    val d = history.getJSONObject(history.length() - 1).optInt("delay", 0)
                    if (d > 0) delays[key] = d
                }
                if (type == "Selector" || type == "URLTest" || type == "Fallback" || type == "LoadBalance") {
                    val allArr = item.optJSONArray("all") ?: JSONArray()
                    val all = (0 until allArr.length()).map { allArr.getString(it) }
                    groups += ProxyGroup(key, type, item.optString("now"), all)
                }
            }
        }
        ProxySnapshot(groups, delays, types)
    }

    /** PUT /proxies/{group} 切换节点。 */
    suspend fun selectNode(group: String, node: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("name", node).toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$API/proxies/${Uri.encode(group)}").put(body).build()
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    /** GET /proxies/{name}/delay 单节点测速，超时/失败返回 null。 */
    suspend fun testDelay(name: String): Int? = withContext(Dispatchers.IO) {
        val path = "/proxies/${Uri.encode(name)}/delay?timeout=5000&url=${Uri.encode(DELAY_TEST_URL)}"
        val raw = apiGet(path) ?: return@withContext null
        runCatching { JSONObject(raw).optInt("delay", 0).takeIf { it > 0 } }.getOrNull()
    }

    /** PUT /configs?force=true 热重载当前 config.yaml。 */
    suspend fun reloadConfig(): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("path", configFile.absolutePath).toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$API/configs?force=true").put(body).build()
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ 配置与订阅

    fun currentConfigText(): String = runCatching { configFile.readText() }.getOrDefault("")

    /** 校验 + 落盘 + 热重载（未运行则冷启动）。返回 null 表示成功，否则为错误信息。 */
    suspend fun saveConfigAndApply(text: String): String? = withContext(Dispatchers.IO) {
        validateYaml(text)?.let { return@withContext it }
        val patched = ensureBasics(text)
        // 原子写入：先写临时文件，备份旧配置，再改名覆盖，避免写入中断导致配置损坏
        runCatching {
            val tmp = File(coreDir, "config.yaml.tmp")
            tmp.writeText(patched)
            if (configFile.exists() && configFile.length() > 10) {
                configFile.copyTo(File(coreDir, "config.yaml.bak"), overwrite = true)
            }
            if (!tmp.renameTo(configFile)) configFile.writeText(patched)
        }.onFailure { return@withContext "写入配置失败: ${it.message}" }
        val ok = if (_state.value == CoreState.RUNNING) reloadConfig() else start()
        if (ok) setMode(preferredMode())
        if (ok) null else "配置已保存，但内核重载失败: ${lastError ?: "请查看内核日志"}"
    }

    /** 拉取订阅 -> 校验 -> 保存 -> 热重载。返回 null 表示成功。 */
    suspend fun updateSubscription(url: String): String? = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext "订阅链接必须以 http(s):// 开头"
        }
        val req = Request.Builder()
            .url(trimmed)
            .header("User-Agent", "clash-verge/v1.7.7")
            .build()
        var userinfo: String?
        var disposition: String?
        val text = try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext "订阅拉取失败: HTTP ${resp.code}"
                userinfo = resp.header("subscription-userinfo")
                disposition = resp.header("content-disposition")
                resp.body?.string() ?: return@withContext "订阅内容为空"
            }
        } catch (e: Exception) {
            return@withContext "订阅拉取失败: ${e.message}"
        }
        val err = saveConfigAndApply(text)
        if (err != null) return@withContext err
        // 保存配置档案（YAML 副本 + 流量/到期/更新时间元数据），供配置卡片展示与离线切换
        runCatching {
            profileFile(trimmed).apply { parentFile?.mkdirs() }.writeText(text)
            val meta = metaObj()
            val o = meta.optJSONObject(trimmed) ?: JSONObject()
            o.put("name", deriveName(trimmed, disposition))
            parseUserinfo(userinfo)?.let {
                o.put("up", it[0]); o.put("down", it[1])
                o.put("total", it[2]); o.put("expire", it[3])
            }
            o.put("updated", System.currentTimeMillis())
            meta.put(trimmed, o)
            saveMeta(meta)
            prefs().edit().putString("active_sub", trimmed).apply()
        }
        null
    }

    /** 应用已保存的配置档案；本地无副本时自动重新拉取。返回 null 表示成功。 */
    suspend fun applyProfile(url: String): String? = withContext(Dispatchers.IO) {
        val f = profileFile(url)
        if (!f.exists() || f.length() <= 10) return@withContext updateSubscription(url)
        val err = saveConfigAndApply(f.readText())
        if (err == null) prefs().edit().putString("active_sub", url).apply()
        err
    }

    fun listProfiles(): List<ProfileInfo> {
        val meta = metaObj()
        val active = activeSub()
        return listSubscriptions().map { url ->
            val o = meta.optJSONObject(url)
            ProfileInfo(
                url = url,
                name = o?.optString("name")?.takeIf { it.isNotBlank() } ?: deriveName(url, null),
                upload = o?.optLong("up") ?: 0L,
                download = o?.optLong("down") ?: 0L,
                total = o?.optLong("total") ?: 0L,
                expire = o?.optLong("expire") ?: 0L,
                updated = o?.optLong("updated") ?: 0L,
                active = url == active
            )
        }
    }

    fun activeSub(): String =
        prefs().getString("active_sub", null) ?: (listSubscriptions().firstOrNull() ?: "")

    /** 手动编辑 YAML 后调用：当前配置不再对应任何订阅档案。 */
    fun markCustomConfig() {
        prefs().edit().putString("active_sub", "").apply()
    }

    private fun profileFile(url: String): File =
        File(coreDir, "profiles/" + java.lang.Integer.toHexString(url.hashCode()) + ".yaml")

    private fun metaObj(): JSONObject =
        runCatching { JSONObject(prefs().getString("sub_meta", "{}") ?: "{}") }.getOrDefault(JSONObject())

    private fun saveMeta(o: JSONObject) {
        prefs().edit().putString("sub_meta", o.toString()).apply()
    }

    /** 解析 subscription-userinfo 响应头：upload/download/total/expire。 */
    private fun parseUserinfo(h: String?): LongArray? {
        if (h.isNullOrBlank()) return null
        val m = HashMap<String, Long>()
        h.split(";").forEach { part ->
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2) kv[1].trim().toLongOrNull()?.let { m[kv[0].trim()] = it }
        }
        if (m.isEmpty()) return null
        return longArrayOf(m["upload"] ?: 0L, m["download"] ?: 0L, m["total"] ?: 0L, m["expire"] ?: 0L)
    }

    /** 档案名称：优先订阅响应头文件名，其次 URL 路径文件名，最后域名。 */
    private fun deriveName(url: String, disposition: String?): String {
        disposition?.let { d ->
            Regex("filename\\*=(?:UTF-8''|utf-8'')([^;]+)", RegexOption.IGNORE_CASE)
                .find(d)?.groupValues?.get(1)?.let {
                    return runCatching { Uri.decode(it.trim().trim('"')) }.getOrDefault(it)
                }
            Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
                .find(d)?.groupValues?.get(1)?.let { return Uri.decode(it.trim()) }
        }
        val seg = runCatching { Uri.parse(url).lastPathSegment }.getOrNull()
        if (!seg.isNullOrBlank() && seg.contains('.')) return Uri.decode(seg)
        return runCatching { Uri.parse(url).host }.getOrNull() ?: url
    }

    internal fun validateYaml(text: String): String? {
        return try {
            val root = Yaml().load<Any?>(text)
            when {
                root !is Map<*, *> -> "配置不是合法的 YAML 映射"
                !root.containsKey("proxies") && !root.containsKey("proxy-providers") ->
                    "配置缺少 proxies 或 proxy-providers 字段，请确认是 Clash 订阅"
                else -> loopbackError(root)
            }
        } catch (e: Exception) {
            "YAML 解析失败: ${e.message?.take(200)}"
        }
    }

    /** 拒绝指向本机混合端口的代理节点，避免导入后形成代理自环。 */
    private fun loopbackError(root: Map<*, *>): String? {
        val proxies = root["proxies"] as? List<*> ?: return null
        for (p in proxies) {
            val m = p as? Map<*, *> ?: continue
            val server = m["server"]?.toString()?.lowercase() ?: continue
            val port = (m["port"] as? Number)?.toInt() ?: continue
            if (port == 7890 && server in setOf("127.0.0.1", "localhost", "::1", "0.0.0.0")) {
                return "含回环代理节点 ${m["name"] ?: server}（指向本机 7890，会自环），已拒绝"
            }
        }
        return null
    }

    /** 强制覆盖本地运行必需的基础字段，避免订阅配置破坏 WebView 代理与 API 通道。 */
    internal fun ensureBasics(yamlText: String): String {
        var text = yamlText
        val required = linkedMapOf(
            "mixed-port" to "7890",
            "port" to "0",
            "socks-port" to "0",
            "allow-lan" to "false",
            "external-controller" to "127.0.0.1:9090",
            "secret" to "\"\""
        )
        val header = StringBuilder()
        for ((k, v) in required) {
            val re = Regex("(?m)^$k:.*$")
            if (re.containsMatchIn(text)) {
                text = re.replace(text, "$k: $v")
            } else {
                header.append("$k: $v\n")
            }
        }
        return header.toString() + text
    }

    // ------------------------------------------------------------------ 运行模式与应用设置

    /** GET /configs 读取当前运行模式（global/rule/direct）。 */
    suspend fun fetchMode(): String? = withContext(Dispatchers.IO) {
        apiGet("/configs")?.let { runCatching { JSONObject(it).optString("mode") }.getOrNull() }
    }

    /** PATCH /configs 切换运行模式，成功后持久化为用户偏好。 */
    suspend fun setMode(mode: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("mode", mode).toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$API/configs").patch(body).build()
        val ok = runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
        if (ok) {
            setPreferredMode(mode)
            // 全局模式语义：所有流量必须经代理转发。若 GLOBAL 组当前选中 DIRECT，
            // 自动改选第一个真实节点，避免"全局"名不副实
            if (mode == "global") ensureGlobalRoute()
        }
        ok
    }

    /** 全局模式下确保 GLOBAL 组选中真实代理节点（而非 DIRECT/策略组）。 */
    private suspend fun ensureGlobalRoute() {
        val snap = fetchProxies()
        val global = snap.groups.firstOrNull { it.name == "GLOBAL" } ?: return
        if (global.now != "DIRECT") return
        val skip = setOf("DIRECT", "REJECT", "PASS")
        val groupTypes = setOf("Selector", "URLTest", "Fallback", "LoadBalance", "Relay")
        val candidate = global.all.firstOrNull { name ->
            name !in skip && (snap.types[name] ?: "") !in groupTypes
        } ?: return
        if (selectNode("GLOBAL", candidate)) {
            appendLog("[ProxyManager] 全局模式：GLOBAL 已自动选择节点 $candidate")
        }
    }

    fun isProxyEnabled(): Boolean = prefs().getBoolean("proxy_enabled", true)
    fun setProxyEnabled(v: Boolean) = prefs().edit().putBoolean("proxy_enabled", v).apply()

    fun preferredMode(): String = prefs().getString("core_mode", "rule") ?: "rule"
    fun setPreferredMode(m: String) = prefs().edit().putString("core_mode", m).apply()

    fun isDesktopUa(): Boolean = prefs().getBoolean("ua_desktop", false)
    fun setDesktopUa(v: Boolean) = prefs().edit().putBoolean("ua_desktop", v).apply()

    /** 网页内打开新页面（target=_blank / window.open）是否默认新建标签。 */
    fun isOpenNewTab(): Boolean = prefs().getBoolean("open_new_tab", true)
    fun setOpenNewTab(v: Boolean) = prefs().edit().putBoolean("open_new_tab", v).apply()

    /** 外观模式：light / dark / system */
    fun themeMode(): String = prefs().getString("theme_mode", "system") ?: "system"
    fun setThemeMode(m: String) = prefs().edit().putString("theme_mode", m).apply()

    // ------------------------------------------------------------------ 多订阅保存

    private fun prefs() = appContext.getSharedPreferences("proxy_prefs", Context.MODE_PRIVATE)

    fun listSubscriptions(): List<String> {
        val arr = JSONArray(prefs().getString("sub_urls", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun addSubscription(url: String) {
        val list = listSubscriptions().toMutableList()
        list.remove(url)
        list.add(0, url)
        prefs().edit().putString("sub_urls", JSONArray(list.take(10)).toString()).apply()
    }

    fun removeSubscription(url: String) {
        val list = listSubscriptions().toMutableList()
        list.remove(url)
        prefs().edit().putString("sub_urls", JSONArray(list).toString()).apply()
        val meta = metaObj()
        meta.remove(url)
        saveMeta(meta)
        runCatching { profileFile(url).delete() }
        if (prefs().getString("active_sub", null) == url) {
            prefs().edit().putString("active_sub", list.firstOrNull() ?: "").apply()
        }
    }
}