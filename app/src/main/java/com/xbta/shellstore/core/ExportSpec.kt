package com.xbta.shellstore.core

import java.util.Random

/**
 * 导出一个 APK 的全部可配置项。纯数据，不依赖 android，方便桌面测试。
 *
 * 对应「导出 APK」表单里的六项：名称、包名、图标、动作类型、详细内容、执行身份。
 */
data class ExportSpec(
    /** 应用显示名称（manifest 的 android:label） */
    val title: String = "Shell 命令",
    /** 留空表示自动生成 */
    val packageName: String = "",
    /** shell / url / activity / broadcast / service */
    val type: String = TYPE_SHELL,
    /** 命令、URL、action 或组件名，按 type 解释 */
    val value: String = "",
    /** 可选 intent action */
    val action: String = "",
    /** 可选 "pkg/.Cls" */
    val component: String = "",
    /** 可选，k=v 每行一个 */
    val extras: String = "",
    /** shell / root / shizuku */
    val exec: String = EXEC_SHELL,
    val showOutput: Boolean = true,
    val timeoutSec: Int = 30,
    /** 图标上的文字（空则取名称首字）；纯文字图标用 */
    val iconText: String = "",
    /** 用户选的图标 PNG；为 null 时按文字自动生成 */
    val iconPng: ByteArray? = null
) {
    companion object {
        const val TYPE_SHELL = "shell"
        const val TYPE_URL = "url"
        const val TYPE_ACTIVITY = "activity"
        const val TYPE_BROADCAST = "broadcast"
        const val TYPE_SERVICE = "service"

        const val EXEC_SHELL = "shell"
        const val EXEC_ROOT = "root"
        const val EXEC_SHIZUKU = "shizuku"

        val TYPES = listOf(TYPE_SHELL, TYPE_URL, TYPE_ACTIVITY, TYPE_BROADCAST, TYPE_SERVICE)
        val EXECS = listOf(EXEC_SHELL, EXEC_ROOT, EXEC_SHIZUKU)

        fun typeLabel(t: String): String = when (t) {
            TYPE_SHELL -> "Shell 命令"
            TYPE_URL -> "打开链接"
            TYPE_ACTIVITY -> "启动界面"
            TYPE_BROADCAST -> "发送广播"
            TYPE_SERVICE -> "启动服务"
            else -> t
        }

        fun execLabel(e: String): String = when (e) {
            EXEC_SHELL -> "普通 shell"
            EXEC_ROOT -> "Root"
            EXEC_SHIZUKU -> "Shizuku"
            else -> e
        }
    }
}

/** 包名的校验与自动生成 */
object PackageRules {

    /** 自动生成的前缀，与商店本体区分开 */
    private const val PREFIX = "com.xbta.exp."

    private const val HEX = "0123456789abcdef"

    /**
     * 生成一个合法包名：`com.xbta.exp.a` + 7 位十六进制。
     * 末段以字母开头（Java 包名规范：每段不能用数字开头）。
     */
    fun auto(rand: Random = Random()): String {
        val sb = StringBuilder(PREFIX).append('a')
        repeat(7) { sb.append(HEX[rand.nextInt(HEX.length)]) }
        return sb.toString()
    }

    /** 返回 null 表示合法，否则是可读原因 */
    fun validate(pkg: String): String? {
        val p = pkg.trim()
        if (p.isEmpty()) return null                       // 空 = 自动生成
        if (p.length > 100) return "包名太长（最多 100 个字符）"
        if (!p.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))) {
            return "需字母开头、至少两段、点分隔，例如 com.example.myapp"
        }
        return null
    }
}
