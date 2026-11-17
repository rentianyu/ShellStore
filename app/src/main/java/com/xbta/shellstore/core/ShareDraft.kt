package com.xbta.shellstore.core

/**
 * 从「分享到 ShellStore」进来的文本 → 一条待确认的新条目草稿。
 *
 * 只做最基本的猜测，标题/类型都能在编辑器里改；目标是省掉
 * 「复制 → 切 App → 新建 → 粘贴」这几步。
 */
object ShareDraft {

    /** 常见前缀：教程里复制出来的命令多半带这些 */
    private val STRIP_PREFIX = listOf("adb shell", "$ ", "# ", "> ")

    private val SCHEME_RE = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")

    fun normalize(raw: String): String {
        var t = raw.trim().replace("\r\n", "\n")
        for (p in STRIP_PREFIX) {
            if (t.startsWith(p)) {
                t = t.removePrefix(p).trim()
                break
            }
        }
        return t
    }

    /** 猜测类型：单行、以 scheme:// 开头、且不是 am/pm 之类命令 → url scheme */
    fun guessType(text: String): EntryType {
        val oneLine = text.lineSequence().count { it.isNotBlank() } == 1
        val first = text.trim()
        return if (oneLine && SCHEME_RE.containsMatchIn(first) &&
            !first.startsWith("am ") && !first.startsWith("pm ")
        ) EntryType.URLSCHEME else EntryType.SHELL
    }

    fun build(raw: String): Entry? {
        val text = normalize(raw)
        if (text.isBlank()) return null
        val first = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val title = first.take(30).ifBlank { "分享导入" }
        return Entry(
            id = "",
            type = guessType(text),
            title = title,
            body = text,
            origin = "分享导入"
        )
    }
}
