package com.jomo.clashbrowser

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class NodeType { FOLDER, BOOKMARK }

/** 收藏夹树节点：文件夹含 children，书签含 url。 */
data class BookmarkNode(
    val id: String,
    val type: NodeType,
    var title: String,
    var url: String = "",
    var time: Long = 0L,
    val children: MutableList<BookmarkNode> = mutableListOf()
)

data class Bookmark(val url: String, val title: String, val time: Long)

/**
 * 收藏夹存储（SharedPreferences JSON 树，支持多级文件夹，书签上限 1000 条）。
 * 旧版扁平数组自动迁移为根目录下的书签。
 */
object BookmarkStore {

    private lateinit var prefs: SharedPreferences
    private const val MAX_ENTRIES = 1000

    /** 数据变更通知（导入/收藏/删除/编辑后 UI 刷新用）。 */
    val version = MutableStateFlow(0)

    private var root: BookmarkNode = BookmarkNode("root", NodeType.FOLDER, "收藏夹")

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val v2 = prefs.getString("bookmarks_v2", null)
        if (v2 != null) {
            root = nodeFromJson(JSONObject(v2))
        } else {
            root = BookmarkNode("root", NodeType.FOLDER, "收藏夹")
            val arr = JSONArray(prefs.getString("bookmarks", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                root.children.add(
                    BookmarkNode(newId(), NodeType.BOOKMARK, o.optString("t"), o.getString("u"), o.optLong("ts"))
                )
            }
            persist()
        }
    }

    fun root(): BookmarkNode = root

    fun nodeById(id: String): BookmarkNode? = find(root) { it.id == id }

    fun nodeByUrl(url: String): BookmarkNode? = find(root) { it.type == NodeType.BOOKMARK && it.url == url }

    fun parentOf(id: String): BookmarkNode? = find(root) { n -> n.children.any { it.id == id } }

