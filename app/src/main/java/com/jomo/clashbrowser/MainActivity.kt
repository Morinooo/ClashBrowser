package com.jomo.clashbrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    // 书签导出：SAF 创建文档，写入 Netscape 书签 HTML
    private val exportBookmarks =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
            if (uri == null) return@registerForActivityResult
            val ok = runCatching {
                val out = contentResolver.openOutputStream(uri) ?: error("无法打开文件")
                out.use { it.write(BookmarkStore.exportHtml().toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            Toast.makeText(this, if (ok) "书签已导出" else "导出失败", Toast.LENGTH_SHORT).show()
        }

    // 书签导入：SAF 选择文件（Via/Chrome/Firefox 等导出的书签 HTML），解析合并
    private val importBookmarks =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val input = contentResolver.openInputStream(uri) ?: error("无法读取文件")
                    val bytes = input.use { it.readBytes() }
                    if (bytes.size > 5_000_000) error("书签文件过大（超过 5MB），已拒绝")
                    BookmarkStore.importHtml(bytes.toString(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) {
                    result.onSuccess {
                        Toast.makeText(
                            this@MainActivity,
                            "导入 ${it[0]} 条书签、${it[2]} 个文件夹，跳过 ${it[1]} 条重复/无效",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure {
                        Toast.makeText(this@MainActivity, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    // 网页文件上传：系统文件选择器结果回传给 WebView 的 FilePathCallback
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val pickFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            fileChooserCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
            )
            fileChooserCallback = null
        }

    fun launchFileChooser(params: WebChromeClient.FileChooserParams, cb: ValueCallback<Array<Uri>>) {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = cb
        runCatching { pickFile.launch(params.createIntent()) }.onFailure {
            fileChooserCallback = null
            cb.onReceiveValue(null)
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    // 网页定位：系统运行时权限结果回传 WebView；拒绝时同步撤销该来源的"允许"记忆
    private var geoPending: Pair<String, android.webkit.GeolocationPermissions.Callback>? = null
    private val geoPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val pending = geoPending ?: return@registerForActivityResult
            geoPending = null
            val granted = result.values.any { it }
            if (!granted) GeoStore.remember(pending.first, false)
            pending.second.invoke(pending.first, granted, false)
        }

    fun requestGeoPermission(origin: String, cb: android.webkit.GeolocationPermissions.Callback) {
        val granted = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            cb.invoke(origin, true, false)
        } else {
            geoPending = origin to cb
            geoPermission.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 崩溃诊断/安全模式要尽早初始化，赶在任何 WebView 相关代码之前
        Diagnostics.init(this)
        ProxyManager.init(this)
        HistoryStore.init(this)
        BookmarkStore.init(this)
        SessionStore.init(this)
        HomeStore.init(this)
        AdBlocker.init(this)
        DownloadStore.init(this)
        GeoStore.init(this)
        // 按持久化外观模式设置窗口主题（影响窗口背景与 WebView 的深浅色判定）
        val mode = ProxyManager.themeMode()
        val initialDark = mode == "dark" || (mode == "system" &&
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        setTheme(if (initialDark) R.style.Theme_ClashBrowser_Dark else R.style.Theme_ClashBrowser)
        startService(Intent(this, CoreKeeperService::class.java))
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                ProxyManager.shutdown()
            }
        })
        setContent {
            var themeMode by remember { mutableStateOf(ProxyManager.themeMode()) }
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            val view = LocalView.current
            SideEffect {
                (view.context as? Activity)?.window?.let { window ->
                    val bar = if (dark) android.graphics.Color.rgb(18, 18, 18)
                    else android.graphics.Color.WHITE
                    window.statusBarColor = bar
                    window.navigationBarColor = bar
                    WindowInsetsControllerCompat(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
            }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BrowserScreen(
                        dark = dark,
                        themeMode = themeMode,
                        onThemeModeChange = {
                            ProxyManager.setThemeMode(it)
                            themeMode = it
                        },
                        onImportBookmarks = { importBookmarks.launch(arrayOf("*/*")) },
                        onExportBookmarks = {
                            val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                            exportBookmarks.launch("ClashBrowser_书签_$day.html")
                        },
                        onFileChooser = { p, c -> launchFileChooser(p, c) },
                        onGeoAllow = { o, c -> requestGeoPermission(o, c) }
                    )
                }
            }
        }
    }
    override fun onDestroy() {
        // 返回键正常退出视为干净退出；进程被直接杀死时不会走到这里
        if (isFinishing) {
            SessionStore.markCleanExit()
            Diagnostics.webViewSessionEnded()
        }
        super.onDestroy()
    }
}

/** 单个标签页的可观察状态。WebView 实例懒创建、切页不销毁。 */
class TabState(val id: Int, val incognito: Boolean = false) {
    var webView: WebView? = null
    var url by mutableStateOf(HOME_MARKER)
    var title by mutableStateOf("新标签页")
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var started by mutableStateOf(false)
    var navVersion by mutableIntStateOf(0)
    var fullscreenView: View? = null
    var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    var ctxJson: String? = null
    var ctxTime: Long = 0L
}

/** 本地主页标记：标签 url 等于此值时显示 Compose 主页而非 WebView。 */
const val HOME_MARKER = "about:home"

/** 阅读模式提取结果。 */
private data class ReaderDoc(val title: String, val paras: List<String>)

/** 桌面模式：覆写 viewport meta 为桌面宽度，配合 wideViewPort/overview 得到真桌面布局。 */
private const val DESKTOP_VIEWPORT_SCRIPT = """
(function () {
  try {
    var m = document.querySelector('meta[name="viewport"]');
    if (!m) {
      m = document.createElement('meta');
      m.name = 'viewport';
      document.head.appendChild(m);
    }
    m.content = 'width=1024, initial-scale=1';
  } catch (e) {}
})();
"""

/** 阅读模式：克隆 DOM 去除噪声节点后提取正文段落（不改动活页面）。 */
private const val READER_SCRIPT = """
(function () {
  try {
    var d = document.cloneNode(true);
    var noise = d.querySelectorAll('script,style,noscript,iframe,nav,aside,footer,header,form,button,svg,link,meta');
    for (var i = 0; i < noise.length; i++) noise[i].parentNode.removeChild(noise[i]);
    var root = d.querySelector('article') || d.querySelector('[role="main"]') || d.querySelector('main') || d.body;
    var paras = [];
    if (root) {
      var nodes = root.querySelectorAll('p,h1,h2,h3,h4,h5,li,pre,blockquote');
      for (var j = 0; j < nodes.length && paras.length < 600; j++) {
        var s = (nodes[j].textContent || '').trim();
        if (s.length > 1) paras.push(s.slice(0, 2000));
      }
      if (paras.length < 3) {
        paras = ((root.textContent || '').split(/\n{2,}/))
          .map(function (x) { return x.trim(); })
          .filter(function (x) { return x.length > 1; })
          .slice(0, 600);
      }
    }
    return { title: document.title || '', paras: paras };
  } catch (e) {
    return { title: '', paras: [] };
  }
})()
"""

/**
 * WebView 的 EME 探测在权限未被宿主响应时会永久挂起，播放器超时报
 * "浏览器不支持这种媒体类型/请开启 DRM"。
 * v1.6.5：宿主已在 onPermissionRequest 放行 RESOURCE_PROTECTED_MEDIA_ID，
 * 故此处改为"限时等待"：4 秒内系统 Widevine 正常响应则原样透传；
 * 仍挂起（无 CDM 等环境）才快速拒绝，让播放器回退非加密流。
 */
private const val EME_FALLBACK_SCRIPT = """
(function () {
  if (location.protocol !== 'http:' && location.protocol !== 'https:') return;
  try {
    function race(orig, args, ms) {
      return new Promise(function (resolve, reject) {
        var done = false;
        var timer = setTimeout(function () {
          if (!done) { done = true; reject(new DOMException('EME probe timeout', 'NotSupportedError')); }
        }, ms);
        function settle(fn, v) { if (!done) { done = true; clearTimeout(timer); fn(v); } }
        try {
          orig.apply(null, args).then(function (v) { settle(resolve, v); }, function (e) { settle(reject, e); });
        } catch (e) { settle(reject, e); }
      });
    }
    if (navigator.requestMediaKeySystemAccess) {
      var origAccess = navigator.requestMediaKeySystemAccess.bind(navigator);
      navigator.requestMediaKeySystemAccess = function (ks, cfg) {
        return race(origAccess, [ks, cfg], 4000);
      };
    }
    var mc = navigator.mediaCapabilities;
    if (mc && mc.decodingInfo) {
      var origInfo = mc.decodingInfo.bind(mc);
      mc.decodingInfo = function (cfg) {
        if (cfg && cfg.keySystemConfiguration) return race(origInfo, [cfg], 4000);
        return origInfo(cfg);
      };
    }
  } catch (e) {}
})();
"""

/** 标准 Android Chrome 移动标识：去掉内嵌 WebView 的 "; wv)" 与 "Version/4.0" 标记。 */
private fun mobileUa(context: android.content.Context): String =
    WebSettings.getDefaultUserAgent(context)
        .replace("; wv)", "")
        .replace("Version/4.0 ", "")

/** 触摸即捕获命中元素上下文（链接/图片/可点击标记），供长按菜单使用。 */
private const val CTX_CAPTURE_SCRIPT = """
(function () {
  if (location.protocol !== 'http:' && location.protocol !== 'https:') return;
  try {
    document.addEventListener('touchstart', function (e) {
      try {
        var el = e.target;
        var a = el && el.closest ? el.closest('a') : null;
        var img = el && el.closest ? el.closest('img') : null;
        var click = !!(el && el.closest && el.closest('a,img,button,[role="button"],input,select,textarea,label,[onclick]'));
        var o = {
          t: Date.now(),
          href: a ? a.href : '',
          text: a ? (a.textContent || '').trim().slice(0, 200) : '',
          src: img ? (img.currentSrc || img.src || '') : '',
          alt: img ? (img.alt || '') : '',
          click: click
        };
        if (window.CbCtx) window.CbCtx.post(JSON.stringify(o));
      } catch (err) {}
    }, true);
  } catch (e) {}
})();
"""

private fun copyToClipboard(context: android.content.Context, text: String) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
    cm.setPrimaryClip(android.content.ClipData.newPlainText("ClashBrowser", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun unwrapActivity(context: android.content.Context): android.app.Activity? {
    var c: android.content.Context? = context
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private fun isHomeUrl(url: String): Boolean =
    url == HOME_MARKER || url.isBlank()

/** 搜索引擎检索地址（纯函数，供单测）。 */
internal fun engineSearchUrl(engine: String, query: String): String {
    val q = URLEncoder.encode(query, "UTF-8")
    return when (engine) {
        "bing" -> "https://www.bing.com/search?q=$q"
        "baidu" -> "https://www.baidu.com/s?wd=$q"
        "duckduckgo" -> "https://duckduckgo.com/?q=$q"
        else -> "https://www.google.com/search?q=$q"
    }
}

internal fun toLoadUrl(input: String, engine: String = "google"): String {
    val t = input.trim()
    if (t.isEmpty()) return HOME_MARKER
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    if (!t.contains(' ') && t.contains('.')) return "https://$t"
    return engineSearchUrl(engine, t)
}

const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/** WebRTC 防泄露：document-start 禁用 RTCPeerConnection，避免 ICE 候选地址暴露代理后真实 IP。 */
const val WEBRTC_BLOCK_SCRIPT = """
(function(){
if (location.protocol !== 'http:' && location.protocol !== 'https:') return;
try{Object.defineProperty(window,'RTCPeerConnection',{value:undefined,writable:false,configurable:false});}
catch(e){window.RTCPeerConnection=undefined;}
window.webkitRTCPeerConnection=undefined;window.mozRTCPeerConnection=undefined;
})();
"""

/**
 * document-start 脚本的允许来源规则。
 *
 * WebView 只接受规范格式：全来源 `*`、精确来源（如 https://example.com）、
 * 子域来源（如 https:// 再跟 *.example.com）。绝不能把 scheme 通配符写成
 * `http://` 后面直接跟 `*` 的形式——v1.16.1 曾这样写，旧 WebView 不校验侥幸能用，
 * 但 WebView 150（Chromium 新版）会抛 IllegalArgumentException: allowedOriginRules … is invalid，
 * 导致创建 WebView 时闪退（v1.16.4 修复）。脚本内部各自做 `location.protocol` 守卫，
 * 因此这里用全来源也不会作用到 file 协议等非 http(s) 文档。
 */
internal val DOCUMENT_START_ORIGINS: Set<String> = setOf("*")

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private fun createWebView(
    context: android.content.Context,
    tab: TabState,
    onTouchDown: () -> Unit = {},
    onLongCtx: (CtxInfo) -> Unit = {},
    onWindowOpen: (android.os.Message) -> Boolean = { false },
    onFileChooser: (WebChromeClient.FileChooserParams, ValueCallback<Array<Uri>>) -> Unit = { _, cb -> cb.onReceiveValue(null) },
    onFindResult: (Int, Int) -> Unit = { _, _ -> },
    onGeoPrompt: (String, android.webkit.GeolocationPermissions.Callback) -> Unit = { o, cb -> cb.invoke(o, false, false) },
    onGeoHide: () -> Unit = {}
): WebView {
    // 标记"网页视图会话进行中"：若进程在此后异常消失（含原生崩溃），下次启动据此进入安全模式
    Diagnostics.webViewSessionStarted()
    val wv = SecureWebView(context)
    // 触摸网页内容（含空白处）即收起地址栏输入焦点；返回 false 不影响网页自身交互
    wv.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouchDown()
        false
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        val origins = DOCUMENT_START_ORIGINS
        runCatching { WebViewCompat.addDocumentStartJavaScript(wv, EME_FALLBACK_SCRIPT, origins) }
        runCatching { WebViewCompat.addDocumentStartJavaScript(wv, CTX_CAPTURE_SCRIPT, origins) }
        // WebRTC 防泄露：无痕强制、普通标签依开关，禁用 RTCPeerConnection 防 ICE 候选地址暴露真实 IP
        if (tab.incognito || ProxyManager.isWebRtcBlock()) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(wv, WEBRTC_BLOCK_SCRIPT, origins) }
        }
    }
    wv.addJavascriptInterface(object {
        @JavascriptInterface
        fun post(json: String) {
            tab.ctxJson = json
            tab.ctxTime = System.currentTimeMillis()
        }
    }, "CbCtx")
    // 长按可点击对象 -> 上下文菜单；纯文本长按保留系统选择行为
    wv.setOnLongClickListener {
        val ht = wv.hitTestResult
        // 可编辑节点（输入框/内容editable）放行 WebView 原生选择/全选/剪切/复制/粘贴菜单
        if (ht.type == WebView.HitTestResult.EDIT_TEXT_TYPE) return@setOnLongClickListener false
        val extra = ht.extra ?: ""
        var link = ""
        var image = ""
        var text = ""
        var alt = ""
        var clickable = ht.type != WebView.HitTestResult.UNKNOWN_TYPE
        when (ht.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> link = extra
            WebView.HitTestResult.IMAGE_TYPE -> image = extra
            else -> {}
        }
        val json = tab.ctxJson
        if (json != null && System.currentTimeMillis() - tab.ctxTime < 1500) {
            runCatching {
                val o = org.json.JSONObject(json)
                link = o.optString("href").ifBlank { link }
                image = o.optString("src").ifBlank { image }
                text = o.optString("text")
                alt = o.optString("alt")
                clickable = clickable || o.optBoolean("click")
            }
        }
        if (!clickable) return@setOnLongClickListener false
        onLongCtx(CtxInfo(link, text, image, alt, tab.url, tab.title))
        true
    }
    wv.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportMultipleWindows(ProxyManager.isOpenNewTab())
        javaScriptCanOpenWindowsAutomatically = true
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = if (tab.incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        userAgentString = if (ProxyManager.isDesktopUa()) DESKTOP_UA
        else mobileUa(context)
    }
    // 页内查找计数回传
    wv.setFindListener { ordinal, total, done ->
        if (done) onFindResult(ordinal, total)
    }
    // DRM 许可证与跨域 CDN 鉴权依赖 Cookie（含第三方 Cookie）
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(wv, true)
    }
    // 非图片下载接管：登记到下载管理器（进度/重试/打开/删除）
    wv.setDownloadListener { url, ua, disposition, mime, length ->
        DownloadStore.enqueue(url, ua, disposition, mime, length)
        Toast.makeText(
            context,
            "开始下载：${URLUtil.guessFileName(url, disposition, mime)}",
            Toast.LENGTH_SHORT
        ).show()
    }
    wv.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            if (AdBlocker.shouldBlock(url)) return AdBlocker.emptyResponse()
            SnifferStore.record(tab.id.toString(), url)
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme ?: return false
            // 只放行 http/https，拦截 intent:// 等外部跳转（无广告拉起）并告知用户
            val block = scheme != "http" && scheme != "https"
            if (block) {
                android.widget.Toast.makeText(
                    view.context, "已拦截外部跳转：$scheme", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return block
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            tab.isLoading = true
            if (url != null) tab.url = url
        }

        override fun onPageFinished(view: WebView, url: String?) {
            tab.isLoading = false
            tab.progress = 100
            tab.title = view.title?.takeIf { it.isNotBlank() } ?: url ?: "新标签页"
            if (url != null) tab.url = url
            tab.navVersion++
            // 首次加载完成说明网页视图链路可用，清掉"会话进行中"标记
            Diagnostics.webViewSessionEnded()
            if (url != null) {
                if (tab.incognito) IncognitoGuard.recordHost(url)
                else HistoryStore.record(url, view.title ?: "")
            }
            if (ProxyManager.isDesktopUa()) view.evaluateJavascript(DESKTOP_VIEWPORT_SCRIPT, null)
            (view as? SecureWebView)?.installPasswordWatch()
        }
    }
    wv.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
        }

        // 网页请求新窗口（target=_blank / window.open）：交给宿主新建标签承载
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message?
        ): Boolean {
            val msg = resultMsg ?: return false
            return onWindowOpen(msg)
        }

        // 网页文件上传（input type=file）：唤起系统文件选择器，支持多选
        override fun onShowFileChooser(
            view: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            onFileChooser(fileChooserParams, filePathCallback)
            return true
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) tab.title = title
        }

        // 网页定位请求：宿主弹来源确认框 + 系统运行时权限，决定按来源持久化
        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: android.webkit.GeolocationPermissions.Callback
        ) {
            onGeoPrompt(origin, callback)
        }

        override fun onGeolocationPermissionsHidePrompt() {
            onGeoHide()
        }

        // 放行网页受保护媒体标识请求（Widevine DRM 许可证初始化所需），主线程 grant
        override fun onPermissionRequest(request: PermissionRequest?) {
            val r = request ?: return
            if (r.resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    r.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
                }
            } else {
                r.deny()
            }
        }

        // 网页视频全屏：把播放器自定义视图挂到 Activity 内容层并隐藏系统栏
        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            val act = unwrapActivity(context) ?: return
            if (tab.fullscreenView != null) {
                callback?.onCustomViewHidden()
                return
            }
            tab.fullscreenView = view
            tab.fullscreenCallback = callback
            val content = act.findViewById<FrameLayout>(android.R.id.content)
            view?.let { content.addView(it, FrameLayout.LayoutParams(-1, -1)) }
            WindowInsetsControllerCompat(act.window, act.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        override fun onHideCustomView() {
            val act = unwrapActivity(context) ?: return
            val content = act.findViewById<FrameLayout>(android.R.id.content)
            tab.fullscreenView?.let { content.removeView(it) }
            tab.fullscreenView = null
            tab.fullscreenCallback?.onCustomViewHidden()
            tab.fullscreenCallback = null
            WindowInsetsControllerCompat(act.window, act.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
    tab.webView = wv
    return wv
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BrowserScreen(
    dark: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onImportBookmarks: () -> Unit,
    onExportBookmarks: () -> Unit,
    onFileChooser: (WebChromeClient.FileChooserParams, ValueCallback<Array<Uri>>) -> Unit,
    onGeoAllow: (String, android.webkit.GeolocationPermissions.Callback) -> Unit
) {
    val context = LocalContext.current
    val rootFocus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val coreState by ProxyManager.state.collectAsState()

    val tabs = remember { mutableStateListOf(TabState(0, HistoryStore.isIncognito())) }
    var nextId by remember { mutableIntStateOf(1) }
    var activeId by remember { mutableIntStateOf(0) }
    val activeTab = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    val isHome = activeTab.url == HOME_MARKER || activeTab.url.isBlank()

    var addressValue by remember { mutableStateOf(TextFieldValue()) }
    var addressFocused by remember { mutableStateOf(false) }

    var proxyReady by remember { mutableStateOf(false) }
    var proxyEnabled by remember { mutableStateOf(ProxyManager.isProxyEnabled()) }
    var bootDone by remember { mutableStateOf(false) }

    var showProxySheet by remember { mutableStateOf(false) }
    var showTabSheet by remember { mutableStateOf(false) }
    var ctxMenu by remember { mutableStateOf<CtxInfo?>(null) }
    var imageModeUrls by remember { mutableStateOf<List<String>?>(null) }
    var showPageInfo by remember { mutableStateOf(false) }
    var qrResult by remember { mutableStateOf<String?>(null) }
    var showToolboxSheet by remember { mutableStateOf(false) }
    var incognito by remember { mutableStateOf(HistoryStore.isIncognito()) }
    var findVisible by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var findOrdinal by remember { mutableIntStateOf(0) }
    var findTotal by remember { mutableIntStateOf(0) }
    var readerDoc by remember { mutableStateOf<ReaderDoc?>(null) }
    var readerSp by remember { mutableIntStateOf(16) }
    var bannerDismissed by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<Suggest.Item>>(emptyList()) }
    var suggestOn by remember { mutableStateOf(ProxyManager.isSuggestEnabled()) }
    var geoRequest by remember { mutableStateOf<Pair<String, android.webkit.GeolocationPermissions.Callback>?>(null) }
    var geoRemember by remember { mutableStateOf(true) }

    // ---- 崩溃诊断 / WebView 容错 / 安全模式 ----
    var safeMode by remember { mutableStateOf(Diagnostics.safeMode) }
    var webViewFailure by remember { mutableStateOf<String?>(null) }
    var diagReport by remember { mutableStateOf<String?>(null) }

    // 启动时若发现上次异常退出，展示可复制的诊断信息（不自动清除，便于用户反馈后再关）
    LaunchedEffect(Unit) {
        val crash = Diagnostics.lastCrashReport()
        diagReport = when {
            crash != null -> {
                val prefix = if (crash.contains("kind: caught")) {
                    "上次运行时网页组件出错（已被拦截，没有闪退）。\n\n"
                } else {
                    "上次运行崩溃（Java 异常）。\n\n"
                }
                prefix + crash
            }
            Diagnostics.lastRunCrashedInWebView ->
                "上次运行在打开网页时异常退出（无 Java 异常，疑似系统 WebView 原生崩溃）。\n\n" +
                    Diagnostics.deviceReport(includeWebView = false) +
                    "\n\n应用已自动进入安全模式：本次不启用代理，网页走直连。" +
                    "若仍崩溃，请在系统设置里更新或清除 Android System WebView 的数据。"
            else -> null
        }
    }

    // 工具箱关闭后刷新建议开关（开关在更多页内修改）
    LaunchedEffect(showToolboxSheet) {
        if (!showToolboxSheet) suggestOn = ProxyManager.isSuggestEnabled()
    }

    // 地址栏建议：250ms 防抖；键变化自动取消上一次协程（含网络请求）
    LaunchedEffect(addressValue.text, addressFocused, incognito, suggestOn) {
        suggestions = emptyList()
        val q = addressValue.text.trim()
        if (!addressFocused || incognito || !suggestOn || q.isBlank()) return@LaunchedEffect
        delay(250)
        val local = Suggest.local(q, HistoryStore.list(), BookmarkStore.list())
        suggestions = local
        val engine = ProxyManager.searchEngine()
        val remote = withContext(Dispatchers.IO) { ProxyManager.httpGet(Suggest.url(engine, q)) }
            ?.let { Suggest.parse(engine, it) } ?: emptyList()
        suggestions = (local + remote.take(5).map { Suggest.Item(it, "", Suggest.Kind.SEARCH) })
            .distinctBy { it.text + it.url }.take(8)
    }

    // 代理/内核状态变化时重新提示（此前被手动关闭的横框随新状态恢复）
    LaunchedEffect(proxyEnabled, coreState, proxyReady) { bannerDismissed = false }

    // 切换标签时清除上一个标签的查找高亮与计数
    LaunchedEffect(activeId) {
        activeTab.webView?.clearMatches()
        findOrdinal = 0
        findTotal = 0
    }

    // WebView 重建版本号与待恢复状态（深浅色切换 / 会话恢复共用）
    var webviewEpoch by remember { mutableIntStateOf(0) }
    val pendingStates = remember { mutableMapOf<Int, Bundle>() }

    // 意外关闭恢复：冷启动且上次未正常退出、且有非主页标签时弹窗询问
    var showRestore by remember { mutableStateOf(SessionStore.shouldPromptRestore { isHomeUrl(it) }) }
    val restoreTabs = remember { SessionStore.loadTabs() }
    val restoreCount = remember { restoreTabs.count { !isHomeUrl(it.first) } }

    // 首页图标管理
    var showHomeIcons by remember { mutableStateOf(false) }
    var editPin by remember { mutableStateOf<HomeEntry?>(null) }
    var homeDataVersion by remember { mutableIntStateOf(0) }
    val homeEntries = remember(homeDataVersion) {
        val bms = BookmarkStore.list()
        HomeStore.pins().map { p ->
            val bm = bms.firstOrNull { it.url == p.key }
            val baseName = bm?.title ?: p.key
            HomeEntry(
                key = p.key,
                name = p.name ?: baseName,
                letter = p.letter ?: homeInitial(baseName),
                link = p.link ?: bm?.url ?: p.key
            )
        }
    }

    // 持续快照未关闭标签（恢复弹窗未决时不覆盖上次快照）
    val snapshotKey = tabs.joinToString("\u0001") { it.url + "\u0002" + it.title }
    LaunchedEffect(snapshotKey, showRestore) {
        if (!showRestore) SessionStore.saveTabs(tabs.filter { !it.incognito }.map { it.url to it.title })
    }

    fun restoreSession() {
        if (restoreTabs.isEmpty()) return
        tabs.forEach { t ->
            t.webView?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
            t.webView = null
        }
        tabs.clear()
        restoreTabs.forEach { (url, title) ->
            val t = TabState(nextId++)
            t.url = url
            t.title = title.ifBlank { url }
            t.started = true
            tabs.add(t)
        }
        activeId = tabs.first().id
        webviewEpoch++
    }

    // 深浅色切换：重建 WebView 以应用新主题，saveState/restoreState 保留各标签前进后退历史
    var lastDark by remember { mutableStateOf(dark) }

    fun rebuildWebViews() {
        tabs.forEach { t ->
            t.webView?.let { wv ->
                val b = Bundle()
                runCatching { wv.saveState(b) }
                pendingStates[t.id] = b
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
                t.webView = null
            }
        }
        webviewEpoch++
    }

    LaunchedEffect(dark) {
        if (dark != lastDark) {
            lastDark = dark
            // 重建前先退出全屏，避免自定义视图残留在内容层
            tabs.forEach { t ->
                if (t.fullscreenView != null) t.webView?.webChromeClient?.onHideCustomView()
            }
            rebuildWebViews()
        }
    }

    // 启动内核 -> 绑定 WebView 代理 -> 成功回调后才允许加载网页（用户关闭代理时直接直连）
    LaunchedEffect(Unit) {
        if (safeMode) {
            // 安全模式：上次死在"打开网页"链路，本次先不启内核、不设置 WebView 代理，保证能直连上网
            proxyReady = false
            bootDone = true
            return@LaunchedEffect
        }
        if (ProxyManager.isProxyEnabled()) {
            val ok = ProxyManager.start()
            val pr = if (ok) WebViewProxyHelper.applyProxy(context) else false
            if (ok && pr) {
                proxyReady = true
            } else {
                // 冷启动失败：回滚启用态，避免开关显示开而实际直连
                if (ok) ProxyManager.shutdown()
                ProxyManager.setProxyEnabled(false)
                proxyEnabled = false
                proxyReady = false
                Toast.makeText(context, "内核启动失败，已切换为直连", Toast.LENGTH_SHORT).show()
            }
        } else {
            proxyReady = false
        }
        bootDone = true
    }

    // 内核停止（被查杀/重启失败）时同步就绪态：开关与红点反映"实际运行"，而非仅"想启用"
    LaunchedEffect(coreState) {
        if (coreState == CoreState.STOPPED) proxyReady = false
    }

    // 代理面板里成功启用代理后，安全模式横幅要随之消失（applyProxy(force) 内部已解除安全模式）
    LaunchedEffect(proxyReady) {
        if (proxyReady && safeMode) safeMode = Diagnostics.safeMode
    }

    LaunchedEffect(activeTab.id, activeTab.url, activeTab.navVersion, addressFocused) {
        if (!addressFocused) addressValue = TextFieldValue(if (isHome) "" else activeTab.url)
    }

    // 回前台自动复活内核：挂后台期间内核可能被系统查杀（幻影进程清理/省电策略），
    // 切回应用时无感重启并重新绑定 WebView 代理，配置始终从磁盘加载
    DisposableEffect(Unit) {
        val owner = ProcessLifecycleOwner.get()
        val observer = LifecycleEventObserver { _, event ->
            val current = tabs.firstOrNull { it.id == activeId }
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // 回前台：恢复网页定时器与渲染
                    current?.webView?.resumeTimers()
                    current?.webView?.onResume()
                    if (bootDone && !safeMode && ProxyManager.isProxyEnabled() &&
                        ProxyManager.state.value != CoreState.RUNNING
                    ) {
                        scope.launch {
                            val ok = ProxyManager.start()
                            val pr = if (ok) WebViewProxyHelper.applyProxy(context) else false
                            if (ok && pr) {
                                proxyReady = true
                            } else {
                                if (ok) ProxyManager.shutdown()
                                ProxyManager.setProxyEnabled(false)
                                proxyEnabled = false
                                proxyReady = false
                                Toast.makeText(context, "内核启动失败，已切换为直连", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    // 挂后台：暂停所有 WebView 的 JS 定时器与额外处理，降低后台占用
                    current?.webView?.pauseTimers()
                    tabs.forEach { it.webView?.onPause() }
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // 切换标签/回到主页：仅活动标签的网页保持活跃，其余暂停，减少多标签后台消耗
    LaunchedEffect(activeId, webviewEpoch, isHome) {
        tabs.forEach { t ->
            if (t.id == activeId && !isHome) t.webView?.onResume() else t.webView?.onPause()
        }
    }

    fun themedContext(): android.content.Context = ContextThemeWrapper(
        context,
        if (dark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light
    )

    /** 创建 WebView 的容错包装：失败时记录诊断并切到错误页，而不是让应用闪退。 */
    fun obtainWebView(tab: TabState): WebView? {
        tab.webView?.let { return it }
        return try {
            val wv = createWebView(
                themedContext(), tab, { rootFocus.clearFocus() }, { ctxMenu = it },
                { msg ->
                    val t = TabState(nextId++, tab.incognito)
                    tabs.add(t)
                    val newWv = obtainWebView(t)
                    if (newWv == null) {
                        tabs.remove(t)
                        false
                    } else {
                        (msg.obj as? WebView.WebViewTransport)?.webView = newWv
                        msg.sendToTarget()
                        activeId = t.id
                        true
                    }
                },
                onFileChooser,
                { o, t -> findOrdinal = o; findTotal = t },
                { origin, cb ->
                    when (GeoStore.decision(origin)) {
                        null -> {
                            geoRemember = true
                            geoRequest = origin to cb
                        }
                        true -> onGeoAllow(origin, cb)
                        false -> cb.invoke(origin, false, false)
                    }
                },
                {
                    geoRequest?.let { (o, cb) -> cb.invoke(o, false, false) }
                    geoRequest = null
                }
            )
            WebViewThemeHelper.apply(wv, dark)
            wv
        } catch (t: Throwable) {
            tab.webView = null
            Diagnostics.recordCaught("createWebView", t)
            webViewFailure = describeWebViewFailure(t)
            null
        }
    }

    /** 安全模式下手动重试：显式启动内核并强制设置 WebView 代理，失败则重新进入安全模式。 */
    fun retryProxyFromSafeMode() {
        scope.launch {
            Diagnostics.leaveSafeMode()
            safeMode = false
            val ok = ProxyManager.start()
            val pr = if (ok) WebViewProxyHelper.applyProxy(context, force = true) else false
            if (ok && pr) {
                proxyReady = true
                proxyEnabled = true
                ProxyManager.setProxyEnabled(true)
                Toast.makeText(context, "代理已恢复", Toast.LENGTH_SHORT).show()
            } else {
                if (ok) ProxyManager.shutdown()
                proxyReady = false
                proxyEnabled = false
                ProxyManager.setProxyEnabled(false)
                Diagnostics.enterSafeMode()
                safeMode = true
                Toast.makeText(context, "代理仍无法启用，继续直连（安全模式）", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun exitFullscreen() {
        if (activeTab.fullscreenView != null) {
            activeTab.webView?.webChromeClient?.onHideCustomView()
        }
    }

    fun goHome() {
        exitFullscreen()
        activeTab.url = HOME_MARKER
    }

    fun commitAddress(text: String) {
        val target = toLoadUrl(text, ProxyManager.searchEngine())
        if (target == HOME_MARKER) {
            goHome()
            return
        }
        val wv = obtainWebView(activeTab) ?: return
        try {
            activeTab.started = true
            wv.loadUrl(target)
            activeTab.url = target
        } catch (t: Throwable) {
            Diagnostics.recordCaught("loadUrl", t)
            activeTab.started = false
            webViewFailure = describeWebViewFailure(t)
        }
    }

    // ---------- 阅读模式提取 ----------
    fun extractReader() {
        val wv = activeTab.webView
        if (wv == null || isHomeUrl(activeTab.url)) {
            Toast.makeText(context, "当前页面不支持阅读模式", Toast.LENGTH_SHORT).show()
            return
        }
        wv.evaluateJavascript(READER_SCRIPT) { raw ->
            val doc = runCatching {
                val o = org.json.JSONObject(raw)
                val arr = o.optJSONArray("paras") ?: org.json.JSONArray()
                val paras = (0 until arr.length()).map { arr.getString(it) }
                if (paras.isEmpty()) null else ReaderDoc(o.optString("title"), paras)
            }.getOrNull()
            if (doc == null) {
                Toast.makeText(context, "未能提取到正文", Toast.LENGTH_SHORT).show()
            } else {
                readerDoc = doc
                showToolboxSheet = false
            }
        }
    }

    // ---------- 长按上下文菜单动作 ----------
    fun handleCtx(action: CtxAction) {
        val info = ctxMenu ?: return
        ctxMenu = null
        when (action) {
            CtxAction.BACKGROUND, CtxAction.NEW_TAB -> {
                val t = TabState(nextId++, HistoryStore.isIncognito())
                tabs.add(t)
                val newWv = obtainWebView(t)
                if (newWv == null) {
                    tabs.remove(t)
                    return
                }
                runCatching { newWv.loadUrl(info.linkUrl) }.onFailure { e ->
                    Diagnostics.recordCaught("loadUrl(ctx)", e)
                    webViewFailure = describeWebViewFailure(e)
                }
                if (action == CtxAction.NEW_TAB) activeId = t.id
                Toast.makeText(
                    context,
                    if (action == CtxAction.NEW_TAB) "已在新标签打开" else "已在后台打开",
                    Toast.LENGTH_SHORT
                ).show()
            }
            CtxAction.VIEW_IMAGE -> commitAddress(info.imageUrl)
            CtxAction.SAVE_IMAGE, CtxAction.DOWNLOAD_IMAGE, CtxAction.SHARE_IMAGE, CtxAction.SCAN_QR -> {
                val url = info.imageUrl
                scope.launch(Dispatchers.IO) {
                    val bytes = MediaActions.fetch(url)
                    withContext(Dispatchers.Main) {
                        if (bytes == null) {
                            Toast.makeText(context, "图片获取失败", Toast.LENGTH_SHORT).show()
                            return@withContext
                        }
                        when (action) {
                            CtxAction.SAVE_IMAGE -> {
                                val p = MediaActions.saveToGallery(context, url, bytes)
                                Toast.makeText(
                                    context,
                                    if (p != null) "已保存：$p" else "保存失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            CtxAction.DOWNLOAD_IMAGE -> {
                                val p = MediaActions.saveToDownload(context, url, bytes)
                                Toast.makeText(
                                    context,
                                    if (p != null) "已下载：$p" else "下载失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            CtxAction.SHARE_IMAGE -> runCatching {
                                MediaActions.shareImage(context, url, bytes)
                            }.onFailure {
                                Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                            }
                            CtxAction.SCAN_QR -> {
                                val r = MediaActions.decodeQr(bytes)
                                if (r == null) {
                                    Toast.makeText(context, "未识别到二维码", Toast.LENGTH_SHORT).show()
                                } else qrResult = r
                            }
                            else -> {}
                        }
                    }
                }
            }
            CtxAction.IMAGE_MODE -> {
                activeTab.webView?.evaluateJavascript(
                    "(function(){var s=[];var seen={};" +
                        "for(var i=0;i<document.images.length;i++){" +
                        "var u=document.images[i].currentSrc||document.images[i].src;" +
                        "if(u&&u.indexOf('http')===0&&!seen[u]){seen[u]=1;s.push(u);if(s.length>=200)break;}}" +
                        "return JSON.stringify(s);})()"
                ) { json ->
                    val list = runCatching {
                        val arr = org.json.JSONArray(json)
                        (0 until arr.length()).map { arr.getString(it) }
                    }.getOrDefault(emptyList())
                    if (list.isEmpty()) {
                        Toast.makeText(context, "页面无图片", Toast.LENGTH_SHORT).show()
                    } else imageModeUrls = list
                }
            }
            CtxAction.PAGE_INFO -> showPageInfo = true
            CtxAction.MARK_AD -> {
                val host = info.host
                if (host.isBlank()) {
                    Toast.makeText(context, "无法识别域名", Toast.LENGTH_SHORT).show()
                } else {
                    AdBlocker.markHost(host)
                    Toast.makeText(context, "已标记 $host 为广告域名", Toast.LENGTH_SHORT).show()
                }
            }
            CtxAction.COPY_TEXT -> copyToClipboard(context, info.linkText.ifBlank { info.pageTitle })
            CtxAction.COPY_LINK -> copyToClipboard(
                context,
                info.linkUrl.ifBlank { info.imageUrl.ifBlank { info.pageUrl } }
            )
            CtxAction.SHARE -> {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, info.linkUrl.ifBlank { info.pageUrl })
                }
                context.startActivity(Intent.createChooser(send, "分享"))
            }
        }
    }


    BackHandler(enabled = !isHome && activeTab.webView?.canGoBack() == true) {
        activeTab.webView?.goBack()
    }

    // 全屏优先于网页后退（后注册的 BackHandler 优先响应）
    BackHandler(enabled = activeTab.fullscreenView != null) {
        activeTab.webView?.webChromeClient?.onHideCustomView()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ---------- 顶部地址栏 ----------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)
        ) {
            val proxyDot: @Composable () -> Unit = {
                IconButton(onClick = { showProxySheet = true }) {
                    val running = coreState == CoreState.RUNNING && proxyReady
                    val dotColor by animateColorAsState(
                        if (running) Color(0xFF2E7D32) else Color(0xFFC62828),
                        label = "proxyDot"
                    )
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
            if (findVisible) {
                // 页内查找条：输入即高亮计数，上/下跳转，关闭清除匹配
                OutlinedTextField(
                    value = findText,
                    onValueChange = {
                        findText = it
                        findOrdinal = 0
                        findTotal = 0
                        activeTab.webView?.findAllAsync(it)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp),
                    placeholder = { Text("页内查找", fontSize = 14.sp) },
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        Text(
                            if (findText.isEmpty()) "" else "$findOrdinal/$findTotal",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                )
                IconButton(onClick = { activeTab.webView?.findNext(false) }) {
                    Icon(Icons.Default.KeyboardArrowUp, "上一个匹配")
                }
                IconButton(onClick = { activeTab.webView?.findNext(true) }) {
                    Icon(Icons.Default.KeyboardArrowDown, "下一个匹配")
                }
                IconButton(onClick = {
                    findVisible = false
                    findText = ""
                    findOrdinal = 0
                    findTotal = 0
                    activeTab.webView?.clearMatches()
                }) { Icon(Icons.Default.Close, "关闭查找") }
            } else if (isHome) {
                // 主页不显示地址栏，仅保留内核状态小圆（位置不变）
                Spacer(Modifier.weight(1f))
                proxyDot()
            } else {
                OutlinedTextField(
                    value = addressValue,
                    onValueChange = { addressValue = it },
                    modifier = Modifier.weight(1f).onFocusChanged {
                        addressFocused = it.isFocused
                        // 聚焦时默认全选框内内容，直接输入即覆盖
                        if (it.isFocused) {
                            addressValue = addressValue.copy(
                                selection = TextRange(0, addressValue.text.length)
                            )
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp),
                    placeholder = { Text("搜索或输入网址", fontSize = 14.sp) },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        commitAddress(addressValue.text)
                        rootFocus.clearFocus()
                    }),
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = addressFocused,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180))
                        ) {
                            IconButton(onClick = { addressValue = TextFieldValue() }) {
                                Icon(Icons.Default.Close, "清空输入")
                            }
                        }
                    }
                )
                // 聚焦时右侧图标收起，搜索框动画扩展占满页顶
                AnimatedVisibility(
                    visible = !addressFocused,
                    enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkHorizontally(tween(220)) + fadeOut(tween(220))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { findVisible = true }) {
                            Icon(Icons.Default.Search, "页内查找")
                        }
                        // 刷新/停止：页顶内核小圆左侧、搜索框外；地址栏展开时随整组隐藏
                        IconButton(onClick = {
                            val wv = activeTab.webView
                            if (activeTab.isLoading) wv?.stopLoading() else wv?.reload()
                        }) {
                            Icon(
                                if (activeTab.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                "刷新/停止"
                            )
                        }
                        if (incognito) {
                            Text(
                                "无痕",
                                fontSize = 10.sp,
                                color = themeColor(Color(0xFF6A1B9A), Color(0xFFCE93D8)),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        proxyDot()
                    }
                }
            }
        }
        // 地址栏建议下拉：本地历史/收藏 + 引擎 suggest，点按直接加载/检索
        if (addressFocused && suggestions.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                ) {
                    suggestions.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                commitAddress(if (s.kind == Suggest.Kind.SEARCH) s.text else s.url)
                                rootFocus.clearFocus()
                            }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (s.kind) {
                                    Suggest.Kind.HISTORY -> Icons.Default.Refresh
                                    Suggest.Kind.BOOKMARK -> Icons.Default.Star
                                    Suggest.Kind.SEARCH -> Icons.Default.Search
                                },
                                null,
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (s.url.isNotBlank()) {
                                    Text(
                                        s.url,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (activeTab.isLoading || activeTab.progress in 1..99) {
            LinearProgressIndicator(
                progress = { activeTab.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }

        // ---------- WebView 内容区 ----------
        Box(Modifier.weight(1f)) {
            val failure = webViewFailure
            if (failure != null) {
                WebViewFailurePanel(
                    detail = failure,
                    onRetry = {
                        webViewFailure = null
                        webviewEpoch++
                    },
                    onCopy = { copyToClipboard(context, failure) },
                    onOpenSettings = { openWebViewSettings(context) },
                    onDismissToHome = {
                        webViewFailure = null
                        goHome()
                    }
                )
            } else if (isHome) {
                HomeScreen(
                    entries = homeEntries,
                    onSearch = { commitAddress(it) },
                    onOpen = { commitAddress(it) },
                    onAdd = { showHomeIcons = true },
                    onEdit = { editPin = it }
                )
            } else if (!bootDone) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在启动代理内核…", fontSize = 14.sp)
                }
            } else {
                val tab = activeTab
                key(tab.id, webviewEpoch) {
                    AndroidView(
                        factory = {
                            val wv = obtainWebView(tab)
                            if (wv == null) {
                                // 创建失败：先给占位 View，下一次重组会切到错误说明页
                                View(context)
                            } else {
                                if (wv.url == null && tab.started) {
                                    val restored = pendingStates.remove(tab.id)?.let {
                                        runCatching { wv.restoreState(it) }.getOrNull()
                                    }
                                    if (restored == null && tab.url.isNotBlank() && !isHomeUrl(tab.url)) {
                                        runCatching { wv.loadUrl(tab.url) }.onFailure { e ->
                                            Diagnostics.recordCaught("restoreWebView", e)
                                            webViewFailure = describeWebViewFailure(e)
                                        }
                                    }
                                }
                                wv
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            val showProxyBanner = safeMode ||
                (!isHome && !bannerDismissed && (!proxyEnabled || coreState != CoreState.RUNNING || !proxyReady))
            if (showProxyBanner) {
                Surface(
                    color = if (safeMode) themeColor(Color(0xFFFFE0B2), Color(0xFF3A2A12))
                    else themeColor(Color(0xFFFFF3E0), Color(0xFF3A2E1E)),
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Text(
                            when {
                                safeMode -> "安全模式：上次打开网页时异常退出，本次已跳过代理设置（可直连上网）"
                                !proxyEnabled -> "代理已关闭 · 直连模式（点右上红点开启）"
                                coreState != CoreState.RUNNING -> "内核未运行 · 自动重启中，网页暂不可用（点右上红点查看日志）"
                                else -> "代理未就绪 · 当前直连（点右上红点配置）"
                            },
                            fontSize = 12.sp,
                            color = themeColor(Color(0xFF8D6E63), Color(0xFFD7B98E)),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                        )
                        if (safeMode) {
                            TextButton(onClick = { retryProxyFromSafeMode() }, modifier = Modifier.height(32.dp)) {
                                Text("重试代理", fontSize = 12.sp)
                            }
                        } else {
                            IconButton(onClick = { bannerDismissed = true }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    "关闭提示",
                                    tint = themeColor(Color(0xFF8D6E63), Color(0xFFD7B98E)),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            // 阅读模式：纯净正文重排，字号可调，覆盖于网页之上
            readerDoc?.let { doc ->
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text(
                                doc.title.ifBlank { "阅读模式" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { readerSp = (readerSp - 1).coerceAtLeast(12) }) {
                                Text("A-", fontSize = 13.sp)
                            }
                            TextButton(onClick = { readerSp = (readerSp + 1).coerceAtMost(26) }) {
                                Text("A+", fontSize = 13.sp)
                            }
                            IconButton(onClick = { readerDoc = null }) {
                                Icon(Icons.Default.Close, "退出阅读模式")
                            }
                        }
                        LazyColumn(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(doc.paras) { p ->
                                Text(
                                    p,
                                    fontSize = readerSp.sp,
                                    lineHeight = (readerSp * 1.7).sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 底部工具栏 ----------
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { activeTab.webView?.goBack() },
                enabled = activeTab.webView?.canGoBack() == true
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "后退") }
            IconButton(
                onClick = { activeTab.webView?.goForward() },
                enabled = activeTab.webView?.canGoForward() == true
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "前进") }
            IconButton(onClick = { goHome() }) { Icon(Icons.Default.Home, "主页") }
            // 长按快速新建标签页，单击打开标签切换器
            Box(
                Modifier
                    .size(48.dp)
                    .combinedClickable(
                        onClick = { showTabSheet = true },
                        onLongClick = {
                            exitFullscreen()
                            val t = TabState(nextId++, HistoryStore.isIncognito())
                            tabs.add(t)
                            activeId = t.id
                            Toast.makeText(context, "已新建标签页", Toast.LENGTH_SHORT).show()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("${tabs.size}", fontSize = 12.sp) }
            }
            IconButton(onClick = { showToolboxSheet = true }) { Icon(Icons.Default.Build, "工具箱") }
        }
    }

    // ---------- 标签页切换器 ----------
    if (showTabSheet) {
        ModalBottomSheet(onDismissRequest = { showTabSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("标签页", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                tabs.toList().forEach { tab ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                exitFullscreen()
                                activeId = tab.id
                                showTabSheet = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tab.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (tab.id == activeId) FontWeight.Bold else FontWeight.Normal
                        )
                        IconButton(onClick = {
                            tab.webView?.destroy()
                            tab.webView = null
                            tabs.remove(tab)
                            SnifferStore.forget(tab.id.toString())
                            if (tab.incognito && tabs.none { it.incognito }) {
                                IncognitoGuard.cleanup(activeTab.webView)
                            }
                            if (tabs.isEmpty()) {
                                exitFullscreen()
                                val t = TabState(nextId++, HistoryStore.isIncognito())
                                tabs.add(t)
                                activeId = t.id
                            } else if (activeId == tab.id) {
                                activeId = tabs.last().id
                            }
                        }) { Icon(Icons.Default.Close, "关闭标签") }
                    }
                }
                TextButton(onClick = {
                    exitFullscreen()
                    val t = TabState(nextId++, HistoryStore.isIncognito())
                    tabs.add(t)
                    activeId = t.id
                    showTabSheet = false
                }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("新建标签页")
                }
            }
        }
    }

    // ---------- 代理配置中心 ----------
    if (showProxySheet) {
        ModalBottomSheet(
            onDismissRequest = { showProxySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ProxyPanel(
                proxyEnabled = proxyEnabled,
                proxyReady = proxyReady,
                onProxyEnabledChange = { proxyEnabled = it },
                onProxyReadyChange = { proxyReady = it }
            )
        }
    }

    // ---------- 工具箱 ----------
    if (showToolboxSheet) {
        ModalBottomSheet(onDismissRequest = { showToolboxSheet = false }) {
            ToolboxSheet(
                incognito = incognito,
                onIncognitoChange = { on ->
                    incognito = on
                    if (!on) IncognitoGuard.cleanup(activeTab.webView)
                },
                onOpenUrl = { url ->
                    commitAddress(url)
                    showToolboxSheet = false
                },
                onUaChange = { desktop ->
                    val ua = if (desktop) DESKTOP_UA else mobileUa(context)
                    tabs.forEach { t ->
                        t.webView?.settings?.userAgentString = ua
                        t.webView?.reload()
                    }
                },
                onWebRtcChange = { webviewEpoch++ },
                tabId = activeTab.id.toString(),
                onReaderMode = { extractReader() },
                currentUrl = activeTab.url,
                currentTitle = activeTab.title,
                themeMode = themeMode,
                onThemeChange = onThemeModeChange,
                onImportBookmarks = onImportBookmarks,
                onExportBookmarks = onExportBookmarks,
                onOpenNewTabChange = { on ->
                    ProxyManager.setOpenNewTab(on)
                    tabs.forEach { t -> t.webView?.settings?.setSupportMultipleWindows(on) }
                }
            )
        }
    }

    // ---------- 长按菜单 / 看图模式 / 页面信息 / 扫码结果 ----------
    // 网页定位来源确认框：允许/拒绝 + 记住选择；允许后再走系统运行时权限
    geoRequest?.let { (origin, cb) ->
        AlertDialog(
            onDismissRequest = {
                geoRequest = null
                cb.invoke(origin, false, false)
            },
            title = { Text("定位权限请求") },
            text = {
                Column {
                    Text("以下网站请求获取您的位置：", fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(origin, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = geoRemember, onCheckedChange = { geoRemember = it })
                        Text("记住此选择", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (geoRemember) GeoStore.remember(origin, true)
                    geoRequest = null
                    onGeoAllow(origin, cb)
                }) { Text("允许") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (geoRemember) GeoStore.remember(origin, false)
                    geoRequest = null
                    cb.invoke(origin, false, false)
                }) { Text("拒绝") }
            }
        )
    }
    ctxMenu?.let { info ->
        ContextMenuSheet(info, onAction = { handleCtx(it) }, onDismiss = { ctxMenu = null })
    }
    imageModeUrls?.let { urls ->
        ImageModeSheet(urls) { imageModeUrls = null }
    }
    if (showPageInfo) {
        PageInfoDialog(
            url = activeTab.url,
            title = activeTab.title,
            onCopy = { copyToClipboard(context, activeTab.url) },
            onDismiss = { showPageInfo = false }
        )
    }
    qrResult?.let { r ->
        QrResultDialog(
            text = r,
            onCopy = { copyToClipboard(context, r) },
            onOpen = { commitAddress(r); qrResult = null },
            onDismiss = { qrResult = null }
        )
    }

    // ---------- 上次异常退出的诊断信息（可复制反馈） ----------
    diagReport?.let { report ->
        AlertDialog(
            onDismissRequest = {
                diagReport = null
                Diagnostics.clearCrashReport()
            },
            title = { Text("上次运行诊断信息") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(report, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { copyToClipboard(context, report) }) { Text("复制详情") }
            },
            dismissButton = {
                TextButton(onClick = {
                    diagReport = null
                    Diagnostics.clearCrashReport()
                }) { Text("关闭") }
            }
        )
    }

    // ---------- 首页图标管理 ----------
    if (showHomeIcons) {
        HomeIconsSheet(onDismiss = {
            showHomeIcons = false
            homeDataVersion++
        })
    }

    // ---------- 长按首页图标编辑 ----------
    editPin?.let { entry ->
        HomePinEditSheet(entry = entry, onDismiss = {
            editPin = null
            homeDataVersion++
        })
    }

    // ---------- 意外关闭恢复弹窗（Material3 主题，自动跟随深/浅色） ----------
    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text("恢复上次页面") },
            text = {
                Text("检测到上次有 $restoreCount 个页面未正常关闭，是否恢复这些页面？")
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreSession()
                    showRestore = false
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = {
                    SessionStore.clearTabs()
                    showRestore = false
                }) { Text("不恢复") }
            }
        )
    }
}

/** WebView 创建/加载失败时的说明页：不闪退，给出原因与可执行的修复步骤。 */
@Composable
private fun WebViewFailurePanel(
    detail: String,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissToHome: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text(
            "网页视图不可用",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text("系统网页组件（WebView）创建失败或异常退出了，应用已拦截闪退。", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Text("可以这样修复：", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text("1. 设置 → 应用 → 显示系统应用 → 找到 Android System WebView（或 Chrome）→ 更新或启用", fontSize = 13.sp)
        Text("2. 若已启用：清除它的存储数据，或卸载其更新后重启手机", fontSize = 13.sp)
        Text("3. 仍失败：检查系统 WebView 是否被精简/替换，必要时恢复系统自带版本", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Text("错误详情", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(detail, fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRetry) { Text("重试") }
            TextButton(onClick = onCopy) { Text("复制详情") }
            TextButton(onClick = onOpenSettings) { Text("WebView 设置") }
            TextButton(onClick = onDismissToHome) { Text("回到主页") }
        }
    }
}

/** 给用户看的 WebView 失败摘要（含设备/WebView 版本，可直接复制反馈）。 */
private fun describeWebViewFailure(t: Throwable): String = buildString {
    appendLine("${t.javaClass.name}: ${t.message ?: "（无错误信息）"}")
    appendLine()
    append(Diagnostics.deviceReport())
}

/** 打开系统 WebView 提供方的应用详情页，方便用户更新/清除数据。 */
private fun openWebViewSettings(context: android.content.Context) {
    val pkg = runCatching { WebView.getCurrentWebViewPackage()?.packageName }.getOrNull()
    val intent = if (pkg != null) {
        Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$pkg")
        )
    } else {
        Intent(android.provider.Settings.ACTION_SETTINGS)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
    }
}
