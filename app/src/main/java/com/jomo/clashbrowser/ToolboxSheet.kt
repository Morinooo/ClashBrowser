@file:OptIn(ExperimentalFoundationApi::class)

package com.jomo.clashbrowser

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工具箱（BottomSheet 内容）：
 * 主页面（历史/收藏/下载/浏览器标识/外观/更多/关于）-> 各子页面；
 * 设置与工具（无痕/搜索引擎/订阅信息/恢复/新标签/阅读/嗅探）集中在「更多」。
 */
@Composable
fun ToolboxSheet(
    incognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    onWebRtcChange: (Boolean) -> Unit = {},
    onOpenUrl: (String) -> Unit,
    onUaChange: (Boolean) -> Unit,
    currentUrl: String,
    currentTitle: String,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onImportBookmarks: () -> Unit,
    onExportBookmarks: () -> Unit,
    onOpenNewTabChange: (Boolean) -> Unit,
    tabId: String,
    onReaderMode: () -> Unit
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf("main") }
    var desktopUa by remember { mutableStateOf(ProxyManager.isDesktopUa()) }
    var history by remember { mutableStateOf(HistoryStore.list()) }
    var bmSearch by remember { mutableStateOf("") }
    val bmVersion by BookmarkStore.version.collectAsState()
    var bmPath by remember { mutableStateOf(listOf<String>()) }
    var bmSelected by remember { mutableStateOf<Set<String>?>(null) }
    var bmMenuNode by remember { mutableStateOf<BookmarkNode?>(null) }
    var bmDialog by remember { mutableStateOf<BmDialog?>(null) }
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(page, bmVersion) {
        if (page == "history") history = HistoryStore.list()
        bmSelected = bmSelected?.mapNotNull { if (BookmarkStore.nodeById(it) != null) it else null }?.toSet()
    }

    // 子页面切换：轻量滑动+淡入淡出（仅布局位移与透明度，无额外内存开销）
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            (slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(220)))
        },
        label = "toolboxPage"
    ) { target ->
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)
        ) {
            when (target) {
            "theme" -> {
                SheetHeader("外观模式") { page = "main" }
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple("light", "浅色", "灰白简洁外观，与默认设计一致"),
                    Triple("dark", "深色", "黑底白字，夜间浏览更护眼"),
                    Triple("system", "跟随系统", "自动跟随设备的深色/浅色设置")
                ).forEach { (mode, title, desc) ->
                    UaOption(title = title, desc = desc, selected = themeMode == mode) {
                        onThemeChange(mode)
                        Toast.makeText(context, "已切换：$title", Toast.LENGTH_SHORT).show()
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "切换时会同步通知应用内网页切换深浅色（取决于网页与系统 WebView 的支持情况）",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            "more" -> {
                SheetHeader("更多") { page = "main" }
                Spacer(Modifier.height(8.dp))
                var restoreOn by remember { mutableStateOf(SessionStore.isRestoreEnabled()) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("意外退出时恢复页面", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (restoreOn) "开启 · 应用被杀后台后，下次打开询问是否恢复"
                            else "关闭 · 不再询问恢复上次页面",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = restoreOn,
                        onCheckedChange = {
                            restoreOn = it
                            SessionStore.setRestoreEnabled(it)
                        }
                    )
                }
                var openNewTab by remember { mutableStateOf(ProxyManager.isOpenNewTab()) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("新页面在新标签打开", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (openNewTab) "开启 · 网页内打开新页面时新建标签，不覆盖当前页"
                            else "关闭 · 新页面直接在当前标签打开",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = openNewTab,
                        onCheckedChange = {
                            openNewTab = it
                            onOpenNewTabChange(it)
                        }
                    )
                }
                // 无痕隔离：不写历史/快照/缓存，退出时清除无痕访问站点的 Cookie
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("无痕模式", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (incognito) "开启 · 不写历史/快照/缓存，退出时清除无痕站点 Cookie"
                            else "关闭 · 正常记录浏览历史",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = incognito,
                        onCheckedChange = {
                            HistoryStore.setIncognito(it)
                            onIncognitoChange(it)
                            Toast.makeText(
                                context,
                                if (it) "无痕模式已开启" else "无痕已退出，无痕站点 Cookie 与缓存已清除",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                // 搜索建议：地址栏下拉（本地历史/收藏 + 引擎 suggest），无痕不出建议
                var suggestOn by remember { mutableStateOf(ProxyManager.isSuggestEnabled()) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("搜索建议", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (suggestOn) "开启 · 地址栏下拉历史/收藏与引擎建议"
                            else "关闭 · 地址栏不显示下拉建议",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = suggestOn,
                        onCheckedChange = {
                            suggestOn = it
                            ProxyManager.setSuggestEnabled(it)
                        }
                    )
                }
                // WebRTC 防泄露：普通标签禁用 RTCPeerConnection（无痕标签强制禁用）
                var rtcBlock by remember { mutableStateOf(ProxyManager.isWebRtcBlock()) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("WebRTC 防泄露", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (rtcBlock) "开启 · 所有标签禁用 RTCPeerConnection"
                            else "关闭 · 仅无痕标签强制禁用",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = rtcBlock,
                        onCheckedChange = {
                            rtcBlock = it
                            ProxyManager.setWebRtcBlock(it)
                            onWebRtcChange(it)
                        }
                    )
                }
                // 桌面模式开关移至主列表「浏览器标识」页，避免重复入口
                var engine by remember { mutableStateOf(ProxyManager.searchEngine()) }
                ToolboxRow(
                    icon = Icons.Default.Search,
                    title = "搜索引擎",
                    desc = when (engine) {
                        "bing" -> "当前：Bing"
                        "baidu" -> "当前：百度"
                        "duckduckgo" -> "当前：DuckDuckGo"
                        else -> "当前：Google"
                    }
                ) { page = "engine" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = Icons.Default.DateRange,
                    title = "订阅信息",
                    desc = "流量/到期解析详情与手动刷新"
                ) { page = "subinfo" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = Icons.Default.Place,
                    title = "定位权限记录",
                    desc = "查看/清除各网站定位允许或拒绝决定"
                ) { page = "geo" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = painterResource(R.drawable.ic_reader),
                    title = "阅读模式",
                    desc = "提取当前页正文，纯净重排阅读"
                ) { onReaderMode() }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = painterResource(R.drawable.ic_sniff),
                    title = "资源嗅探",
                    desc = "当前标签的媒体/流地址：复制、下载、打开"
                ) { page = "sniffer" }
            }

            "geo" -> {
                SheetHeader("定位权限") { page = "more" }
                Spacer(Modifier.height(8.dp))
                var geoVersion by remember { mutableIntStateOf(0) }
                val entries = remember(geoVersion) { GeoStore.list() }
                var geoDelete by remember { mutableStateOf<String?>(null) }
                var geoClear by remember { mutableStateOf(false) }
                if (entries.isEmpty()) {
                    Text("暂无网站定位权限记录", fontSize = 12.sp, color = Color.Gray)
                }
                entries.forEach { (origin, allow) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(origin, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (allow) "已允许" else "已拒绝", fontSize = 11.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { geoDelete = origin }) {
                            Icon(Icons.Default.Delete, "删除记录")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { geoClear = true }) { Text("清空全部记录") }
                Text(
                    "删除记录后，该网站下次请求定位会重新弹窗询问",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                geoDelete?.let { o ->
                    AlertDialog(
                        onDismissRequest = { geoDelete = null },
                        title = { Text("删除定位记录") },
                        text = { Text("删除 $o 的定位权限决定？") },
                        confirmButton = {
                            TextButton(onClick = {
                                GeoStore.forget(o)
                                geoVersion++
                                geoDelete = null
                            }) { Text("删除") }
                        },
                        dismissButton = {
                            TextButton(onClick = { geoDelete = null }) { Text("取消") }
                        }
                    )
                }
                if (geoClear) {
                    AlertDialog(
                        onDismissRequest = { geoClear = false },
                        title = { Text("清空定位记录") },
                        text = { Text("清空全部网站的定位权限决定？") },
                        confirmButton = {
                            TextButton(onClick = {
                                GeoStore.clear()
                                geoVersion++
                                geoClear = false
                            }) { Text("清空") }
                        },
                        dismissButton = {
                            TextButton(onClick = { geoClear = false }) { Text("取消") }
                        }
                    )
                }
            }

            "ua" -> {
                SheetHeader("浏览器标识") { page = "main" }
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple(false, "移动标识", "安卓手机 UA + 移动视口（默认）"),
                    Triple(true, "桌面标识", "电脑 UA + 1024 宽视口，加载桌面版网页")
                ).forEach { (desktop, title, desc) ->
                    UaOption(title = title, desc = desc, selected = desktopUa == desktop) {
                        desktopUa = desktop
                        ProxyManager.setDesktopUa(desktop)
                        onUaChange(desktop)
                        Toast.makeText(
                            context,
                            if (desktop) "已切换为桌面模式" else "已切换为移动模式",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "切换后会重载所有已打开标签；桌面标识同时启用桌面宽度视口",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            "engine" -> {
                SheetHeader("搜索引擎") { page = "more" }
                Spacer(Modifier.height(8.dp))
                var engineSel by remember { mutableStateOf(ProxyManager.searchEngine()) }
                listOf(
                    Triple("google", "Google", "默认引擎，境外检索质量佳"),
                    Triple("bing", "Bing", "微软必应，国内可直连"),
                    Triple("baidu", "百度", "国内中文检索"),
                    Triple("duckduckgo", "DuckDuckGo", "强调隐私、不追踪检索词")
                ).forEach { (key, title, desc) ->
                    UaOption(title = title, desc = desc, selected = engineSel == key) {
                        engineSel = key
                        ProxyManager.setSearchEngine(key)
                        Toast.makeText(context, "搜索引擎已切换：$title", Toast.LENGTH_SHORT).show()
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "地址栏与主页搜索框的非网址输入将使用该引擎检索",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            "subinfo" -> {
                SheetHeader("订阅信息") { page = "more" }
                Spacer(Modifier.height(8.dp))
                var tick by remember { mutableIntStateOf(0) }
                var refreshing by remember { mutableStateOf(false) }
                val subScope = rememberCoroutineScope()
                val active = remember(tick) { ProxyManager.listProfiles().firstOrNull { it.active } }
                if (active == null) {
                    Text("暂无订阅档案：请先在代理面板导入订阅", fontSize = 13.sp, color = Color.Gray)
                } else {
                    Text(active.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    AboutLine("上传 / 下载", "${DownloadStore.fmtSize(active.upload)} / ${DownloadStore.fmtSize(active.download)}")
                    AboutLine("总量", if (active.total > 0) DownloadStore.fmtSize(active.total) else "未知（机场未提供）")
                    AboutLine(
                        "到期",
                        if (active.expire > 0) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(active.expire * 1000))
                        else "未知（机场未提供）"
                    )
                    AboutLine(
                        "更新时间",
                        if (active.updated > 0) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(active.updated))
                        else "未知"
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        enabled = !refreshing,
                        onClick = {
                            refreshing = true
                            subScope.launch {
                                val err = ProxyManager.updateSubscription(active.url)
                                refreshing = false
                                tick++
                                Toast.makeText(context, err ?: "订阅信息已刷新", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text(if (refreshing) "刷新中…" else "刷新订阅信息", fontSize = 13.sp) }
                    Text(
                        "解析容错：键大小写、空格、引号、缺失字段、非法数值与毫秒时间戳均已兼容",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            "sniffer" -> {
                SheetHeader("资源嗅探") { page = "more" }
                Spacer(Modifier.height(8.dp))
                val sv by SnifferStore.version.collectAsState()
                val sniffItems = remember(sv, tabId) { SnifferStore.list(tabId) }
                var sniffClearAsk by remember { mutableStateOf(false) }
                if (sniffItems.isEmpty()) {
                    Text(
                        "当前标签暂无嗅探到的媒体资源；播放视频/音频后这里会列出流地址",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { sniffClearAsk = true }) {
                            Text("清空", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sniffItems) { s ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    s.kind + " · " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(s.time)),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Text(s.url, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("ClashBrowser", s.url))
                                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                    }) { Text("复制", fontSize = 13.sp) }
                                    TextButton(onClick = {
                                        DownloadStore.enqueue(
                                            s.url, null, null,
                                            if (s.kind == "音频") "audio/mpeg" else "video/mp4",
                                            -1
                                        )
                                        Toast.makeText(context, "已加入下载管理", Toast.LENGTH_SHORT).show()
                                    }) { Text("下载", fontSize = 13.sp) }
                                    TextButton(onClick = {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setDataAndType(
                                                android.net.Uri.parse(s.url),
                                                if (s.kind == "音频") "audio/*" else "video/*"
                                            )
                                        }
                                        runCatching { context.startActivity(intent) }.onFailure {
                                            Toast.makeText(context, "没有可播放该资源的应用", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Text("打开", fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                }
                if (sniffClearAsk) {
                    AlertDialog(
                        onDismissRequest = { sniffClearAsk = false },
                        title = { Text("确认清空") },
                        text = { Text("将清空当前标签的嗅探记录，不可恢复。", fontSize = 14.sp) },
                        confirmButton = {
                            TextButton(onClick = {
                                SnifferStore.clear(tabId)
                                sniffClearAsk = false
                            }) { Text("清空", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { sniffClearAsk = false }) { Text("取消") } }
                    )
                }
            }

            "bookmarks" -> {
                val bmRoot = remember(bmVersion) { BookmarkStore.root() }
                var cur = bmRoot
                val crumbs = mutableListOf<BookmarkNode>()
                for (id in bmPath) {
                    val n = cur.children.firstOrNull { it.id == id && it.type == NodeType.FOLDER } ?: break
                    crumbs.add(n)
                    cur = n
                }
                val selecting = bmSelected != null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        when {
                            selecting -> bmSelected = null
                            crumbs.isNotEmpty() -> bmPath = bmPath.dropLast(1)
                            else -> page = "main"
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                    Text(
                        if (selecting) "已选 ${bmSelected?.size ?: 0} 项" else "收藏夹",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (selecting) {
                        TextButton(onClick = { bmSelected = null }) { Text("取消") }
                        TextButton(
                            onClick = {
                                val ids = bmSelected.orEmpty()
                                if (ids.isNotEmpty()) {
                                    bmDialog = BmDialog.Delete(
                                        ids,
                                        "将删除所选 ${ids.size} 项（文件夹含其全部内容），不可恢复。"
                                    )
                                }
                            },
                            enabled = (bmSelected?.size ?: 0) > 0
                        ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    } else {
                        TextButton(onClick = {
                            bmDialog = BmDialog.AddBookmark(currentUrl, currentTitle)
                        }) { Text("收藏当前页") }
                    }
                }
                OutlinedTextField(
                    value = bmSearch,
                    onValueChange = { bmSearch = it },
                    placeholder = { Text("搜索收藏", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportBookmarks, modifier = Modifier.weight(1f)) {
                        Text("导入书签", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onExportBookmarks, modifier = Modifier.weight(1f)) {
                        Text("导出书签", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (bmSearch.isNotBlank()) {
                    val results = BookmarkStore.list().filter {
                        it.title.contains(bmSearch, ignoreCase = true) ||
                            it.url.contains(bmSearch, ignoreCase = true)
                    }.mapNotNull { BookmarkStore.nodeByUrl(it.url) }
                    if (results.isEmpty()) {
                        Text(
                            "没有匹配的收藏",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth()
                        )
                    } else {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(results, key = { it.id }) { node ->
                                BmRow(
                                    node = node,
                                    selecting = selecting,
                                    checked = bmSelected?.contains(node.id) == true,
                                    onClick = {
                                        if (selecting) {
                                            bmSelected = if (bmSelected?.contains(node.id) == true) {
                                                bmSelected!!.minus(node.id)
                                            } else {
                                                bmSelected!!.plus(node.id)
                                            }
                                        } else onOpenUrl(node.url)
                                    },
                                    onLongClick = { bmMenuNode = node },
                                    onCheck = {
                                        bmSelected = if (bmSelected?.contains(node.id) == true) {
                                            bmSelected!!.minus(node.id)
                                        } else {
                                            (bmSelected ?: emptySet()).plus(node.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { bmPath = emptyList() }) {
                                Text("收藏夹", fontSize = 13.sp)
                            }
                            crumbs.forEachIndexed { i, n ->
                                Text("›", fontSize = 13.sp, color = Color.Gray)
                                TextButton(onClick = { bmPath = bmPath.take(i + 1) }) {
                                    Text(n.title, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }
                        TextButton(onClick = { bmDialog = BmDialog.NewFolder(cur.id) }) {
                            Text("新建文件夹", fontSize = 13.sp)
                        }
                    }
                    val entries = cur.children.filter { it.type == NodeType.FOLDER } +
                        cur.children.filter { it.type == NodeType.BOOKMARK }
                    if (entries.isEmpty()) {
                        Text(
                            "暂无内容，可收藏当前页或新建文件夹",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth()
                        )
                    } else {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(entries, key = { it.id }) { node ->
                                val checked = bmSelected?.contains(node.id) == true
                                BmRow(
                                    node = node,
                                    selecting = selecting,
                                    checked = checked,
                                    onClick = {
                                        when {
                                            selecting -> bmSelected =
                                                if (checked) bmSelected!!.minus(node.id)
                                                else bmSelected!!.plus(node.id)
                                            node.type == NodeType.FOLDER -> bmPath = bmPath + node.id
                                            else -> onOpenUrl(node.url)
                                        }
                                    },
                                    onLongClick = {
                                        if (selecting) {
                                            bmSelected =
                                                if (checked) bmSelected!!.minus(node.id)
                                                else bmSelected!!.plus(node.id)
                                        } else bmMenuNode = node
                                    },
                                    onCheck = {
                                        bmSelected =
                                            if (checked) bmSelected!!.minus(node.id)
                                            else bmSelected!!.plus(node.id)
                                    }
                                )
                            }
                        }
                    }
                }

                // 长按菜单：书签 / 文件夹
                bmMenuNode?.let { node ->
                    AlertDialog(
                        onDismissRequest = { bmMenuNode = null },
                        title = {
                            Text(node.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        text = {
                            Column {
                                if (node.type == NodeType.BOOKMARK) {
                                    MenuOption("修改名称") {
                                        bmDialog = BmDialog.Rename(node); bmMenuNode = null
                                    }
                                    MenuOption("修改网址") {
                                        bmDialog = BmDialog.EditUrl(node); bmMenuNode = null
                                    }
                                    MenuOption("移动") {
                                        bmDialog = BmDialog.Move(node); bmMenuNode = null
                                    }
                                    MenuOption("删除书签") {
                                        bmDialog = BmDialog.Delete(
                                            setOf(node.id),
                                            "将删除书签「${node.title}」，不可恢复。"
                                        )
                                        bmMenuNode = null
                                    }
                                    MenuOption("多选") {
                                        bmSelected = setOf(node.id); bmMenuNode = null
                                    }
                                    MenuOption("删除全部书签") {
                                        bmDialog = BmDialog.Delete(
                                            emptySet(),
                                            "将删除全部书签与文件夹，不可恢复。"
                                        )
                                        bmMenuNode = null
                                    }
                                } else {
                                    MenuOption("重命名文件夹") {
                                        bmDialog = BmDialog.Rename(node); bmMenuNode = null
                                    }
                                    MenuOption("新建子文件夹") {
                                        bmDialog = BmDialog.NewFolder(node.id); bmMenuNode = null
                                    }
                                    MenuOption("删除文件夹") {
                                        bmDialog = BmDialog.Delete(
                                            setOf(node.id),
                                            "将删除文件夹「${node.title}」及其全部内容，不可恢复。"
                                        )
                                        bmMenuNode = null
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { bmMenuNode = null }) { Text("取消") }
                        }
                    )
                }

                // 编辑 / 新建 / 删除二次确认弹窗
                when (val d = bmDialog) {
                    is BmDialog.Rename -> {
                        var text by remember(d.node.id) { mutableStateOf(d.node.title) }
                        AlertDialog(
                            onDismissRequest = { bmDialog = null },
                            title = { Text(if (d.node.type == NodeType.FOLDER) "重命名文件夹" else "修改名称") },
                            text = {
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    singleLine = true,
                                    label = { Text("名称") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { if (BookmarkStore.rename(d.node.id, text)) bmDialog = null },
                                    enabled = text.isNotBlank()
                                ) { Text("保存") }
                            },
                            dismissButton = { TextButton(onClick = { bmDialog = null }) { Text("取消") } }
                        )
                    }
                    is BmDialog.EditUrl -> {
                        var text by remember(d.node.id) { mutableStateOf(d.node.url) }
                        AlertDialog(
                            onDismissRequest = { bmDialog = null },
                            title = { Text("修改网址") },
                            text = {
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    singleLine = true,
                                    label = { Text("网址") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (BookmarkStore.updateUrl(d.node.id, text)) bmDialog = null
                                    else Toast.makeText(context, "网址无效或已存在", Toast.LENGTH_SHORT).show()
                                }) { Text("保存") }
                            },
                            dismissButton = { TextButton(onClick = { bmDialog = null }) { Text("取消") } }
                        )
                    }
                    is BmDialog.NewFolder -> {
                        var text by remember(d.parentId) { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { bmDialog = null },
                            title = { Text("新建文件夹") },
                            text = {
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    singleLine = true,
                                    label = { Text("文件夹名称") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (BookmarkStore.addFolder(d.parentId, text) != null) bmDialog = null
                                        else Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = text.isNotBlank()
                                ) { Text("创建") }
                            },
                            dismissButton = { TextButton(onClick = { bmDialog = null }) { Text("取消") } }
                        )
                    }
                    is BmDialog.AddBookmark -> {
                        FolderPickDialog(
                            title = "收藏到文件夹",
                            onPick = { fid ->
                                val added = BookmarkStore.add(d.url, d.title, fid)
                                Toast.makeText(
                                    context,
                                    if (added) "已收藏" else "该页面已在收藏夹中或不可收藏",
                                    Toast.LENGTH_SHORT
                                ).show()
                                bmDialog = null
                            },
                            onDismiss = { bmDialog = null }
                        )
                    }
                    is BmDialog.Move -> {
                        FolderPickDialog(
                            title = "移动「${d.node.title}」到",
                            onPick = { fid ->
                                if (BookmarkStore.move(d.node.id, fid)) {
                                    Toast.makeText(context, "已移动", Toast.LENGTH_SHORT).show()
                                    bmDialog = null
                                } else {
                                    Toast.makeText(context, "已在该文件夹中", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDismiss = { bmDialog = null }
                        )
                    }
                    is BmDialog.Delete -> {
                        AlertDialog(
                            onDismissRequest = { bmDialog = null },
                            title = { Text("确认删除") },
                            text = { Text(d.message, fontSize = 14.sp) },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (d.ids.isEmpty()) BookmarkStore.clearAll()
                                    else BookmarkStore.removeIds(d.ids)
                                    bmSelected = null
                                    bmDialog = null
                                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = { TextButton(onClick = { bmDialog = null }) { Text("取消") } }
                        )
                    }
                    null -> {}
                }
            }

            "downloads" -> {
                SheetHeader("下载管理") { page = "main" }
                Spacer(Modifier.height(8.dp))
                val dlTasks by DownloadStore.tasks.collectAsState()
                var dlDeleteId by remember { mutableStateOf<String?>(null) }
                if (dlTasks.isEmpty()) {
                    Text("暂无下载任务", fontSize = 13.sp, color = Color.Gray)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dlTasks) { t ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(12.dp)
                            ) {
                                Text(t.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                when (t.state) {
                                    DlState.RUNNING -> {
                                        val pct = if (t.total > 0) (t.done * 100 / t.total).toInt() else -1
                                        LinearProgressIndicator(
                                            progress = { if (pct >= 0) pct / 100f else 0f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            if (pct >= 0) "下载中 $pct% · ${DownloadStore.fmtSize(t.done)} / ${DownloadStore.fmtSize(t.total)}"
                                            else "下载中 · ${DownloadStore.fmtSize(t.done)}",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    DlState.DONE -> Text(
                                        "已完成 · ${DownloadStore.fmtSize(t.total)}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    DlState.FAILED -> Text(
                                        "失败：${t.error}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (t.state == DlState.FAILED) {
                                        TextButton(onClick = { DownloadStore.retry(t.id) }) { Text("重试", fontSize = 13.sp) }
                                    }
                                    if (t.state == DlState.DONE) {
                                        TextButton(onClick = {
                                            if (!DownloadStore.open(context, t)) {
                                                Toast.makeText(context, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show()
                                            }
                                        }) { Text("打开", fontSize = 13.sp) }
                                    }
                                    TextButton(onClick = { dlDeleteId = t.id }) {
                                        Text("删除", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                dlDeleteId?.let { id ->
                    AlertDialog(
                        onDismissRequest = { dlDeleteId = null },
                        title = { Text("确认删除") },
                        text = { Text("将删除下载记录与文件，不可恢复。", fontSize = 14.sp) },
                        confirmButton = {
                            TextButton(onClick = {
                                DownloadStore.delete(id)
                                dlDeleteId = null
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { dlDeleteId = null }) { Text("取消") } }
                    )
                }
            }

            "history" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = "main" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                    Text("浏览记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        HistoryStore.clear()
                        history = emptyList()
                        Toast.makeText(context, "浏览记录已清空", Toast.LENGTH_SHORT).show()
                    }) { Text("清空") }
                }
                if (history.isEmpty()) {
                    Text(
                        "暂无浏览记录" + if (incognito) "（无痕模式开启中）" else "",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth()
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(history, key = { it.url + it.time }) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenUrl(entry.url) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.title,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${timeFmt.format(Date(entry.time))} · ${entry.url}",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    HistoryStore.remove(entry)
                                    history = HistoryStore.list()
                                }) { Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
            }

            "about" -> {
                SheetHeader("关于应用") { page = "main" }
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                ) {
                    Text("ClashBrowser", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("v1.16.1 · 轻量无广告代理浏览器", fontSize = 12.sp, color = Color.Gray)
                    Text("Made by Morin with Qwen.", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))

                    Text("基于开源项目", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    AboutLine("mihomo（MetaCubeX/mihomo）", "Clash Meta 代理内核 · GPL-3.0")
                    AboutLine("meta-rules-dat（MetaCubeX）", "GeoIP / GeoSite 规则数据 · GPL-3.0")
                    AboutLine("Jetpack Compose / AndroidX / WebKit", "界面与 WebView 代理 · Apache-2.0")
                    AboutLine("OkHttp（Square）", "订阅拉取与内核 API 通信 · Apache-2.0")
                    AboutLine("kotlinx.coroutines（JetBrains）", "异步与进程管理 · Apache-2.0")
                    AboutLine("SnakeYAML", "订阅 YAML 校验 · Apache-2.0")
                    Spacer(Modifier.height(16.dp))

                    Text("更新记录", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "版本号已按 semver 梳理：新增功能=次版本、修复=修订号；旧号对照见仓库 CHANGELOG。",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(6.dp))
                    ChangelogBlock(
                        "v1.16.1（当前）",
                        "安全加固五项：配置显式 bind-address 127.0.0.1 + 每次启动随机 API secret（全部 REST 调用带鉴权）；" +
                            "WebRTC 防泄露（无痕强制禁用 RTCPeerConnection，普通标签更多里可开关）；" +
                            "订阅链接/日志默认掩码 token 参数，订阅与配置 Keystore AES/GCM 加密静置、退出清运行明文，" +
                            "书签导入≤5MB/2万条、订阅与 YAML≤2MB 兜底；" +
                            "JS 注入限定 http/https 作用域、外部跳转拦截提示、FileProvider 最小授权复核；" +
                            "内核锁定 mihomo v1.19.34 + SHA-256 校验、Geo 数据库固化校验、release 关 debuggable 开混淆。"
                    )
                    Spacer(Modifier.height(6.dp))
                    ChangelogBlock(
                        "v1.16.0",
                        "地址栏搜索建议/自动补全：输入防抖下拉本地历史/收藏匹配与当前引擎 suggest（Google/Bing/百度/DuckDuckGo），" +
                            "点按直接加载或检索，无痕不出建议，更多里可开关；" +
                            "网页定位权限（geolocation API）：来源确认框（允许/拒绝+记住选择）+ 系统运行时权限，" +
                            "按来源持久化决定，更多-定位权限记录可查看/删除/清空（二次确认）。"
                    )
                    Spacer(Modifier.height(6.dp))
                    ChangelogBlock(
                        "v1.15.1",
                        "修复网页输入框长按被自定义菜单拦截：可编辑节点放行系统选择/全选/剪切/复制/粘贴菜单；" +
                            "订阅链接框新增一键粘贴按钮（读剪贴板），长按浮窗受限时仍可粘贴；" +
                            "页顶代理/内核提示横框右侧新增关闭按钮，状态变化时重新提示；" +
                            "阅读模式与资源嗅探移入工具箱-更多，浏览器标识恢复主列表独立入口（移动/桌面切换）。"
                    )
                    Spacer(Modifier.height(6.dp))
                    ChangelogBlock(
                        "v1.15.0",
                        "新增页内查找（高亮计数/上下跳转）、阅读模式（正文重排/字号可调）、资源嗅探（媒体流列表/复制/下载/打开）；" +
                            "工具箱-更多整合四项设置：搜索引擎可选、桌面模式真视口、无痕隔离（退出清 Cookie/缓存）、订阅信息解析加固与刷新；" +
                            "下载管理保持独立入口；历史版本号按 semver 重排。"
                    )
                    ChangelogBlock(
                        "v1.14.0",
                        "新增下载管理器：接管网页非图片下载，任务列表含进度/失败重试/打开/删除，API29+ 存入系统 Download/ClashBrowser，" +
                            "旧系统存入应用私有目录免存储权限；新增文件上传：网页 input type=file 唤起系统文件选择器（含多选）。"
                    )
                    ChangelogBlock(
                        "v1.13.2",
                        "导入订阅/本地配置时拒绝指向本机 7890 的回环代理节点（防代理自环），port/socks-port 强制归零防端口冲突；" +
                            "NOTICE 中 mihomo 许可署名修正为 GPL-3.0；新增 JVM 单元测试（广告拦截、配置校验、地址解析）与 CI lint 门禁。"
                    )
                    ChangelogBlock(
                        "v1.13.1",
                        "修复代理启用失败却静默直连：启用态仅在内核与 WebView 代理均就绪后持久化，失败回滚开关并明确提示直连；" +
                            "开关与页顶横幅按内核实际状态显示「实际运行」；冷启动/回前台启动失败自动回退直连并提示。"
                    )
                    ChangelogBlock(
                        "v1.13.0",
                        "网页内点击打开新页面默认新建标签、不覆盖当前页，工具箱-更多新增「新页面在新标签打开」开关；" +
                            "收藏当前页时弹窗选择目标文件夹（根目录或自建文件夹，按层级展示）；" +
                            "长按书签菜单新增「移动」，可把书签移入任意文件夹。"
                    )
                    ChangelogBlock(
                        "v1.12.0",
                        "任意页面长按可点击对象弹出上下文菜单：后台打开、新标签打开、查看图片、保存图片、" +
                            "下载图片、分享图片、看图模式（页面图片网格+全屏浏览）、页面信息、标记广告（自定义拦截域名）、" +
                            "复制链接文本、扫描二维码、复制链接、分享；菜单按链接/图片/普通元素动态给出；" +
                            "图片拉取与 WebView 同走应用内代理。"
                    )
                    ChangelogBlock(
                        "v1.11.1",
                        "DRM 加密视频播放修复：放行网页受保护媒体权限请求（RESOURCE_PROTECTED_MEDIA_ID，" +
                            "主线程 grant）；启用全局与第三方 Cookie 保障许可证/跨域 CDN 鉴权；" +
                            "移动端标识去除内嵌 WebView 标记、保留标准 Android Chrome UA；" +
                            "Widevine/Castlabs/BuyDRM 等许可证服务商与主流媒资 CDN 加入拦截白名单；" +
                            "EME 探测改为 4 秒限时等待，权限放行后优先走系统 Widevine，挂起环境才回退非加密流。"
                    )
                    ChangelogBlock(
                        "v1.11.0",
                        "修复部分在线视频报\u201c不支持这种媒体类型/请开启 DRM\u201d无法播放：" +
                            "WebView 内 DRM 探测永久挂起时改为快速回退非加密流；新增网页视频全屏播放支持；" +
                            "媒体流与切片不再被广告拦截误伤；底栏调整为后退/前进/主页/标签/工具箱，" +
                            "刷新按钮移至页顶内核小圆左侧，地址栏展开时自动隐藏、收缩后复原。"
                    )
                    ChangelogBlock(
                        "v1.10.0",
                        "打开网页后点地址栏默认全选框内内容，直接输入即可覆盖；" +
                            "收藏夹支持多级文件夹：新建/重命名/嵌套子文件夹，导入书签文件保留原有文件夹层级；" +
                            "长按书签或文件夹弹出菜单：修改名称、修改网址、删除、多选、删除全部书签，" +
                            "所有删除操作均需二次弹窗确认。"
                    )
                    ChangelogBlock(
                        "v1.9.0",
                        "任何页面点开顶部搜索框后，点网页空白处即可退出输入，未输入新网址时当前网页保持不动；" +
                            "收藏夹新增书签导入/导出：可导出为通用书签文件，也可从 Via、Chrome、Firefox 等" +
                            "浏览器导出的书签文件一键导入，自动去重并按既有格式编排。"
                    )
                    ChangelogBlock(
                        "v1.8.0",
                        "主页点空白处即可收起键盘；主页隐藏页顶搜索框、仅保留内核状态小圆，" +
                            "打开网页后地址栏恢复显示当前网址；长按主页圆形图标可编辑：" +
                            "从主页移除、自定义图标字母、对应网址与显示名称。"
                    )
                    ChangelogBlock(
                        "v1.7.0",
                        "全新本地主页：浏览器图标 + 搜索框 + 可自选的收藏夹圆形图标（最多 8 个），" +
                            "新建标签与回到主页均打开主页；顶部搜索框聚焦时动画扩展占满页顶并带清空按钮；" +
                            "工具箱新增「更多」页，可开关意外退出时恢复页面；关于页新增开源声明。"
                    )
                    ChangelogBlock(
                        "v1.6.2",
                        "配置页改为卡片式档案管理：名称、流量用量、到期日期、用量进度条、" +
                            "更新时间一目了然，点卡片切换配置，菜单更新/删除；应用图标改为灰白配色；" +
                            "移除模式切换的提示文字与底部弹窗。"
                    )
                    ChangelogBlock(
                        "v1.6.1",
                        "代理模式语义优化：全局模式所有流量经 GLOBAL 组转发（覆盖订阅规则，" +
                            "GLOBAL 选中直连时自动改选真实节点）；规则模式按订阅规则分流" +
                            "（国内直连、境外代理），节点面板不再显示 GLOBAL 组；新增模式说明文字。"
                    )
                    ChangelogBlock(
                        "v1.6.0",
                        "安全键盘调起加固：密码框获焦自动重建输入连接并重新弹出键盘，" +
                            "一次点击即稳定调起系统安全键盘；新增意外关闭恢复：应用被直接杀后台后" +
                            "再次打开时弹窗询问是否恢复上次未关闭的页面（弹窗跟随应用主题）。"
                    )
                    ChangelogBlock(
                        "v1.5.0",
                        "新增深/浅色/跟随系统外观切换，网页同步深浅色（保留标签历史）；" +
                            "后台占用精简：挂后台暂停网页定时器与渲染、收紧网络空闲连接池、移除未使用依赖。"
                    )
                    ChangelogBlock(
                        "v1.4.0",
                        "密码输入改写为原生密码类型，可触发 vivo 等系统安全键盘；" +
                            "工具箱新增收藏夹：收藏当前页、搜索、打开与删除，统一管理常用页面。"
                    )
                    ChangelogBlock(
                        "v1.3.1",
                        "修复挂后台一段时间后内核被系统查杀导致的\u201c内核未运行/配置消失\u201d问题：" +
                            "回前台自动无感重启内核、运行中被查杀自动复活、配置不再被健康检查误重置；" +
                            "节点列表改为紧凑灰白卡片，选中节点浅蓝高亮；工具箱子页面与分组展开加入轻量动画。"
                    )
                    ChangelogBlock(
                        "v1.3.0",
                        "修复退出重进后代理配置丢失的问题（残留进程清理、配置原子写入与备份自愈、启动失败自动重试）；" +
                            "工具箱新增无痕模式开关与浏览记录；网页密码输入框自动调用输入法私密键盘模式。"
                    )
                    ChangelogBlock(
                        "v1.2.0",
                        "节点改为卡片式展示（国旗/协议/延迟），策略组支持展开与收起；" +
                            "底部栏新增工具箱；浏览器标识切换移入工具箱；新增关于应用页。"
                    )
                    ChangelogBlock(
                        "v1.1.0",
                        "新增代理总开关；全局/规则/直连模式切换；默认主页改为 Google；" +
                            "长按标签按钮快速新建标签页；新增浏览器标识（手机/电脑）切换。"
                    )
                    ChangelogBlock(
                        "v1.0.0（初版）",
                        "内置 mihomo 内核与 Geo 数据库自动集成；WebView 局部代理（免 VPN）；" +
                            "广告追踪拦截；多标签浏览；订阅导入与 YAML 编辑；节点切换与批量测速；内核日志。"
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("开源声明", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "本软件遵循 GPL-3.0 协议开源；内置 mihomo 内核同为 GPL-3.0 协议，" +
                            "GeoIP / GeoSite 数据来自 MetaCubeX/meta-rules-dat（GPL-3.0）；" +
                            "AndroidX / Jetpack Compose、OkHttp、kotlinx.coroutines、SnakeYAML 等依赖遵循 Apache-2.0 协议。" +
                            "欢迎自由使用、修改与分发，请保留相应许可与署名。",
                        fontSize = 12.sp,
                        color = themeColor(Color(0xFF555555), Color(0xFFB8BCC0)),
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            else -> {
                Text("工具箱", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                ToolboxRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "浏览记录",
                    desc = "查看、打开或删除历史记录"
                ) { page = "history" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = Icons.Default.Star,
                    title = "收藏夹",
                    desc = "收藏与管理常用页面"
                ) { page = "bookmarks" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = Icons.Default.KeyboardArrowDown,
                    title = "下载管理",
                    desc = "下载任务：进度、重试、打开、删除"
                ) { page = "downloads" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = painterResource(R.drawable.ic_ua),
                    title = "浏览器标识",
                    desc = if (desktopUa) "当前：桌面标识 + 桌面视口" else "当前：移动标识"
                ) { page = "ua" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = Icons.Default.Settings,
                    title = "外观模式",
                    desc = when (themeMode) {
                        "dark" -> "当前：深色"
                        "light" -> "当前：浅色"
                        else -> "当前：跟随系统"
                    }
                ) { page = "theme" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = Icons.Default.MoreVert,
                    title = "更多",
                    desc = "无痕/搜索引擎/订阅信息/阅读/嗅探等"
                ) { page = "more" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = Icons.Default.Info,
                    title = "关于应用",
                    desc = "开源项目与更新记录"
                ) { page = "about" }
            }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ToolboxRow(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    ToolboxRow(rememberVectorPainter(icon), title, desc, onClick)
}

@Composable
private fun ToolboxRow(icon: Painter, title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 12.sp, color = Color.Gray)
        }
        Text("›", fontSize = 20.sp, color = Color.Gray)
    }
}

@Composable
private fun UaOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(desc, fontSize = 12.sp, color = Color.Gray)
            }
            if (selected) {
                Icon(Icons.Default.Check, "已选中", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AboutLine(name: String, desc: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(desc, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun ChangelogBlock(version: String, content: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(version, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(content, fontSize = 12.sp, color = themeColor(Color(0xFF555555), Color(0xFFB8BCC0)), lineHeight = 18.sp)
    }
}

/** 收藏夹列表行：文件夹/书签图标 + 标题 + 副标题，多选时显示勾选框。 */
@Composable
private fun BmRow(
    node: BookmarkNode,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheck: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.type == NodeType.FOLDER) {
            Icon(
                painterResource(R.drawable.ic_folder),
                null,
                tint = Color(0xFF757575),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                Icons.Default.Star,
                null,
                tint = Color(0xFF5AA9E6),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(node.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (node.type == NodeType.FOLDER) "${node.children.size} 项" else node.url,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selecting) {
            Checkbox(checked = checked, onCheckedChange = { onCheck() })
        }
    }
}

@Composable
private fun MenuOption(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
    }
}

/** 收藏夹编辑/新建/删除确认弹窗描述。Delete 的 ids 为空表示删除全部。 */
private sealed interface BmDialog {
    data class Rename(val node: BookmarkNode) : BmDialog
    data class EditUrl(val node: BookmarkNode) : BmDialog
    data class NewFolder(val parentId: String) : BmDialog
    data class Delete(val ids: Set<String>, val message: String) : BmDialog
    data class AddBookmark(val url: String, val title: String) : BmDialog
    data class Move(val node: BookmarkNode) : BmDialog
}

/** 全部文件夹（先序 + 层级深度），供选择目标文件夹弹窗使用。 */
private fun folderOptions(): List<Pair<BookmarkNode, Int>> {
    val out = mutableListOf<Pair<BookmarkNode, Int>>()
    fun walk(n: BookmarkNode, depth: Int) {
        for (c in n.children) {
            if (c.type == NodeType.FOLDER) {
                out.add(c to depth)
                walk(c, depth + 1)
            }
        }
    }
    walk(BookmarkStore.root(), 0)
    return out
}

/** 选择目标文件夹弹窗：根目录 + 全部自建文件夹（按层级缩进）。 */
@Composable
private fun FolderPickDialog(title: String, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val folders = remember { folderOptions() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item {
                    Text(
                        "根目录",
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(null) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                    )
                }
                items(folders) { (n, depth) ->
                    Text(
                        n.title,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(n.id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .padding(start = (12 + depth * 16).dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}