    /** 扁平书签列表（树内先序顺序），供主页图标选择等使用。 */
    fun list(): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        fun walk(n: BookmarkNode) {
            if (n.type == NodeType.BOOKMARK) out.add(Bookmark(n.url, n.title, n.time))
            n.children.forEach { walk(it) }
        }
        walk(root)
        return out
    }

    fun contains(url: String): Boolean = nodeByUrl(url) != null

    fun countBookmarks(): Int = list().size

    /** 收藏页面到指定文件夹（默认根目录）。返回 false 表示已存在或链接无效/超上限。 */
    fun add(url: String, title: String, folderId: String? = null): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (contains(url) || countBookmarks() >= MAX_ENTRIES) return false
        val parent = folderId?.let { nodeById(it) }?.takeIf { it.type == NodeType.FOLDER } ?: root
        parent.children.add(
            0,
            BookmarkNode(newId(), NodeType.BOOKMARK, title.ifBlank { url }, url, System.currentTimeMillis())
        )
        persist()
        return true
    }

    /** 移动书签到目标文件夹（null 为根目录）。已在该文件夹或节点无效时返回 false。 */
    fun move(id: String, folderId: String?): Boolean {
        val node = nodeById(id)?.takeIf { it.type == NodeType.BOOKMARK } ?: return false
        val target = if (folderId == null) root else nodeById(folderId)?.takeIf { it.type == NodeType.FOLDER } ?: return false
        val parent = parentOf(id) ?: return false
        if (parent.id == target.id) return false
        parent.children.remove(node)
        target.children.add(0, node)
        persist()
        return true
    }

    /** 新建文件夹（同名同父级复用）。返回 null 表示名称为空。 */
    fun addFolder(parentId: String?, name: String): BookmarkNode? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val parent = parentId?.let { nodeById(it) }?.takeIf { it.type == NodeType.FOLDER } ?: root
        parent.children.firstOrNull { it.type == NodeType.FOLDER && it.title == clean }?.let { return it }
        val folder = BookmarkNode(newId(), NodeType.FOLDER, clean, time = System.currentTimeMillis())
        parent.children.add(folder)
        persist()
        return folder
    }

    fun rename(id: String, title: String): Boolean {
        val clean = title.trim()
        val node = nodeById(id) ?: return false
        if (clean.isEmpty()) return false
        node.title = clean
        persist()
        return true
    }

    /** 修改书签网址。返回 false 表示节点不存在、网址无效或与其它书签重复。 */
    fun updateUrl(id: String, url: String): Boolean {
        val clean = url.trim()
        val node = nodeById(id) ?: return false
        if (node.type != NodeType.BOOKMARK) return false
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return false
        if (list().any { it.url == clean && nodeByUrl(it.url)?.id != id }) return false
        node.url = clean
        persist()
        return true
    }

    fun removeIds(ids: Set<String>) {
        fun prune(n: BookmarkNode) {
            n.children.removeAll { it.id in ids }
            n.children.forEach { prune(it) }
        }
        prune(root)
        persist()
    }

    /** 删除全部书签与文件夹。 */
    fun clearAll() {
        root.children.clear()
        persist()
    }

    // ------------------------------------------------------------------ 导入 / 导出

    /** 导出为 Netscape 书签 HTML（保留文件夹层级，Via / Chrome / Firefox 通用）。 */
    fun exportHtml(): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
        appendChildren(sb, root, 1)
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    private fun appendChildren(sb: StringBuilder, node: BookmarkNode, depth: Int) {
        val ind = "  ".repeat(depth)
        node.children.forEach { c ->
            if (c.type == NodeType.FOLDER) {
                sb.append(ind).append("<DT><H3 ADD_DATE=\"").append(c.time / 1000).append("\">")
                    .append(esc(c.title)).append("</H3>\n")
                sb.append(ind).append("<DL><p>\n")
                appendChildren(sb, c, depth + 1)
                sb.append(ind).append("</DL><p>\n")
            } else {
                sb.append(ind).append("<DT><A HREF=\"").append(esc(c.url))
                    .append("\" ADD_DATE=\"").append(c.time / 1000).append("\">")
                    .append(esc(c.title)).append("</A>\n")
            }
        }
    }

    /**
     * 导入 Netscape 书签 HTML（兼容 Via / Chrome / Firefox 导出）。
     * 保留文件夹层级（同名同父级文件夹合并），书签按 URL 去重、保留文件内顺序。
     * 返回 [新增书签数, 跳过数, 新增文件夹数]。
     */
    fun importHtml(text: String): IntArray {
        val tokenRe = Regex(
            "<DL\\b[^>]*>|</DL>|<H3[^>]*>([\\s\\S]*?)</H3>|<A\\s+[^>]*>([\\s\\S]*?)</A>",
            RegexOption.IGNORE_CASE
        )
        val stack = ArrayDeque<BookmarkNode>()
        stack.addLast(root)
        var pending: BookmarkNode? = null
        var rootDlSeen = false
        val createdFolders = mutableListOf<BookmarkNode>()
        val existingUrls = list().map { it.url }.toHashSet()
        var added = 0
        var skipped = 0
        var total = countBookmarks()
        tokenRe.findAll(text).forEach { m ->
            val t = m.value
            when {
                t.startsWith("<DL", ignoreCase = true) -> {
                    val p = pending
                    if (p != null) {
                        stack.addLast(p)
                        pending = null
                    } else if (!rootDlSeen) {
                        rootDlSeen = true
                    }
                }
                t.startsWith("</DL", ignoreCase = true) -> {
                    if (stack.size > 1) stack.removeLast()
                }
                t.startsWith("<H3", ignoreCase = true) -> {
                    val name = clean(m.groupValues[1]).ifBlank { "未命名文件夹" }
                    val parent = stack.last()
                    val exist = parent.children.firstOrNull { it.type == NodeType.FOLDER && it.title == name }
                    if (exist != null) {
                        pending = exist
                    } else {
                        val folder = BookmarkNode(newId(), NodeType.FOLDER, name, time = System.currentTimeMillis())
                        parent.children.add(folder)
                        createdFolders.add(folder)
                        pending = folder
                    }
                }
                t.startsWith("<A", ignoreCase = true) -> {
                    val href = Regex("HREF\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
                        .find(t)?.groupValues?.get(1) ?: return@forEach
                    val url = unesc(href).trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        skipped++
                        return@forEach
                    }
                    if (url in existingUrls || total >= MAX_ENTRIES) {
                        skipped++
                        return@forEach
                    }
                    val title = clean(m.groupValues[2]).ifBlank { url }
                    val addDate = Regex("ADD_DATE\\s*=\\s*\"(\\d+)\"", RegexOption.IGNORE_CASE)
                        .find(t)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    stack.last().children.add(
                        BookmarkNode(
                            newId(), NodeType.BOOKMARK, title, url,
                            if (addDate > 0) addDate * 1000 else System.currentTimeMillis()
                        )
                    )
                    existingUrls.add(url)
                    added++
                    total++
                }
            }
        }
        // 清理本次新建但最终为空的文件夹（自底向上）
        createdFolders.asReversed().forEach { f ->
            if (f.children.isEmpty()) parentOf(f.id)?.children?.remove(f)
        }
        val folders = createdFolders.count { parentOf(it.id) != null }
        if (added > 0 || folders > 0) persist()
        return intArrayOf(added, skipped, folders)
    }

    // ------------------------------------------------------------------ 内部工具

    private fun find(n: BookmarkNode, pred: (BookmarkNode) -> Boolean): BookmarkNode? {
        if (pred(n)) return n
        n.children.forEach { c -> find(c, pred)?.let { return it } }
        return null
    }

    private fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    private fun persist() {
        prefs.edit().putString("bookmarks_v2", nodeToJson(root).toString()).apply()
        version.value++
    }

    private fun nodeToJson(n: BookmarkNode): JSONObject {
        val arr = JSONArray()
        n.children.forEach { arr.put(nodeToJson(it)) }
        return JSONObject()
            .put("id", n.id)
            .put("k", if (n.type == NodeType.FOLDER) "f" else "b")
            .put("t", n.title)
            .put("u", n.url)
            .put("ts", n.time)
            .put("c", arr)
    }

    private fun nodeFromJson(o: JSONObject): BookmarkNode {
        val n = BookmarkNode(
            o.optString("id").ifBlank { newId() },
            if (o.optString("k") == "f") NodeType.FOLDER else NodeType.BOOKMARK,
            o.optString("t"), o.optString("u"), o.optLong("ts")
        )
        val c = o.optJSONArray("c") ?: JSONArray()
        for (i in 0 until c.length()) n.children.add(nodeFromJson(c.getJSONObject(i)))
        return n
    }

    private fun clean(s: String): String = unesc(s.replace(Regex("<[^>]+>"), "")).trim()

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun unesc(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&")
}