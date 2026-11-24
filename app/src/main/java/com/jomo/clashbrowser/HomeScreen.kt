package com.jomo.clashbrowser

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 首页圆形图标取标题首字符（中文取首字，字母大写）。 */
internal fun homeInitial(title: String): String {
    val c = title.trim().firstOrNull() ?: return "★"
    return c.uppercaseChar().toString()
}

/**
 * 本地主页：顶部浏览器图标 + 搜索框（回车走 Google 搜索/直达网址）+
 * 用户自选的收藏夹圆形图标（最多 8 个，4 列排布）+ 添加按钮。
 * 点空白处收起键盘；长按圆形图标编辑（移除/字母/网址/名称）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    entries: List<HomeEntry>,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (HomeEntry) -> Unit
) {
    val focus = LocalFocusManager.current
    var text by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focus.clearFocus() }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Icon(
            painterResource(
                if (MaterialTheme.colorScheme.surface.luminance() > 0.5f)
                    R.drawable.ic_home_logo_light else R.drawable.ic_home_logo_dark
            ),
            contentDescription = "ClashBrowser",
            tint = Color.Unspecified,
            modifier = Modifier.size(88.dp, 110.dp)
        )
        Spacer(Modifier.height(44.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("搜索或输入网址", fontSize = 14.sp) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp),
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                onSearch(text)
            }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(40.dp))
        val cells: List<HomeEntry?> = entries.take(HomeStore.MAX_PINS) + null
        cells.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { e ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { if (e == null) onAdd() else onOpen(e.link) },
                                onLongClick = { if (e != null) onEdit(e) }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(themeColor(Color(0xFFF0F2F4), Color(0xFF2A2E33))),
                            contentAlignment = Alignment.Center
                        ) {
                            if (e == null) {
                                Icon(
                                    Icons.Default.Add, "添加首页图标",
                                    tint = themeColor(Color(0xFF616161), Color(0xFFB0B6BB))
                                )
                            } else {
                                Text(
                                    e.letter,
                                    fontSize = if (e.letter.length > 1) 16.sp else 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = themeColor(Color(0xFF424242), Color(0xFFD6D9DC))
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            e?.name ?: "添加",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = themeColor(Color(0xFF616161), Color(0xFFB0B6BB))
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.width(72.dp)) }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (entries.isEmpty()) {
            Text(
                "点 + 从收藏夹选择要显示在首页的网页",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 首页图标管理：勾选收藏夹中的网页显示在首页（最多 8 个，按勾选顺序）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeIconsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bookmarks = remember { BookmarkStore.list() }
    var pinKeys by remember { mutableStateOf(HomeStore.pins().map { it.key }) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("首页图标", style = MaterialTheme.typography.titleMedium)
            Text(
                "选择显示在首页的收藏网页（最多 ${HomeStore.MAX_PINS} 个，按选择顺序排列）",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            if (bookmarks.isEmpty()) {
                Text(
                    "收藏夹为空，先在工具箱里收藏网页",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(bookmarks, key = { it.url }) { bm ->
                        val checked = pinKeys.contains(bm.url)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pinKeys = if (checked) {
                                        HomeStore.removePin(bm.url)
                                        HomeStore.pins().map { it.key }
                                    } else {
                                        if (pinKeys.size >= HomeStore.MAX_PINS) {
                                            Toast.makeText(
                                                context,
                                                "首页最多显示 ${HomeStore.MAX_PINS} 个图标",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@clickable
                                        }
                                        HomeStore.addPin(bm.url)
                                        HomeStore.pins().map { it.key }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(themeColor(Color(0xFFF0F2F4), Color(0xFF2A2E33))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    homeInitial(bm.title),
                                    fontSize = 16.sp,
                                    color = themeColor(Color(0xFF424242), Color(0xFFD6D9DC))
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bm.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bm.url, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}

/** 长按首页图标后的编辑弹窗：显示名称 / 图标字母 / 对应网址 / 从主页移除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePinEditSheet(entry: HomeEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(entry.name) }
    var letter by remember { mutableStateOf(entry.letter) }
    var link by remember { mutableStateOf(entry.link) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("编辑首页图标", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("主页显示名称") },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = letter,
                onValueChange = { if (it.length <= 2) letter = it },
                label = { Text("图标字母（最多 2 字符，留空用标题首字）") },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("对应网址") },
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    HomeStore.removePin(entry.key)
                    onDismiss()
                }) { Text("从主页移除", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val l = link.trim()
                    if (l.isNotEmpty() && !l.startsWith("http://") && !l.startsWith("https://")) {
                        Toast.makeText(context, "网址必须以 http(s):// 开头", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    HomeStore.updatePin(
                        entry.key,
                        name.trim().takeIf { it.isNotEmpty() },
                        letter.trim().takeIf { it.isNotEmpty() },
                        l.takeIf { it.isNotEmpty() }
                    )
                    onDismiss()
                }) { Text("保存") }
            }
        }
    }
}