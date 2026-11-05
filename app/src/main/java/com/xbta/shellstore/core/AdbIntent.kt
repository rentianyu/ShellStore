package com.xbta.shellstore.core

/**
 * 解析 `am start / am broadcast / am startservice` 形式的 adb 命令，拆成 Intent 的四个要素。
 *
 * 为什么需要：商店里的很多条目（尤其是 scheme 跳转）在源文件里是用
 * `am start -a android.intent.action.VIEW -d "xxx://"` 写的，或者干脆带个 `@adb`。
 * 导出成独立 App 时若把这整串当成 URL 塞给 `Uri.parse()`，必然打不开；
 * 塞给 shell 又要额外一层权限 —— 用户要的是「点开即跳转」的最简路径，
 * 所以这里把命令解析成结构化参数，导出时直接走 `Intent`，不经过任何 shell。
 *
 * 纯 Kotlin、无 android 依赖，可用 kotlinc 在桌面验证。
 */
object AdbIntent {

    data class Parsed(
        val verb: String = "start",       // start / broadcast / startservice
        val action: String = "",
        val data: String = "",
        val component: String = "",
        val packageName: String = "",
        val mime: String = "",
        val categories: List<String> = emptyList(),
        /** k=v 每行一个 */
        val extras: String = ""
    ) {
        val ok: Boolean get() = action.isNotBlank() || data.isNotBlank() || component.isNotBlank() || packageName.isNotBlank()
    }

    /** 看起来是一条命令（而不是一个 URI） */
    fun looksLikeCommand(s: String): Boolean {
        val t = s.trimStart()
        if (t.isEmpty()) return false
        val re = Regex("^(adb\\s+(shell\\s+)?)?(am|app_process)\\s")
        if (re.containsMatchIn(t)) return true
        if (t.startsWith("sh -c") || t.startsWith("su -c") || t.startsWith("bash -c")) return true
        // 纯 URI 一定没有空格；有空格又不是命令的，交给调用方自己判断
        return false
    }

    /** 解析失败返回 null（不是 am 命令，或没有可用的 Intent 要素） */
    fun parse(raw: String): Parsed? {
        val tokens = tokenize(stripWrapper(raw))
        val i = tokens.indexOfFirst { it == "am" }
        if (i < 0 || i + 1 >= tokens.size) return null
        val verb = when (tokens[i + 1]) {
            "broadcast" -> "broadcast"
            "startservice", "start-service" -> "startservice"
            "start", "start-activity" -> "start"
            else -> return null
        }
        var action = ""
        var data = ""
        var component = ""
        var pkg = ""
        var mime = ""
        val cats = mutableListOf<String>()
        val extras = mutableListOf<String>()

        var k = i + 2
        while (k < tokens.size) {
            val t = tokens[k]
            when (t) {
                "-a", "--action" -> { action = tokens.getOrElse(++k) { "" }; k++ }
                "-d", "--data", "-d-uri" -> { data = tokens.getOrElse(++k) { "" }; k++ }
                "-n", "--component" -> { component = tokens.getOrElse(++k) { "" }; k++ }
                "-p", "--package" -> { pkg = tokens.getOrElse(++k) { "" }; k++ }
                "-t", "--mime", "--type" -> { mime = tokens.getOrElse(++k) { "" }; k++ }
                "-c", "--category" -> { tokens.getOrElse(++k) { "" }.let { if (it.isNotBlank()) cats.add(it) }; k++ }
                "-f", "--flags", "--flag", "-W", "-P", "--user", "--esn", "-S" -> { k += 2 }
                "--es", "--ez", "--ei", "--el", "--ef", "--eu", "--ecn", "--eia", "--eial", "--ela" -> {
                    val key = tokens.getOrElse(k + 1) { "" }
                    val value = tokens.getOrElse(k + 2) { "" }
                    if (key.isNotBlank()) extras.add("$key=$value")
                    k += 3
                }
                else -> k++
            }
        }
        val parsed = Parsed(
            verb = verb,
            action = action,
            data = data,
            component = component,
            packageName = pkg,
            mime = mime,
            categories = cats,
            extras = extras.joinToString("\n")
        )
        return if (parsed.ok) parsed else null
    }

    /**
     * 按目标动作类型做规范化：把「一条 am 命令」拆成各个字段，保证导出的 App
     * 直接用 Intent 打开，不经过任何 shell。
     *
     *  - url：value = URI（从命令里抠 -d）
     *  - activity：value = URI，action / component 各自单独给
     *  - broadcast：value = 广播 action，data 单独给
     *  - service：value = 组件名，data / action 单独给
     *
     * 不是命令时原样返回（只 trim）—— 用户手填的纯 URI / 纯命令不受影响。
     */
    fun normalize(
        type: String,
        value: String,
        action: String = "",
        component: String = "",
        extras: String = "",
        data: String = ""
    ): Normalized {
        val v = value.trim()
        val act = action.trim()
        val comp = component.trim()
        val dat = data.trim()
        val p = if (looksLikeCommand(v)) parse(v) else null
        if (p == null) return Normalized(v, act, comp, dat, extras)
        return when (type) {
            ExportSpec.TYPE_URL -> Normalized(p.data.ifBlank { v }, "", "", "", extras)
            ExportSpec.TYPE_BROADCAST -> Normalized(
                p.action.ifBlank { v }, "", comp, dat.ifBlank { p.data },
                extras.ifBlank { p.extras }
            )
            ExportSpec.TYPE_SERVICE -> Normalized(
                p.component.ifBlank { v }, act.ifBlank { p.action }, comp.ifBlank { p.component },
                dat.ifBlank { p.data }, extras.ifBlank { p.extras }
            )
            ExportSpec.TYPE_ACTIVITY -> Normalized(
                p.data.ifBlank { if (v.contains("://") || v.contains(":")) v else "" },
                act.ifBlank { p.action },
                comp.ifBlank { p.component },
                dat,
                extras.ifBlank { p.extras }
            )
            // shell 类型不该被改：命令原样跑
            else -> Normalized(v, act, comp, dat, extras)
        }
    }

    data class Normalized(
        val value: String,
        val action: String,
        val component: String,
        val data: String,
        val extras: String
    )

    // ---------------- 内部 ----------------

    /** 去掉 `adb shell` / `sh -c` 这类外壳与整体引号 */
    private fun stripWrapper(s: String): String {
        var t = s.trim()
        for (p in listOf("adb shell ", "adb ", "sh -c ", "su -c ", "bash -c ")) {
            if (t.startsWith(p)) t = t.removePrefix(p).trim()
        }
        if (t.length >= 2 &&
            ((t.startsWith("'") && t.endsWith("'")) || (t.startsWith("\"") && t.endsWith("\"")))
        ) {
            t = t.substring(1, t.length - 1).trim()
        }
        return t
    }

    /** 按空格切分，支持单引号与双引号包裹 */
    private fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quote = ' '
        for (c in s) {
            when {
                quote != ' ' -> if (c == quote) quote = ' ' else sb.append(c)
                c == '\'' || c == '"' -> quote = c
                c.isWhitespace() -> if (sb.isNotEmpty()) {
                    out.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}
