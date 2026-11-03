package com.xbta.shellstore.core

/**
 * WebDAV 的地址拼接与错误码文案。
 *
 * 单独放在 core 里，是为了能脱离 Android 环境编译与自测：
 * 这类「拼错一个斜杠就 404」的逻辑，靠肉眼审查最不可靠。
 */
object WebDavPath {

    /** 去掉尾部斜杠；协议头缺失时按 https 补。返回空串表示没填 */
    fun normalizeBase(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
        while (s.length > 8 && s.endsWith("/")) s = s.dropLast(1)
        return s
    }

    /** 路径归一：去前导/尾随斜杠、合并重复斜杠、丢掉 . 与 .. */
    fun normalizePath(raw: String): String =
        raw.trim().split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")

    fun fileUrl(base: String, path: String): String =
        "${normalizeBase(base)}/${normalizePath(path)}"

    /**
     * 需要逐级 MKCOL 的目录地址（不含文件名本身）。
     * 例：base=https://d/a，path=shell/store/b.json → [https://d/a/shell, https://d/a/shell/store]
     */
    fun dirUrls(base: String, path: String): List<String> {
        val parts = normalizePath(path).split('/').filter { it.isNotBlank() }
        if (parts.size <= 1) return emptyList()
        val b = normalizeBase(base)
        val out = mutableListOf<String>()
        val acc = StringBuilder()
        for (i in 0 until parts.size - 1) {
            if (i > 0) acc.append('/')
            acc.append(parts[i])
            out.add("$b/$acc")
        }
        return out
    }

    /** HTTP 码 → 人能读的话 */
    fun explain(code: Int): String = when (code) {
        0 -> "网络异常"
        400 -> "请求被服务端拒绝"
        401 -> "认证失败（账号或密码不对）"
        403 -> "无权限（检查账号对该目录的写权限）"
        404 -> "路径不存在"
        405 -> "服务端不接受该操作（可能不是 WebDAV 地址）"
        409 -> "上级目录不存在"
        423 -> "文件被锁定"
        507 -> "云端空间不足"
        in 500..599 -> "服务端错误 $code"
        else -> "HTTP $code"
    }
}
