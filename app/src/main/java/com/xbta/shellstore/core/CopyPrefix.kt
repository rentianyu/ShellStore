package com.xbta.shellstore.core

/**
 * 复制前缀：把一条命令复制成「能贴到别处直接执行」的样子。
 *
 * 与「运行方式」是两件事：
 * - 运行方式决定**在 App 里**由谁执行（系统 / Shizuku / Root / Termux）；
 * - 复制前缀决定**粘出去**的文本长什么样（贴到电脑终端、贴到聊天框……）。
 *
 * 所以这里是一份独立清单：默认给「无 / adb shell / su -c」三档（和 2.19 的行为一致），
 * 想加「adb -s 123456 shell」「termux-exec」这类就自己添一条，模板可写 `$command` 占位符。
 */
data class CopyPrefixItem(
    val id: String,
    val name: String,
    /** 前缀模板；空串 = 原样复制。`$command` 会被替换成命令本身 */
    val template: String = "",
    /** 内置项：名称和模板都能改，但不能删 */
    val builtin: Boolean = false
) {
    /** 空模板，或者模板就是占位符本身 → 不加任何前缀 */
    val isPlain: Boolean get() = template.isBlank() || template.trim() == CopyPrefixes.PH

    /** 给列表一行预览用 */
    val summary: String get() = if (isPlain) "原样复制" else template.trim()
}

object CopyPrefixes {

    const val PH = "\$command"

    const val ID_NONE = "none"
    const val ID_ADB = "adb"
    const val ID_SU = "su"

    /** 编辑弹窗里做实时预览用的示例命令 */
    const val EXAMPLE = "pm list packages"

    /** 默认三档，与 2.19 及更早版本的「无 / adb shell / su -c」完全对应 */
    fun defaults(): List<CopyPrefixItem> = listOf(
        CopyPrefixItem(ID_NONE, "无", "", builtin = true),
        CopyPrefixItem(ID_ADB, "adb shell", "adb shell $PH", builtin = true),
        CopyPrefixItem(ID_SU, "su -c", "su -c $PH", builtin = true)
    )

    /** 读盘/导入备份后的清洗：去空 id、去重、trim 模板；空清单回退默认 */
    fun sanitize(list: List<CopyPrefixItem>?): List<CopyPrefixItem> {
        val l = list.orEmpty()
            .filter { it.id.isNotBlank() }
            .map { it.copy(name = it.name.trim().ifBlank { it.id }, template = it.template.trim()) }
            .distinctBy { it.id }
        return l.ifEmpty { defaults() }
    }

    /** 取当前选中的那一项；id 失效（比如备份来自别的设备）时回退到内置「无」 */
    fun pick(list: List<CopyPrefixItem>, id: String): CopyPrefixItem? =
        list.firstOrNull { it.id == id }
            ?: list.firstOrNull { it.id == ID_NONE }

    /** 把命令渲染成带前缀的文本 */
    fun render(item: CopyPrefixItem?, cmd: String): String {
        if (item == null) return cmd
        val t = item.template.trim()
        if (t.isEmpty() || t == PH) return cmd
        // 模板里没写占位符：当成纯前缀，命令接在后面（如 "adb -s 123 shell"）
        if (!t.contains(PH)) return "$t $cmd"
        // 有占位符：交给 RunTemplate 做 shell 安全引用（单引号 / 双引号内转义）
        return RunTemplate.render(t, cmd)
    }

    /** 老版本存的是枚举名（NONE / ADB_SHELL / SU），迁移到新 id */
    fun fromLegacy(name: String): String = when (name.trim().uppercase()) {
        "ADB_SHELL", "ADB", "ADB SHELL" -> ID_ADB
        "SU", "ROOT", "SU -C" -> ID_SU
        else -> ID_NONE
    }

    /**
     * 从一种运行方式推一条前缀。
     * Termux 是「交出去执行」、复制成文本没有对应形态；系统（无前缀）也没必要单列 → 返回 null。
     */
    fun fromRunProfile(p: RunProfile): CopyPrefixItem? {
        if (p.host == RunHost.TERMUX) return null
        val t = if (p.host == RunHost.SHIZUKU) "adb shell $PH" else p.templateOrPlain
        if (t.trim() == PH) return null
        return CopyPrefixItem(id = "run_" + p.id, name = p.name, template = t)
    }

    /** 一键从「运行方式」补齐：只加当前清单里没有的（按 id 与模板双重去重） */
    fun syncFromRunProfiles(
        current: List<CopyPrefixItem>,
        profiles: List<RunProfile>
    ): List<CopyPrefixItem> {
        val ids = current.map { it.id }.toMutableSet()
        val tpls = current.map { it.template.trim() }.toMutableSet()
        val out = current.toMutableList()
        profiles.forEach { p ->
            val item = fromRunProfile(p) ?: return@forEach
            if (!ids.add(item.id)) return@forEach
            if (!tpls.add(item.template.trim())) return@forEach
            out += item
        }
        return out
    }
}
