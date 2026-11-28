package com.jomo.clashbrowser

import android.content.Context
import android.os.Build
import android.webkit.WebView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地崩溃诊断 + 安全模式（全程离线，不上传任何数据）。
 *
 * 背景：少数设备的系统 WebView（或某个 WebView 版本）在"首次创建/加载网页视图"时会直接
 * 让应用进程崩溃，且不产生 Java 异常对话框。此前的实现对此没有任何兜底，用户看到的就是"闪退"。
 *
 * 这里做三件事：
 * 1. 把未捕获异常的堆栈、以及"网页视图会话进行中"的标记写入应用私有目录；
 * 2. 下次启动时可由界面展示/复制，便于用户反馈；
 * 3. 若上次运行正好死在网页视图会话里，则本次启动进入"安全模式"：跳过给 WebView 设置代理
 *    （已知在个别 WebView 版本上会导致崩溃），先保证能直连打开网页；用户可手动重试代理。
 */
object Diagnostics {

    private const val CRASH_FILE = "diag_last_crash.txt"
    private const val WEBVIEW_MARKER = "diag_webview_session.marker"
    private const val PREFS = "diag_prefs"
    private const val KEY_SAFE_MODE = "safe_mode"
    private const val MAX_CHARS = 20_000

    private var appContext: Context? = null
    private var handlerInstalled = false

    /** 本次运行是否跳过"给 WebView 设置代理"。 */
    @Volatile
    var safeMode: Boolean = false
        private set

    /** 上次运行是否在网页视图会话中被杀（原生崩溃/进程被杀都能留下这个痕迹）。 */
    @Volatile
    var lastRunCrashedInWebView: Boolean = false
        private set

    fun init(context: Context) {
        if (handlerInstalled) return
        val ctx = context.applicationContext
        appContext = ctx
        handlerInstalled = true
        runCatching { ctx.filesDir.mkdirs() }
        // 上次进程若死在"打开网页"过程中，标记文件会残留 → 本次进入安全模式
        val marker = File(ctx.filesDir, WEBVIEW_MARKER)
        lastRunCrashedInWebView = marker.exists()
        runCatching { marker.delete() }
        if (lastRunCrashedInWebView) {
            runCatching { prefs(ctx).edit().putBoolean(KEY_SAFE_MODE, true).apply() }
        }
        safeMode = runCatching { prefs(ctx).getBoolean(KEY_SAFE_MODE, false) }.getOrDefault(false)
        installCrashHandler()
    }

    fun enterSafeMode() {
        safeMode = true
        appContext?.let { ctx -> runCatching { prefs(ctx).edit().putBoolean(KEY_SAFE_MODE, true).apply() } }
    }

    fun leaveSafeMode() {
        safeMode = false
        appContext?.let { ctx -> runCatching { prefs(ctx).edit().putBoolean(KEY_SAFE_MODE, false).apply() } }
    }

    /** 网页视图会话开始（创建 WebView 前调用）；正常加载完成后由 [webViewSessionEnded] 清除。 */
    fun webViewSessionStarted() {
        appContext?.let { ctx ->
            runCatching {
                File(ctx.filesDir, WEBVIEW_MARKER).writeText(
                    "${stamp()} · app ${BuildConfig.VERSION_NAME}\n${safeDeviceReport()}"
                )
            }
        }
    }

    /** 首次加载完成，说明网页视图链路可用。 */
    fun webViewSessionEnded() {
        appContext?.let { ctx -> runCatching { File(ctx.filesDir, WEBVIEW_MARKER).delete() } }
    }

    fun recordCaught(tag: String, t: Throwable) {
        appContext?.let { ctx ->
            runCatching { File(ctx.filesDir, CRASH_FILE).writeText(buildReport("caught/$tag", t)) }
        }
    }

    fun lastCrashReport(): String? = appContext?.let { ctx ->
        runCatching {
            File(ctx.filesDir, CRASH_FILE).takeIf { it.exists() && it.length() > 0 }?.readText()
        }.getOrNull()
    }

    fun clearCrashReport() {
        appContext?.let { ctx -> runCatching { File(ctx.filesDir, CRASH_FILE).delete() } }
    }

    /**
     * 设备与 WebView 环境信息，用户复制即可反馈。
     * [includeWebView] 为 false 时不查询 WebView 包信息——启动阶段/写崩溃标记时必须用这种模式，
     * 因为查询 WebView 包可能触发 WebView provider 初始化，在坏掉的 WebView 上反而会提前崩溃。
     */
    fun deviceReport(includeWebView: Boolean = true): String = buildString {
        appendLine(safeDeviceReport())
        if (includeWebView) appendLine("webview: ${webViewPackageInfo()}")
        append("pageSize: ").append(if (Build.VERSION.SDK_INT >= 35) "16KB 或 4KB（由系统决定）" else "4KB")
    }

    private fun safeDeviceReport(): String = buildString {
        appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        append("device: ${Build.MANUFACTURER} ${Build.MODEL} / ${Build.DEVICE}")
    }

    fun webViewPackageInfo(): String = runCatching {
        val pkg = WebView.getCurrentWebViewPackage() ?: return@runCatching "系统未安装可用的 WebView"
        "${pkg.packageName} ${pkg.versionName}"
    }.getOrDefault("读取失败")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                appContext?.let { ctx ->
                    File(ctx.filesDir, CRASH_FILE).writeText(buildReport("uncaught on ${thread.name}", throwable))
                }
            }
            previous?.uncaughtException(thread, throwable)
                ?: android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun buildReport(kind: String, t: Throwable?): String {
        val sb = StringBuilder()
        sb.appendLine("# ClashBrowser crash report")
        sb.appendLine("time: ${stamp()}")
        sb.appendLine("kind: $kind")
        sb.appendLine()
        sb.appendLine(deviceReport())
        sb.appendLine()
        if (t != null) {
            sb.appendLine("## stack")
            sb.appendLine(t.stackTraceToString().take(MAX_CHARS))
            var cause = t.cause
            var depth = 0
            while (cause != null && depth < 5) {
                sb.appendLine("## cause ${++depth}: ${cause.javaClass.name}: ${cause.message}")
                sb.appendLine(cause.stackTraceToString().take(MAX_CHARS / 2))
                cause = cause.cause
            }
        }
        return sb.toString().take(MAX_CHARS)
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
}
