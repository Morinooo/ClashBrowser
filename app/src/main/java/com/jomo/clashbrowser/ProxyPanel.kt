package com.jomo.clashbrowser

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 代理配置中心（BottomSheet 内容）：
 * 运行状态 / 订阅导入 / YAML 编辑 / 策略组节点切换与测速 / 内核日志
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyPanel(
    proxyEnabled: Boolean,
    proxyReady: Boolean,
    onProxyEnabledChange: (Boolean) -> Unit,
    onProxyReadyChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coreState by ProxyManager.state.collectAsState()
    val logs by ProxyManager.logs.collectAsState()

    var profiles by remember { mutableStateOf(ProxyManager.listProfiles()) }
    var pendingDelete by remember { mutableStateOf<ProfileInfo?>(null) }
    var subUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<ProxyGroup>>(emptyList()) }
    var delays by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var testing by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var coreVersion by remember { mutableStateOf<String?>(null) }
    var coreMode by remember { mutableStateOf(ProxyManager.preferredMode()) }
    var nodeTypes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // 规则/直连模式隐藏 GLOBAL 组（该组仅全局模式生效）；全局模式 GLOBAL 置顶
    val visibleGroups = if (coreMode == "global") {
        groups.sortedBy { if (it.name == "GLOBAL") 0 else 1 }
    } else {
        groups.filter { it.name != "GLOBAL" }
    }

    fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_SHORT).show()

    suspend fun refresh() {
        val snap = ProxyManager.fetchProxies()
        groups = snap.groups
        delays = delays + snap.delays
        nodeTypes = snap.types
        coreVersion = ProxyManager.version()
        coreMode = ProxyManager.fetchMode() ?: coreMode
    }

    LaunchedEffect(coreState) {
        if (coreState == CoreState.RUNNING) {
            refresh()
        } else {
            // 内核未运行时清掉上一次的节点/策略组缓存，避免面板里残留"僵尸节点"
            // （用户看到"内核未运行"但下面还挂着已删除订阅的策略组）
            groups = emptyList()
            delays = emptyMap()
            nodeTypes = emptyMap()
            coreVersion = null
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ---------- 运行状态 / 代理总开关 / 模式切换 ----------
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor by animateColorAsState(
                        if (coreState == CoreState.RUNNING) Color(0xFF2E7D32) else Color(0xFFC62828),
                        label = "panelDot"
                    )
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (coreState) {
                                CoreState.RUNNING -> "内核运行中"
                                CoreState.STARTING -> "内核启动中…"
                                CoreState.ERROR -> "内核启动失败"
                                CoreState.STOPPED -> if (proxyEnabled) "内核未运行" else "代理已关闭"
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            coreVersion?.let { "mihomo $it · 混合端口 ${ProxyManager.mixedPort}" }
                                ?: (ProxyManager.lastError ?: "未配置节点时内核走直连规则"),
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(enabled = !busy && proxyEnabled, onClick = {
                        scope.launch {
                            busy = true
                            ProxyManager.shutdown()
                            val ok = ProxyManager.start()
                            val pr = if (ok) WebViewProxyHelper.applyProxy(context, force = true) else false
                            onProxyReadyChange(pr)
                            if (ok) {
                                ProxyManager.setMode(ProxyManager.preferredMode())
                                refresh()
                            }
                            busy = false
                            toast(if (ok) "内核已重启" else "启动失败，请查看内核日志")
                        }
                    }) { Text("重启内核") }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("启用代理", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = proxyEnabled && proxyReady,
                        enabled = !busy,
                        onCheckedChange = { enable ->
                            scope.launch {
                                busy = true
                                if (enable) {
                                    // 先启动内核并绑定 WebView 代理，全部就绪后才持久化"启用"，
                                    // 失败则回滚开关，杜绝"开关显示开、实际直连"的静默失败
                                    val ok = ProxyManager.start()
                                    val pr = if (ok) WebViewProxyHelper.applyProxy(context, force = true) else false
                                    if (ok && pr) {
                                        ProxyManager.setProxyEnabled(true)
                                        onProxyEnabledChange(true)
                                        onProxyReadyChange(true)
                                        ProxyManager.setMode(ProxyManager.preferredMode())
                                        refresh()
                                        toast("代理已启用")
                                    } else {
                                        if (ok) ProxyManager.shutdown()
                                        ProxyManager.setProxyEnabled(false)
                                        onProxyEnabledChange(false)
                                        onProxyReadyChange(false)
                                        toast("内核启动失败，代理未启用，当前直连")
                                    }
                                } else {
                                    ProxyManager.setProxyEnabled(false)
                                    onProxyEnabledChange(false)
                                    WebViewProxyHelper.clearProxy(context)
                                    ProxyManager.shutdown()
                                    onProxyReadyChange(false)
                                    groups = emptyList()
                                    toast("代理已关闭，切换为直连")
                                }
                                busy = false
                            }
                        }
                    )
                }
                if (proxyEnabled && coreState == CoreState.RUNNING) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("global" to "全局", "rule" to "规则", "direct" to "直连").forEach { (m, label) ->
                            FilterChip(
                                selected = coreMode == m,
                                onClick = {
                                    scope.launch {
                                        if (ProxyManager.setMode(m)) {
                                            coreMode = m
                                            refresh()
                                        } else {
                                            toast("模式切换失败")
                                        }
                                    }
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        // ---------- 订阅导入 ----------
        item {
            Column {
                Text("机场订阅", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = subUrl,
                    onValueChange = { subUrl = it },
                    placeholder = { Text("粘贴订阅链接（http/https）", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp),
                    trailingIcon = {
                        // 一键粘贴：部分机型系统长按浮窗受限时仍可完成粘贴
                        TextButton(onClick = {
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            val clip = cm?.primaryClip
                            val text = if (clip != null && clip.itemCount > 0) {
                                clip.getItemAt(0).coerceToText(context).toString()
                            } else ""
                            if (text.isNotBlank()) {
                                subUrl = text.trim()
                                toast("已粘贴订阅链接")
                            } else {
                                toast("剪贴板为空")
                            }
                        }) {
                            Text("粘贴", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    enabled = !busy && subUrl.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            message = "正在拉取订阅并校验 YAML…"
                            val err = ProxyManager.updateSubscription(subUrl)
                            if (err == null) {
                                ProxyManager.addSubscription(subUrl.trim())
                                profiles = ProxyManager.listProfiles()
                                if (ProxyManager.state.value == CoreState.RUNNING) {
                                    onProxyReadyChange(WebViewProxyHelper.applyProxy(context, force = true))
                                }
                                refresh()
                                message = null
                                toast("订阅已更新并热重载")
                            } else {
                                message = err
                            }
                            busy = false
                        }
                    }
                ) { Text(if (busy) "处理中…" else "更新订阅") }
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }

        // ---------- 配置档案卡片（名称/流量/到期/更新时间，点按切换，⋮ 更新或删除） ----------
        items(profiles, key = { it.url }) { p ->
            ProfileCard(
                p = p,
                busy = busy,
                onApply = {
                    scope.launch {
                        busy = true
                        val err = ProxyManager.applyProfile(p.url)
                        busy = false
                        if (err == null) {
                            profiles = ProxyManager.listProfiles()
                            if (ProxyManager.state.value == CoreState.RUNNING) {
                                onProxyReadyChange(WebViewProxyHelper.applyProxy(context, force = true))
                            }
                            refresh()
                            toast("已切换配置：${p.name}")
                        } else {
                            toast(err)
                        }
                    }
                },
                onUpdate = {
                    scope.launch {
                        busy = true
                        val err = ProxyManager.updateSubscription(p.url)
                        busy = false
                        if (err == null) {
                            ProxyManager.addSubscription(p.url)
                            profiles = ProxyManager.listProfiles()
                            if (ProxyManager.state.value == CoreState.RUNNING) {
                                onProxyReadyChange(WebViewProxyHelper.applyProxy(context, force = true))
                            }
                            refresh()
                            toast("订阅已更新：${p.name}")
                        } else {
                            toast(err)
                        }
                    }
                },
                onDelete = { pendingDelete = p }
            )
        }

        // ---------- 操作按钮 ----------
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showEditor = true }) { Text("编辑配置") }
                OutlinedButton(
                    enabled = coreState == CoreState.RUNNING && groups.isNotEmpty() && !testing,
                    onClick = {
                        scope.launch {
                            testing = true
                            val names = groups.flatMap { it.all }.distinct()
                                .filter { it != "DIRECT" && it != "REJECT" && it != "PASS" }
                            val sem = Semaphore(6)
                            names.map { name ->
                                async {
                                    sem.withPermit {
                                        val d = ProxyManager.testDelay(name) ?: -1
                                        delays = delays + (name to d)
                                    }
                                }
                            }.awaitAll()
                            testing = false
                            toast("测速完成")
                        }
                    }
                ) { Text(if (testing) "测速中…" else "全部测速") }
            }
        }

        // ---------- 策略组与节点（紧凑卡片，可折叠，带展开动画） ----------
        visibleGroups.forEachIndexed { gi, group ->
            item(key = "group_${group.name}") {
                val expanded = expandedGroups[group.name] ?: (gi == 0)
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expandedGroups[group.name] = !expanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${group.all.size} 节点 · ${group.now}",
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (expanded) "收起 ▾" else "展开 ▸",
                            fontSize = 12.sp,
                            color = Color(0xFF5AA9E6)
                        )
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
                        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            group.all.chunked(2).forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    row.forEach { node ->
                                        NodeCard(
                                            name = node,
                                            type = nodeTypes[node] ?: "",
                                            delay = delays[node],
                                            selected = node == group.now,
                                            onSelect = {
                                                if (group.type == "Selector") {
                                                    scope.launch {
                                                        if (ProxyManager.selectNode(group.name, node)) {
                                                            refresh()
                                                            toast("已切换: $node")
                                                        } else {
                                                            toast("切换失败")
                                                        }
                                                    }
                                                } else {
                                                    toast("该组为自动策略（${group.type}），不支持手动选择")
                                                }
                                            },
                                            onTest = {
                                                scope.launch {
                                                    val d = ProxyManager.testDelay(node) ?: -1
                                                    delays = delays + (node to d)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 内核日志 ----------
        item {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showLogs = !showLogs }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "内核日志（${logs.size} 行）",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (showLogs) "收起 ▲" else "展开 ▼", fontSize = 12.sp)
                }
                if (showLogs) {
                    Surface(
                        color = Color(0xFF101418),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    ) {
                        val scroll = rememberScrollState()
                        LaunchedEffect(logs.size) { scroll.animateScrollTo(scroll.maxValue) }
                        Column(Modifier.verticalScroll(scroll).padding(8.dp)) {
                            logs.takeLast(120).forEach { line ->
                                Text(
                                    line,
                                    color = Color(0xFF9CCC65),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 删除配置确认 ----------
    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除配置") },
            text = {
                val activeHint = if (p.active) {
                    "\n\n该配置当前正在生效：删除后会切换到其它配置；若无其它配置，将恢复为默认直连配置。"
                } else {
                    ""
                }
                Text("将删除配置档案「${p.name}」及本地保存的 YAML 副本，删除后不可恢复。$activeHint")
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            busy = true
                            val err = ProxyManager.removeSubscriptionAndApply(p.url)
                            busy = false
                            profiles = ProxyManager.listProfiles()
                            // 先清掉旧节点/策略组缓存，避免删除后仍显示上一个订阅的节点
                            groups = emptyList()
                            delays = emptyMap()
                            nodeTypes = emptyMap()
                            if (ProxyManager.state.value == CoreState.RUNNING) refresh()
                            toast(if (err == null) "已删除配置：${p.name}" else err)
                        }
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    // ---------- 手动 YAML 编辑器（全屏对话框） ----------
    if (showEditor) {
        var yamlText by remember { mutableStateOf(ProxyManager.currentConfigText()) }
        Dialog(
            onDismissRequest = { showEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize().imePadding()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        TextButton(onClick = { showEditor = false }) { Text("取消") }
                        Text(
                            "编辑 config.yaml",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                val err = ProxyManager.saveConfigAndApply(yamlText)
                                busy = false
                                if (err == null) {
                                    ProxyManager.markCustomConfig()
                                    profiles = ProxyManager.listProfiles()
                                    if (ProxyManager.state.value == CoreState.RUNNING) {
                                        onProxyReadyChange(WebViewProxyHelper.applyProxy(context, force = true))
                                    }
                                    refresh()
                                    showEditor = false
                                    toast("配置已保存并生效")
                                } else {
                                    toast(err)
                                }
                            }
                        }) { Text("保存生效") }
                    }
                    OutlinedTextField(
                        value = yamlText,
                        onValueChange = { yamlText = it },
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                }
            }
        }
    }
}

/**
 * 紧凑节点卡片：与浏览器统一的灰白配色，无 emoji。
 * 未选中：灰黑文字；选中：浅蓝文字 + 浅蓝描边（颜色渐变过渡）。
 * 点卡片切换节点，点右下角延迟文字单节点测速。
 */
@Composable
private fun NodeCard(
    name: String,
    type: String,
    delay: Int?,
    selected: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nameColor by animateColorAsState(
        if (selected) Color(0xFF5AA9E6) else themeColor(Color(0xFF424242), Color(0xFFD6D9DC)),
        animationSpec = tween(250),
        label = "nodeName"
    )
    val borderColor by animateColorAsState(
        if (selected) Color(0xFF90CAF9) else themeColor(Color(0xFFE3E6E8), Color(0xFF33383D)),
        animationSpec = tween(250),
        label = "nodeBorder"
    )
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) themeColor(Color(0xFFE3F2FD), Color(0xFF1B2C3D))
        else themeColor(Color(0xFFFAFBFC), Color(0xFF1E2124)),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.clickable(onClick = onSelect)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                name,
                color = nameColor,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    type.ifBlank { "Proxy" },
                    color = themeColor(Color(0xFF9E9E9E), Color(0xFF8A9096)),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    when {
                        delay == null -> "测速"
                        delay > 0 -> "${delay}ms"
                        else -> "超时"
                    },
                    color = if (selected) Color(0xFF5AA9E6)
                    else themeColor(Color(0xFF9E9E9E), Color(0xFF8A9096)),
                    fontSize = 10.5.sp,
                    modifier = Modifier
                        .clickable(onClick = onTest)
                        .padding(start = 6.dp)
                )
            }
        }
    }
}

/**
 * 配置档案卡片（参照主流 Clash 客户端配置页）：
 * 名称 + 流量用量/到期日期 + 用量进度条 + 相对更新时间；
 * 当前生效配置浅蓝高亮；点卡片切换配置，⋮ 菜单更新/删除。
 */
@Composable
private fun ProfileCard(
    p: ProfileInfo,
    busy: Boolean,
    onApply: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (p.active) themeColor(Color(0xFFE3F2FD), Color(0xFF1B2C3D))
        else themeColor(Color(0xFFFAFBFC), Color(0xFF1E2124)),
        border = BorderStroke(
            1.dp,
            if (p.active) Color(0xFF90CAF9) else themeColor(Color(0xFFE3E6E8), Color(0xFF33383D))
        ),
        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onApply)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, "更多操作", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("更新订阅") },
                            onClick = { menu = false; onUpdate() }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = { menu = false; onDelete() }
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (p.total > 0) "${fmtBytes(p.upload + p.download)} / ${fmtBytes(p.total)}"
                    else "无流量信息",
                    fontSize = 13.sp,
                    color = themeColor(Color(0xFF616161), Color(0xFFB0B6BB)),
                    modifier = Modifier.weight(1f)
                )
                if (p.expire > 0) {
                    Text(fmtDate(p.expire), fontSize = 13.sp, color = Color.Gray)
                }
            }
            if (p.total > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { ((p.upload + p.download).toFloat() / p.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF5AA9E6),
                    trackColor = themeColor(Color(0xFFE3E6E8), Color(0xFF33383D))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (p.updated > 0) fmtAgo(p.updated) else "尚未更新",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

private fun fmtBytes(b: Long): String {
    if (b <= 0) return "0B"
    val gb = b / 1073741824.0
    if (gb >= 1) return String.format(Locale.getDefault(), "%.1fGB", gb).replace(".0GB", "GB")
    val mb = b / 1048576.0
    if (mb >= 1) return String.format(Locale.getDefault(), "%.1fMB", mb).replace(".0MB", "MB")
    val kb = b / 1024.0
    if (kb >= 1) return String.format(Locale.getDefault(), "%.1fKB", kb).replace(".0KB", "KB")
    return "${b}B"
}

private fun fmtDate(epochSec: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochSec * 1000))

private fun fmtAgo(epochMs: Long): String {
    val d = System.currentTimeMillis() - epochMs
    val h = d / 3600000
    return when {
        h < 1 -> "${(d / 60000).coerceAtLeast(1)} 分钟前"
        h < 24 -> "$h 小时前"
        else -> "${h / 24} 天前"
    }
}