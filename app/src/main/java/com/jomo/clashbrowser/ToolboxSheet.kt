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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工具箱（BottomSheet 内容）：
 * 主页面 -> 浏览器标识 / 关于应用 两个子页面
 */
@Composable
fun ToolboxSheet(
    incognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onUaChange: (Boolean) -> Unit,
    currentUrl: String,
    currentTitle: String,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onImportBookmarks: () -> Unit,
    onExportBookmarks: () -> Unit,
    onOpenNewTabChange: (Boolean) -> Unit
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
            "ua" -> {
                SheetHeader("浏览器标识") { page = "main" }
                Spacer(Modifier.height(8.dp))
                UaOption(
                    title = "安卓手机",
                    desc = "系统默认移动端标识，适合触屏版网页",
                    selected = !desktopUa
                ) {
                    desktopUa = false
                    ProxyManager.setDesktopUa(false)
                    onUaChange(false)
                    Toast.makeText(context, "已切换为安卓手机标识", Toast.LENGTH_SHORT).show()
                }
                Spacer(Modifier.height(8.dp))
                UaOption(
                    title = "电脑",
                    desc = "Windows Chrome 标识，访问桌面版网页",
                    selected = desktopUa
                ) {
                    desktopUa = true
                    ProxyManager.setDesktopUa(true)
                    onUaChange(true)
                    Toast.makeText(context, "已切换为电脑标识", Toast.LENGTH_SHORT).show()
                }
            }

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
                    Text("v1.6.8 · 轻量无广告代理浏览器", fontSize = 12.sp, color = Color.Gray)
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
                    ChangelogBlock(
                        "v1.6.8（当前）",
                        "修复代理启用失败却静默直连：启用态仅在内核与 WebView 代理均就绪后持久化，失败回滚开关并明确提示直连；" +
                            "开关与页顶横幅按内核实际状态显示「实际运行」；冷启动/回前台启动失败自动回退直连并提示。"
                    )
                    ChangelogBlock(
                        "v1.6.7",
                        "网页内点击打开新页面默认新建标签、不覆盖当前页，工具箱-更多新增「新页面在新标签打开」开关；" +
                            "收藏当前页时弹窗选择目标文件夹（根目录或自建文件夹，按层级展示）；" +
                            "长按书签菜单新增「移动」，可把书签移入任意文件夹。"
                    )
                    ChangelogBlock(
                        "v1.6.6",
                        "任意页面长按可点击对象弹出上下文菜单：后台打开、新标签打开、查看图片、保存图片、" +
                            "下载图片、分享图片、看图模式（页面图片网格+全屏浏览）、页面信息、标记广告（自定义拦截域名）、" +
                            "复制链接文本、扫描二维码、复制链接、分享；菜单按链接/图片/普通元素动态给出；" +
                            "图片拉取与 WebView 同走应用内代理。"
                    )
                    ChangelogBlock(
                        "v1.6.5",
                        "DRM 加密视频播放修复：放行网页受保护媒体权限请求（RESOURCE_PROTECTED_MEDIA_ID，" +
                            "主线程 grant）；启用全局与第三方 Cookie 保障许可证/跨域 CDN 鉴权；" +
                            "移动端标识去除内嵌 WebView 标记、保留标准 Android Chrome UA；" +
                            "Widevine/Castlabs/BuyDRM 等许可证服务商与主流媒资 CDN 加入拦截白名单；" +
                            "EME 探测改为 4 秒限时等待，权限放行后优先走系统 Widevine，挂起环境才回退非加密流。"
                    )
                    ChangelogBlock(
                        "v1.6.4",
                        "修复部分在线视频报\u201c不支持这种媒体类型/请开启 DRM\u201d无法播放：" +
                            "WebView 内 DRM 探测永久挂起时改为快速回退非加密流；新增网页视频全屏播放支持；" +
                            "媒体流与切片不再被广告拦截误伤；底栏调整为后退/前进/主页/标签/工具箱，" +
                            "刷新按钮移至页顶内核小圆左侧，地址栏展开时自动隐藏、收缩后复原。"
                    )
                    ChangelogBlock(
                        "v1.6.3",
                        "打开网页后点地址栏默认全选框内内容，直接输入即可覆盖；" +
                            "收藏夹支持多级文件夹：新建/重命名/嵌套子文件夹，导入书签文件保留原有文件夹层级；" +
                            "长按书签或文件夹弹出菜单：修改名称、修改网址、删除、多选、删除全部书签，" +
                            "所有删除操作均需二次弹窗确认。"
                    )
                    ChangelogBlock(
                        "v1.6.2",
                        "任何页面点开顶部搜索框后，点网页空白处即可退出输入，未输入新网址时当前网页保持不动；" +
                            "收藏夹新增书签导入/导出：可导出为通用书签文件，也可从 Via、Chrome、Firefox 等" +
                            "浏览器导出的书签文件一键导入，自动去重并按既有格式编排。"
                    )
                    ChangelogBlock(
                        "v1.6.1",
                        "主页点空白处即可收起键盘；主页隐藏页顶搜索框、仅保留内核状态小圆，" +
                            "打开网页后地址栏恢复显示当前网址；长按主页圆形图标可编辑：" +
                            "从主页移除、自定义图标字母、对应网址与显示名称。"
                    )
                    ChangelogBlock(
                        "v1.6.0",
                        "全新本地主页：浏览器图标 + 搜索框 + 可自选的收藏夹圆形图标（最多 8 个），" +
                            "新建标签与回到主页均打开主页；顶部搜索框聚焦时动画扩展占满页顶并带清空按钮；" +
                            "工具箱新增「更多」页，可开关意外退出时恢复页面；关于页新增开源声明。"
                    )
                    ChangelogBlock(
                        "v1.4.5",
                        "配置页改为卡片式档案管理：名称、流量用量、到期日期、用量进度条、" +
                            "更新时间一目了然，点卡片切换配置，菜单更新/删除；应用图标改为灰白配色；" +
                            "移除模式切换的提示文字与底部弹窗。"
                    )
                    ChangelogBlock(
                        "v1.4.4",
                        "代理模式语义优化：全局模式所有流量经 GLOBAL 组转发（覆盖订阅规则，" +
                            "GLOBAL 选中直连时自动改选真实节点）；规则模式按订阅规则分流" +
                            "（国内直连、境外代理），节点面板不再显示 GLOBAL 组；新增模式说明文字。"
                    )
                    ChangelogBlock(
                        "v1.4.3",
                        "安全键盘调起加固：密码框获焦自动重建输入连接并重新弹出键盘，" +
                            "一次点击即稳定调起系统安全键盘；新增意外关闭恢复：应用被直接杀后台后" +
                            "再次打开时弹窗询问是否恢复上次未关闭的页面（弹窗跟随应用主题）。"
                    )
                    ChangelogBlock(
                        "v1.4.2",
                        "新增深/浅色/跟随系统外观切换，网页同步深浅色（保留标签历史）；" +
                            "后台占用精简：挂后台暂停网页定时器与渲染、收紧网络空闲连接池、移除未使用依赖。"
                    )
                    ChangelogBlock(
                        "v1.4.1",
                        "密码输入改写为原生密码类型，可触发 vivo 等系统安全键盘；" +
                            "工具箱新增收藏夹：收藏当前页、搜索、打开与删除，统一管理常用页面。"
                    )
                    ChangelogBlock(
                        "v1.4",
                        "修复挂后台一段时间后内核被系统查杀导致的\u201c内核未运行/配置消失\u201d问题：" +
                            "回前台自动无感重启内核、运行中被查杀自动复活、配置不再被健康检查误重置；" +
                            "节点列表改为紧凑灰白卡片，选中节点浅蓝高亮；工具箱子页面与分组展开加入轻量动画。"
                    )
                    ChangelogBlock(
                        "v1.3",
                        "修复退出重进后代理配置丢失的问题（残留进程清理、配置原子写入与备份自愈、启动失败自动重试）；" +
                            "工具箱新增无痕模式开关与浏览记录；网页密码输入框自动调用输入法私密键盘模式。"
                    )
                    ChangelogBlock(
                        "v1.2",
                        "节点改为卡片式展示（国旗/协议/延迟），策略组支持展开与收起；" +
                            "底部栏新增工具箱；浏览器标识切换移入工具箱；新增关于应用页。"
                    )
                    ChangelogBlock(
                        "v1.1",
                        "新增代理总开关；全局/规则/直连模式切换；默认主页改为 Google；" +
                            "长按标签按钮快速新建标签页；新增浏览器标识（手机/电脑）切换。"
                    )
                    ChangelogBlock(
                        "v1.0（初版）",
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
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("无痕模式", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (incognito) "已开启 · 不再记录浏览历史" else "关闭 · 正常记录浏览历史",
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
                                if (it) "无痕模式已开启" else "无痕模式已关闭",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
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
                    icon = Icons.Default.Phone,
                    title = "浏览器标识",
                    desc = if (desktopUa) "当前：电脑" else "当前：安卓手机"
                ) { page = "ua" }
                Spacer(Modifier.height(4.dp))
                ToolboxRow(
                    icon = Icons.Default.MoreVert,
                    title = "更多",
                    desc = "恢复页面等更多设置"
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