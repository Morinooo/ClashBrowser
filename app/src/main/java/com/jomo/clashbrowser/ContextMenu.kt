package com.jomo.clashbrowser

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/** 长按命中对象的信息（链接/图片/页面）。 */
data class CtxInfo(
    val linkUrl: String,
    val linkText: String,
    val imageUrl: String,
    val imageAlt: String,
    val pageUrl: String,
    val pageTitle: String
) {
    val hasLink: Boolean get() = linkUrl.startsWith("http://") || linkUrl.startsWith("https://")
    val hasImage: Boolean get() = imageUrl.startsWith("http://") || imageUrl.startsWith("https://")
    val host: String
        get() = runCatching {
            URI(if (hasImage) imageUrl else if (hasLink) linkUrl else pageUrl).host
        }.getOrNull()?.lowercase() ?: ""
}

enum class CtxAction {
    BACKGROUND, NEW_TAB, VIEW_IMAGE, SAVE_IMAGE, DOWNLOAD_IMAGE, SHARE_IMAGE,
    IMAGE_MODE, PAGE_INFO, MARK_AD, COPY_TEXT, SCAN_QR, COPY_LINK, SHARE
}

/** 长按上下文菜单：按命中对象（链接/图片/普通可点击元素）动态给出可用操作。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuSheet(info: CtxInfo, onAction: (CtxAction) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                info.linkText.ifBlank { info.imageAlt.ifBlank { info.linkUrl.ifBlank { info.imageUrl.ifBlank { info.pageTitle } } } },
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            val items = buildList {
                if (info.hasLink) {
                    add(CtxAction.BACKGROUND to "后台打开")
                    add(CtxAction.NEW_TAB to "新标签打开")
                }
                if (info.hasImage) {
                    add(CtxAction.VIEW_IMAGE to "查看图片")
                    add(CtxAction.SAVE_IMAGE to "保存图片")
                    add(CtxAction.DOWNLOAD_IMAGE to "下载图片")
                    add(CtxAction.SHARE_IMAGE to "分享图片")
                }
                add(CtxAction.IMAGE_MODE to "看图模式")
                add(CtxAction.PAGE_INFO to "页面信息")
                add(CtxAction.MARK_AD to "标记广告")
                if (info.hasLink) add(CtxAction.COPY_TEXT to "复制链接文本")
                if (info.hasImage) add(CtxAction.SCAN_QR to "扫描二维码")
                add(CtxAction.COPY_LINK to "复制链接")
                add(CtxAction.SHARE to "分享")
            }
            items.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (act, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f).clickable { onAction(act) }
                        ) {
                            Text(
                                label,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 看图模式：抽取页面全部图片为网格，点按全屏浏览（左右切换）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageModeSheet(urls: List<String>, onDismiss: () -> Unit) {
    var viewer by remember { mutableIntStateOf(-1) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("看图模式 · ${urls.size} 张", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
            ) {
                items(urls) { url ->
                    Thumb(url) { viewer = urls.indexOf(url) }
                }
            }
        }
    }
    if (viewer in urls.indices) {
        Dialog(
            onDismissRequest = { viewer = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                FullImage(urls[viewer])
                IconButton(
                    onClick = { viewer = -1 },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) { Icon(Icons.Default.Close, "关闭", tint = Color.White) }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { if (viewer > 0) viewer-- },
                        enabled = viewer > 0
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一张", tint = Color.White) }
                    Text("${viewer + 1} / ${urls.size}", color = Color.White, fontSize = 13.sp)
                    IconButton(
                        onClick = { if (viewer < urls.size - 1) viewer++ },
                        enabled = viewer < urls.size - 1
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "下一张", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun Thumb(url: String, onClick: () -> Unit) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        val b = withContext(Dispatchers.IO) {
            MediaActions.fetch(url)?.let { MediaActions.decode(it, 4) }
        }
        bmp = b
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun FullImage(url: String) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        val b = withContext(Dispatchers.IO) {
            MediaActions.fetch(url)?.let { MediaActions.decode(it, 1) }
        }
        bmp = b
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

/** 页面信息：网址与标题，可复制网址。 */
@Composable
fun PageInfoDialog(url: String, title: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("页面信息") },
        text = {
            Column {
                Text("网址", fontSize = 11.sp, color = Color.Gray)
                Text(url, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("标题", fontSize = 11.sp, color = Color.Gray)
                Text(title.ifBlank { "（无标题）" }, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("复制网址") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 二维码扫描结果：可复制、可当网址打开。 */
@Composable
fun QrResultDialog(text: String, onCopy: () -> Unit, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val isUrl = text.startsWith("http://") || text.startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫描结果") },
        text = { Text(text, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onOpen, enabled = isUrl) { Text("打开") }
        },
        dismissButton = { TextButton(onClick = onCopy) { Text("复制") } }
    )
}