package com.xbta.shellstore.core

/**
 * 执行一条命令时，进程由谁创建。
 *
 * 注意这里只有三种「宿主」，`su -c ...` / `/sdcard/bin/python -c ...` 这类都是
 * **模板**里写全的命令行，不是单独一种宿主——这样用户想加什么执行器就加什么，
 * 不用改代码。
 */
enum class RunHost(val label: String) {
    /** App 自身的 sh（uid 同 App） */
    DIRECT("本机"),

    /** 通过 Shizuku 起的进程（权限等同 adb shell） */
    SHIZUKU("Shizuku"),

    /** 不执行、直接交给 Termux 在后台跑（拿不到输出） */
    TERMUX("交给 Termux")
}

/**
 * 一种「怎么跑」的方案。
 *
 * [template] 是命令行模板，`$command` 会被替换成**已经做过 shell 安全引用**的命令。
 * 例：
 * - `$command`                    → 直接跑
 * - `su -c $command`              → 以 root 跑
 * - `su -c "$command"`            → 同上（占位符两侧有双引号时按双引号内转义）
 * - `/sdcard/bin/python -c $command` → 用指定解释器跑
 */
data class RunProfile(
    val id: String,
    val name: String,
    val host: RunHost = RunHost.DIRECT,
    val template: String = PH,
    val enabled: Boolean = true,
    /** 内置项：名称/宿主/模板可改，但不能删除 */
    val builtin: Boolean = false
) {
    val templateOrPlain: String get() = template.ifBlank { PH }

    /** 「系统 › su -c $command」这种一行摘要，给列表与详情页用 */
    val summary: String get() = host.label + " › " + templateOrPlain

    companion object {
        const val PH = "\$command"

        const val ID_SYS = "sys"
        const val ID_SHIZUKU = "shizuku"
        const val ID_ROOT = "root"
        const val ID_TERMUX = "termux"

        fun sys() = RunProfile(ID_SYS, "系统", RunHost.DIRECT, PH, enabled = true, builtin = true)
        fun shizuku() = RunProfile(ID_SHIZUKU, "Shizuku", RunHost.SHIZUKU, PH, enabled = true, builtin = true)
        fun root() = RunProfile(ID_ROOT, "Root", RunHost.DIRECT, "su -c $PH", enabled = true, builtin = true)

        /** Termux 只是「交出去」，拿不到结果，所以内置但不默认启用 */
        fun termux() = RunProfile(ID_TERMUX, "交给 Termux", RunHost.TERMUX, PH, enabled = false, builtin = true)

        /** 顺序即默认尝试顺序：本机 → Shizuku → Root →（Termux 关着） */
        fun defaults(): List<RunProfile> = listOf(sys(), shizuku(), root(), termux())

        /** 缺字段/老配置时的兜底 */
        fun sanitize(list: List<RunProfile>): List<RunProfile> {
            if (list.isEmpty()) return defaults()
            val seen = mutableSetOf<String>()
            return list.filter { it.id.isNotBlank() && seen.add(it.id) }
                .map { if (it.name.isBlank()) it.copy(name = it.id) else it }
        }
    }
}

/** 模板渲染：把 `$command` 换成 shell 安全的命令串 */
object RunTemplate {

    /**
     * 单引号包裹：`'a'\''b'`。这是 POSIX 下唯一对所有字符都安全的写法，
     * 命令里带 `$`、反引号、换行都不会被外层 shell 提前解释。
     */
    fun quoteSingle(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** 双引号内部转义：把 \ " $ ` 四个字符转义，用于占位符被 `"…"` 包住的情况 */
    fun quoteDoubleInner(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\$", "\\\$")
        .replace("`", "\\`")

    /**
     * 渲染模板。
     *
     * - 模板**就是占位符本身**（`$command`，允许首尾空白）：原样替换，不加任何引号。
     *   这条最关键：结果会交给 `sh -c` 解析，一加引号整条命令就变成一个词，
     *   `sh -c 'getprop ro.product.device'` 会去找名为「getprop ro.product.device」的程序 → 127。
     * - 模板里没有占位符：原样返回
     * - 占位符紧邻双引号（`su -c "$command"`）：按双引号内转义
     * - 其余情况（命令是别的程序的参数，如 `su -c $command`、`python -c $command`）：单引号包裹
     */
    fun render(template: String, cmd: String): String {
        val ph = RunProfile.PH
        val t = template.ifBlank { ph }
        if (t.trim() == ph) return cmd
        if (!t.contains(ph)) return t

        val sb = StringBuilder()
        var i = 0
        while (true) {
            val j = t.indexOf(ph, i)
            if (j < 0) {
                sb.append(t, i, t.length)
                break
            }
            sb.append(t, i, j)
            val before = if (j > 0) t[j - 1] else ' '
            val afterIdx = j + ph.length
            val after = if (afterIdx < t.length) t[afterIdx] else ' '
            sb.append(
                if (before == '"' && after == '"') quoteDoubleInner(cmd)
                else quoteSingle(cmd)
            )
            i = afterIdx
        }
        return sb.toString()
    }
}
